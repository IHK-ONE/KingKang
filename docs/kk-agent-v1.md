# KK-Agent v1

KK-Agent is the stable contract between the KingKang Android app's native surfaces and
the local dsh runtime inside Kali. Those native surfaces never talk to dsh's HTTP API and
never parse dsh event types directly. A small Node sidecar
(`app/src/main/assets/agent/bridge.mjs`) owns that translation, so a dsh update can be
absorbed by the sidecar without rewriting the app.

```
Android app (native)  ── NDJSON over stdio ──  bridge.mjs  ── loopback HTTP + WebSocket ──  dsh
Android app (Harness) ───────── loopback HTTP + cookie, same origin ──────────────────────  dsh
```

The one deliberate exception is the DeepSeek Harness screen: a WebView that loads
dsh-web itself for what only its own React tree can render — plugin-contributed pages,
the plugin manager, and the complete settings tree. It reaches the same loopback origin
the bridge authenticated, reuses dsh's own cookie handshake, and is navigated nowhere
else.

The bridge and dsh both run inside the Kali rootfs. dsh binds `127.0.0.1` only; the app
does not open a listener. Cleartext HTTP is permitted for loopback hosts only
(`res/xml/network_security_config.xml`, with `usesCleartextTraffic="false"`).

## Transport

- One JSON object per UTF-8 line on stdin/stdout.
- stderr is a human-readable diagnostic log. It is never parsed as protocol.
- The bridge is started with:

  ```sh
  /opt/kingkang/node/bin/node /opt/kingkang/agent/bridge.mjs
  ```

- The bridge starts dsh itself:

  ```sh
  /opt/kingkang/dsh/bin/dsh --profile web --host 127.0.0.1 --port 0 \
    --trusted-host 127.0.0.1 --trusted-host localhost --no-open
  ```

  It reads the printed `dsh web: http://127.0.0.1:<port>/?token=<token>` line, exchanges
  the token for a signed API cookie at `GET /connect?token=…&interactions=answer`,
  and then opens one WebSocket to `ws://127.0.0.1:<port>/api/remote.mux` with that
  cookie and a loopback `Origin`. dsh's trust fence must contain that host, which is
  why `--trusted-host` is part of the launch contract.

## dsh protocol as seen by the bridge

This section is version-specific knowledge that lives only in `bridge.mjs`.

- **RPC calls** are `POST /api/<endpoint>` with the typert gateway envelope. Arguments
  are keyed by the descriptor's parameter names, not passed positionally:
  `session/list` takes `{"_request":{}}`; `session/create`, `session/page`,
  `session/prompt`, `session/cancel` and friends take `{"request":{…}}`;
  `session/modelCatalog` and `session/control` take no arguments. A mismatch fails with
  `args fields do not match the descriptor`.
- **Streams** share the mux socket. Client frames:
  `{"type":"open","streamId","endpoint","payload":{"args":{}}}`,
  `{"type":"item","streamId","value"}` (for `$events/result`),
  `{"type":"end","streamId"}`, `{"type":"cancel","streamId"}`. Server frames are
  `item`, `end`, and `error`.
- **`$events`** is the live interaction stream: payload `{"args":{}}`. It delivers
  `ready` (`clientId`, `host`), `emit`, `waterfall`, and `cancel`. The bridge answers an
  approval waterfall with an outcome string and a question waterfall with
  `{answers:[…]}`; any other waterfall is delegated with `{kind:"next"}`.
- **`session/follow`** is the history and live-event stream:
  `{request:{address:{kind:"session",sessionId},assistantStream:true,maxMessages:4}}`.
  Its `snapshot` item carries the cursor, projections, and any active assistant
  attempt; then contiguous `event` items and `assistant-stream` frames arrive.
  History is a fresh follow snapshot plus `session/page` up to that cursor.

## Envelopes

### Request (app → bridge)

```json
{"v":1,"id":"uuid","method":"session.prompt","params":{}}
```

### Success response (bridge → app)

```json
{"v":1,"id":"uuid","ok":true,"result":{}}
```

### Error response (bridge → app)

```json
{"v":1,"id":"uuid","ok":false,"error":{"code":"...","message":"...","details":{}}}
```

### Event (bridge → app)

```json
{"v":1,"type":"event","event":"assistant.delta","data":{}}
```

## Handshake

1. Bridge prints `hello`:

   ```json
   {"v":1,"type":"event","event":"hello","data":{"bridgeVersion":"1.0.0","protocol":1}}
   ```

2. It starts dsh, opens both streams, then prints `ready`:

   ```json
   {"v":1,"type":"event","event":"ready","data":{
     "dshVersion":"0.2.1-rc.2",
     "port":41234,
     "capabilities":["session.list","session.prompt","session.updateQueue",
       "session.attachment","command.list","command.execute","skill.list",
       "web.url","workspace.list","workspace.create","plugin.list",
       "plugin.setEnabled","permission.catalog","settings.mutate","credentials.set"]
   }}
   ```

   The list above is abbreviated; the bridge advertises every method it dispatches.
   `port` is the loopback port dsh bound; `web.url` reports the same origin plus the
   current launch URL.

3. If startup fails, it prints `fatal` and exits:

   ```json
   {"v":1,"type":"event","event":"fatal","data":{"message":"..."}}
   ```

The app considers the agent usable only after `ready`.

## Methods

All methods are optional by capability. Unknown methods fail with
`unsupported-method`; the app must degrade gracefully.

| Method | Params | Result |
|---|---|---|
| `session.list` | `{}` | `{sessions:[SessionSummary]}` |
| `session.create` | `{sessionId?,cwd?,workspaceId?,agentPreset?}` | `{sessionId,agentPreset?}` |
| `session.history` | `{sessionId,beforeSeq?,maxMessages?}` | `{events:[AgentEvent],hasMore,projections?}` |
| `session.prompt` | `{sessionId,content:[{type:"text",text}\|{type:"image",mediaType,data,name?}],mode?:"queue"\|"steer"}` | `{accepted:true,command?:{kind:"success",text?}}` |
| `session.cancel` | `{sessionId}` | `{accepted:true}` |
| `session.updateQueue` | `{sessionId,itemId,action}` | dsh receipt |
| `session.attachment` | `{sessionId,attachmentId}` | `{attachment,data}` (`data` is base64) |
| `session.models` | `{sessionId}` | `Models` |
| `session.selectModel` | `{sessionId,provider,model,reasoningEffort?}` | `{selected}` |
| `session.rename` | `{sessionId,title}` | `{title,seq}` |
| `session.fork` | `{sessionId,atSeq?}` | `{sessionId}` |
| `subagent.list` | `{parentSessionId}` | `{entries,parentAvailable}` |
| `subagent.history` | `{parentSessionId,childSessionId,mode?,beforeSeq?,maxMessages?}` | `{events,hasMore,projections:null}` |
| `subagent.prompt` | `{parentSessionId,childSessionId,content,delivery?:"queue"\|"steer",rpcId?}` | `{messageId}` |
| `subagent.interrupt` | `{parentSessionId,childSessionId}` | `{accepted:true}` |
| `command.list` | `{sessionId}` | `{commands:[CommandDescriptor]}` |
| `skill.list` | `{sessionId}` | `{skills:[{name,description,whenToUse?,modelInvocable,path?}]}` |
| `command.execute` | `{sessionId,line,submittedAttachments?}` | `{commandId,result:{kind,text}}` or `null` |
| `web.url` | `{}` | `{url,port}` |
| `workspace.list` | `{}` | `{workspaces:[Workspace],archivedSessionIds,pinnedSessionIds}` |
| `workspace.create` | `{path}` | `{workspace,created}` |
| `plugin.list` | `{}` | `{plugins:[Plugin]}` |
| `plugin.setEnabled` | `{name,enabled}` | `{changed,application,restartRequired,restartReason?,stage?,target?,enabled,error?}` |
| `permission.catalog` | `{}` | `{options,defaultOptions,defaultPreset}` |
| `settings.describe` | `{}` | `{writable,namespaces:[SettingsNamespace]}` |
| `settings.mutate` | `{ns,ops,expectedRevision?}` | `{revision,...}` |
| `workspace.archiveSession` | `{sessionId}` | `{archivedSessionIds}` |
| `workspace.unarchiveSession` | `{sessionId}` | `{archivedSessionIds}` |
| `workspace.pinSession` | `{sessionId}` | `{pinnedSessionIds}` |
| `workspace.unpinSession` | `{sessionId}` | `{pinnedSessionIds}` |
| `credentials.describe` | `{refs:[string]}` | `{refs:{<ref>:{configured,source?,writable}}}` |
| `credentials.set` | `{ref,value}` | `{stored:true}` |
| `credentials.unset` | `{ref}` | `{removed:true}` |
| `respond` | `{rpcId,value}` | `{accepted:true}` |

`credentials.*` proxies dsh's settings controller. The value is write-only: `describe`
reports whether a reference resolves and which layer supplies it, but never returns the
secret. The app currently surfaces `DEEPSEEK_API_KEY`.

`respond` answers an approval or question. The bridge forwards `value` as the
`client-response` result after checking the pending request id. If dsh answers
`{accepted:false,reason:"not-pending"}`, another surface or a reconnect already settled
the request; the bridge returns that receipt as a success because the app's local removal
is still correct.

## Slash commands

dsh itself routes a prompt whose content is exactly one text block starting with `/`
through its command registry (mode-agnostic); it is never sent to the model, and the
receipt carries an optional `command:{kind:"success",text?}` slot. The bridge and app use
`command.execute` instead, because it names the command explicitly and tells "no handler"
apart from "handled". Both paths emit the same `command/run` and `command/done` session
events, which is what the transcript is rebuilt from.

`command.list` returns dsh's `CommandDescriptor` array — `{name,description,input?}`
where `input` may carry `hint` and `attachments`. The app renders this list as a
suggestion panel while the composer holds a `/word`, so the available commands and their
descriptions always come from the running dsh.

`skill.list` reads dsh's per-session `skills/list` catalog. A user-invocable skill shares
the same `/name` namespace as a command, so the composer merges both feeds: commands
first, then a “技能” section, each row showing its invocable token and description.
Known built-ins use dsh web's Chinese copy (压缩 / 导出 / 反馈 / 目标 / 权限 / 计划) while
unknown or future commands keep the host text. Matching accepts the English token, the
localized label, a substring, or a subsequence. Opening the panel re-reads models,
commands and skills so plugin installs and preset switches show up immediately; the
bridge additionally invalidates both catalogs on `agent-preset/selected` and
`plugin-manager/changed` and emits `commands.changed` / `skills.changed`.

MCP tools are not slash commands and have no catalog endpoint. They appear only as live
`tool.call` events named `mcp__<server>__<tool>`, which the transcript labels
`MCP · server / tool`.

`command.execute` runs one line. `{commandId,result:{kind,text}}` means a handler owned
the line; `null` (dsh returns `undefined`) means none did. `null` is preserved rather
than collapsed to `{}` because the app must distinguish "not a command" from an empty
receipt. The execution also emits `command/run` and `command/done` on the session event
stream, and those two are what rebuilds the transcript after a history load — the RPC
result itself is not replayed.

The app treats a command as a real transcript entry: the run line becomes a
`UserItem` with `source:"command"`, and the reply becomes a `NoticeItem`. Keys are
derived from `commandId` in both the live and history paths, so the optimistic run line
and the replayed one coincide.

## Attachments

`session.prompt` content blocks follow dsh's own `PromptContentPart`: a prompt may mix
`{type:"image",mediaType,data,name?}` and `{type:"text",text}`. The app sends images
first and puts the text block after them, because dsh expects the single-text-block rule
for slash-command routing to still hold; a prompt carrying images is never a command.

dsh stores the bytes as an attachment and echoes durable refs in the event stream:

```json
{"type":"user.message","text":"…","images":[
  {"attachmentId":"sha256:…","mediaType":"image/png","bytes":582636,
   "width":1080,"height":2400,"name":"…"}],
 "source":"user","rpcId":"kk-…"}
```

`user.message` is built from `event.data` itself (`content`/`source`/`id`); older builds
wrapped the same fields in `data.message`, so the bridge accepts both. Events whose
`source.kind` is not `user` — dsh records runtime-context and time-context snapshots as
`user/message` too — are dropped by the bridge and never reach the transcript.

The durable refs carry no bytes. Before the echo arrives the app already has the picked
file in memory (`UserItem.images[].localData`, a `data:` URL); after that, opening or
reloading a session fetches each missing image through `session.attachment` and caches it
under `sessionId:attachmentId` (bounded, not persisted). Thumbnails decode at 512 px and
the preview dialog at 1600 px, so a 12 MP screenshot does not become a full-size bitmap
in the chat list. The assistant's own output may contain image blocks as well; those
arrive through `assistant.message.images` and render the same way.

Composer limits: at most 4 images per prompt, 8 MB each, `image/png|jpeg|webp|gif`.
Queue edits replace the whole content array, so `session.updateQueue` edits re-send the
queued message's durable image refs alongside the new text.

Arbitrary files cannot travel over `session/prompt`. The composer copies up to 5 files
(64 MB each) into `<sharedDir>/uploads/<batch>/`, which Kali sees through the existing
shared bind mount at `/root/shared`, and appends a stable note to the prompt text:

```text
[KingKang 附件]
已上传 1 个文件到 Kali 共享目录：
- /root/shared/uploads/1730…/scan.txt（scan.txt，12.4 KB）
需要时请直接读取这些路径。
```

The model reads the real guest path. The app stores the user's original text plus
`AgentFile` chips in `UserItem.files`, and strips the same marker when rebuilding history
or the queue, so the prompt plumbing never appears as a chat bubble. Editing a queued
message re-appends the note from its parsed files, preserving their guest paths. A prompt
carrying files or images is never routed as a slash command.

## Subagents

A delegated child is a real dsh session. `subagent.list` returns the direct children of
the named session — a root, or a child that is currently open — in catalog order, so the
same call drives every level of a nested chain:

```json
{"entries":[
  {"kind":"child","id":"4bfed1af-…","mode":"continuable","label":"Explain TCP handshake",
   "activity":"inactive","hasChildren":false}
 ],"parentAvailable":true}
```

`entries[].kind` is `child` or `diagnostic` (the latter carries `reason`, for a child dsh
itself cannot address). `activity` is `running` or `inactive`. The bridge derives all of
it from dsh projections, not from a dedicated list endpoint:

- the named session's `subagentCatalog` projection lists direct children (`id`, `mode`,
  `label?`);
- each child's `subagentTiming` projection says whether a turn is active;
- each child's own `subagentCatalog` says whether it has children.

`subagent.history` pages the child's durable log through dsh's `session/page` with
`address:{kind:"subagent",parentSessionId,childSessionId,mode:"unknown"}` and
`throughSeq` taken from the child's projection cursor. It is a snapshot read, never a
`session/follow`, so the parent keeps its live subscription while a child is open.
`throughSeq:-1` pages nothing, and a just-spawned grandchild can briefly answer without a
projection cursor, so the bridge re-reads the cursor once before paging instead of
opening on a blank transcript.

`subagent.prompt` delivers `{content:[…],delivery}` to a continuable child through
`subagents/prompt {request:{parentSessionId,childSessionId,mode:"continuable",
delivery,requestId,content}}`; `subagent.interrupt` calls
`subagents/interruptByParent {childSessionId,parentSessionId,mode:"continuable"}`. The
control surface only accepts continuable addresses, so a one-shot child is read-only and
reports back through the parent tool result. A child can be idle or absent; dsh cold
resumes it from persistence.

### Drill-down

While a child owns the screen, the app makes that child `activeSessionId` with a local
synthetic session row, so the normal transcript fold applies unchanged. The open chain
lives in `subagentTrail`: root-to-open entries, each carrying the parent that spawned it.
`subagentView` is the last entry, `subagentDepth` is its length, and the SummaryBar lists
`subagents` — the *viewed* child's own children, fetched with `subagent.list(view.id)` —
labelled “下级子 Agent” below the root. Back pops exactly one level and re-reads that
level's transcript; the trail bar's label changes from “返回上一级” to
“返回主对话” at depth 1. The system Back key walks the same trail (`BackHandler` in
`HomeScreen` while a child is open), so Back never drops the whole app from a child view.

dsh's `session/list` includes subagent sessions, marked `origin:"subagent"`. They are
never drawer conversations: the app filters them from `session/list` and, while a trail is
open, keeps its levels locally because the list omits children. A forked root also records
`parentSessionId`, so `origin` — not the parent id — is the discriminator. Drawer rows
exclude every trail id; the trail bar is the only way back into that chain. dsh may
dispose an idle child's activation (a `session.removed` event) while its durable session
stays readable; the app keeps the open child view in that case.

How deep a chain can go is dsh's call: its subagent runtime caps delegation depth
(`maxDepth`, dsh default 1 — a child cannot spawn a grandchild). This app keeps the
profile patch at `[]`, so every setting including delegation depth stays at dsh's own
default; raising the cap is the user's own edit to
`$DSH_HOME/profiles/<name>/cordis.patch.yml`. The app renders whatever depth dsh produces.

Writing into a child is dsh's decision too. `subagents/prompt` requires the *exact live
direct parent* Agent, and dsh's session API refuses to resume a subagent-owned session on
its own, so after a restart the chain is cold: the 1st-level child still accepts messages
once its root is live, but a 2nd-level-or-deeper child (only possible after the user raises
`maxDepth`) answers `subagent/parent-unavailable` until that parent is woken by a message
in its own view. The app shows this as a plain Chinese notice
(`发送给子 Agent 失败：上一级子 Agent 不在线…`) rather than the raw wire error; viewing and
navigation keep working at any depth.

## Queue actions

`session.updateQueue` takes `{request:{sessionId,itemId,action}}` with:

- `{kind:"edit",content:[{type:"text",text:"…"}]}`
- `{kind:"remove"}`
- `{kind:"steer"}` — start this queued message now instead of waiting its turn

The bridge defaults `action` to `{kind:"remove"}` when the field is missing or not an
object. The resulting inbox projection arrives as the `session.queue` push event; the app
never mutates its queue state from the RPC response alone.

## Permission presets

`permission.catalog` proxies dsh's process preset catalog:

```json
{"options":[{"value":"read-only","name":"只读","description":"…"}],
 "defaultOptions":[…],"defaultPreset":"workspace-write"}
```

The active session's current preset arrives as a projection: history and follow
snapshots carry `projections:{values:{"permissions":{…}},asOfSeq}`, and the live value is
`{currentValue:"workspace-write"}` (`PermissionSelection`). The app accepts a flat block
as well, so a nesting change cannot silently drop the mode.

`options` are the per-session choices, `defaultOptions` the eligible defaults. The app
does not hardcode this list: labels, descriptions, and any preset a future dsh adds are
rendered as supplied. Switching the active session uses dsh's own command —
`command.execute {line:"/permission <value>"}` — so the change flows through the normal
projection update.

The default for new sessions lives in dsh settings, namespace `permission`, path
`["defaultPreset"]`. `settings.describe` returns
`{writable,namespaces:[{ns,revision,value,user}]}`; the app reads the `permission`
entry and posts `settings.mutate {ns:"permission",ops:[{op:"set",path:["defaultPreset"],
value}],expectedRevision}`. `expectedRevision` is sent when known so a concurrent edit
fails instead of being silently overwritten. The value is written by dsh, not by the app.

## Session summary

```json
{
  "id":"...",
  "title":"...",
  "updatedAt":1720000000000,
  "running":false,
  "blank":false,
  "parentId":null,
  "origin":"user",
  "cwd":"/root",
  "projections":{"asOfSeq":12,"values":{}}
}
```

`title` is already extracted from the projection, so the app never needs to know that
dsh stores the title inside `projections.values.title`. `origin` is `subagent` for a
delegated child and `user` (or absent) for a drawer conversation.

## Workspaces

dsh's Workspace registry is a set of named directories that own session ids. The bridge
reads it from `workspace/follow`, which is a stream: the bridge takes the first
`baseline` item and closes the stream, because the drawer only needs the current state,
not live workspace edits.

```json
{
  "workspaces":[
    {"id":"w-…","path":"/root","title":"root","sessionIds":["…"],
     "createdAt":"2026-…","updatedAt":"2026-…"}
  ],
  "archivedSessionIds":["…"],
  "pinnedSessionIds":["…"]
}
```

`workspace.create {path}` calls dsh's `workspace/create` and returns
`{workspace,created}` — `created:false` means the directory was already registered, so
the caller can just select it. `session.create` accepts the same `workspaceId`, which is
how a new conversation is filed under a workspace.

The drawer derives its grouping entirely from this baseline: a session belongs to the
first workspace whose `sessionIds` contains it; everything else is 未分组. Pinned ids
sort before the rest and archived ids are filtered out. Archived sessions are never
deleted; they stay readable through `session.history` and can be reached from the
Harness if dsh-web exposes them.

## Plugins

`plugin.list` reads dsh's `pluginManager/listBundles` and keeps the bundles a user would
call their plugins: those installed from the registry into the profile (`installed ===
true`), plus the optional bundles dsh ships (`optional === true`). Optional bundles are
listed even while disabled, otherwise the Settings switch could not turn them back on.
Catalog entries that were never installed are neither, and dsh's own composition (base,
web app) is required rather than optional, so both stay out.

```json
{"plugins":[{"name":"…","title":"…","description":"…","version":"1.2.3",
  "enabled":true,"removable":true}]}
```

`title` and `description` prefer dsh's localized `meta` copy. This inventory drives the
Settings → 插件 list, where each row carries a switch — there is no per-chat plugin picker
in dsh, so the switches describe the profile every session started afterwards uses.
Plugin-contributed *conversation views* are React components with no protocol surface, so
they are rendered only by the Harness WebView, never by the native transcript.

`plugin.setEnabled` proxies `pluginManager/setBundleEnabled(name, enabled)` and returns the
effective outcome. When dsh reports `application: "restart-required"`, the app keeps the
state but tells the reader the change lands after restarting the Agent; `error` carries the
manager's own failure text. Installation and removal stay in dsh's own plugin manager,
reached through the Harness. It installs with pnpm inside the profile, so the Agent 核心
install also provisions `pnpm` into `/opt/kingkang/node` (best effort; dsh itself runs
without it).

## Harness WebView

`web.url` returns dsh's own launch URL — `http://127.0.0.1:<port>/?token=<token>` — plus
the port. The bridge has already exchanged that token for the API cookie; loading the
same URL in a WebView lets dsh-web repeat the exchange for itself and set its cookie in
the WebView's jar. Do not log the URL: the token is a one-shot credential.

The Harness screen is the app's only HTTP client:

- It requests portrait from the Activity while it is on screen and restores the previous
  orientation on dispose; dsh-web is responsive and collapses its sidebar into a menu at
  phone width.
- Navigation is fenced to `http`/`https` on `127.0.0.1`, `localhost` or `::1`.
  `shouldOverrideUrlLoading` returns `true` for any other host, so an external link does
  not turn the screen into a second browser.
- `file://` and content access are disabled; JavaScript and DOM storage are on because
  dsh-web is a React app.
- File chooser requests are forwarded to the system picker, so Harness uploads work the
  same way the native composer's do.
- Back walks the WebView history first and only then leaves the screen. Leaving refreshes
  plugins and workspaces, because both may have changed inside dsh-web.

The bridge does not proxy WebView traffic, does not inject the cookie, and does not
read the WebView's DOM. The screen is dsh-web, unmodified, on its own loopback origin.

Some WebView builds report a zero-height layout viewport (`innerHeight` is correct but
`vh`/`dvh`/percentage heights resolve to 0), which collapses dsh-web's shell. The
Harness injects a shim on `onPageFinished` that pins the shell to the visual viewport in
pixels and rewrites height-based viewport units only when that bug is detected; healthy
WebViews are untouched.

## AgentEvent

Every event carries:

```json
{"sessionId":"...","seq":42,"time":1720000000000,"dshType":"assistant/message","kind":"assistant.message"}
```

`kind` is the stable surface. Current kinds:

| kind | data |
|---|---|
| `user.message` | `text`, `images`, `source`, `rpcId?` |
| `assistant.delta` | `turn`, `step`, `chunk` (raw chunk, see below); dsh emits this as `assistant/chunk` in 0.0.x and `assistant/live-chunk` in 0.2.x, both normalized here |
| `assistant.message` | `turn`, `step`, `blocks`, `images`, `usage?`, `source?` |
| `tool.call` | `callId`, `name`, `arguments`, `view?` |
| `tool.result` | `callId`, `text`, `isError`, `view?` |
| `turn.start` | `turn` |
| `turn.end` | `turn`, `reason` |
| `session.title` | `title` |
| `subagent.catalog` | no payload; the parent catalog changed, refresh `subagent.list` |
| `todo.write` | `todos` |
| `command` | `commandId`, `phase` (`run`\|`done`), `name`+`args` on `run`, `isError` on `done`, `text?` |
| `unknown` | `dshType`, `data` |

`assistant.delta.chunk` follows dsh's chunk vocabulary and is forwarded unchanged:

- `block-start` (`index`, `blockType`)
- `text-delta` (`index`, `text`)
- `reasoning-delta` (`index`, `text`)
- `tool-call-delta` (`index`, ...)
- `block-end` (`index`, `block`)
- `usage` (`usage`)

The app folds text and reasoning deltas. If a future dsh uses a new chunk type, the
bridge forwards it and the app ignores it, so a bridge-only update can add handling
without breaking old clients.

## Tool views

`tool.call` and `tool.result` may include `view`, the dsh host-computed render intent.
The app treats it as optional decoration. When absent, it renders a generic card from
`name`, `arguments`, `text`, and `isError`. This is why a new tool does not require an
app update.

## Interaction frames

```json
{"v":1,"type":"event","event":"approval.requested","data":{
  "rpcId":"...","sessionId":"...","approvalId":"...","toolName":"bash",
  "callId":"...","reason":"..."
}}
```

The app answers with:

```json
{"v":1,"id":"...","method":"respond","params":{
  "rpcId":"...",
  "value":{"sessionId":"...","approvalId":"...","outcome":"allowed-once"}
}}
```

Questions use the same path:

```json
{"v":1,"type":"event","event":"question.requested","data":{
  "rpcId":"...","sessionId":"...","questions":[
    {"id":"...","question":"...","header":null,"options":[{"label":"..."}],"multiSelect":false}
  ]
}}
```

```json
{"v":1,"id":"...","method":"respond","params":{
  "rpcId":"...",
  "value":{"sessionId":"...","answer":{"answers":[
    {"id":"...","selected":["..."],"custom":"..."}
  ]}}
}}
```

`approval.resolved`, `question.resolved`, `session.queue`, and `session.projection`
are pure push events; the app updates state and does not answer them.

## Host events

- `session.added` / `session.removed`
- `session.status` (`sessionId`, `running`)
- `session.error` (`sessionId`, `message`)
- `host.frame` (a generic frame the app currently ignores)
- `stream.error` (`message` or `error`)

## Error codes

- `bad-request` — malformed request envelope
- `unsupported-method` — bridge or dsh does not expose the method
- `dsh-error` — dsh returned a structured RPC error (the original code is in `details.code`)
- `transport` — loopback HTTP/WebSocket failure
- `startup` — dsh did not start or did not print a URL
- `internal` — unexpected bridge failure

## Versioning

- `v` is the protocol generation. It only changes when the envelope shape changes.
- `bridgeVersion` changes independently. The app displays it in Settings.
- `capabilities` lets the app hide a feature rather than fail when dsh is older.
- New dsh event kinds and chunk types are additive. The app ignores unknown ones.
- A future bridge can be shipped as an asset with its own manifest and rollback; the
  app only needs to keep the v1 envelope.

## Security posture

- The bridge is a child of the app inside the Kali rootfs. It is not reachable from
  another app or from the network. The app opens no listener.
- dsh's web server is loopback-only and uses its own trust fence.
- Model output is never rendered as HTML. The only WebView is the Harness, and it loads
  dsh-web itself from a loopback origin — never transcript content, never a URL supplied
  by the model.
- Cleartext HTTP is limited to loopback hosts by the network security config; everything
  else stays HTTPS-only.
- The Harness launch URL carries dsh's one-shot token. It is handed to the WebView, never
  logged, and never persisted.
- Approval and question answers are explicit; the default policy is `ask`.
