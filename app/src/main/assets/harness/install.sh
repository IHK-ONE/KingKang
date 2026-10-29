#!/bin/sh
# Installs or updates DeepSeek Harness. The Node.js runtime is downloaded and verified by
# the Android app and unpacked into /opt/kingkang/node before this script runs, so this
# script never depends on apt, curl or the guest's CA store.
set -u
export npm_config_update_notifier=false
export npm_config_cache=/opt/kingkang/npm-cache
export PATH=/opt/kingkang/node/bin:/opt/kingkang/dsh/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

LOG=/tmp/kingkang-dsh.log
: > "$LOG"

fail() {
  echo "KK_ERROR=$1"
  tail -n 12 "$LOG" 2>/dev/null
  exit 1
}

step() {
  echo "KK_STEP=$1"
}

step "正在准备 Node.js…"
[ -x /opt/kingkang/node/bin/node ] || fail "NODE_MISSING"
[ -x /opt/kingkang/node/bin/npm ] || fail "NPM_MISSING"
ln -sf /opt/kingkang/node/bin/node /usr/local/bin/node
ln -sf /opt/kingkang/node/bin/npm /usr/local/bin/npm
ln -sf /opt/kingkang/node/bin/npx /usr/local/bin/npx

install_dsh() {
  npm install -g --prefix /opt/kingkang/dsh @deepseek-ai/dsh@latest --no-audit --no-fund
}

step "正在安装 dsh 依赖（约 1.2 GB，首次需要 20–40 分钟）…"
mkdir -p /opt/kingkang/dsh
# npm moves every downloaded packument and tarball into place with rename(2); Android's
# app sandbox rejects that syscall, so the app preloads the shim from compat.sh.
install_dsh >>"$LOG" 2>&1 || fail "DSH_INSTALL_FAILED"

[ -x /opt/kingkang/dsh/bin/dsh ] || fail "DSH_BIN_MISSING"
ln -sf /opt/kingkang/dsh/bin/dsh /usr/local/bin/dsh

step "正在安装插件管理器（pnpm）…"
# dsh's plugin manager runs pnpm inside the profile; the Harness plugin UI (and the
# app's plugin list) is only complete when it exists. Best effort: dsh itself keeps
# working without it, and plugin installs then report their own failure.
if npm install -g --prefix /opt/kingkang/node pnpm@10 --no-audit --no-fund >>"$LOG" 2>&1; then
  ln -sf /opt/kingkang/node/bin/pnpm /usr/local/bin/pnpm
  ln -sf /opt/kingkang/node/bin/pnpx /usr/local/bin/pnpx
  echo "KK_PNPM=$(pnpm --version 2>/dev/null | head -n 1 | tr -d '\r')"
else
  echo "KK_WARN=PNPM_INSTALL_FAILED"
fi

step "正在读取版本…"
VERSION=$(dsh --version 2>>"$LOG" | head -n 1 | tr -d '\r')
[ -n "$VERSION" ] || fail "DSH_RUN_FAILED"
echo "KK_INSTALLED=1"
echo "KK_VERSION=${VERSION:-unknown}"
echo "KK_NODE=$(node --version 2>/dev/null | head -n 1 | tr -d '\r')"
echo "KK_OK=1"
