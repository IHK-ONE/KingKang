# 第三方组件与许可

KingKang 的基座代码以 Apache License 2.0 发布（见 [`LICENSE`](LICENSE)）。仓库内还包含或运行以下第三方组件；分发 APK 时必须保留本文件，并遵守各组件的许可。

| 组件 | 用途 | 许可 | 来源 |
| --- | --- | --- | --- |
| PRoot（Termux 构建） | 无 root 运行 Linux 用户空间 | GPL-2.0-or-later | https://github.com/termux/proot |
| Termux 运行库（`libtalloc.so.2`、`libandroid-shmem.so`、`loader`） | PRoot 依赖 | LGPL-3.0 / GPL-2.0（按组件） | https://github.com/termux/termux-packages |
| Kali NetHunter minimal rootfs | 本地 Kali 文件系统 | 多种自由软件许可（以镜像内 `/usr/share/doc/*/copyright` 为准） | https://www.kali.org/get-kali/#kali-nethunter |
| xterm.js 5.3.0 | 终端前端 | MIT | https://github.com/xtermjs/xterm.js |
| xterm-addon-fit 0.8.0 | 终端自适应 | MIT | https://github.com/xtermjs/xterm.js |
| XZ for Java 1.9 | 解压 `.tar.xz` | Public Domain | https://tukaani.org/xz/java.html |
| AndroidX / Jetpack Compose / Kotlin | 应用框架 | Apache-2.0 | https://developer.android.com/jetpack |

## 分发说明

- PRoot 与 Termux 组件采用 GPL/LGPL。分发包含这些二进制文件的 APK 时，需要同时提供对应源代码及构建脚本；本仓库的 `app/src/main/assets/runtime/` 仅存放二进制，源代码请以组件表中链接的对应版本为准。
- Kali Linux 是 Offensive Security 的商标。KingKang 与 Offensive Security 无隶属关系，也不代表 Kali 官方。
- 应用仅用于**获得授权的安全测试、教学和自有设备上的实验**。用户应自行确保对目标系统的测试授权。
- 首次安装的 Kali rootfs 来自 `kali.download` 的公开 NetHunter 镜像，安装前会校验 SHA-256；应用不会自动获取 root，也不会修改设备的系统分区。
