#!/bin/sh
# docs-shots.sh — regenerate the tutorial screenshots from the real app.
#
# Boots the assembled app with -Dnmox.shots.dir set; DocsShots (ui module)
# cycles each suite tab, paints the main window into a 2x PNG per tab, and
# exits the app. Run after any UI-visible change so the docs never drift
# from the shipping product.
#
#   scripts/docs-shots.sh [output-dir] [locale]   (default: docs/images/tabs)
#
# v2.161.0: a translated document is illustrated in ITS language. Pass a
# locale as the second argument and the app boots with --locale, so every
# window paints the reader's own words (and mirrors for he/ar):
#   scripts/docs-shots.sh docs/images/he/tabs he
# NMOX_SHOTS_KEEP names the shots to keep (space-separated basenames
# without .png); the rest are deleted after the run so a language dir never
# holds a shot no document of that language references (the ImageRefsTest
# orphan law). NMOX_SHOTS_APP points at an assembled app elsewhere (a
# sibling worktree's) when this tree has none.
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
LOCALE="${2:-}"
LOCALE_OPT=""
[ -n "$LOCALE" ] && LOCALE_OPT="--locale $LOCALE"
mkdir -p "$OUT"
OUT_ABS="$(cd "$OUT" && pwd)"

APP="${NMOX_SHOTS_APP:-application/target/nmoxstudio/bin/nmoxstudio}"
if [ "${NMOX_SHOTS_NO_BUILD:-0}" != "1" ]; then
  echo "== assembling the app from current sources (mvn package -DskipTests) =="
  mvn -q clean package -DskipTests
fi
[ -x "$APP" ] || { echo "no assembled app at $APP"; exit 1; }

WORK="$(mktemp -d)"
UD="$WORK/shots-userdir"
CD="$WORK/shots-cachedir"
echo "== booting with nmox.shots.dir=$OUT_ABS${LOCALE:+ --locale $LOCALE} (throwaway userdir + cachedir) =="
# shellcheck disable=SC2086 — a locale code has no spaces; unquoted on purpose
"$APP" --nosplash --userdir "$UD" --cachedir "$CD" $LOCALE_OPT \
  -J-Dnmox.shots.dir="$OUT_ABS" \
  -J-Dnmox.shots.fakerun="Run — meridian|http://localhost:3000/" \
  -J-Dplugin.manager.check.updates=false \
  -J-Dapple.awt.application.name="NMOX Studio"

echo "== shots =="
missing=0
if [ -n "${NMOX_SHOTS_KEEP:-}" ]; then
  for png in "$OUT_ABS"/*.png; do
    base="$(basename "$png" .png)"
    case " $NMOX_SHOTS_KEEP " in
      *" $base "*) ;;
      *) rm -f "$png"; echo "  drop $base.png (not in NMOX_SHOTS_KEEP)";;
    esac
  done
fi
for f in ${NMOX_SHOTS_KEEP:-workbench the-task-rack project-studio db-studio contract-studio \
         infra-designer api-studio docker-panel block-studio \
         learning-spaces wizards-and-kits}; do
  if [ -s "$OUT_ABS/$f.png" ]; then
    echo "  ok   $f.png"
  else
    echo "  MISS $f.png"
    missing=1
  fi
done
rm -rf "$WORK" 2>/dev/null || true
exit $missing
