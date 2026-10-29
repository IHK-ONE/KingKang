#!/bin/sh
# Reports the installed DeepSeek Harness and Node.js versions. Read-only.
export PATH=/opt/kingkang/node/bin:/opt/kingkang/dsh/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

if command -v dsh >/dev/null 2>&1; then
  echo "KK_INSTALLED=1"
  VERSION=$(dsh --version 2>/dev/null | head -n 1 | tr -d '\r')
  echo "KK_VERSION=${VERSION:-unknown}"
else
  echo "KK_INSTALLED=0"
fi

if command -v node >/dev/null 2>&1; then
  echo "KK_NODE=$(node --version 2>/dev/null | head -n 1 | tr -d '\r')"
fi

echo "KK_OK=1"
