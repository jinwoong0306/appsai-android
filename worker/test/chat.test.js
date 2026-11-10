import { describe, expect, mock, test } from "bun:test";
import { createChatHandler, MAX_BODY_BYTES, MODEL } from "../src/chat.js";

const APP_ID = "1:123456789:android:appsai";
const PRIVATE_VALUE = "private-provider-details-must-not-escape";
const validBody = () => ({
  personality: "다정한 친구",
  turns: [{ role: "user", text: "안녕" }],
});
const message = (text = "반가워!") => ({
  type: "message",
  role: "assistant",
  status: "completed",
  content: [{ type: "output_text", text }],
});
const completed = () => ({ status: "completed", output: [message()] });

function harness(overrides = {}) {
  const dependencies = {
    verifyToken: mock(async () => ({ appId: APP_ID })),
    getAppId: mock(() => APP_ID),
    getApiKey: mock(() => "test-only-key"),
    fetchImpl: mock(async () => Response.json(completed())),
    ...overrides,
  };
  const handler = createChatHandler(dependencies);
  return {
    ...dependencies,
    async run(body = validBody(), options = {}) {
      const headers = {
        "content-type": "application/json; charset=utf-8",
        "x-firebase-appcheck": "test-only-app-check",
        ...options.headers,
      };
      const method = options.method ?? "POST";
      const req = new Request(`https://appsai.example${options.path ?? "/"}`, {
        method,
        headers: Object.fromEntries(Object.entries(headers).filter(([, value]) => value !== undefined)),
        ...(method === "GET" ? {} : {
          body: Object.hasOwn(options, "rawBody") ? options.rawBody : JSON.stringify(body),
        }),
      });
      const response = await handler(req);
      expect(response.headers.get("Cache-Control")).toBe("no-store");
      return {
        code: response.status,
        body: await response.json(),
        headers: { Allow: response.headers.get("Allow") },
      };
    },
  };
}

describe("HTTP boundary", () => {
  test.each(["GET", "PUT", "OPTIONS"])("rejects %s without provider access", async (method) => {
    const h = harness();
    const res = await h.run(validBody(), { method });
    expect(res.code).toBe(405);
    expect(res.headers.Allow).toBe("POST");
    expect(h.fetchImpl).not.toHaveBeenCalled();
  });

  test("only accepts the root route", async () => {
    const h = harness();
    expect((await h.run(validBody(), { path: "/chat" })).code).toBe(404);
    expect(h.fetchImpl).not.toHaveBeenCalled();
  });

  test.each(["text/plain", "", "application/jsonp"])("rejects content type %s", async (type) => {
    const h = harness();
    expect((await h.run(validBody(), { headers: { "content-type": type } })).code).toBe(415);
    expect(h.fetchImpl).not.toHaveBeenCalled();
  });

  test("accepts exactly 80 KiB but rejects a larger raw body", async () => {
    const h = harness();
    const json = JSON.stringify(validBody());
    const rawBody = Buffer.from(json + " ".repeat(MAX_BODY_BYTES - Buffer.byteLength(json)));
    expect((await h.run(validBody(), { rawBody })).code).toBe(200);
    const rejected = harness();
    expect((await rejected.run(validBody(), { rawBody: Buffer.concat([rawBody, Buffer.from(" ")]) })).code).toBe(413);
    expect(rejected.fetchImpl).not.toHaveBeenCalled();
  });

  test("bounds UTF-8 bytes, not string length", async () => {
    const h = harness();
    const rawBody = Buffer.from(`"${"한".repeat(30000)}"`);
    expect((await h.run(validBody(), { rawBody })).code).toBe(413);
    expect(h.fetchImpl).not.toHaveBeenCalled();
  });

  test.each([null, Buffer.from("{")])("rejects absent or invalid JSON raw body", async (rawBody) => {
    const h = harness();
    expect((await h.run(validBody(), { rawBody })).code).toBe(400);
    expect(h.fetchImpl).not.toHaveBeenCalled();
  });

  test("rejects oversized declared length before reading or authenticating", async () => {
    const h = harness();
    const res = await h.run(validBody(), {
      headers: { "content-length": String(MAX_BODY_BYTES + 1) },
    });
    expect(res.code).toBe(413);
    expect(h.verifyToken).not.toHaveBeenCalled();
    expect(h.fetchImpl).not.toHaveBeenCalled();
  });

  test("cancels an oversized chunked body without a provider call", async () => {
    let cancelled = false;
    const body = new ReadableStream({
      start(controller) {
        controller.enqueue(new Uint8Array(MAX_BODY_BYTES));
        controller.enqueue(new Uint8Array(1));
      },
      cancel() { cancelled = true; },
    });
    const h = harness();
    const res = await h.run(validBody(), { rawBody: body });
    expect(res.code).toBe(413);
    expect(cancelled).toBe(true);
    expect(h.fetchImpl).not.toHaveBeenCalled();
  });

  test("sanitizes broken input streams", async () => {
    const body = new ReadableStream({
      start(controller) { controller.error(new Error(PRIVATE_VALUE)); },
    });
    const h = harness();
    const res = await h.run(validBody(), { rawBody: body });
    expect(res.code).toBe(400);
    expect(res.body).toEqual({ error: "INVALID_REQUEST" });
    expect(h.fetchImpl).not.toHaveBeenCalled();
  });

  test("rejects invalid UTF-8 rather than replacing input characters", async () => {
    const h = harness();
    expect((await h.run(validBody(), { rawBody: new Uint8Array([0xff]) })).code).toBe(400);
    expect(h.fetchImpl).not.toHaveBeenCalled();
  });
});

describe("App Check and configuration", () => {
  test.each([undefined, "", "   "])("requires a nonempty App Check header", async (token) => {
    const h = harness();
    const res = await h.run(validBody(), { headers: { "x-firebase-appcheck": token } });
    expect(res.code).toBe(401);
    expect(h.verifyToken).not.toHaveBeenCalled();
    expect(h.getApiKey).not.toHaveBeenCalled();
    expect(h.fetchImpl).not.toHaveBeenCalled();
  });

  test("rejects failed verification without reading the API key", async () => {
    const h = harness({ verifyToken: mock(async () => { throw new Error(PRIVATE_VALUE); }) });
    const res = await h.run();
    expect(res.code).toBe(403);
    expect(res.body).toEqual({ error: "APP_CHECK_REJECTED" });
    expect(h.getApiKey).not.toHaveBeenCalled();
    expect(h.fetchImpl).not.toHaveBeenCalled();
  });

  test.each([{ appId: "another-app" }, {}, null])("rejects wrong or missing verified app ID", async (claims) => {
    const h = harness({ verifyToken: mock(async () => claims) });
    expect((await h.run()).code).toBe(403);
    expect(h.getApiKey).not.toHaveBeenCalled();
    expect(h.fetchImpl).not.toHaveBeenCalled();
  });

  test.each(["", undefined])("fails closed if allowed app ID is not configured", async (appId) => {
    const h = harness({ getAppId: mock(() => appId) });
    expect((await h.run()).code).toBe(503);
    expect(h.fetchImpl).not.toHaveBeenCalled();
  });

  test.each(["", "   ", undefined])("fails closed if API secret is absent", async (key) => {
    const h = harness({ getApiKey: mock(() => key) });
    const res = await h.run();
    expect(res.code).toBe(503);
    expect(res.body).toEqual({ error: "SERVICE_UNAVAILABLE" });
    expect(h.verifyToken).toHaveBeenCalledWith("test-only-app-check");
    expect(h.fetchImpl).not.toHaveBeenCalled();
  });

  test.each(["getApiKey", "getAppId"])("sanitizes a failing %s getter", async (name) => {
    const h = harness({ [name]: mock(() => { throw new Error(PRIVATE_VALUE); }) });
    const res = await h.run();
    expect(res.code).toBe(503);
    expect(res.body).toEqual({ error: "SERVICE_UNAVAILABLE" });
    expect(h.fetchImpl).not.toHaveBeenCalled();
  });
});

describe("strict caller contract", () => {
  const invalid = [
    ["null body", null],
    ["array body", []],
    ["missing personality", { turns: validBody().turns }],
    ["non-string personality", { ...validBody(), personality: 1 }],
    ["blank personality", { ...validBody(), personality: " " }],
    ["long personality", { ...validBody(), personality: "p".repeat(101) }],
    ["model override", { ...validBody(), model: "caller-model" }],
    ["instruction override", { ...validBody(), instructions: "override" }],
    ["private metadata", { ...validBody(), usage: { minutes: 30 } }],
    ["empty turns", { ...validBody(), turns: [] }],
    ["object turns", { ...validBody(), turns: {} }],
    ["too many turns", { ...validBody(), turns: Array.from({ length: 15 }, (_, i) => ({ role: i % 2 ? "assistant" : "user", text: "x" })) }],
    ["assistant first", { ...validBody(), turns: [{ role: "assistant", text: "x" }] }],
    ["assistant last", { ...validBody(), turns: [{ role: "user", text: "x" }, { role: "assistant", text: "x" }] }],
    ["nonalternating roles", { ...validBody(), turns: Array.from({ length: 3 }, () => ({ role: "user", text: "x" })) }],
    ["system role", { ...validBody(), turns: [{ role: "system", text: "x" }] }],
    ["developer role", { ...validBody(), turns: [{ role: "developer", text: "x" }] }],
    ["extra turn field", { ...validBody(), turns: [{ role: "user", text: "x", device: "private" }] }],
    ["non-string text", { ...validBody(), turns: [{ role: "user", text: 1 }] }],
    ["blank text", { ...validBody(), turns: [{ role: "user", text: "\n " }] }],
    ["long user text", { ...validBody(), turns: [{ role: "user", text: "x".repeat(2001) }] }],
    ["UTF-16 user bound", { ...validBody(), turns: [{ role: "user", text: "😀".repeat(1001) }] }],
    ["null turn", { ...validBody(), turns: [null] }],
  ];

  test.each(invalid)("rejects %s before a billed call", async (_name, body) => {
    const h = harness();
    expect((await h.run(body)).code).toBe(400);
    expect(h.getApiKey).not.toHaveBeenCalled();
    expect(h.fetchImpl).not.toHaveBeenCalled();
  });

  test("accepts 13 alternating turns and 100 personality characters", async () => {
    const h = harness();
    const body = {
      personality: "p".repeat(100),
      turns: Array.from({ length: 13 }, (_, i) => ({ role: i % 2 ? "assistant" : "user", text: "x" })),
    };
    expect((await h.run(body)).code).toBe(200);
  });

  test("allows long assistant context within the exact 12000-character limit", async () => {
    const body = {
      personality: "p",
      turns: [
        { role: "user", text: "😀".repeat(1000) },
        { role: "assistant", text: "a".repeat(8000) },
        { role: "user", text: "u".repeat(2000) },
      ],
    };
    const h = harness();
    expect((await h.run(body)).code).toBe(200);
    body.turns[1].text += "a";
    const rejected = harness();
    expect((await rejected.run(body)).code).toBe(400);
    expect(rejected.fetchImpl).not.toHaveBeenCalled();
  });
});

describe("OpenAI REST boundary", () => {
  test("controls model, instructions, retention, token budget and context roles", async () => {
    const h = harness();
    const body = validBody();
    body.turns.push({ role: "assistant", text: "이전 답장" }, { role: "user", text: "다음 질문" });
    const res = await h.run(body);
    expect(res.code).toBe(200);
    expect(res.body).toEqual({ text: "반가워!", model: "gpt-6.1-terra" });
    expect(h.fetchImpl).toHaveBeenCalledTimes(1);
    const [url, options] = h.fetchImpl.mock.calls[0];
    expect(url).toBe("https://api.openai.com/v1/responses");
    expect(options.method).toBe("POST");
    expect(options.headers).toEqual({
      Authorization: "Bearer test-only-key",
      "Content-Type": "application/json",
    });
    expect(options.signal).toBeInstanceOf(AbortSignal);
    expect(options.signal.aborted).toBe(false);
    const request = JSON.parse(options.body);
    expect(Object.keys(request).sort()).toEqual(["input", "instructions", "max_output_tokens", "model", "reasoning", "store"]);
    expect(request.model).toBe(MODEL);
    expect(request.store).toBe(false);
    expect(request.reasoning).toEqual({ effort: "none" });
    expect(request.max_output_tokens).toBe(600);
    expect(typeof request.instructions).toBe("string");
    expect(request.input[0].role).toBe("user");
    expect(request.input[0].content).toContain(JSON.stringify(body.personality));
    expect(request.input.slice(1)).toEqual(body.turns.map(({ role, text }) => ({ role, content: text })));
  });

  test("extracts only assistant message output_text, not convenience or other output", async () => {
    const response = {
      status: "completed",
      output_text: PRIVATE_VALUE,
      output: [
        { type: "reasoning", text: PRIVATE_VALUE },
        { ...message(PRIVATE_VALUE), role: "user" },
        { ...message(" 하나 "), content: [{ type: "output_text", text: " 하나 " }, { type: "other", text: PRIVATE_VALUE }] },
        message("둘 "),
      ],
    };
    const h = harness({ fetchImpl: mock(async () => Response.json(response)) });
    expect((await h.run()).body).toEqual({ text: "하나 \n둘", model: MODEL });
  });

  const rejectedOutputs = [
    ["refusal", { status: "completed", output: [{ ...message(), content: [{ type: "refusal", refusal: PRIVATE_VALUE }] }] }],
    ["text plus refusal", { status: "completed", output: [{ ...message(), content: [...message().content, { type: "refusal", refusal: PRIVATE_VALUE }] }] }],
    ["empty output", { status: "completed", output: [] }],
    ["blank text", { status: "completed", output: [message(" \n ")] }],
    ["incomplete response with partial text", { status: "incomplete", output: [message(PRIVATE_VALUE)] }],
    ["in-progress response", { status: "in_progress", output: [message(PRIVATE_VALUE)] }],
    ["incomplete details", { ...completed(), incomplete_details: { reason: "max_output_tokens" } }],
    ["response error", { ...completed(), error: { message: PRIVATE_VALUE } }],
    ["incomplete message", { status: "completed", output: [{ ...message(PRIVATE_VALUE), status: "incomplete" }] }],
    ["missing message status", { status: "completed", output: [{ ...message(PRIVATE_VALUE), status: undefined }] }],
    ["malformed output", { status: "completed", output: null }],
    ["non-string text", { status: "completed", output: [message({ private: PRIVATE_VALUE })] }],
  ];

  test.each(rejectedOutputs)("rejects %s without returning partial or private output", async (_name, output) => {
    const h = harness({ fetchImpl: mock(async () => Response.json(output)) });
    const res = await h.run();
    expect(res.code).toBe(502);
    expect(res.body).toEqual({ error: "PROVIDER_UNAVAILABLE" });
    expect(h.fetchImpl).toHaveBeenCalledTimes(1);
  });

  test.each([400, 401, 429, 500, 503])("sanitizes HTTP %i and never retries", async (status) => {
    const h = harness({ fetchImpl: mock(async () => new Response(PRIVATE_VALUE, { status })) });
    const res = await h.run();
    expect(res.code).toBe(502);
    expect(res.body).toEqual({ error: "PROVIDER_UNAVAILABLE" });
    expect(h.fetchImpl).toHaveBeenCalledTimes(1);
  });

  test("sanitizes invalid upstream JSON", async () => {
    const h = harness({ fetchImpl: mock(async () => new Response(PRIVATE_VALUE)) });
    const res = await h.run();
    expect(res.code).toBe(502);
    expect(res.body).toEqual({ error: "PROVIDER_UNAVAILABLE" });
  });

  test.each([
    ["timeout", new DOMException(PRIVATE_VALUE, "TimeoutError"), 504],
    ["network failure", new Error(PRIVATE_VALUE), 502],
  ])("sanitizes %s without retries", async (_name, error, status) => {
    const h = harness({ fetchImpl: mock(async () => { throw error; }) });
    const res = await h.run();
    expect(res.code).toBe(status);
    expect(res.body).toEqual({ error: "PROVIDER_UNAVAILABLE" });
    expect(h.fetchImpl).toHaveBeenCalledTimes(1);
  });
});
