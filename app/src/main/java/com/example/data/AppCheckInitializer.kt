package com.example.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProvider
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.AppCheckToken
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.internal.DefaultFirebaseAppCheck
import com.google.firebase.appcheck.recaptcha.internal.RecaptchaAppCheckProvider
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

private const val TAG = "AppCheckInit"

// Mandatory App Check contract keys — do not rename
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val TOKEN_CACHE_STORE_PREFIX = "com.google.firebase.appcheck.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val HARNESS_INJECTED_SECRET_KEY = "aistudio.harness.INJECTED_DEBUG_SECRET"
private const val NOVA_META_PREFS = "com.aistudio.novaai.appcheck.meta"
private const val NOVA_ACTIVE_PROVIDER_KEY = "active_provider_mode"
private const val MODE_DEBUG_HARNESS = "debug_harness"
private const val MODE_RECAPTCHA = "recaptcha_enterprise"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

/**
 * Self-healing reCAPTCHA Enterprise AppCheckProviderFactory.
 * Unlike the default singleton provider (which caches a failed RecaptchaTasksClient Task forever
 * if cold-start mobile connectivity hiccups), this provider recreates a fresh
 * RecaptchaAppCheckProvider whenever token attestation fails.
 */
private class ResilientRecaptchaProviderFactory(
    private val siteKey: String
) : AppCheckProviderFactory {
    override fun create(firebaseApp: FirebaseApp): AppCheckProvider {
        return ResilientRecaptchaProvider(firebaseApp, siteKey)
    }
}

private class ResilientRecaptchaProvider(
    private val firebaseApp: FirebaseApp,
    private val siteKey: String
) : AppCheckProvider {

    private val liteExecutor = Executors.newFixedThreadPool(2)
    private val blockingExecutor = Executors.newCachedThreadPool()
    private val lock = Any()

    @Volatile
    private var delegate: RecaptchaAppCheckProvider = buildFreshDelegate()

    private fun buildFreshDelegate(): RecaptchaAppCheckProvider {
        return RecaptchaAppCheckProvider(
            firebaseApp,
            siteKey,
            liteExecutor,
            blockingExecutor
        ).also { provider ->
            runCatching { provider.initializeRecaptchaClient() }
        }
    }

    private fun rotateDelegate(): RecaptchaAppCheckProvider {
        synchronized(lock) {
            val fresh = buildFreshDelegate()
            delegate = fresh
            return fresh
        }
    }

    override fun getToken(): Task<AppCheckToken> {
        val current = delegate
        return current.token.continueWithTask(liteExecutor) { task ->
            if (task.isSuccessful) {
                task
            } else {
                Log.w(TAG, "reCAPTCHA token fetch failed; rotating client and retrying once", task.exception)
                rotateDelegate().token
            }
        }
    }

    override fun getLimitedUseToken(): Task<AppCheckToken> {
        val current = delegate
        return current.limitedUseToken.continueWithTask(liteExecutor) { task ->
            if (task.isSuccessful) {
                task
            } else {
                Log.w(TAG, "reCAPTCHA limited-use token failed; rotating client and retrying once", task.exception)
                rotateDelegate().limitedUseToken
            }
        }
    }
}

/**
 * Coordinates App Check token pre-warming and recovery so Firebase AI Logic's internal
 * 10-second AppCheckHeaderProvider always receives an already-valid cached token instead
 * of timing out on cold mobile networks or sending DUMMY_TOKEN.
 */
object AppCheckTokenCoordinator {
    private val prewarmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile
    private var activeFactory: AppCheckProviderFactory? = null

    @Volatile
    private var hasWarmValidToken: Boolean = false

    internal fun onFactoryInstalled(factory: AppCheckProviderFactory, resetWarmFlag: Boolean) {
        activeFactory = factory
        if (resetWarmFlag) {
            hasWarmValidToken = false
        }
    }

    fun prewarmInBackground() {
        if (isRobolectricEnvironment()) return
        prewarmScope.launch {
            ensureValidToken(forceRefresh = false)
        }
    }

    suspend fun ensureValidToken(forceRefresh: Boolean = false) {
        if (isRobolectricEnvironment()) return
        if (!forceRefresh && hasWarmValidToken) return

        mutex.withLock {
            if (!forceRefresh && hasWarmValidToken) return
            try {
                val token = withTimeout(22_000L) {
                    FirebaseAppCheck.getInstance().getAppCheckToken(forceRefresh).await()
                }
                if (token.token.isNotBlank()) {
                    hasWarmValidToken = true
                    Log.d(TAG, "App Check token verified and cached.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Initial App Check pre-warm warning: ${e.message}")
            }
        }
    }

    suspend fun recoverAndRefreshToken() {
        if (isRobolectricEnvironment()) return
        mutex.withLock {
            hasWarmValidToken = false
            try {
                val appCheck = FirebaseAppCheck.getInstance()
                val factory = activeFactory
                if (factory != null && appCheck is DefaultFirebaseAppCheck) {
                    // Evict any poisoned in-memory or disk token and re-bind the active factory
                    appCheck.resetAppCheckState()
                    appCheck.installAppCheckProviderFactory(factory)
                }
                val token = withTimeout(25_000L) {
                    appCheck.getAppCheckToken(true).await()
                }
                if (token.token.isNotBlank()) {
                    hasWarmValidToken = true
                    Log.d(TAG, "App Check token recovered successfully.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "App Check recovery failed: ${e.message}", e)
            }
        }
    }

    private fun isRobolectricEnvironment(): Boolean {
        return Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
    }
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val appContext = context.applicationContext
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val debugPrefs = appContext.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        val tokenCachePrefs = appContext.getSharedPreferences(
            "$TOKEN_CACHE_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        val metaPrefs = appContext.getSharedPreferences(
            NOVA_META_PREFS,
            Context.MODE_PRIVATE
        )
        val previousMode = metaPrefs.getString(NOVA_ACTIVE_PROVIDER_KEY, null)

        if (BuildConfig.DEBUG) {
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            val savedHarnessToken = debugPrefs.getString(HARNESS_INJECTED_SECRET_KEY, null)

            if (!debugToken.isNullOrEmpty()) {
                // Path 1: Launched inside AI Studio Emulator / CI with registered harness debug token
                val modeOrTokenChanged = previousMode != MODE_DEBUG_HARNESS || savedHarnessToken != debugToken
                if (modeOrTokenChanged) {
                    resetStaleAppCheckCache(tokenCachePrefs)
                }
                // CRITICAL: synchronous .commit() per App Check contract
                debugPrefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .putString(HARNESS_INJECTED_SECRET_KEY, debugToken)
                    .commit()
                metaPrefs.edit()
                    .putString(NOVA_ACTIVE_PROVIDER_KEY, MODE_DEBUG_HARNESS)
                    .commit()

                val factory = DebugAppCheckProviderFactory.getInstance()
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
                AppCheckTokenCoordinator.onFactoryInstalled(factory, resetWarmFlag = modeOrTokenChanged)
                AppCheckTokenCoordinator.prewarmInBackground()
                Log.d(TAG, "App Check debug secret configured from launch Intent.")
            } else if (!savedHarnessToken.isNullOrEmpty()) {
                // Path 2: Re-opened from Recents inside AI Studio Emulator
                val modeChanged = previousMode != MODE_DEBUG_HARNESS
                if (modeChanged) {
                    resetStaleAppCheckCache(tokenCachePrefs)
                }
                debugPrefs.edit()
                    .putString(DEBUG_SECRET_KEY, savedHarnessToken)
                    .commit()
                metaPrefs.edit()
                    .putString(NOVA_ACTIVE_PROVIDER_KEY, MODE_DEBUG_HARNESS)
                    .commit()

                val factory = DebugAppCheckProviderFactory.getInstance()
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
                AppCheckTokenCoordinator.onFactoryInstalled(factory, resetWarmFlag = modeChanged)
                AppCheckTokenCoordinator.prewarmInBackground()
                Log.d(TAG, "App Check restored previously injected harness debug secret.")
            } else {
                // Path 3: Sideloaded Debug APK on a physical Android phone (no harness debug token).
                // Purge any unregistered self-generated debug secret and use resilient reCAPTCHA Enterprise.
                val hadDebugSecret = debugPrefs.contains(DEBUG_SECRET_KEY)
                if (hadDebugSecret || previousMode != MODE_RECAPTCHA) {
                    debugPrefs.edit().remove(DEBUG_SECRET_KEY).commit()
                    resetStaleAppCheckCache(tokenCachePrefs)
                    metaPrefs.edit()
                        .putString(NOVA_ACTIVE_PROVIDER_KEY, MODE_RECAPTCHA)
                        .commit()
                }
                if (!installResilientRecaptchaProvider(appContext, resetWarmFlag = hadDebugSecret || previousMode != MODE_RECAPTCHA)) {
                    val fallback = DebugAppCheckProviderFactory.getInstance()
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(fallback)
                    AppCheckTokenCoordinator.onFactoryInstalled(fallback, resetWarmFlag = true)
                    Log.w(TAG, "Fallback DebugAppCheckProviderFactory installed (no recaptcha_site_key).")
                }
            }
        } else {
            // Release APK on physical Android device
            val modeChanged = previousMode != MODE_RECAPTCHA
            if (modeChanged || debugPrefs.contains(DEBUG_SECRET_KEY)) {
                debugPrefs.edit().remove(DEBUG_SECRET_KEY).commit()
                resetStaleAppCheckCache(tokenCachePrefs)
                metaPrefs.edit()
                    .putString(NOVA_ACTIVE_PROVIDER_KEY, MODE_RECAPTCHA)
                    .commit()
            }
            installResilientRecaptchaProvider(appContext, resetWarmFlag = modeChanged)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

private fun resetStaleAppCheckCache(tokenCachePrefs: android.content.SharedPreferences) {
    runCatching {
        (FirebaseAppCheck.getInstance() as? DefaultFirebaseAppCheck)?.resetAppCheckState()
    }
    tokenCachePrefs.edit().clear().commit()
}

private fun installResilientRecaptchaProvider(
    context: Context,
    resetWarmFlag: Boolean
): Boolean {
    val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
    if (resId != 0) {
        val siteKey = context.getString(resId)
        if (siteKey.isNotBlank()) {
            val factory = ResilientRecaptchaProviderFactory(siteKey)
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
            AppCheckTokenCoordinator.onFactoryInstalled(factory, resetWarmFlag = resetWarmFlag)
            AppCheckTokenCoordinator.prewarmInBackground()
            Log.d(TAG, "ResilientRecaptchaProviderFactory installed and pre-warming.")
            return true
        } else {
            Log.e(TAG, "recaptcha_site_key resource string is blank.")
        }
    } else {
        Log.e(TAG, "recaptcha_site_key string resource not found in package.")
    }
    return false
}
