#!/bin/sh
# Removes the Agent core from Kali: the dsh package, the Node.js runtime that KingKang
# installed next to it, and the npm cache. Nothing else inside the rootfs is touched.
set -u
export PATH=/opt/kingkang/node/bin:/opt/kingkang/dsh/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

echo "KK_STEP=正在卸载 Agent 核心…"

# The app deletes /opt/kingkang afterwards: PRoot hides its own bookkeeping files from
# `rm`, and a guest-side recursive delete of ~1.4 GB can take minutes. Only the symlinks
# are removed here; the app then removes the tree itself.
rm -f /usr/local/bin/dsh /usr/local/bin/node /usr/local/bin/npm /usr/local/bin/npx \
  /usr/local/bin/pnpm /usr/local/bin/pnpx 2>/dev/null

echo "KK_INSTALLED=0"
echo "KK_OK=1"
