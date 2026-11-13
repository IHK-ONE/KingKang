#!/bin/sh
# Compares the installed dsh with the latest version published on the configured npm source.
export PATH=/opt/kingkang/node/bin:/opt/kingkang/dsh/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export npm_config_update_notifier=false
# Keep npm's cache out of /root/.npm: overwriting an existing entry there can fail
# (EEXIST/ENOSYS) under PRoot's link2symlink emulation.
export npm_config_cache=/opt/kingkang/npm-cache
# Same source the app uses for installs; blank means npm's own default.
if [ -n "${KINGKANG_NPM_REGISTRY:-}" ]; then
  export npm_config_registry="$KINGKANG_NPM_REGISTRY"
fi
mkdir -p "$npm_config_cache" 2>/dev/null

if ! command -v npm >/dev/null 2>&1; then
  echo "KK_ERROR=NPM_MISSING"
  exit 1
fi

INSTALLED=$(dsh --version 2>/dev/null | head -n 1 | tr -d '\r')
if [ "${KINGKANG_DSH_PRERELEASE:-0}" = "1" ] && command -v node >/dev/null 2>&1; then
  # The npm `latest` tag can stay on a prerelease, so the preview channel reads the whole
  # version list and picks the highest semver instead.
  LATEST=$(npm view @deepseek-ai/dsh versions --json 2>/dev/null | node -e '
let s = "";
process.stdin.on("data", (d) => (s += d)).on("end", () => {
  let versions = [];
  try { versions = JSON.parse(s); } catch { versions = []; }
  if (!Array.isArray(versions)) versions = [];
  const parse = (raw) => {
    const main = String(raw).split("+")[0];
    const core = main.split("-")[0].split(".").map((n) => parseInt(n, 10) || 0);
    const pre = main.split("-").slice(1).join("-").split(".").filter(Boolean);
    return { core, pre };
  };
  const cmp = (a, b) => {
    const x = parse(a), y = parse(b);
    for (let i = 0; i < 3; i++) {
      const d = (x.core[i] || 0) - (y.core[i] || 0);
      if (d !== 0) return d;
    }
    if (!x.pre.length && !y.pre.length) return 0;
    if (!x.pre.length) return 1;
    if (!y.pre.length) return -1;
    const n = Math.max(x.pre.length, y.pre.length);
    for (let i = 0; i < n; i++) {
      if (x.pre[i] === undefined) return -1;
      if (y.pre[i] === undefined) return 1;
      const xa = x.pre[i], ya = y.pre[i];
      const xn = /^\d+$/.test(xa), yn = /^\d+$/.test(ya);
      if (xn && yn) { const d = Number(xa) - Number(ya); if (d !== 0) return d; }
      else if (xn) return -1;
      else if (yn) return 1;
      else if (xa !== ya) return xa < ya ? -1 : 1;
    }
    return 0;
  };
  versions.sort(cmp);
  process.stdout.write(versions[versions.length - 1] || "");
});
' | head -n 1 | tr -d '\r')
else
  LATEST=$(npm view @deepseek-ai/dsh version 2>/dev/null | head -n 1 | tr -d '\r')
fi

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
