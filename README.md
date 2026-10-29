# KingKang for Android

> 仅用于已获授权的安全测试、教学与自有设备上的实验。运行时全部在本机完成，不连接远程
> Kali，也不会尝试获取 root。

本地优先的 Android 安全测试工作台基座：一台手机里同时装下 Kali 与对话式 Agent。
当前版本包含：

- Compose 单 Activity 界面：主页、左侧边栏、`Kali 终端`、设置。提供浅色与深色两套中性
  界面（默认跟随系统），切换时同步写入 dsh 的 `ui-theme` 设置，Harness 页面随之变色；
  终端保留深色以符合终端语义。
- 对话直接接入 Kali 内的 DeepSeek Harness（`dsh`）：应用内 Node 桥接通过 stdio 与
  `dsh --profile web` 通信，dsh 只监听 Kali 回环地址。会话与历史由 dsh 保存，不再使用
  本地 `chats.json`。
- 原生对话界面包含流式文本与思考过程、工具调用卡片、审批/提问面板、待办与计划、
  排队/引导消息、模型与推理等级选择、停止、加载更早历史、会话归档与前台保活。
- 子 Agent：对话顶部显示 dsh 委派出的直接子会话，可打开子会话自己的完整过程；子 Agent
  再往下委派时同样可以逐层下钻（“下级子 Agent”），返回按钮一级一级退回，能走多深由
  dsh 自身的委派深度上限决定（应用不覆盖 dsh 默认配置）。可对话的子 Agent 能直接追加
  消息或停止；只读（一次性）子 Agent 仅展示过程，结果回到主对话。运行状态取自 dsh 的
  投影，不轮询猜测；dsh 拒绝的操作会在界面上明确提示，而不是静默失败。
- 斜杠补全合并 dsh 的命令注册表与当前会话可调用的技能（`/` 时显示，分「命令」「技能」
  两组）；内置命令采用 dsh web 的中文文案，未知命令保留宿主文本。打开补全时会重新读取
  模型、命令与技能目录，插件或预设变化立即反映。MCP 工具不是斜杠命令，实时工具调用卡片
  以 `MCP · server / tool` 显示。会话权限预设与「新会话默认权限」都读写 dsh 自身的目录与
  设置，名单和文案由 dsh 提供。
- 输入框可附带图片（最多 4 张，每张 8 MB，PNG/JPEG/WebP/GIF）以及任意文件（最多 5 个，
  每个 64 MB）。图片随消息进入 dsh 的附件存储；文件暂存到 Kali 可见的共享目录，并在
  提示词中写入真实路径。历史消息里的图片按需从 dsh 取回，缩略图与预览分开解码。
- 对话顶部有「对话 / 轨迹」标签页，左右滑动切换，与 dsh web 一致：轨迹页按轮次展示
  用户、助手、工具与提示的完整过程。会话没有内容或正在查看子 Agent 时不显示标签页。
- 输入框上方左侧是当前会话的权限预设，右侧是模型与推理等级，点一下即可切换；对话列表
  底部用渐变弱化边缘，提示可以继续下拉。
- 侧边栏加入工作区：可按 Kali 目录添加工作区，新会话归属到工作区，未归属的会话显示在
  「未分组」；顶部可按会话名称搜索。
- 侧边栏的「插件」页直接读写 dsh 的插件管理器：列出已安装与随附的可选插件并可直接开关，
  列出可安装的官方包，也接受 npm 包名、本地路径、git 或 tarball 地址（先经 dsh 检查再
  交给 pnpm 安装）。dsh 按 profile 管理插件，因此开关与安装作用于之后开始的新会话，需要
  重启的插件会明确提示；安装期间页面显示进度，同一时间只运行一个安装任务。卸载与插件
  自定义页仍在 Harness 中完成。
- 运行模式读写 dsh 自己的 `agent-preset-registry`（标准 / PTC / 极简 / 创造等，名单由 dsh
  提供）：dsh 出厂默认是 PTC，应用把新会话默认设为**标准模式**，只在新用户从未选择过时写入，
  之后完全跟随用户在设置里的选择，不会覆盖。
- 「DeepSeek Harness」入口（Kali 终端下方，其次是「插件」）固定竖屏，在 WebView 中加载
  dsh web 本身，用于插件自定义页、插件安装与 dsh 的完整设置树；返回时恢复原屏幕方向并
  刷新插件/工作区列表。WebView 只允许访问本机回环地址。
- JNI PTY 桥接（`libkingkang_pty.so`）与 xterm.js 5.3 终端。
- 无 root 的 PRoot 运行方式，以及检测到 `su` 后可切换的 chroot 运行方式。
- 首次使用时从官方 NetHunter 镜像下载 Kali **full** rootfs（arm64 约 1.8 GB / amd64 约 2.2 GB），
  SHA-256 校验后流式解压到应用私有目录；之后可在设置中检查更新，有新镜像时自动更新。
- 终端支持软键盘、ESC/TAB/CTRL/方向键、粘贴和自适应尺寸。
- 设置中的「Agent 核心」可把 DeepSeek Harness（`dsh`）装进 Kali：安装 Node.js 24（官方
  tarball + SHA-256 校验）后从官方 npm 源安装 `@deepseek-ai/dsh`，并附装插件管理所需的
  `pnpm`，支持检查更新（有新版本自动更新）与卸载。安装后约 1.4 GB（另占约 300 MB 依赖缓存），
  首次视网络需要 20–40 分钟。

当前对话链路已经可用，原生界面覆盖日常会话与 dsh 的协议能力；插件安装与开关已有原生
页面，插件自定义页面和 dsh 的全部设置项通过「DeepSeek Harness」中的真实 dsh web 完成，
两者合起来与 dsh web 的能力一致。凭据与模型 provider 可在设置中图形化配置（DEEPSEEK_API_KEY），
凭据值只写入 dsh，不会回读。能力缺失时会退回文本或通用工具卡片，不会伪造执行结果。
模型 provider 需先在 dsh 中配置好可用凭据；`session.models` 的 `routable` 会显示
provider 是否可用。

桥接协议见 [`docs/kk-agent-v1.md`](docs/kk-agent-v1.md)。应用只解析这个稳定协议，
dsh 的事件类型变化由 `bridge.mjs` 吸收。

## 构建

环境：JDK 17、Android SDK（compileSdk 34）、Android NDK r27+。

```bash
# 1. 编译 JNI（若 app/src/main/jniLibs 已有对应 .so 可跳过）
export ANDROID_NDK_HOME=/path/to/android-ndk
./scripts/build-native.sh

# 2. 构建 APK
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

`targetSdk` 固定为 28：Android 10+ 的 W^X 限制不允许应用从私有目录执行文件，而 PRoot 和
Kali 的 `bash` 都需要从应用数据目录启动。这是本地 rootless Linux 的已知取舍。

## 运行方式

- **PRoot（默认）**：无需 root，使用 Termux 构建的 PRoot 5.1 与 Kali rootfs，文件都在
  `filesDir/rootfs/kali`。
- **Root（可选）**：设置中开启后会探测 `su`；只有用户已授权 root 时才使用 `chroot`。
  应用不会尝试获取 root，也不会修改系统分区。未检测到 `su` 时自动回退到 PRoot。

## 已知限制

- Kali 使用 NetHunter full 镜像，下载与解压都需要较大存储空间；解压前请预留足够空间，
  空间不足时安装会中止并保留原有环境。
- rootfs 中的硬链接（如 `perlbug`、`perl5.42.3`）无法在 Android 应用数据目录创建，解压时会
  跳过；常用命令（`perl`、`zcat` 等）不受影响，运行期的硬链接由 PRoot 的
  `--link2symlink` 处理。
- rootfs、dsh 会话与终端会话都只在应用私有目录，卸载应用会一并删除。
- Agent 桥接以 PRoot 启动，不依赖 chroot/su；前台服务保持桥接存活，通知栏可停止。
  桥接与 dsh 之间使用本机回环 WebSocket，断线后自动恢复：dsh 的 API 会话过期（401）时
  重新换取 cookie 并重放请求；事件通道断开时快速重试后每 30 秒重试；dsh 进程崩溃时由桥接
  就地重启 dsh 并重新认证；桥接进程本身退出时由应用按退避自动重连。恢复后自动重新加载当前
  会话记录。只有连续多次重连失败才会提示手动重启。
- dsh web 只绑定 `127.0.0.1`，并用每次启动生成的一次性令牌换取会话 cookie；应用不会把它
  暴露到局域网或公网。Harness 把该回环地址交给 WebView，令牌不写入日志，也不持久化。

## 目录

```
app/src/main/cpp/pty_bridge.c          PTY / fork / exec JNI
app/src/main/java/.../terminal/        终端会话与 WebView 桥
app/src/main/java/.../runtime/         Rootfs、PRoot/chroot、下载与校验
app/src/main/assets/terminal/          xterm.js 前端
app/src/main/assets/runtime/<abi>/     proot、loader、依赖库
app/src/main/assets/harness/           dsh 安装、版本检测、更新与卸载脚本
app/src/main/assets/agent/bridge.mjs   dsh ↔ 应用 的 KK-Agent 桥接
app/src/main/java/.../agent/           桥接客户端、会话投影、前台服务
docs/kk-agent-v1.md                    桥接协议
docs/design.html                       设计文档
NOTICE.md                              第三方组件许可
```

## 边界

仅用于获得授权的安全测试、教学和在自有设备上的实验。运行时不会连接远程 Kali；
Kali 是 Offensive Security 的商标，本项目与其无隶属关系。

## 许可

本项目以 [Apache License 2.0](LICENSE) 发布：可自由使用、修改与再分发，包括商业用途，
但须保留版权与许可声明；「KingKang」名称与图标不在许可范围内。

随应用分发或运行的第三方组件（PRoot、Termux 运行库、Kali NetHunter rootfs、xterm.js 等）
沿用各自的许可，详见 [NOTICE.md](NOTICE.md)。
