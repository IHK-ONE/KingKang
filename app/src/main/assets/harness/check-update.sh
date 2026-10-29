#!/bin/sh
# Compares the installed dsh with the latest version published on the official npm registry.
export PATH=/opt/kingkang/node/bin:/opt/kingkang/dsh/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export npm_config_update_notifier=false
# Keep npm's cache out of /root/.npm: overwriting an existing entry there can fail
# (EEXIST/ENOSYS) under PRoot's link2symlink emulation.
export npm_config_cache=/opt/kingkang/npm-cache
mkdir -p "$npm_config_cache" 2>/dev/null

if ! command -v npm >/dev/null 2>&1; then
  echo "KK_ERROR=NPM_MISSING"
  exit 1
fi

INSTALLED=$(dsh --version 2>/dev/null | head -n 1 | tr -d '\r')
LATEST=$(npm view @deepseek-ai/dsh version 2>/dev/null | head -n 1 | tr -d '\r')

if [ -z "$LATEST" ]; then
  echo "KK_ERROR=CHECK_FAILED"
  exit 1
fi

echo "KK_INSTALLED=$(command -v dsh >/dev/null 2>&1 && echo 1 || echo 0)"
echo "KK_VERSION=${INSTALLED:-unknown}"
echo "KK_LATEST=$LATEST"
if command -v node >/dev/null 2>&1; then
  echo "KK_NODE=$(node --version 2>/dev/null | head -n 1 | tr -d '\r')"
fi
echo "KK_OK=1"
