/**
 * Nova AI — Secure Serverless Proxy for GitHub Pages (Cloudflare Workers)
 *
 * Keeps your GEMINI_API_KEY completely hidden in Cloudflare Secrets (never on GitHub)
 * so visitors to your website can chat and use Voice Mode directly with NO key prompt.
 *
 * Quick 2-minute setup (100% free tier):
 * 1. Go to https://dash.cloudflare.com -> Workers & Pages -> Create Worker.
 * 2. Paste this file and click Deploy.
 * 3. Open Worker Settings -> Variables and Secrets -> Add Secret named: GEMINI_API_KEY
 * 4. Copy your Worker URL (e.g. https://nova-ai-proxy.yourname.workers.dev) into
 *    NOVA_PROXY_URL inside index.html and push to GitHub.
 */

const ALLOWED_MODELS = new Set([
  "gemini-2.5-flash",
  "gemini-2.5-flash-lite",
  "gemini-2.5-flash-preview-tts",
  "gemini-3.5-flash-lite",
  "gemini-3.1-flash-tts-preview",
]);

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    if (request.method !== "POST") {
      return Response.json(
        { error: { message: "Method not allowed. Use POST." } },
        { status: 405, headers: CORS_HEADERS }
      );
    }

    if (!env.GEMINI_API_KEY) {
      return Response.json(
        { error: { message: "Proxy missing GEMINI_API_KEY secret." } },
        { status: 500, headers: CORS_HEADERS }
      );
    }

    try {
      const body = await request.json();
      const action = body?.action === "tts" ? "tts" : "chat";
      const requestedModel = String(body?.model || "gemini-2.5-flash");
      const model = ALLOWED_MODELS.has(requestedModel)
        ? requestedModel
        : action === "tts"
          ? "gemini-2.5-flash-preview-tts"
          : "gemini-2.5-flash";

      const endpoint =
        action === "tts"
          ? `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`
          : `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:streamGenerateContent?alt=sse`;

      const upstream = await fetch(endpoint, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-goog-api-key": env.GEMINI_API_KEY,
        },
        body: JSON.stringify(body?.payload || {}),
      });

      const headers = new Headers(CORS_HEADERS);
      headers.set(
        "Content-Type",
        action === "tts"
          ? "application/json; charset=utf-8"
          : upstream.headers.get("Content-Type") || "text/event-stream; charset=utf-8"
      );
      headers.set("Cache-Control", "no-store");

      return new Response(upstream.body, {
        status: upstream.status,
        headers,
      });
    } catch (err) {
      return Response.json(
        { error: { message: err?.message || "Invalid proxy request." } },
        { status: 400, headers: CORS_HEADERS }
      );
    }
  },
};
