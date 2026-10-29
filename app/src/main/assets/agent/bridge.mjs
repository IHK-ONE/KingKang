#!/usr/bin/env node
/**
 * KK-Agent v1 bridge.
 *
 * This process is owned by the KingKang Android app. It starts dsh's web profile on
 * loopback, authenticates to dsh's Remote API, and exposes a small, stable NDJSON
 * protocol on stdio. No network listener is opened for the app.
 *
 * dsh 0.2.x carries every Remote call as JSON over one WebSocket mux
 * (`/api/remote.mux`): logical streams are opened with `{type:"open", streamId,
 * endpoint, payload:{args}}`. The app never imports dsh types, and every
 * version-specific detail (event names, payload shapes, waterfall answers) stays
 * in this file.
 */

import { spawn, spawnSync } from "node:child_process";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  readlinkSync,
  symlinkSync,
  unlinkSync,
  writeFileSync,
} from "node:fs";
import { createInterface } from "node:readline";
import { randomUUID } from "node:crypto";
import process from "node:process";

const PROTOCOL = 1;
const BRIDGE_VERSION = "2.4.0";
const NODE_BIN = process.env.KK_NODE_BIN || "/opt/kingkang/node/bin/node";
const DSH_BIN = process.env.KK_DSH_BIN || "/opt/kingkang/dsh/bin/dsh";
const WORKDIR = process.env.KK_WORKDIR || "/root";
/** dsh profile the bridge starts; its directory is what pnpm reads `.npmrc` from. */
const PROFILE_NAME = "web";
const PROFILE_DIR = `${WORKDIR}/.dsh/profiles/${PROFILE_NAME}`;
/** Alibaba's npm mirror; written into a profile that has no registry line of its own. */
const DEFAULT_PLUGIN_REGISTRY = "https://registry.npmmirror.com/";
const PATH = [
  "/opt/kingkang/node/bin",
  "/opt/kingkang/dsh/bin",
  "/usr/local/sbin",
  "/usr/local/bin",
  "/usr/sbin",
  "/usr/bin",
  "/sbin",
  "/bin",
].join(":");

const CAPABILITIES = [
  "connection.refresh",
  "session.list",
  "session.create",
  "session.history",
  "session.prompt",
  "session.cancel",
  "session.updateQueue",
  "session.attachment",
  "session.models",
  "session.selectModel",
  "session.rename",
  "session.fork",
  "subagent.list",
  "subagent.history",
  "subagent.prompt",
  "subagent.interrupt",
  "command.list",
  "command.execute",
  "skill.list",
  "web.url",
  "workspace.list",
  "workspace.create",
  "workspace.archiveSession",
  "workspace.unarchiveSession",
  "workspace.pinSession",
  "workspace.unpinSession",
  "plugin.list",
  "plugin.setEnabled",
  "plugin.inspect",
  "plugin.install",
  "plugin.update",
  "plugin.registry.get",
  "plugin.registry.set",
  "approval.respond",
  "question.respond",
  "credentials.describe",
  "credentials.set",
  "credentials.unset",
  "permission.catalog",
  "settings.describe",
  "settings.mutate",
  "agentPreset.list",
  "agentPreset.select",
];

const HISTORY_DEFAULT = 80;
const HISTORY_MAX = 500;
const SOCKET_OPEN = 1;

let dshProcess = null;
let baseUrl = null;
let applicationUrl = null;
let wsUrl = null;
let launchToken = null;
let apiCookie = null;
let dshVersion = null;
let shuttingDown = false;

let socket = null;
let eventsClientId = null;
let controlStreamId = null;
let reconnectAttempts = 0;
let reconnectTimer = null;
/** One in-flight /connect re-trade, shared by every caller that hits a stale cookie. */
let authRefreshPromise = null;
/** One in-flight dsh web replacement, so concurrent reconnect failures cannot race. */
let dshRestartPromise = null;
/** True while a *planned* shutdown of the dsh child runs, so its exit is not fatal. */
let restartingDsh = false;
let lastDshRestartAt = 0;
/** Pending in-bridge respawn of a crashed dsh child. */
let dshRespawnTimer = null;
/** Crashes inside the current window; bounds how often we respawn dsh ourselves. */
let dshCrashCount = 0;
let lastDshCrashAt = 0;
/** Announce the outage once per episode, not on every retry. */
let streamErrorAnnounced = false;
/** True between the first failed reconnect and the next successful one. */
let outageActive = false;

/** Fast retries before the first outage notice, then a patient background retry. */
const RECONNECT_FAST_LIMIT = 6;
const RECONNECT_SLOW_MS = 30_000;
/** Replacing a wedged dsh web process is the last resort; keep it rate limited. */
const DSH_RESTART_COOLDOWN_MS = 10 * 60_000;
/** A crashed dsh is respawned in-process this many times before the bridge gives up. */
const DSH_CRASH_LIMIT = 3;
/** A dsh that stays up this long resets the crash budget. */
const DSH_CRASH_WINDOW_MS = 10 * 60_000;

/** Active logical streams by id, each with item/end/error callbacks. */
const streams = new Map();
/** Pending approval/question waterfalls, keyed by the app-visible rpcId. */
const pendingWaterfalls = new Map();
/** Live assistant attempts of the active follow, keyed by attemptId. */
const attempts = new Map();
/** Latest wire projection values per session, for cheap model selection reads. */
const projectionCache = new Map();

let activeFollow = null;
let lastFollowSessionId = null;

function writeLine(value) {
  process.stdout.write(JSON.stringify(value) + "\n");
}

function emit(name, data) {
  writeLine({ v: PROTOCOL, type: "event", event: name, data });
}

function info(message) {
  process.stderr.write(`[kk-agent] ${message}\n`);
}

function reply(id, result) {
  writeLine({ v: PROTOCOL, id, ok: true, result: result === undefined ? {} : result });
}

function fail(id, code, message, details = {}) {
  writeLine({
    v: PROTOCOL,
    id,
    ok: false,
    error: { code, message: String(message || code), details },
  });
}

function codedError(code, message, details) {
  return Object.assign(new Error(message), { code, details });
}

function badRequest(message) {
  return codedError("bad-request", message);
}

function describeError(error) {
  if (error == null) return "未知错误";
  if (typeof error === "string") return error;
  if (typeof error.message === "string" && error.message) return error.message;
  try {
    return JSON.stringify(error);
  } catch {
    return String(error);
  }
}

function clampInt(value, minimum, maximum, fallback) {
  const number = Number(value);
  if (!Number.isFinite(number)) return fallback;
  return Math.min(maximum, Math.max(minimum, Math.trunc(number)));
}

function textOf(content) {
  if (typeof content === "string") return content;
  if (Array.isArray(content)) {
    return content
      .map((block) => {
        if (typeof block === "string") return block;
        if (!block || typeof block !== "object") return "";
        if (typeof block.text === "string") return block.text;
        if (typeof block.content === "string") return block.content;
        if (Array.isArray(block.content)) return textOf(block.content);
        return "";
      })
      .filter(Boolean)
      .join("\n");
  }
  if (content && typeof content === "object") {
    if (typeof content.text === "string") return content.text;
    if (typeof content.content === "string") return content.content;
  }
  return "";
}

/**
 * Image blocks of a message content array, reduced to the durable reference the app
 * fetches through `session.attachment`. Prompt-time bytes never reach the log, so a
 * history replay only ever sees this shape.
 */
function imagesOf(content) {
  if (!Array.isArray(content)) return [];
  const images = [];
  for (const block of content) {
    if (!block || typeof block !== "object" || block.type !== "image") continue;
    const ref = block.attachment;
    if (!ref || typeof ref !== "object" || !ref.attachmentId) continue;
    images.push({
      attachmentId: String(ref.attachmentId),
      mediaType: typeof ref.mediaType === "string" ? ref.mediaType : "image/png",
      bytes: Number(ref.bytes) || 0,
      width: Number(ref.width) || 0,
      height: Number(ref.height) || 0,
      name: typeof ref.name === "string" ? ref.name : null,
    });
  }
  return images;
}

function titleOfProjection(value) {
  if (typeof value === "string") return value;
  if (value && typeof value === "object") {
    if (typeof value.title === "string") return value.title;
    if (typeof value.value === "string") return value.value;
  }
  return "";
}

/**
 * The app's session row. dsh's list already sorts by activity and puts the
 * title projection under `projections.values`, next to the cache hints.
 */
function normalizeSession(item) {
  const projections = item?.projections;
  const values = projections && typeof projections === "object" ? projections.values : undefined;
  const presetProjection = values?.agentPreset;
  const presetId =
    typeof presetProjection === "string"
      ? presetProjection
      : typeof presetProjection?.value === "string"
        ? presetProjection.value
        : null;
  return {
    id: String(item?.sessionId ?? item?.id ?? ""),
    title: titleOfProjection(values?.title),
    updatedAt: Number(item?.updatedAt) || 0,
    running: Boolean(item?.running),
    blank: Boolean(item?.blank),
    parentId: item?.parentSessionId ? String(item.parentSessionId) : null,
    origin: item?.origin ?? null,
    cwd: typeof item?.cwd === "string" ? item.cwd : null,
    presetId,
    projections: projections ?? null,
  };
}

/** The app's workspace row. `path` and `title` come straight from the registry. */
function normalizeWorkspace(item) {
  if (!item || typeof item !== "object") return null;
  const id = String(item.workspaceId ?? item.id ?? "");
  if (!id) return null;
  return {
    id,
    path: typeof item.path === "string" ? item.path : "",
    title: typeof item.title === "string" ? item.title : "",
    sessionIds: Array.isArray(item.sessionIds)
      ? item.sessionIds.map((value) => String(value)).filter(Boolean)
      : [],
    createdAt: typeof item.createdAt === "string" ? item.createdAt : null,
    updatedAt: typeof item.updatedAt === "string" ? item.updatedAt : null,
  };
}

/** The complete one-shot Workspace projection the sidebar needs. */
function normalizeWorkspaceBaseline(value) {
  const items = Array.isArray(value?.items) ? value.items : [];
  const ids = (list) =>
    Array.isArray(list) ? list.map((entry) => String(entry)).filter(Boolean) : [];
  return {
    workspaces: items.map(normalizeWorkspace).filter(Boolean),
    archivedSessionIds: ids(value?.archivedSessionIds),
    pinnedSessionIds: ids(value?.pinnedSessionIds),
  };
}

/** Event types that only describe host bookkeeping and have no Android surface. */
const SILENT_EVENT_TYPES = new Set([
  "step/start",
  "step/end",
  "agent/inbox/spliced",
  "model/selection",
  "assistant/attempt",
  "system/message",
  "request/header",
  "request/context",
  "session/end-seed",
  // Approval and job lifecycle already reach the app through the live waterfall and
  // the tool-call events; the durable copies would only be logged as unknown.
  "approval/asked",
  "approval/decided",
  "approval/requested",
  "approval/resolved",
  "job/registered",
  "job/settled",
  // Composition metadata for a child session; identity already arrives through the
  // `subagent` projection, so the durable event itself renders nothing.
  "subagent/descriptor",
]);

/** Prefixes of host-internal event families that never reach the app surface. */
const SILENT_EVENT_PREFIXES = ["session-log-", "session/title-llm"];

function isSilentEvent(type) {
  return (
    SILENT_EVENT_TYPES.has(type) ||
    SILENT_EVENT_PREFIXES.some((prefix) => type.startsWith(prefix))
  );
}

function normalizeEvent(sessionId, event, view) {
  if (!event || typeof event !== "object") return null;
  const type = String(event.type || "");
  if (isSilentEvent(type)) return null;
  const data = event.data && typeof event.data === "object" ? event.data : {};
  const base = {
    sessionId,
    seq: typeof event.seq === "number" ? event.seq : null,
    time: typeof event.time === "number" ? event.time : null,
    dshType: type || "unknown",
  };

  switch (type) {
    case "user/message": {
      // `user/message` carries the message itself on `data` (content, source, id);
      // some dsh builds wrap it as `data.message`, so accept both shapes.
      const message =
        data.message && typeof data.message === "object" ? data.message : data;
      const source =
        message.source && typeof message.source === "object" ? message.source : {};
      // dsh also records runtime/time context snapshots as `user/message`; only a
      // real user turn (source.kind "user") belongs in the transcript.
      if (source.kind && source.kind !== "user") return null;
      return {
        ...base,
        kind: "user.message",
        text: textOf(message.content),
        images: imagesOf(message.content),
        source: source.kind || null,
        rpcId: typeof source.rpcId === "string" ? source.rpcId : null,
      };
    }
    // Older dsh releases appended `assistant/chunk`; 0.2.x streams live chunks
    // outside the log. Both carry the same chunk vocabulary, and the app only
    // ever sees `assistant.delta`.
    case "assistant/chunk":
    case "assistant/live-chunk":
      return {
        ...base,
        kind: "assistant.delta",
        turn: data.turn,
        step: data.step,
        chunk: data.chunk,
      };
    case "assistant/message":
      return {
        ...base,
        kind: "assistant.message",
        turn: data.turn,
        step: data.step,
        blocks: data.message?.content ?? [],
        images: imagesOf(data.message?.content),
        usage: data.usage ?? null,
        source: data.message?.source ?? null,
      };
    case "tool/call":
      return {
        ...base,
        kind: "tool.call",
        callId: data.callId == null ? null : String(data.callId),
        name: data.name || "tool",
        arguments: data.arguments ?? null,
        view: view ?? null,
      };
    case "tool/result": {
      const content = data.message?.content ?? data.content ?? [];
      const isError =
        Boolean(data.isError) ||
        Boolean(data.message?.isError) ||
        Boolean(data.meta?.isError) ||
        Boolean(data.error) ||
        (Array.isArray(content) && content.some((block) => block?.isError === true));
      return {
        ...base,
        kind: "tool.result",
        callId:
          data.message?.source?.callId == null && data.callId == null
            ? null
            : String(data.message?.source?.callId ?? data.message?.toolCallId ?? data.callId),
        text: textOf(content),
        isError,
        view: view ?? null,
      };
    }
    case "turn/start":
      return { ...base, kind: "turn.start", turn: data.turn };
    case "turn/end":
      return { ...base, kind: "turn.end", turn: data.turn, reason: data.reason ?? null };
    case "session/title":
      return { ...base, kind: "session.title", title: String(data.title || "") };
    case "subagent/catalog":
      // The parent's durable child-discovery fact; the app refreshes the catalog
      // rather than rendering the event itself.
      return { ...base, kind: "subagent.catalog" };
    case "todo/write":
      return { ...base, kind: "todo.write", todos: data.todos ?? [] };
    default:
      if (type.startsWith("command/")) {
        const phase = type.slice("command/".length);
        // `command/run` names the command and its raw input; `command/done`
        // settles it with the handler's verbatim outcome. Both carry the
        // commandId that pairs them, and the app rebuilds one transcript row
        // per invocation from that pair.
        return {
          ...base,
          kind: "command",
          phase,
          commandId: data.commandId == null ? null : String(data.commandId),
          name: phase === "run" ? String(data.name || "") : "",
          args:
            phase === "run" && typeof data.args === "string" ? data.args : "",
          isError: phase === "done" && data.kind === "error",
          text: textOf(data),
        };
      }
      return { ...base, kind: "unknown", data };
  }
}

function normalizeQuestions(questions) {
  if (!Array.isArray(questions)) return [];
  return questions.map((q) => ({
    id: String(q?.id ?? ""),
    question: String(q?.question ?? q?.text ?? ""),
    header: q?.header == null ? null : String(q.header),
    detail: q?.detail == null ? null : String(q.detail),
    options: Array.isArray(q?.options)
      ? q.options.map((o) => ({
          label: String(o?.label ?? o ?? ""),
          description: o?.description == null ? null : String(o.description),
        }))
      : [],
    multiSelect: Boolean(q?.multiSelect),
    intent: q?.intent ?? null,
  }));
}

/**
 * dsh's `inbox` projection already is the two ordered pending lists, each a
 * UserMessage. The app wants the flat queue item shape it renders in the
 * composer's queue strip.
 */
function queueItems(inbox) {
  const items = [];
  const push = (list, placement) => {
    if (!Array.isArray(list)) return;
    for (const message of list) {
      if (!message || typeof message !== "object") continue;
      items.push({
        id: String(message.id ?? ""),
        placement,
        message: { content: message.content ?? [] },
      });
    }
  };
  push(inbox?.["next-turn"], "queued");
  push(inbox?.["next-step"], "steering");
  return items;
}

// ---------------------------------------------------------------------------
// Remote transport
// ---------------------------------------------------------------------------

/**
 * dsh mints a launch token per process and only accepts it at `/connect`, where it
 * trades it for an authority-bound signed API cookie. After that the cookie is the
 * only credential the /api routes and the WebSocket mux accept.
 */
function applyDshUrl(url) {
  applicationUrl = String(url);
  let parsed;
  try {
    parsed = new URL(applicationUrl);
  } catch {
    baseUrl = String(applicationUrl).replace(/\/+$/, "");
    wsUrl = null;
    launchToken = null;
    return null;
  }
  baseUrl = `${parsed.protocol}//${parsed.host}`;
  wsUrl = `${parsed.protocol === "https:" ? "wss:" : "ws:"}//${parsed.host}/api/remote.mux`;
  launchToken = parsed.searchParams.get("token");
  return parsed;
}

/** One `/connect` round trip, returning the `name=value` cookie pair or null. */
async function requestSessionCookie() {
  if (!launchToken) {
    info("dsh web URL has no launch token; assuming loopback access without a session cookie");
    return null;
  }
  const endpoint = new URL("/connect", baseUrl);
  endpoint.searchParams.set("token", launchToken);
  endpoint.searchParams.set("interactions", "answer");
  const response = await fetch(endpoint, {
    redirect: "manual",
    headers: { accept: "*/*", origin: baseUrl },
    signal: AbortSignal.timeout(20_000),
  });
  const cookies =
    typeof response.headers.getSetCookie === "function"
      ? response.headers.getSetCookie()
      : [response.headers.get("set-cookie")].filter(Boolean);
  return (
    cookies
      .map((value) => String(value).split(";", 1)[0].trim())
      .find(Boolean) || null
  );
}

async function authenticateDsh(url) {
  applyDshUrl(url);
  if (!launchToken) {
    info("dsh web URL has no launch token; assuming loopback access without a session cookie");
    return;
  }
  const cookie = await requestSessionCookie();
  if (!cookie) {
    throw codedError("startup", "dsh /connect 未返回 API 会话 cookie");
  }
  apiCookie = cookie;
  info("dsh API session authorized");
}

/**
 * Re-trades the launch token for a fresh cookie. dsh binds its cookie to a
 * per-activation signing secret and to the request authority, so a cached cookie can
 * start failing with 401 while the web process is still healthy (the process restarted,
 * or dsh rotated its secret). The launch token stays valid for the process lifetime, so
 * one cheap local `/connect` heals that without restarting anything.
 */
function refreshAuth() {
  if (authRefreshPromise) return authRefreshPromise;
  if (!baseUrl || !launchToken) return Promise.resolve(false);
  const pending = (async () => {
    try {
      const cookie = await requestSessionCookie();
      if (!cookie) {
        info("刷新 dsh API 会话失败：/connect 未返回 cookie");
        return false;
      }
      apiCookie = cookie;
      info("dsh API 会话已刷新");
      return true;
    } catch (error) {
      info(`刷新 dsh API 会话失败：${describeError(error)}`);
      return false;
    }
  })();
  authRefreshPromise = pending.finally(() => {
    if (authRefreshPromise === pending) authRefreshPromise = null;
  });
  return authRefreshPromise;
}

function apiHeaders(extra = {}) {
  return apiCookie ? { ...extra, cookie: apiCookie } : extra;
}

async function postJsonOnce(path, body, timeoutMs) {
  const response = await fetch(new URL(path, baseUrl), {
    method: "POST",
    headers: apiHeaders({
      "content-type": "application/json",
      accept: "application/json",
      origin: baseUrl,
    }),
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(timeoutMs),
  });
  const text = await response.text();
  if (!response.ok) {
    throw codedError("transport", `HTTP ${response.status} for ${path}`, {
      status: response.status,
      body: text.slice(0, 500),
    });
  }
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    throw codedError("transport", `invalid JSON from ${path}`, { body: text.slice(0, 500) });
  }
}

/**
 * A 401 means the cookie we hold is no longer the one dsh accepts. Refresh it and
 * replay the call exactly once, so a stale session never surfaces as a failed send.
 */
async function postJson(path, body, timeoutMs = 60_000, allowAuthRetry = true) {
  try {
    return await postJsonOnce(path, body, timeoutMs);
  } catch (error) {
    if (allowAuthRetry && error?.details?.status === 401) {
      info(`dsh 拒绝了 ${path} 的会话（HTTP 401），正在重新认证`);
      if (await refreshAuth()) return postJson(path, body, timeoutMs, false);
      throw codedError("auth", "dsh 会话已失效，正在重新连接", {
        status: 401,
        body: error.details?.body,
      });
    }
    throw error;
  }
}

/**
 * One unary Remote call. dsh's gateway insists the URL path, the `method`
 * envelope field, and the endpoint be the same string, and that the payload be
 * exactly `{args}`.
 */
async function callDsh(endpoint, args = {}, timeoutMs = 60_000) {
  const rpcId = randomUUID();
  const body = await postJson(`/api/${endpoint}`, {
    type: "client-request",
    rpcId,
    method: endpoint,
    payload: { args },
  }, timeoutMs);
  if (!body || body.type !== "server-response") {
    throw codedError("transport", `unexpected response for ${endpoint}`);
  }
  if (body.rpcId !== rpcId) {
    throw codedError("transport", `rpcId mismatch for ${endpoint}`);
  }
  if (!body.result || body.result.ok !== true) {
    const error = body.result?.error || {};
    throw codedError("dsh-error", error.message || `${endpoint} failed`, {
      dshCode: error.code || "unknown",
      ...(error.details && typeof error.details === "object" ? error.details : {}),
    });
  }
  return body.result.value;
}

function openSocket() {
  return new Promise((resolve, reject) => {
    let settled = false;
    const finish = (error) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      if (error) reject(error);
      else resolve(ws);
    };
    const timer = setTimeout(
      () => finish(codedError("transport", "连接 dsh 事件通道超时")),
      15_000
    );
    let ws;
    try {
      ws = new WebSocket(wsUrl, {
        headers: apiCookie
          ? { cookie: apiCookie, origin: baseUrl }
          : { origin: baseUrl },
      });
    } catch (error) {
      finish(codedError("transport", `无法创建事件通道：${describeError(error)}`));
      return;
    }

    ws.addEventListener(
      "open",
      () => {
        if (settled) {
          try {
            ws.close();
          } catch {
            // ignore
          }
          return;
        }
        socket = ws;
        ws.addEventListener("message", (message) => {
          if (socket === ws) onSocketMessage(message.data);
        });
        ws.addEventListener("close", () => onSocketClosed(ws));
        ws.addEventListener("error", () => {
          // A following close event carries the actionable path; nothing to do here.
        });
        finish(null);
      },
      { once: true }
    );
    ws.addEventListener(
      "error",
      () => finish(codedError("transport", "dsh 事件通道连接失败")),
      { once: true }
    );
  });
}

function onSocketMessage(raw) {
  let frame;
  try {
    frame = JSON.parse(typeof raw === "string" ? raw : String(raw));
  } catch {
    info("dropping malformed Remote stream frame");
    return;
  }
  if (!frame || typeof frame !== "object") return;
  const streamId = typeof frame.streamId === "string" ? frame.streamId : null;
  const handler = streamId === null ? undefined : streams.get(streamId);
  switch (frame.type) {
    case "item":
      if (handler) handler.onItem?.(frame.value);
      break;
    case "end":
      if (handler) {
        streams.delete(streamId);
        handler.onEnd?.();
      }
      break;
    case "error":
      if (handler) {
        streams.delete(streamId);
        handler.onError?.(frame.error);
      }
      break;
    default:
      // Unknown mux frames belong to newer dsh features the app does not use yet.
      break;
  }
}

function openLogicalStream(endpoint, args, handlers) {
  if (!socket || socket.readyState !== SOCKET_OPEN) {
    throw codedError("transport", "事件通道尚未连接");
  }
  const streamId = randomUUID();
  streams.set(streamId, handlers);
  socket.send(JSON.stringify({ type: "open", streamId, endpoint, payload: { args } }));
  return streamId;
}

function closeStream(streamId) {
  if (!streamId) return;
  if (!streams.delete(streamId)) return;
  if (socket && socket.readyState === SOCKET_OPEN) {
    try {
      socket.send(JSON.stringify({ type: "cancel", streamId }));
    } catch {
      // ignore
    }
  }
}

/**
 * Reads the first item of a logical stream and closes it. `workspace/follow` is a stream
 * whose first frame is the complete current projection, so one item is the whole answer
 * the app needs for its sidebar.
 */
function firstStreamItem(endpoint, args, timeoutMs = 15_000) {
  return new Promise((resolve, reject) => {
    let streamId = null;
    let settled = false;
    const finish = (error, value) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      if (streamId) closeStream(streamId);
      if (error) reject(error);
      else resolve(value);
    };
    const timer = setTimeout(
      () => finish(codedError("transport", `${endpoint} 读取超时`)),
      timeoutMs
    );
    try {
      streamId = openLogicalStream(endpoint, args, {
        onItem: (value) => finish(null, value),
        onEnd: () => finish(codedError("transport", `${endpoint} 没有返回数据`)),
        onError: (error) =>
          finish(
            codedError("transport", error?.message || `${endpoint} 读取失败`, {
              dshCode: error?.code || "unknown",
            })
          ),
      });
    } catch (error) {
      finish(describeError(error));
    }
  });
}

/** Picks the Chinese display text out of a dsh localized string or map. */
function localizedText(value) {
  if (typeof value === "string") return value;
  if (value && typeof value === "object") {
    for (const key of ["zh-CN", "zh", "en"]) {
      if (typeof value[key] === "string" && value[key]) return value[key];
    }
  }
  return "";
}

function onSocketClosed(ws) {
  if (socket !== ws) return;
  socket = null;
  eventsClientId = null;
  controlStreamId = null;
  attempts.clear();

  const handlers = [...streams.values()];
  streams.clear();
  for (const handler of handlers) {
    try {
      handler.onError?.({ code: "transport", message: "事件通道已断开" });
    } catch {
      // ignore
    }
  }
  if (activeFollow) {
    const follow = activeFollow;
    activeFollow = null;
    finishFollow(follow, codedError("transport", "事件通道已断开"));
  }
  if (!shuttingDown) scheduleReconnect();
}

function scheduleReconnect() {
  if (shuttingDown || reconnectTimer) return;
  outageActive = true;
  const fast = reconnectAttempts < RECONNECT_FAST_LIMIT;
  if (!fast && !streamErrorAnnounced) {
    streamErrorAnnounced = true;
    // One notice per outage, then keep recovering quietly in the background.
    emit("stream.error", {
      error: { code: "transport", message: "与 dsh 的事件通道已断开" },
      retrying: true,
    });
  }
  const delay = fast
    ? Math.min(1_000 * 2 ** reconnectAttempts, 15_000)
    : RECONNECT_SLOW_MS;
  reconnectAttempts += 1;
  if (fast) {
    emit("stream.reconnecting", {
      attempt: reconnectAttempts,
      max: RECONNECT_FAST_LIMIT,
    });
  }
  reconnectTimer = setTimeout(async () => {
    reconnectTimer = null;
    if (shuttingDown) return;
    try {
      // A dead socket and a stale cookie usually arrive together, and only the cookie
      // can be healed locally; re-trading the token is cheap.
      await refreshAuth();
      await connectTransport();
      reconnectAttempts = 0;
      info("Remote event channel reconnected");
      if (outageActive) {
        outageActive = false;
        streamErrorAnnounced = false;
        emit("stream.recovered", {});
      }
    } catch (error) {
      info(`Remote event channel reconnect failed: ${describeError(error)}`);
      if (reconnectAttempts >= RECONNECT_FAST_LIMIT) {
        // The process answers but refuses every session, so nothing local can heal it:
        // replace the dsh web process once instead of waiting for a manual restart.
        await restartDshWebOnce().catch((restartError) => {
          info(`自动重启 dsh 失败：${describeError(restartError)}`);
        });
      }
      scheduleReconnect();
    }
  }, delay);
  reconnectTimer.unref?.();
}

async function connectTransport({ refresh = false } = {}) {
  if (refresh) await refreshAuth();
  await openSocket();
  await openEventsStream();
  openControlStream();
  if (lastFollowSessionId) {
    startFollow(lastFollowSessionId, { fresh: true }).catch((error) => {
      info(`恢复会话跟随失败: ${describeError(error)}`);
    });
  }
}

/** Stops the current dsh web child without treating its exit as a crash. */
async function stopDshProcess() {
  const child = dshProcess;
  if (!child) return;
  restartingDsh = true;
  try {
    const exited = new Promise((resolve) => {
      const timer = setTimeout(() => {
        try {
          child.kill("SIGKILL");
        } catch {
          // already gone
        }
        resolve();
      }, 5_000);
      timer.unref?.();
      child.once("exit", () => {
        clearTimeout(timer);
        resolve();
      });
    });
    try {
      child.kill("SIGTERM");
    } catch {
      // already gone
    }
    await exited;
  } finally {
    if (dshProcess === child) dshProcess = null;
    restartingDsh = false;
  }
}

/**
 * Replaces the dsh web process and re-authenticates against the URL it prints. Sessions
 * live on disk, so the app recovers its transcript through the normal history path.
 * Rate limited: a dsh that keeps rejecting us should not be respawned in a loop.
 */
function restartDshWebOnce() {
  if (dshRestartPromise) return dshRestartPromise;
  if (Date.now() - lastDshRestartAt < DSH_RESTART_COOLDOWN_MS) {
    return Promise.resolve(false);
  }
  lastDshRestartAt = Date.now();
  info("正在自动重启 dsh web 进程");
  return respawnDshWeb();
}

/**
 * Stops the current dsh web child (if any), starts a fresh one and re-trades its
 * token for a session cookie. Shared by the rate-limited restart above and by the
 * crash handler, so a wedged process and a dead one heal the same way.
 */
function respawnDshWeb() {
  if (dshRestartPromise) return dshRestartPromise;
  const pending = (async () => {
    closeDshSocket();
    await stopDshProcess();
    const url = await startDsh();
    applyDshUrl(url);
    const cookie = await requestSessionCookie();
    if (!cookie) throw codedError("startup", "dsh 重启后未能取得 API 会话");
    apiCookie = cookie;
    info("dsh web 已重启并重新认证");
    return true;
  })();
  dshRestartPromise = pending.finally(() => {
    if (dshRestartPromise === pending) dshRestartPromise = null;
  });
  return dshRestartPromise;
}

/** Re-attaches the mux right after a respawn instead of waiting for the retry timer. */
async function reconnectAfterRespawn() {
  if (shuttingDown) return;
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  try {
    await connectTransport();
    reconnectAttempts = 0;
    if (outageActive) {
      outageActive = false;
      streamErrorAnnounced = false;
      emit("stream.recovered", {});
    }
    info("已连接到重启后的 dsh");
  } catch (error) {
    info(`dsh 重启后重新连接失败：${describeError(error)}`);
    scheduleReconnect();
  }
}

/**
 * dsh dying is the commonest outage, and respawning it inside the live bridge is far
 * cheaper than letting the app tear down the whole rootfs. Bounded, because a dsh that
 * crashes on boot must fall through to the app-level restart instead of looping.
 */
function scheduleDshRespawn(reason) {
  if (shuttingDown) return false;
  // A respawn is already queued; it owns the recovery from here.
  if (dshRespawnTimer) return true;
  const now = Date.now();
  if (now - lastDshCrashAt > DSH_CRASH_WINDOW_MS) dshCrashCount = 0;
  if (dshCrashCount >= DSH_CRASH_LIMIT) return false;
  dshCrashCount += 1;
  lastDshCrashAt = now;
  const delay = 1_000 * 2 ** (dshCrashCount - 1);
  info(`${reason}，${delay / 1000} 秒后自动重启 dsh web（第 ${dshCrashCount} 次）`);
  emit("dsh.restarting", { attempt: dshCrashCount, max: DSH_CRASH_LIMIT, reason });
  dshRespawnTimer = setTimeout(() => {
    dshRespawnTimer = null;
    if (shuttingDown) return;
    respawnDshWeb()
      .then(() => reconnectAfterRespawn())
      .catch((error) => {
        info(`自动重启 dsh 失败：${describeError(error)}`);
        if (!scheduleDshRespawn("dsh 重启失败")) {
          emit("fatal", { message: `自动重启 dsh 失败：${describeError(error)}` });
          shutdown(1);
        }
      });
  }, delay);
  dshRespawnTimer.unref?.();
  return true;
}

/** Drops the mux socket without scheduling a competing reconnect. */
function closeDshSocket() {
  const current = socket;
  socket = null;
  eventsClientId = null;
  controlStreamId = null;
  if (current) {
    try {
      current.close();
    } catch {
      // ignore
    }
  }
}

// ---------------------------------------------------------------------------
// Forwarded host events and waterfalls
// ---------------------------------------------------------------------------

function openEventsStream() {
  return new Promise((resolve, reject) => {
    let settled = false;
    const streamId = randomUUID();
    const timer = setTimeout(() => {
      settle(codedError("transport", "dsh 事件通道握手超时"));
    }, 15_000);
    const settle = (error, value) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      if (error) reject(error);
      else resolve(value);
    };

    streams.set(streamId, {
      onItem(value) {
        const type = value?.type;
        if (type === "ready") {
          eventsClientId = String(value.clientId || "");
          settle(null, value);
          return;
        }
        if (type === "emit") {
          routeEmit(value.event, value.args);
          return;
        }
        if (type === "waterfall") {
          routeWaterfall(value);
          return;
        }
        if (type === "cancel") {
          settleCancelledWaterfall(value.eventId);
        }
      },
      onEnd() {
        settle(codedError("transport", "dsh 事件通道已关闭"));
        recoverEventsStream();
      },
      onError(error) {
        settle(codedError("transport", describeError(error)));
        recoverEventsStream();
      },
    });

    socket.send(
      JSON.stringify({
        type: "open",
        streamId,
        endpoint: "$events",
        payload: { args: {} },
      })
    );
  });
}

/**
 * The forwarded-event stream can end while the WebSocket itself stays healthy
 * (for example when the Host event source is restarted). Re-open only that
 * logical stream; a dead socket is handled by {@link scheduleReconnect}.
 */
function recoverEventsStream() {
  eventsClientId = null;
  if (shuttingDown) return;
  if (!socket) {
    scheduleReconnect();
    return;
  }
  setTimeout(() => {
    if (shuttingDown || !socket || eventsClientId !== null) return;
    reopenEventsStream().catch((error) => {
      info(`reopening Remote event stream failed: ${describeError(error)}`);
      scheduleReconnect();
    });
  }, 500).unref?.();
}

function reopenEventsStream() {
  return openEventsStream().then(() => {
    info("Remote event stream reopened");
    // Any waterfall that is still pending is re-delivered by the fresh stream,
    // but panels the app already shows would otherwise stay invisible to the
    // new client. Pending entries are kept so responses still correlate.
  });
}

function routeEmit(event, args) {
  const argv = Array.isArray(args) ? args : [];
  switch (event) {
    case "api-session/added": {
      const summary = argv[0] && typeof argv[0] === "object" ? argv[0] : {};
      emit("session.added", {
        sessionId: String(summary.sessionId ?? ""),
        blank: Boolean(summary.blank),
        parentId: summary.parentSessionId ?? null,
        cwd: summary.cwd ?? null,
        agentPreset: summary.agentPreset ?? null,
      });
      return;
    }
    case "api-session/removed":
      emit("session.removed", { sessionId: String(argv[0] ?? "") });
      return;
    case "api-session/status":
      emit("session.status", {
        sessionId: String(argv[0] ?? ""),
        running: Boolean(argv[1]),
      });
      return;
    case "api-session/error":
      emit("session.error", {
        sessionId: String(argv[0] ?? ""),
        message: describeError(argv[1]) || "Agent 运行失败",
      });
      return;
    case "commands/change":
      emit("commands.changed", {});
      return;
    case "agent-preset/selected":
      emit("commands.changed", {
        sessionId: String(argv[0] ?? ""),
      });
      emit("skills.changed", {
        sessionId: String(argv[0] ?? ""),
      });
      return;
    case "plugin-manager/changed":
      // A manager operation can add or remove whole skill providers and command
      // packages; the desktop client clears every cached catalog in response.
      emit("commands.changed", {});
      emit("skills.changed", {});
      return;
    case "llm/adapters-updated":
    case "credentials/record-updated":
    case "credentials/reference-updated":
      emit("models.changed", {});
      return;
    default:
      // Everything else is a host surface this app does not render. Keep it out
      // of the chat; the diagnostics log stays readable.
      return;
  }
}

function routeWaterfall(frame) {
  const eventId = String(frame?.eventId ?? "");
  const sessionId = String(frame?.agentId ?? "");
  const request = frame?.request && typeof frame.request === "object" ? frame.request : {};
  if (!eventId) return;

  if (frame.event === "approval/request") {
    pendingWaterfalls.set(eventId, { eventId, kind: "approval", sessionId });
    emit("approval.requested", {
      rpcId: eventId,
      sessionId,
      approvalId: eventId,
      toolName: String(request.toolName || "工具"),
      callId: request.callId ?? null,
      reason:
        (typeof request.reason === "string" && request.reason) ||
        request.displayReason?.zh ||
        request.displayReason?.en ||
        null,
    });
    return;
  }

  if (frame.event === "user-questions/request") {
    pendingWaterfalls.set(eventId, { eventId, kind: "question", sessionId });
    emit("question.requested", {
      rpcId: eventId,
      sessionId,
      questions: normalizeQuestions(request.questions),
    });
    return;
  }

  // Any other waterfall belongs to a surface this app does not implement. Delegate
  // it so the host falls back to its own behaviour instead of hanging.
  answerRemoteEvent(eventId, { kind: "next" }).catch((error) => {
    info(`delegating waterfall ${frame.event} failed: ${describeError(error)}`);
  });
}

function settleCancelledWaterfall(eventId) {
  const pending = pendingWaterfalls.get(eventId);
  if (!pending) return;
  pendingWaterfalls.delete(eventId);
  if (pending.kind === "approval") {
    emit("approval.resolved", {
      sessionId: pending.sessionId,
      approvalId: eventId,
      outcome: "cancelled",
    });
  } else {
    emit("question.resolved", {
      sessionId: pending.sessionId,
      rpcId: eventId,
      outcome: "cancelled",
    });
  }
}

/** POST one Remote event outcome through dsh's `$events/result` unary RPC. */
async function answerRemoteEvent(eventId, outcome) {
  if (!eventsClientId) {
    throw codedError("transport", "dsh 事件通道尚未就绪");
  }
  await callDsh("$events/result", {
    clientId: eventsClientId,
    eventId,
    outcome,
  });
}

async function respond(rpcId, value) {
  if (typeof rpcId !== "string" || !rpcId) {
    throw badRequest("missing rpcId");
  }
  const pending = pendingWaterfalls.get(rpcId);
  if (!pending) {
    // Already settled (another surface answered, or a reconnect dropped it). The
    // app has removed the panel locally, so this is not an error.
    return { accepted: false, reason: "not-pending" };
  }

  let outcomeValue;
  if (pending.kind === "approval") {
    const outcome = typeof value?.outcome === "string" ? value.outcome : value;
    if (!["allowed-once", "rejected", "cancelled", "unavailable"].includes(outcome)) {
      throw badRequest("approval outcome must be allowed-once or rejected");
    }
    outcomeValue = outcome;
  } else {
    const answer = value?.answer ?? value;
    if (!answer || !Array.isArray(answer.answers)) {
      throw badRequest("question answer must contain an answers array");
    }
    outcomeValue = {
      answers: answer.answers.map((item) => ({
        id: String(item?.id ?? ""),
        selected: Array.isArray(item?.selected) ? item.selected.map(String) : [],
        ...(typeof item?.custom === "string" && item.custom
          ? { custom: item.custom }
          : {}),
      })),
    };
  }

  pendingWaterfalls.delete(rpcId);
  try {
    await answerRemoteEvent(pending.eventId, { kind: "result", value: outcomeValue });
  } catch (error) {
    pendingWaterfalls.set(rpcId, pending);
    throw error;
  }
  return { accepted: true };
}

// ---------------------------------------------------------------------------
// Live control stream (projections for every session)
// ---------------------------------------------------------------------------

function openControlStream() {
  closeStream(controlStreamId);
  controlStreamId = null;
  if (!socket) return;
  try {
    controlStreamId = openLogicalStream("session/control", {}, {
      onItem(value) {
        if (value?.type === "baseline") {
          const projections = value.value?.projections;
          if (projections && typeof projections === "object") {
            for (const [sessionId, baseline] of Object.entries(projections)) {
              applyProjectionValues(sessionId, baseline?.values);
            }
          }
          return;
        }
        if (value?.type === "projection") {
          applyProjection(String(value.sessionId ?? ""), String(value.key ?? ""), value.value);
        }
      },
      onEnd() {
        controlStreamId = null;
        if (!shuttingDown && socket) {
          setTimeout(() => {
            if (!shuttingDown && socket && !controlStreamId) openControlStream();
          }, 500).unref?.();
        }
      },
      onError(error) {
        controlStreamId = null;
        info(`session control stream failed: ${describeError(error)}`);
        if (!shuttingDown && socket) {
          setTimeout(() => {
            if (!shuttingDown && socket && !controlStreamId) openControlStream();
          }, 500).unref?.();
        }
      },
    });
  } catch (error) {
    info(`opening session control stream failed: ${describeError(error)}`);
  }
}

function cacheProjectionValues(sessionId, values) {
  if (!sessionId || !values || typeof values !== "object") return;
  let cache = projectionCache.get(sessionId);
  if (!cache) {
    cache = new Map();
    projectionCache.set(sessionId, cache);
  }
  for (const [key, value] of Object.entries(values)) cache.set(key, value);
}

function applyProjectionValues(sessionId, values) {
  cacheProjectionValues(sessionId, values);
  if (!values || typeof values !== "object") return;
  for (const [key, value] of Object.entries(values)) {
    if (key === "inbox") emitQueue(sessionId, value);
    else if (key === "permissions") emitPermissions(sessionId, value);
    else if (key === "title" || key === "todos" || key === "plan") {
      emit("session.projection", { sessionId, key, value });
    }
  }
}

function applyProjection(sessionId, key, value) {
  if (!sessionId || !key) return;
  let cache = projectionCache.get(sessionId);
  if (!cache) {
    cache = new Map();
    projectionCache.set(sessionId, cache);
  }
  cache.set(key, value);
  if (key === "inbox") emitQueue(sessionId, value);
  else if (key === "permissions") emitPermissions(sessionId, value);
  else if (key === "title" || key === "todos" || key === "plan") {
    emit("session.projection", { sessionId, key, value });
  }
}

function emitQueue(sessionId, inbox) {
  emit("session.queue", { sessionId, items: queueItems(inbox) });
}

function emitPermissions(sessionId, value) {
  const mode =
    typeof value === "string"
      ? value
      : value && typeof value === "object"
        ? value.currentValue ?? value.mode ?? value.permissionMode ?? null
        : null;
  if (mode == null) return;
  emit("session.projection", { sessionId, key: "permissions", value: { mode } });
}

// ---------------------------------------------------------------------------
// Session follow (live durable events + live assistant chunks)
// ---------------------------------------------------------------------------

function finishFollow(follow, error) {
  if (!follow || follow.closed) return;
  follow.closed = true;
  follow.settle?.(error);
}

function closeFollow() {
  const follow = activeFollow;
  if (!follow) return;
  activeFollow = null;
  finishFollow(follow, codedError("transport", "会话跟随已关闭"));
  if (follow.streamId) closeStream(follow.streamId);
  attempts.clear();
}

/**
 * Open one `session/follow`. The opening snapshot carries the cursor used by
 * `session/page` and the projection baseline; its records are intentionally
 * ignored because the app loads history through `session.history`.
 *
 * `fresh: true` closes the previous follow even for the same session, so the
 * cursor is the current log tail (activation/reconnect must not page from a
 * stale cut).
 */
function startFollow(sessionId, options = {}) {
  const fresh = options.fresh === true;
  // A follow that has not delivered its snapshot yet is still a valid fresh cut: joining
  // it avoids closing the very handshake another request is awaiting (activation runs
  // history and session refresh concurrently).
  if (
    activeFollow &&
    !activeFollow.closed &&
    activeFollow.sessionId === sessionId &&
    (!fresh || activeFollow.cursor < 0)
  ) {
    return activeFollow.ready;
  }

  let inheritedSeq = 0;
  if (activeFollow && activeFollow.sessionId === sessionId) {
    inheritedSeq = activeFollow.maxEmittedSeq;
  }
  closeFollow();
  attempts.clear();
  lastFollowSessionId = sessionId;

  const follow = {
    sessionId,
    streamId: null,
    cursor: -1,
    projections: null,
    maxEmittedSeq: inheritedSeq,
    closed: false,
    ready: null,
    settle: null,
  };

  let resolveReady;
  let rejectReady;
  let settled = false;
  let timer = null;
  follow.ready = new Promise((resolve, reject) => {
    resolveReady = resolve;
    rejectReady = reject;
  });
  // A follow may be opened only to resume live delivery; nobody awaits it then.
  follow.ready.catch(() => {});
  follow.settle = (error) => {
    if (settled) return;
    settled = true;
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
    if (error) rejectReady(error);
    else resolveReady(follow);
  };
  timer = setTimeout(() => {
    timer = null;
    finishFollow(follow, codedError("transport", "会话跟随握手超时"));
  }, 20_000);
  timer.unref?.();

  try {
    follow.streamId = openLogicalStream(
      "session/follow",
      {
        request: {
          address: { kind: "session", sessionId },
          assistantStream: true,
          maxMessages: 4,
        },
      },
      {
        onItem(value) {
          if (value?.type === "snapshot") {
            follow.cursor = Number(value.cursor ?? -1);
            follow.projections = value.projections ?? null;
            const attempt = value.assistantStream?.activeAttempt;
            if (attempt && attempt.attemptId != null) {
              attempts.set(String(attempt.attemptId), {
                turn: Number(attempt.turn) || 0,
                step: Number(attempt.step) || 0,
              });
            }
            follow.settle?.();
            return;
          }
          if (value?.type === "event") {
            feedFollowEvent(follow, value.event);
            return;
          }
          if (value?.type === "assistant-stream") {
            feedAssistantFrame(follow, value.frame);
          }
        },
        onEnd() {
          finishFollow(follow, codedError("transport", "会话跟随已结束"));
          if (activeFollow === follow) activeFollow = null;
          scheduleFollowReopen(follow);
        },
        onError(error) {
          finishFollow(follow, codedError("transport", describeError(error)));
          if (activeFollow === follow) activeFollow = null;
          scheduleFollowReopen(follow);
        },
      }
    );
  } catch (error) {
    finishFollow(follow, error);
    throw error;
  }

  activeFollow = follow;
  return follow.ready;
}

function scheduleFollowReopen(follow) {
  if (shuttingDown || !socket || lastFollowSessionId !== follow.sessionId) return;
  setTimeout(() => {
    if (!shuttingDown && socket && !activeFollow && lastFollowSessionId === follow.sessionId) {
      startFollow(follow.sessionId, { fresh: true }).catch((error) => {
        info(`重新跟随会话失败: ${describeError(error)}`);
      });
    }
  }, 1_000).unref?.();
}

function feedFollowEvent(follow, event) {
  if (!event || typeof event !== "object") return;
  const seq = typeof event.seq === "number" ? event.seq : null;
  if (seq != null && seq <= follow.maxEmittedSeq) return;
  if (seq != null && typeof event.seq === "number") {
    follow.maxEmittedSeq = Math.max(follow.maxEmittedSeq, seq);
  }
  const normalized = normalizeEvent(follow.sessionId, event, null);
  if (!normalized) return;
  emit("session.event", normalized);
}

function feedAssistantFrame(follow, frame) {
  if (!frame || typeof frame !== "object") return;
  const attemptId = frame.attemptId == null ? "" : String(frame.attemptId);
  if (!attemptId) return;

  if (frame.type === "start") {
    attempts.set(attemptId, {
      turn: Number(frame.turn) || 0,
      step: Number(frame.step) || 0,
    });
    return;
  }
  if (frame.type === "chunk") {
    const attempt = attempts.get(attemptId);
    if (!attempt) return;
    emit("session.event", {
      sessionId: follow.sessionId,
      seq: null,
      time: Number(frame.time) || null,
      dshType: "assistant/assistant-stream",
      kind: "assistant.delta",
      turn: attempt.turn,
      step: attempt.step,
      chunk: frame.chunk,
    });
    return;
  }
  if (frame.type === "end") {
    attempts.delete(attemptId);
  }
}

// ---------------------------------------------------------------------------
// dsh process
// ---------------------------------------------------------------------------

/**
 * A bridge killed with SIGKILL (Android's stop, a crash) cannot stop the dsh web process
 * it spawned, and an orphan keeps the profile busy and its port bound. The pid is
 * recorded so the next bridge retires exactly that process, never an unrelated one.
 */
const DSH_PID_FILE = `${WORKDIR}/.kingkang-dsh.pid`;

function recordDshPid(pid) {
  try {
    writeFileSync(DSH_PID_FILE, String(pid));
  } catch (error) {
    info(`记录 dsh 进程号失败：${describeError(error)}`);
  }
}

function forgetDshPid(pid) {
  try {
    if (readFileSync(DSH_PID_FILE, "utf8").trim() === String(pid)) unlinkSync(DSH_PID_FILE);
  } catch {
    // Nothing recorded, or the file is already gone.
  }
}

/**
 * dsh's atomic writer only takes over a `<file>.lock` sibling when it records the dead
 * holder's pid. A process killed between creating the lock and writing that record
 * leaves an empty lock, which every later writer waits on until it times out: settings
 * changes (including the theme sync), plugin installs and updates then fail for good.
 * Called before dsh starts, when no writer can own a lock in this profile.
 */
function clearIncompleteWriteLocks() {
  let names;
  try {
    names = readdirSync(PROFILE_DIR);
  } catch {
    return;
  }
  for (const name of names) {
    if (!name.endsWith(".lock")) continue;
    const file = `${PROFILE_DIR}/${name}`;
    try {
      if (readFileSync(file).length === 0) {
        unlinkSync(file);
        info(`已清理损坏的写入锁 ${name}`);
      }
    } catch {
      // Another writer removed it, or it is unreadable: nothing to repair.
    }
  }
}

/**
 * dsh runs `pnpm` from PATH for every plugin operation. The Harness installer installs
 * pnpm into the Node prefix and links it into place, but on this sandbox that install
 * can leave the package extracted while the bin link never appears, which makes every
 * plugin install and update end in "pnpm was not found". Restore the link from the
 * package's own entry point while the guest is idle.
 */
function ensurePnpmLink() {
  const target = "/opt/kingkang/node/lib/node_modules/pnpm/bin/pnpm.cjs";
  if (!existsSync(target)) return;
  for (const link of ["/opt/kingkang/node/bin/pnpm", "/usr/local/bin/pnpm"]) {
    let current = null;
    try {
      current = readlinkSync(link);
    } catch {
      // Missing, or a regular file someone placed there on purpose.
    }
    if (current === target) continue;
    if (current === null && existsSync(link)) continue;
    try {
      unlinkSync(link);
    } catch {
      // Nothing to replace.
    }
    try {
      symlinkSync(target, link);
      info(`已恢复 pnpm 入口 ${link}`);
    } catch (error) {
      info(`恢复 pnpm 入口失败：${describeError(error)}`);
    }
  }
}

function retireStaleDsh() {
  let recorded;
  try {
    recorded = Number(readFileSync(DSH_PID_FILE, "utf8").trim());
  } catch {
    return;
  }
  if (!Number.isInteger(recorded) || recorded <= 1 || recorded === process.pid) return;
  let cmdline = "";
  try {
    cmdline = readFileSync(`/proc/${recorded}/cmdline`, "utf8");
  } catch {
    forgetDshPid(recorded);
    return;
  }
  // Pids get recycled; only a process that still looks like dsh is ours to stop.
  if (!cmdline.split("\u0000").some((part) => part.includes("dsh/"))) {
    forgetDshPid(recorded);
    return;
  }
  try {
    process.kill(recorded, "SIGTERM");
    info(`已清理上次遗留的 dsh 进程 ${recorded}`);
  } catch {
    // Already gone.
  }
  forgetDshPid(recorded);
}

function startDsh() {
  return new Promise((resolve, reject) => {
    dshVersion = readDshVersion();
    const env = {
      ...process.env,
      HOME: WORKDIR,
      TERM: "xterm-256color",
      LANG: "C.UTF-8",
      PATH,
    };
    const child = spawn(
      DSH_BIN,
      [
        "--profile",
        PROFILE_NAME,
        "--host",
        "127.0.0.1",
        "--port",
        "0",
        "--trusted-host",
        "127.0.0.1",
        "--trusted-host",
        "localhost",
        "--no-open",
      ],
      { cwd: WORKDIR, env, stdio: ["ignore", "pipe", "pipe"] }
    );
    dshProcess = child;
    recordDshPid(child.pid);

    let settled = false;
    // Set when this start attempt was already given up on, so the kill it triggers
    // does not look like a crash worth respawning.
    let abandoned = false;
    let output = "";
    const timer = setTimeout(() => {
      if (settled) return;
      settled = true;
      abandoned = true;
      try {
        child.kill("SIGTERM");
      } catch {
        // ignore
      }
      reject(codedError("startup", "dsh web 启动超时"));
    }, 120_000);

    const inspect = (chunk) => {
      const text = String(chunk);
      output = (output + text).slice(-8_000);
      process.stderr.write(text);
      if (!settled) {
        const match = output.match(/dsh web:\s+(https?:\/\/[^\s)]+)/);
        if (match) {
          settled = true;
          clearTimeout(timer);
          info("dsh web process is ready");
          resolve(match[1]);
        }
        return;
      }
      // dsh may restart its own web server, printing a new port and token. Follow it
      // rather than staying pinned to a URL that no longer answers.
      const latest = text.match(/dsh web:\s+(https?:\/\/[^\s)]+)/);
      if (latest && latest[1] !== applicationUrl) {
        info("dsh web 已更换地址，正在重新连接");
        applyDshUrl(latest[1]);
        closeDshSocket();
        refreshAuth()
          .then(() => scheduleReconnect())
          .catch((error) => info(`跟随新的 dsh 地址失败：${describeError(error)}`));
      }
    };

    child.stdout.on("data", inspect);
    child.stderr.on("data", inspect);
    child.on("error", (error) => {
      if (!settled) {
        settled = true;
        abandoned = true;
        clearTimeout(timer);
        reject(codedError("startup", `无法启动 dsh: ${error.message}`));
      }
    });
    child.on("exit", (code, signal) => {
      if (dshProcess === child) dshProcess = null;
      forgetDshPid(child.pid);
      // A replacement we asked for is not a crash; the caller drives the recovery.
      if (shuttingDown || restartingDsh || abandoned) return;
      const detail = output.trim().split("\n").slice(-6).join("\n");
      const reason = `dsh 已退出（code=${code ?? "null"}, signal=${signal ?? "null"}）`;
      if (!settled) {
        // It never answered: fail the start now instead of racing a second dsh
        // against the caller's 120 s clock.
        settled = true;
        clearTimeout(timer);
        reject(codedError("startup", reason + (detail ? `\n${detail}` : "")));
        return;
      }
      // Heal a crash here first: the archive, the bridge and the rootfs are all still
      // up, so only dsh has to come back. The app-level restart stays the last resort.
      if (scheduleDshRespawn(reason)) return;
      emit("fatal", { message: reason + (detail ? `\n${detail}` : "") });
      shutdown(1);
    });
  });
}

function readDshVersion() {
  try {
    const result = spawnSync(DSH_BIN, ["--version"], {
      cwd: WORKDIR,
      env: {
        ...process.env,
        HOME: WORKDIR,
        PATH,
      },
      encoding: "utf8",
      timeout: 15_000,
    });
    const line = String(result.stdout || "")
      .split("\n")
      .map((value) => value.trim())
      .find(Boolean);
    return line || null;
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// App-facing methods
// ---------------------------------------------------------------------------

/**
 * History is a `session/page` over the cut captured by a fresh `session/follow`.
 * The follow stays open after the page, so live events and assistant chunks keep
 * flowing while the app renders the durable tail.
 */
async function sessionHistory(payload) {
  const sessionId = String(payload?.sessionId || "").trim();
  if (!sessionId) throw badRequest("sessionId is required");
  const beforeSeq =
    payload?.beforeSeq == null ? null : Number(payload.beforeSeq);
  if (beforeSeq !== null && !Number.isFinite(beforeSeq)) {
    throw badRequest("beforeSeq must be a number");
  }

  const follow = await startFollow(sessionId, { fresh: beforeSeq === null });

  const request = {
    address: { kind: "session", sessionId },
    throughSeq: Number.isFinite(follow.cursor) ? follow.cursor : -1,
    maxMessages: clampInt(payload?.maxMessages, 1, HISTORY_MAX, HISTORY_DEFAULT),
  };
  if (beforeSeq !== null) request.beforeSeq = beforeSeq;

  const page = await callDsh("session/page", { request });
  const records = Array.isArray(page?.records) ? page.records : [];
  const events = [];
  for (const record of records) {
    const raw = record?.type === "event" ? record.event : record;
    const normalized = normalizeEvent(sessionId, raw, null);
    if (normalized) events.push(normalized);
  }
  return {
    events,
    hasMore: Boolean(page?.hasMore),
    projections: beforeSeq === null ? follow.projections ?? null : null,
  };
}

function subagentAddress(payload) {
  const parentSessionId = String(payload?.parentSessionId || "").trim();
  const childSessionId = String(payload?.childSessionId || "").trim();
  if (!parentSessionId) throw badRequest("parentSessionId is required");
  if (!childSessionId) throw badRequest("childSessionId is required");
  return { parentSessionId, childSessionId };
}

async function readProjections(sessionId) {
  if (!sessionId) return null;
  const value = await callDsh("session/projections", { request: { sessionId } });
  const values = value && typeof value.values === "object" ? value.values : null;
  if (!values) return null;
  return {
    asOfSeq: Number.isFinite(value.asOfSeq) ? value.asOfSeq : null,
    values,
  };
}

/**
 * dsh >= 0.2 keeps the child catalog in the parent's own `subagentCatalog`
 * projection (folded from `subagent/catalog`), and the child's live/idle state in
 * its `subagentTiming` projection. Reading projections avoids activating either
 * side; the per-child reads are bounded by the direct-child count.
 */
async function subagentList(payload) {
  const parentSessionId = String(payload?.parentSessionId || "").trim();
  if (!parentSessionId) throw badRequest("parentSessionId is required");
  const parent = await readProjections(parentSessionId);
  const catalog = Array.isArray(parent?.values?.subagentCatalog)
    ? parent.values.subagentCatalog
    : [];

  const entries = await Promise.all(
    catalog.map(async (row) => {
      const id = String(row?.id || "");
      const mode = String(row?.mode || "one-shot");
      if (!id) return null;
      if (mode === "external" || mode === "unknown") {
        return { kind: "diagnostic", id, reason: mode };
      }
      let child = null;
      try {
        child = await readProjections(id);
      } catch (error) {
        // One unreadable child must not hide its healthy siblings.
        info(`subagent ${id.slice(0, 8)} projections failed: ${describeError(error)}`);
      }
      const timing = child?.values?.subagentTiming;
      const label = typeof row?.label === "string" ? row.label : undefined;
      return {
        kind: "child",
        id,
        mode,
        ...(label ? { label } : {}),
        activity: timing && timing.active ? "running" : "inactive",
        hasChildren: Array.isArray(child?.values?.subagentCatalog)
          ? child.values.subagentCatalog.length > 0
          : false,
      };
    })
  );
  return {
    entries: entries.filter(Boolean),
    parentAvailable: true,
  };
}

/**
 * A child transcript is a persisted page over a subagent address. `throughSeq`
 * comes from the child's own projection cursor, so no follow stream is replaced —
 * the app's main session keeps its live subscription while a child is open.
 */
async function subagentHistory(payload) {
  const address = subagentAddress(payload);
  let projections = await readProjections(address.childSessionId);
  // dsh pages nothing for `throughSeq: -1`, and a child it has not indexed yet can
  // answer without a projection cursor. Re-read once before paging so a just-spawned
  // grandchild does not open on a blank transcript.
  if (projections?.asOfSeq == null) {
    await new Promise((resolve) => setTimeout(resolve, 300));
    try {
      projections = (await readProjections(address.childSessionId)) ?? projections;
    } catch {
      // Keep the first answer; paging below still surfaces the real failure.
    }
  }
  const request = {
    // `unknown` is dsh's address-side wildcard: it validates a child without
    // repeating a mode the parent catalog already fixed.
    address: { kind: "subagent", ...address, mode: "unknown" },
    throughSeq: projections?.asOfSeq ?? -1,
    maxMessages: clampInt(payload?.maxMessages, 1, HISTORY_MAX, HISTORY_DEFAULT),
  };
  if (payload?.beforeSeq != null) {
    const beforeSeq = Number(payload.beforeSeq);
    if (!Number.isFinite(beforeSeq)) throw badRequest("beforeSeq must be a number");
    request.beforeSeq = beforeSeq;
  }
  const page = await callDsh("session/page", { request });
  const records = Array.isArray(page?.records) ? page.records : [];
  const events = [];
  for (const record of records) {
    const raw = record?.type === "event" ? record.event : record;
    const normalized = normalizeEvent(address.childSessionId, raw, null);
    if (normalized) events.push(normalized);
  }
  return {
    events,
    hasMore: Boolean(page?.hasMore),
    projections: null,
  };
}

async function sessionModels(payload) {
  const sessionId = String(payload?.sessionId || "");
  const catalog = await callDsh("session/modelCatalog", {});
  let selection = sessionId ? projectionCache.get(sessionId)?.get("modelSelection") : null;
  if (sessionId && !selection) {
    try {
      const projections = await callDsh("session/projections", { request: { sessionId } });
      if (projections && typeof projections === "object" && projections.values) {
        cacheProjectionValues(sessionId, projections.values);
        selection = projections.values.modelSelection ?? null;
      }
    } catch (error) {
      info(`reading model selection failed: ${describeError(error)}`);
    }
  }
  const current = selection?.next ?? selection?.lastUsed ?? catalog?.default ?? null;
  return {
    current: current ?? null,
    groups: Array.isArray(catalog?.groups) ? catalog.groups : [],
    routable:
      Array.isArray(catalog?.routableProviders) && catalog.routableProviders.length > 0,
    failures: Array.isArray(catalog?.failures) ? catalog.failures : [],
  };
}

async function dispatch(method, params) {
  const payload = params && typeof params === "object" ? params : {};
  switch (method) {
    case "ping":
      return { alive: true, port: baseUrl ? Number(new URL(baseUrl).port) : null };
    case "connection.refresh": {
      const refreshed = await refreshAuth();
      if (!socket || socket.readyState !== SOCKET_OPEN) {
        if (reconnectTimer) {
          clearTimeout(reconnectTimer);
          reconnectTimer = null;
        }
        await connectTransport();
      }
      return {
        refreshed,
        connected: Boolean(socket && socket.readyState === SOCKET_OPEN),
      };
    }
    case "session.list": {
      const value = await callDsh("session/list", { _request: {} });
      const items = value?.items || [];
      return { sessions: items.map(normalizeSession) };
    }
    case "session.create": {
      const request = {};
      if (payload.sessionId) request.sessionId = String(payload.sessionId);
      if (payload.cwd) request.cwd = String(payload.cwd);
      if (payload.workspaceId) request.workspaceId = String(payload.workspaceId);
      if (payload.agentPreset) request.agentPreset = String(payload.agentPreset);
      const value = await callDsh("session/create", { request });
      return {
        sessionId: String(value?.sessionId ?? ""),
        agentPreset: value?.agentPreset ?? null,
      };
    }
    case "session.history":
      return sessionHistory(payload);
    case "session.prompt": {
      const content = Array.isArray(payload.content)
        ? payload.content
        : [{ type: "text", text: String(payload.text || "") }];
      const value = await callDsh("session/prompt", {
        request: {
          // The app may mint the id so its provisional bubble and the durable
          // `user/message` echo share a correlation key (an image-only prompt has no
          // text to match on).
          requestId:
            typeof payload.rpcId === "string" && payload.rpcId
              ? payload.rpcId
              : randomUUID(),
          sessionId: String(payload.sessionId || ""),
          mode: payload.mode === "steer" ? "steer" : "queue",
          content,
        },
      });
      return value ?? { accepted: true };
    }
    case "session.cancel":
      return callDsh("session/cancel", {
        request: { sessionId: String(payload.sessionId || "") },
      });
    case "session.updateQueue":
      return callDsh("session/updateQueue", {
        request: {
          sessionId: String(payload.sessionId || ""),
          itemId: String(payload.itemId || ""),
          action:
            payload.action && typeof payload.action === "object"
              ? payload.action
              : { kind: "remove" },
        },
      });
    case "session.attachment": {
      const value = await callDsh("session/attachment", {
        request: {
          sessionId: String(payload.sessionId || ""),
          attachmentId: String(payload.attachmentId || ""),
        },
      });
      return {
        attachment: value?.attachment ?? null,
        data: typeof value?.data === "string" ? value.data : "",
      };
    }
    case "session.models":
      return sessionModels(payload);
    case "session.selectModel": {
      const request = {
        sessionId: String(payload.sessionId || ""),
        provider: String(payload.provider || ""),
        model: String(payload.model || ""),
      };
      if (payload.reasoningEffort) request.reasoningEffort = String(payload.reasoningEffort);
      return callDsh("session/selectModel", { request });
    }
    case "session.rename":
      return callDsh("session/rename", {
        request: {
          sessionId: String(payload.sessionId || ""),
          title: String(payload.title || ""),
        },
      });
    case "session.fork": {
      const request = { sessionId: String(payload.sessionId || "") };
      if (payload.atSeq != null) request.atSeq = Number(payload.atSeq);
      return callDsh("session/fork", { request });
    }
    case "subagent.list":
      return subagentList(payload);
    case "subagent.history":
      return subagentHistory(payload);
    case "subagent.prompt": {
      const address = subagentAddress(payload);
      const content = Array.isArray(payload.content)
        ? payload.content
        : [{ type: "text", text: String(payload.text || "") }];
      const request = {
        ...address,
        // The control surface only accepts continuable addresses; a one-shot child
        // reports through its parent instead.
        mode: "continuable",
        delivery: payload.delivery === "steer" ? "steer" : "queue",
        requestId:
          typeof payload.rpcId === "string" && payload.rpcId
            ? payload.rpcId
            : randomUUID(),
        content,
      };
      const value = await callDsh("subagents/prompt", { request });
      return { messageId: value?.messageId ?? null };
    }
    case "subagent.interrupt": {
      const address = subagentAddress(payload);
      const value = await callDsh("subagents/interruptByParent", {
        ...address,
        mode: "continuable",
      });
      return { accepted: value?.accepted !== false };
    }
    case "command.list":
      return callDsh("commands/list", {
        agentId: String(payload.sessionId || payload.agentId || ""),
      });
    case "skill.list": {
      const value = await callDsh("skills/list", {
        request: { sessionId: String(payload.sessionId || "") },
      });
      return Array.isArray(value?.skills) ? value.skills : [];
    }
    case "command.execute": {
      const value = await callDsh("commands/execute", {
        agentId: String(payload.sessionId || payload.agentId || ""),
        line: String(payload.line || ""),
        submittedAttachments: Array.isArray(payload.submittedAttachments)
          ? payload.submittedAttachments
          : [],
      });
      // `undefined` is dsh's "no handler owns this line"; null preserves that
      // distinction across the line protocol instead of looking like `{}`.
      return value === undefined ? null : value;
    }
    case "web.url":
      return {
        url: applicationUrl || (baseUrl ? `${baseUrl}/` : null),
        port: baseUrl ? Number(new URL(baseUrl).port) : null,
      };
    case "workspace.list": {
      const frame = await firstStreamItem("workspace/follow", {});
      if (!frame || frame.type !== "baseline" || !frame.value) {
        throw codedError("transport", "workspace/follow 没有返回 baseline");
      }
      return normalizeWorkspaceBaseline(frame.value);
    }
    case "workspace.create": {
      const path = String(payload.path || "").trim();
      if (!path.startsWith("/")) throw badRequest("工作区必须是 Kali 中的绝对路径");
      // The app lets the user name a directory that does not exist yet; create it
      // first so dsh's own existence check sees a real workspace root.
      let directoryCreated = false;
      try {
        if (!existsSync(path)) {
          mkdirSync(path, { recursive: true });
          directoryCreated = true;
        }
      } catch (error) {
        throw codedError(
          "workspace",
          `无法创建工作区目录 ${path}：${error.message || error}`
        );
      }
      const value = await callDsh("workspace/create", {
        request: { path },
      });
      return {
        workspace: normalizeWorkspace(value?.workspace),
        created: Boolean(value?.created),
        directoryCreated,
      };
    }
    case "plugin.list": {
      const bundles = await callDsh("pluginManager/listBundles", {});
      const items = Array.isArray(bundles) ? bundles : [];
      // The inventory the user thinks of as "plugins": packages installed from the
      // registry into the profile, plus the switchable optional bundles dsh ships.
      // Optional bundles stay listed while off so the switch can turn them back on.
      // dsh's own composition (base, web app) is neither and stays out.
      const describe = (bundle) => ({
        name: String(bundle.name || ""),
        title:
          localizedText(bundle.meta?.title) ||
          String(bundle.name || ""),
        description:
          localizedText(bundle.meta?.description) ||
          String(bundle.description || ""),
        version: bundle.version ? String(bundle.version) : null,
        enabled: bundle.enabled !== false,
        removable: bundle.removable !== false,
        official: bundle.official === true,
      });
      // On-demand official catalog entries: not installed, but they name the exact
      // package to install, so the app can offer a one-tap install.
      const offerTarget = (bundle) =>
        bundle &&
        bundle.installed !== true &&
        bundle.installTarget &&
        typeof bundle.installTarget.spec === "string" &&
        bundle.installTarget.spec
          ? bundle.installTarget
          : null;
      const plugins = items
        .filter(
          (bundle) =>
            bundle &&
            (bundle.installed === true || bundle.optional === true) &&
            !offerTarget(bundle)
        )
        .map(describe)
        .filter((plugin) => plugin.name);
      const offers = items
        .filter((bundle) => offerTarget(bundle))
        .map((bundle) => {
          const target = offerTarget(bundle);
          return {
            ...describe(bundle),
            spec: String(target.spec),
            version: target.version
              ? String(target.version)
              : bundle.version
                ? String(bundle.version)
                : null,
          };
        })
        .filter((offer) => offer.name && offer.spec);
      return { plugins, offers };
    }
    case "plugin.inspect": {
      const spec = String(payload.spec || "").trim();
      if (!spec) throw codedError("invalid-argument", "缺少插件名称");
      const inspected = await callDsh("pluginManager/inspect", { spec }, 90_000);
      if (!inspected || typeof inspected !== "object") {
        throw codedError("dsh-error", "dsh 没有返回插件检查结果");
      }
      return inspected;
    }
    case "plugin.install": {
      const spec = String(payload.spec || "").trim();
      if (!spec) throw codedError("invalid-argument", "缺少插件名称");
      // Installations run pnpm inside Kali and can legitimately take minutes, so they
      // get their own budget instead of the bridge's default one-minute HTTP timeout.
      const requestId = randomUUID();
      const options = { enabled: payload.enabled !== false, requestId };
      let change;
      try {
        change = await callDsh(
          "pluginManager/installBundle",
          { spec, options },
          15 * 60_000
        );
      } catch (first) {
        // A dropped reply does not mean a failed install: the Host keeps running it.
        // Ask once for the settled outcome before relaying the original error.
        try {
          change = await callDsh(
            "pluginManager/waitForInstall",
            { requestId },
            15 * 60_000
          );
        } catch {
          change = null;
        }
        if (!change) throw first;
      }
      const error = change?.error;
      const errorText = error
        ? [error.code, error.diagnostic].filter(Boolean).join("：")
        : null;
      const failureKind = change?.packageResult?.kind
        ? String(change.packageResult.kind)
        : null;
      return {
        changed: change?.changed === true,
        application: String(change?.application || ""),
        restartRequired: change?.application === "restart-required",
        restartReason: change?.restartReason ? String(change.restartReason) : null,
        stage: change?.stage ? String(change.stage) : null,
        target: change?.target ? String(change.target) : null,
        enabled: change?.enabled !== false,
        error:
          errorText ||
          (change?.application === "failed"
            ? failureKind || "operation-error"
            : null),
        pendingBuilds: Array.isArray(change?.pendingBuilds)
          ? change.pendingBuilds.map(String)
          : [],
        bundle: change?.bundle ? String(change.bundle) : null,
        version: change?.version ? String(change.version) : null,
      };
    }
    case "plugin.update": {
      // dsh refuses to install a package its profile already records, so a plugin pinned
      // to an older dsh release cannot be repaired through pluginManager/installBundle.
      // dsh's own CLI forwards `plugin add` to pnpm, which does replace a recorded
      // dependency at a new exact version; run that, then let the app restart dsh.
      const name = String(payload.name || "").trim();
      const version = String(payload.version || "").trim();
      if (!/^(?:@[a-z0-9][\w.-]*\/)?[a-z0-9][\w.-]*$/i.test(name)) {
        throw codedError("invalid-argument", "插件名称无效");
      }
      if (!/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(version)) {
        throw codedError("invalid-argument", "插件版本无效");
      }
      // Only a package the profile already depends on may be updated here: this action
      // repairs a version pin, it is not a second way to install something new.
      if (!Object.hasOwn(readProfileDependencies(), name)) {
        throw codedError("invalid-argument", `${name} 不在 profile 的依赖中`);
      }
      let outcome = await runPluginUpdate(name, version);
      if (outcome.code !== 0 && /pnpm|Cannot find module|SyntaxError|ERR_MODULE_NOT_FOUND/i.test(outcome.output)) {
        // An interrupted harness install can leave pnpm's package truncated while its
        // entry file still exists; dsh then dies inside require(). Reinstall the package
        // once so an already-installed harness repairs itself instead of failing forever.
        info("插件更新失败，正在修复 pnpm 后重试");
        const repair = await repairPnpm();
        info(`pnpm 修复结果：exit=${repair.code ?? "null"}`);
        if (repair.code === 0) {
          ensurePnpmLink();
          outcome = await runPluginUpdate(name, version);
        }
      }
      if (outcome.code !== 0) {
        throw codedError(
          "install-failed",
          failureDetail(outcome.output, outcome.code)
        );
      }
      const recorded = readProfileDependencies()[name];
      return {
        changed: true,
        bundle: name,
        version: String(recorded || version),
        error: null,
      };
    }
    case "plugin.setEnabled": {
      const name = String(payload.name || "");
      if (!name) throw codedError("invalid-argument", "缺少插件名称");
      const change = await callDsh("pluginManager/setBundleEnabled", {
        name,
        enabled: payload.enabled === true,
      });
      const error = change?.error;
      const errorText = error
        ? [error.code, error.diagnostic].filter(Boolean).join("：")
        : null;
      return {
        changed: change?.changed === true,
        application: String(change?.application || ""),
        restartRequired: change?.application === "restart-required",
        restartReason: change?.restartReason ? String(change.restartReason) : null,
        stage: change?.stage ? String(change.stage) : null,
        target: change?.target ? String(change.target) : null,
        enabled: change?.enabled !== false,
        error: errorText,
      };
    }
    case "workspace.archiveSession":
      return callDsh("workspace/archiveSession", {
        request: {
          sessionId: String(payload.sessionId || ""),
          ...(payload.stopActivity === true ? { stopActivity: true } : {}),
        },
      });
    case "workspace.unarchiveSession":
      return callDsh("workspace/unarchiveSession", {
        request: { sessionId: String(payload.sessionId || "") },
      });
    case "workspace.pinSession":
      return callDsh("workspace/pinSession", {
        request: { sessionId: String(payload.sessionId || "") },
      });
    case "workspace.unpinSession":
      return callDsh("workspace/unpinSession", {
        request: { sessionId: String(payload.sessionId || "") },
      });
    case "credentials.describe": {
      const refs = Array.isArray(payload.refs)
        ? payload.refs.map((ref) => String(ref)).filter(Boolean)
        : [];
      if (refs.length === 0) return { refs: {} };
      const value = await callDsh("credentials/describe", { refs });
      return { refs: value && typeof value === "object" ? value : {} };
    }
    case "credentials.set":
      await callDsh("credentials/set", {
        ref: String(payload.ref || ""),
        value: String(payload.value || ""),
      });
      return { stored: true };
    case "credentials.unset":
      await callDsh("credentials/unset", { ref: String(payload.ref || "") });
      return { removed: true };
    case "permission.catalog":
      return callDsh("permissionPresets/catalog", {});
    case "agentPreset.list": {
      // dsh's roster carries every declaration, including ones that failed to load;
      // the app renders those as unavailable instead of hiding why they are missing.
      const value = await callDsh("agentPresets/list", {});
      const rows = Array.isArray(value?.presets) ? value.presets : [];
      // dsh reports a failed preset either as a message string or as a detail object;
      // both have to reach the app, or a broken mode would look selectable.
      const brokenReason = (broken) => {
        if (typeof broken === "string" && broken) return broken;
        if (broken && typeof broken === "object" && typeof broken.message === "string") {
          return broken.message;
        }
        return null;
      };
      return {
        presets: rows
          .map((row) => ({
            id: String(row?.id || ""),
            name: typeof row?.name === "string" ? row.name : "",
            description:
              typeof row?.description === "string" ? row.description : null,
            broken: brokenReason(row?.broken),
            isDefault: Boolean(row?.isDefault),
          }))
          .filter((row) => row.id),
      };
    }
    case "agentPreset.select": {
      // Only a session whose conversation has not started may change composition;
      // dsh answers `agent-preset/locked` otherwise and the app keeps the default.
      const sessionId = String(payload.sessionId || "");
      const agentPreset = String(payload.agentPreset || "");
      if (!sessionId || !agentPreset) {
        throw badRequest("sessionId 与 agentPreset 均为必填");
      }
      const value = await callDsh("agentPresets/select", {
        agentId: sessionId,
        agentPreset,
      });
      return { agentPreset: String(value || agentPreset) };
    }
    case "settings.describe":
      return callDsh("settings/describe", {});
    case "settings.mutate": {
      const args = {
        ns: String(payload.ns || ""),
        ops: Array.isArray(payload.ops) ? payload.ops : [],
      };
      if (Number.isFinite(payload.expectedRevision)) {
        args.expectedRevision = Number(payload.expectedRevision);
      }
      return callDsh("settings/mutate", args);
    }
    case "plugin.registry.get":
      return { registry: readPluginRegistry() };
    case "plugin.registry.set": {
      const registry = String(payload.registry || "").trim();
      if (registry && !/^https?:\/\/\S+$/i.test(registry)) {
        throw codedError("invalid-argument", "插件源必须是 http(s) 地址");
      }
      return { registry: writePluginRegistry(registry) };
    }
    case "respond":
      return respond(payload.rpcId, payload.value);
    default:
      throw codedError("unsupported-method", `unsupported method: ${method}`);
  }
}

// ---------------------------------------------------------------------------
// Plugin source (npm registry)
// ---------------------------------------------------------------------------

/**
 * pnpm — and with it dsh's plugin manager, which runs pnpm in the profile directory —
 * reads the project `.npmrc`, so the primary registry is one `registry=` line there.
 * A profile without that line gets Aliyun's mirror on startup, which keeps plugin
 * checks and installs fast on mainland networks. An explicit line always wins.
 */
function pluginRegistryFile() {
  return `${PROFILE_DIR}/.npmrc`;
}

/** Gives a fresh profile the app's default source once; never overwrites a choice. */
function ensureDefaultPluginRegistry() {
  if (readPluginRegistry()) return;
  try {
    writePluginRegistry(DEFAULT_PLUGIN_REGISTRY);
    info(`npm 源默认为 ${DEFAULT_PLUGIN_REGISTRY}`);
  } catch (error) {
    info(`写入默认 npm 源失败：${describeError(error)}`);
  }
}

function readPluginRegistry() {
  try {
    for (const line of readFileSync(pluginRegistryFile(), "utf8").split(/\r?\n/)) {
      const match = line.match(/^\s*registry\s*=\s*(\S+)\s*$/);
      if (match) return match[1];
    }
  } catch {
    // No profile .npmrc yet, or it is unreadable: dsh's default applies.
  }
  return "";
}

function writePluginRegistry(registry) {
  const file = pluginRegistryFile();
  let lines = [];
  try {
    lines = readFileSync(file, "utf8").split(/\r?\n/);
  } catch {
    // No .npmrc yet; one is created only when a registry is set.
  }
  const kept = lines
    .map((line) => line.trimEnd())
    .filter((line) => line !== "" && !/^\s*registry\s*=/.test(line));
  if (registry) kept.push(`registry=${registry}`);
  if (kept.length > 0) {
    mkdirSync(PROFILE_DIR, { recursive: true });
    writeFileSync(file, kept.join("\n") + "\n");
  } else {
    try {
      unlinkSync(file);
    } catch {
      // Nothing to remove.
    }
  }
  return registry;
}

/** The profile's recorded dependencies; empty when the profile has no readable manifest. */
function readProfileDependencies() {
  try {
    const manifest = JSON.parse(readFileSync(`${PROFILE_DIR}/package.json`, "utf8"));
    const dependencies = manifest?.dependencies;
    return dependencies && typeof dependencies === "object" ? dependencies : {};
  } catch {
    return {};
  }
}

/**
 * Runs dsh's own plugin command, which forwards `add` to the profile's pnpm. It is a
 * separate process from the live dsh web server; pnpm rewrites package.json and
 * node_modules under the same lock dsh's plugin manager uses, so a live install is safe.
 * A package update downloads new files, so it gets the same generous budget as installs.
 */
function runPluginUpdate(name, version) {
  return new Promise((resolve, reject) => {
    const child = spawn(
      DSH_BIN,
      [
        "plugin",
        "--profile",
        PROFILE_NAME,
        "add",
        `${name}@${version}`,
        "--save-exact",
      ],
      {
        cwd: WORKDIR,
        env: {
          ...process.env,
          HOME: WORKDIR,
          TERM: "dumb",
          LANG: "C.UTF-8",
          PATH,
        },
        stdio: ["ignore", "pipe", "pipe"],
      }
    );
    let output = "";
    const collect = (chunk) => {
      output = (output + String(chunk)).slice(-16_000);
    };
    child.stdout.on("data", collect);
    child.stderr.on("data", collect);
    const timer = setTimeout(() => {
      try {
        child.kill("SIGTERM");
      } catch {
        // Already gone.
      }
      reject(codedError("timeout", "更新插件超时：pnpm 未在 10 分钟内完成"));
    }, 10 * 60_000);
    child.on("error", (error) => {
      clearTimeout(timer);
      reject(codedError("install-failed", `无法运行 dsh: ${error.message}`));
    });
    child.on("exit", (code, signal) => {
      clearTimeout(timer);
      resolve({ code, signal, output });
    });
  });
}

/**
 * The part of a failed command's output that explains the failure. pnpm floods the stream
 * with progress lines, and the cause sits at one end: a Node crash prints its message
 * first, while dsh's own summary is last. Keep both ends and drop the middle.
 */
function failureDetail(text, exitCode) {
  const lines = String(text || "")
    .split(/\r?\n/)
    .map((line) => line.trimEnd())
    .filter((line) => line.trim() && !/^Progress: resolved /.test(line));
  if (lines.length === 0) {
    return `dsh plugin add 失败（exit=${exitCode ?? "null"}）`;
  }
  const selected =
    lines.length > 7
      ? [...lines.slice(0, 3), "…", ...lines.slice(-4)]
      : lines;
  const joined = selected.join("\n");
  return joined.length > 1500
    ? `${joined.slice(0, 700)}\n…\n${joined.slice(-700)}`
    : joined;
}

/**
 * Reinstalls pnpm into the shared Node prefix. Only used to recover from an interrupted
 * install; a healthy pnpm is never touched.
 */
function repairPnpm() {
  return new Promise((resolve) => {
    const child = spawn(
      "npm",
      [
        "install",
        "-g",
        "--prefix",
        "/opt/kingkang/node",
        "pnpm@10",
        "--force",
        "--no-audit",
        "--no-fund",
      ],
      {
        cwd: WORKDIR,
        env: {
          ...process.env,
          HOME: WORKDIR,
          TERM: "dumb",
          LANG: "C.UTF-8",
          PATH,
        },
        stdio: ["ignore", "pipe", "pipe"],
      }
    );
    let output = "";
    const collect = (chunk) => {
      output = (output + String(chunk)).slice(-8_000);
    };
    child.stdout.on("data", collect);
    child.stderr.on("data", collect);
    const timer = setTimeout(() => {
      try {
        child.kill("SIGTERM");
      } catch {
        // Already gone.
      }
      resolve({ code: null, output });
    }, 5 * 60_000);
    child.on("error", (error) => {
      clearTimeout(timer);
      resolve({ code: null, output: `${output}\n${error.message}` });
    });
    child.on("exit", (code) => {
      clearTimeout(timer);
      resolve({ code, output });
    });
  });
}

async function handleRequest(request) {
  if (!request || typeof request !== "object") {
    fail(null, "bad-request", "request is not an object");
    return;
  }
  if (request.v !== undefined && request.v !== PROTOCOL) {
    fail(request.id, "bad-request", `unsupported protocol ${request.v}`);
    return;
  }
  const id = typeof request.id === "string" ? request.id : null;
  const method = typeof request.method === "string" ? request.method : "";
  if (!id || !method) {
    fail(id, "bad-request", "missing id or method");
    return;
  }
  if (method === "shutdown") {
    reply(id, { accepted: true });
    shutdown(0);
    return;
  }
  const startedAt = Date.now();
  try {
    const result = await dispatch(method, request.params);
    info(`${method} ok (${Date.now() - startedAt}ms)`);
    reply(id, result);
  } catch (error) {
    const code = error?.code || "internal";
    const details = error?.details && typeof error.details === "object" ? error.details : {};
    info(`${method} failed: ${code} ${error?.message || ""}`.trim());
    fail(id, code, error?.message || "bridge request failed", details);
  }
}

function shutdown(exitCode = 0) {
  if (shuttingDown) return;
  shuttingDown = true;
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  if (dshRespawnTimer) {
    clearTimeout(dshRespawnTimer);
    dshRespawnTimer = null;
  }
  for (const streamId of [...streams.keys()]) {
    const handler = streams.get(streamId);
    streams.delete(streamId);
    try {
      if (socket && socket.readyState === SOCKET_OPEN) {
        socket.send(JSON.stringify({ type: "cancel", streamId }));
      }
    } catch {
      // ignore
    }
    try {
      handler?.onEnd?.();
    } catch {
      // ignore
    }
  }
  if (socket) {
    try {
      socket.close();
    } catch {
      // ignore
    }
    socket = null;
  }

  const child = dshProcess;
  dshProcess = null;
  if (child) {
    try {
      child.kill("SIGTERM");
    } catch {
      // ignore
    }
    const timer = setTimeout(() => {
      try {
        child.kill("SIGKILL");
      } catch {
        // ignore
      }
    }, 2_000);
    timer.unref?.();
  }
  setTimeout(() => process.exit(exitCode), 120);
}

async function main() {
  emit("hello", {
    bridgeVersion: BRIDGE_VERSION,
    protocol: PROTOCOL,
    node: process.version,
  });

  try {
    retireStaleDsh();
    clearIncompleteWriteLocks();
    ensurePnpmLink();
    const applicationUrl = await startDsh();
    await authenticateDsh(applicationUrl);
    ensureDefaultPluginRegistry();
    await connectTransport();
    emit("ready", {
      dshVersion,
      port: Number(new URL(baseUrl).port),
      capabilities: CAPABILITIES,
    });
  } catch (error) {
    emit("fatal", { message: describeError(error) });
    shutdown(1);
    return;
  }

  const lines = createInterface({ input: process.stdin, crlfDelay: Infinity });
  lines.on("line", (line) => {
    if (!line.trim()) return;
    let request;
    try {
      request = JSON.parse(line);
    } catch {
      fail(null, "bad-request", "stdin line is not JSON");
      return;
    }
    handleRequest(request).catch((error) => {
      info(`unhandled request failure: ${error?.stack || error}`);
    });
  });
  lines.on("close", () => shutdown(0));
}

main().catch((error) => {
  emit("fatal", { message: String(error?.stack || error) });
  shutdown(1);
});
