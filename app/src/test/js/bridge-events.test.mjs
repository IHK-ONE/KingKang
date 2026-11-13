/**
 * Contract tests for the bridge's event normalizer (DSH-009).
 *
 * dsh upgrades rename event fields; these fixtures are copied from the durable event
 * shapes in dsh 0.2.2 so a rename fails here instead of silently blanking the
 * transcript. Run with: node --test app/src/test/js
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import { normalizeEvent } from "../../main/assets/agent/bridge.mjs";

const imageBlock = {
  type: "image",
  attachment: {
    attachmentId: "att-1",
    mediaType: "image/png",
    bytes: 42,
    width: 10,
    height: 10,
  },
};

test("user/message keeps text, source and image refs", () => {
  const event = normalizeEvent("s1", {
    type: "user/message",
    seq: 4,
    time: 100,
    data: {
      id: "m1",
      source: { kind: "user", rpcId: "rpc-1" },
      content: [{ type: "text", text: "hello" }, imageBlock],
    },
  });
  assert.equal(event.kind, "user.message");
  assert.equal(event.text, "hello");
  assert.equal(event.source, "user");
  assert.equal(event.rpcId, "rpc-1");
  assert.equal(event.images.length, 1);
  assert.equal(event.images[0].attachmentId, "att-1");
  assert.equal(event.seq, 4);
});

test("schedule deliveries become context messages, runtime snapshots are dropped", () => {
  const scheduled = normalizeEvent("s1", {
    type: "user/message",
    data: {
      source: { kind: "schedule", form: "reminder", summary: "reply OK" },
      content: [{ type: "text", text: "reply OK" }],
    },
  });
  assert.equal(scheduled.kind, "context.message");
  assert.equal(scheduled.source, "schedule");
  assert.equal(scheduled.summary, "reply OK");

  const runtime = normalizeEvent("s1", {
    type: "user/message",
    data: { source: { kind: "runtime" }, content: [{ type: "text", text: "clock" }] },
  });
  assert.equal(runtime, null);
});

test("assistant/message carries turn, usage and the final message id", () => {
  const event = normalizeEvent("s1", {
    type: "assistant/message",
    seq: 9,
    data: {
      turn: 2,
      step: 1,
      usage: { inputTokens: 3, outputTokens: 5 },
      message: {
        id: "msg-9",
        content: [{ type: "text", text: "done" }, imageBlock],
      },
    },
  });
  assert.equal(event.kind, "assistant.message");
  assert.equal(event.turn, 2);
  assert.equal(event.step, 1);
  assert.equal(event.usage.inputTokens, 3);
  assert.equal(event.messageId, "msg-9");
  assert.equal(event.images.length, 1);
});

test("tool/result forwards meta and image blocks", () => {
  const event = normalizeEvent("s1", {
    type: "tool/result",
    data: {
      message: {
        source: { callId: "call-1" },
        content: [{ type: "text", text: "rendered" }, imageBlock],
      },
      meta: { diffs: [] },
    },
  });
  assert.equal(event.kind, "tool.result");
  assert.equal(event.callId, "call-1");
  assert.equal(event.isError, false);
  assert.equal(event.meta.diffs.length, 0);
  assert.equal(event.images[0].attachmentId, "att-1");
});

test("tool/result marks errors from content and meta", () => {
  const event = normalizeEvent("s1", {
    type: "tool/result",
    data: { content: [{ type: "text", text: "boom", isError: true }] },
  });
  assert.equal(event.isError, true);
});

test("silent bookkeeping events never reach the app", () => {
  for (const type of ["step/start", "approval/asked", "goal/change", "feedback/record"]) {
    assert.equal(normalizeEvent("s1", { type, data: {} }), null, type);
  }
});

test("turn, change and unknown events keep their durable facts", () => {
  const end = normalizeEvent("s1", {
    type: "turn/end",
    data: { turn: 3, reason: { kind: "error", code: "TIMEOUT" } },
  });
  assert.equal(end.kind, "turn.end");
  assert.equal(end.reason.code, "TIMEOUT");

  const changes = normalizeEvent("s1", { type: "workspace/changes", seq: 12, data: { turn: 3 } });
  assert.equal(changes.kind, "changes");
  assert.equal(changes.turn, 3);

  const unknown = normalizeEvent("s1", { type: "future/thing", data: { a: 1 } });
  assert.equal(unknown.kind, "unknown");
  assert.equal(unknown.dshType, "future/thing");
});
