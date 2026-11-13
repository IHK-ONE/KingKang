#!/bin/sh
# Installs or updates DeepSeek Harness. The Node.js runtime is downloaded and verified by
# the Android app and unpacked into /opt/kingkang/node before this script runs, so this
# script never depends on apt, curl or the guest's CA store.
set -u
export npm_config_update_notifier=false
export npm_config_cache=/opt/kingkang/npm-cache
export PATH=/opt/kingkang/node/bin:/opt/kingkang/dsh/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
# The app passes its configured npm source (Aliyun by default). This also covers the
# pnpm install below, and plugin installs later read the same value from the profile.
if [ -n "${KINGKANG_NPM_REGISTRY:-}" ]; then
  export npm_config_registry="$KINGKANG_NPM_REGISTRY"
fi

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

# An update replaces the npm package only: the tree is the same prefix, npm reuses the
# cache, and the profile under /root/.dsh (sessions, plugins, credentials) is untouched.
UPDATE=0
if [ "${KINGKANG_DSH_UPDATE:-0}" = "1" ]; then
  UPDATE=1
fi
TARGET="${KINGKANG_DSH_VERSION:-latest}"

# npm 11.6+ gates dependency lifecycle scripts. dsh's trusted native helpers need
# theirs (node-pty, koffi, protobufjs, @google/genai, dsh-subprocess-local), or the
# package installs without its prebuilt binaries.
ALLOW_SCRIPTS="--allow-scripts=@deepseek-ai/dsh-subprocess-local,koffi,node-pty,@google/genai,protobufjs"

install_dsh() {
  if [ "$UPDATE" = "1" ]; then
    npm install -g --prefix /opt/kingkang/dsh "@deepseek-ai/dsh@$TARGET" \
      --no-audit --no-fund --prefer-offline $ALLOW_SCRIPTS
  else
    npm install -g --prefix /opt/kingkang/dsh @deepseek-ai/dsh@latest \
      --no-audit --no-fund $ALLOW_SCRIPTS
  fi
}

if [ "$UPDATE" = "1" ]; then
  echo "KK_MODE=update"
  step "正在更新 dsh（复用缓存，保留现有配置与插件）…"
else
  step "正在安装 dsh 依赖（约 1.2 GB，首次需要 20–40 分钟）…"
fi
mkdir -p /opt/kingkang/dsh
# npm moves every downloaded packument and tarball into place with rename(2); Android's
# app sandbox rejects that syscall, so the app preloads the shim from compat.sh.
install_dsh >>"$LOG" 2>&1 || fail "DSH_INSTALL_FAILED"

[ -x /opt/kingkang/dsh/bin/dsh ] || fail "DSH_BIN_MISSING"
ln -sf /opt/kingkang/dsh/bin/dsh /usr/local/bin/dsh

step "正在安装插件管理器（pnpm）…"
# dsh runs `pnpm` from PATH for every plugin operation; the Harness plugin UI (and the
# app's plugin list and updates) only work when it does. npm's own bin links do not
# always survive this sandbox, so link the package entry point directly and verify it.
PNPM_PKG=/opt/kingkang/node/lib/node_modules/pnpm
pnpm_ok() {
  [ -f "$PNPM_PKG/bin/pnpm.cjs" ] &&
    node "$PNPM_PKG/bin/pnpm.cjs" --version >/dev/null 2>&1
}
# A run that was killed midway can leave the package truncated while its entry file still
# exists, so check that pnpm actually starts and reinstall when it does not.
if ! pnpm_ok; then
  npm install -g --prefix /opt/kingkang/node pnpm@10 --force --no-audit --no-fund >>"$LOG" 2>&1 || true
fi
if pnpm_ok; then
  ln -sf "$PNPM_PKG/bin/pnpm.cjs" /opt/kingkang/node/bin/pnpm
  ln -sf "$PNPM_PKG/bin/pnpm.cjs" /usr/local/bin/pnpm
  echo "KK_PNPM=$(node "$PNPM_PKG/bin/pnpm.cjs" --version 2>/dev/null | head -n 1 | tr -d '\r')"
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
