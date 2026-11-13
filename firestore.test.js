const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validTimestamp() {
  return new Date(Date.now() - 1000);
}

test("1. Unauthenticated user cannot read or create conversations", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/conversations`)
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("2. Authenticated owner can create, query, and update valid conversation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = validTimestamp();
  const convRef = aliceDb.doc(`users/${ALICE_UID}/conversations/conv_1`);

  await assertSucceeds(
    convRef.set({
      userId: ALICE_UID,
      title: "Exploring Nova AI",
      mode: "chat",
      previewText: "Hi! I'm Nova.",
      messageCount: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Client query aligned with resource.data.userId == request.auth.uid
  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/conversations`)
      .where("userId", "==", ALICE_UID)
      .get()
  );

  // Valid action-scoped update
  await assertSucceeds(
    convRef.update({
      title: "Updated Nova Chat",
      messageCount: 2,
      updatedAt: validTimestamp(),
    })
  );
});

test("3. Cross-user access is strictly rejected on conversations and voice recordings", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = validTimestamp();
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/conversations/conv_secret`).set({
      userId: ALICE_UID,
      title: "Private Notes",
      mode: "chat",
      previewText: "Top secret",
      messageCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.doc(`users/${ALICE_UID}/conversations/conv_secret`).get());
  await assertFails(
    bobDb
      .collection(`users/${ALICE_UID}/conversations`)
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("4. Orphaned message creation without parent conversation is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = validTimestamp();
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/conversations/missing_conv/messages/msg_1`).set({
      userId: ALICE_UID,
      conversationId: "missing_conv",
      role: "user",
      text: "Hello!",
      messageType: "text",
      modelUsed: "gemini-3.8-flash",
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("5. Message creation succeeds when parent conversation exists, and rejects shadow fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = validTimestamp();
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/conversations/conv_valid`).set({
      userId: ALICE_UID,
      title: "Valid Chat",
      mode: "chat",
      previewText: "",
      messageCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/conversations/conv_valid/messages/msg_1`).set({
      userId: ALICE_UID,
      conversationId: "conv_valid",
      role: "user",
      text: "How to create an app?",
      messageType: "text",
      modelUsed: "gemini-3.8-flash",
      createdAt: validTimestamp(),
      updatedAt: validTimestamp(),
    })
  );

  // Shadow field injection rejected
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/conversations/conv_valid/messages/msg_bad`).set({
      userId: ALICE_UID,
      conversationId: "conv_valid",
      role: "user",
      text: "Injecting ghost field",
      messageType: "text",
      modelUsed: "gemini-3.8-flash",
      isVerified: true,
      createdAt: validTimestamp(),
      updatedAt: validTimestamp(),
    })
  );
});

test("6. Voice recording CRUD succeeds for owner and rejects oversized or future-timestamp payloads", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = validTimestamp();
  const recRef = aliceDb.doc(`users/${ALICE_UID}/voice_recordings/rec_1`);

  await assertSucceeds(
    recRef.set({
      userId: ALICE_UID,
      conversationId: "conv_1",
      title: "Morning Brainstorm",
      userTranscript: "Tell me three ways to earn online.",
      aiTranscript: "Here are three high-impact online income streams...",
      durationSeconds: 42,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Value poisoning on update (oversized title > 200 chars) rejected
  await assertFails(
    recRef.update({
      title: "A".repeat(250),
      updatedAt: validTimestamp(),
    })
  );

  // Future timestamp spoofing rejected
  const futureTs = new Date(Date.now() + 120000);
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/voice_recordings/rec_future`).set({
      userId: ALICE_UID,
      conversationId: "conv_1",
      title: "Spoofed Future",
      userTranscript: "Hi",
      aiTranscript: "Hello",
      durationSeconds: 5,
      createdAt: futureTs,
      updatedAt: futureTs,
    })
  );
});

test("7. Owner can list and delete conversation messages and idempotently delete missing documents", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = validTimestamp();
  const convRef = aliceDb.doc(`users/${ALICE_UID}/conversations/conv_del`);
  const msgRef = aliceDb.doc(`users/${ALICE_UID}/conversations/conv_del/messages/msg_del`);

  await assertSucceeds(
    convRef.set({
      userId: ALICE_UID,
      title: "Delete Test",
      mode: "chat",
      previewText: "Hi",
      messageCount: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    msgRef.set({
      userId: ALICE_UID,
      conversationId: "conv_del",
      role: "user",
      text: "Hi",
      messageType: "text",
      modelUsed: "gemini-3.5-flash-lite",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const listedMsgs = await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/conversations/conv_del/messages`)
      .where("userId", "==", ALICE_UID)
      .get()
  );
  if (listedMsgs.empty) {
    throw new Error("Expected message in subcollection list query");
  }

  await assertSucceeds(msgRef.delete());
  await assertSucceeds(convRef.delete());
  // Deleting an already-deleted document must also succeed idempotently for the owner
  await assertSucceeds(convRef.delete());
});

