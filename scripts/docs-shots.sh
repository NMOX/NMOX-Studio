#!/bin/sh
# docs-shots.sh — regenerate the tutorial screenshots from the real app.
#
# Boots the assembled app with -Dnmox.shots.dir set; DocsShots (ui module)
# cycles each suite tab, paints the main window into a 2x PNG per tab, and
# exits the app. Run after any UI-visible change so the docs never drift
# from the shipping product.
#
#   scripts/docs-shots.sh [output-dir]     (default: docs/images/tabs)
#
# The forge OWNS its output dir — every file there is regenerable. The
# curated, hand-staged shots in docs/images/ (real DB rows, a hit
# breakpoint, a running container) are never touched by this script.
#
# Rebuilds the app first so the shots always reflect the CURRENT sources —
# a stale application/target assembly silently captures yesterday's UI
# (and, before DocsShots existed there, never exits at all). Set
# NMOX_SHOTS_NO_BUILD=1 to skip the rebuild when you know it's current.
#
# Needs a logged-in graphical session (the app paints itself — no OS
# screen-recording permission is involved). Uses a throwaway userdir AND
# cachedir so every run captures the same first-launch state and never
# contends with a running installed app's caches.
set -e
cd "$(dirname "$0")/.."

OUT="${1:-docs/images/tabs}"
mkdir -p "$OUT"
OUT_ABS="$(cd "$OUT" && pwd)"

APP=application/target/nmoxstudio/bin/nmoxstudio
if [ "${NMOX_SHOTS_NO_BUILD:-0}" != "1" ]; then
  echo "== assembling the app from current sources (mvn package -DskipTests) =="
  mvn -q clean package -DskipTests
fi
[ -x "$APP" ] || { echo "no assembled app at $APP"; exit 1; }

WORK="$(mktemp -d)"
UD="$WORK/shots-userdir"
CD="$WORK/shots-cachedir"
echo "== booting with nmox.shots.dir=$OUT_ABS (throwaway userdir + cachedir) =="
"$APP" --nosplash --userdir "$UD" --cachedir "$CD" \
  -J-Dnmox.shots.dir="$OUT_ABS" \
  -J-Dplugin.manager.check.updates=false \
  -J-Dapple.awt.application.name="NMOX Studio"

echo "== shots =="
missing=0
for f in workbench the-task-rack project-studio db-studio contract-studio \
         infra-designer api-studio docker-panel block-studio; do
  if [ -s "$OUT_ABS/$f.png" ]; then
    echo "  ok   $f.png"
  else
    echo "  MISS $f.png"
    missing=1
  fi
done
rm -rf "$WORK" 2>/dev/null || true
exit $missing
