#!/bin/sh
# platform-walk.sh — boot the assembled app on THIS operating system and
# photograph it: every suite tab and the forge's dialogs, plus the log.
#
#   scripts/platform-walk.sh <output-dir>
#
# Every walk of this product until 3.5 was taken on a Mac. Windows and Linux
# ran the tests (ledger 37: "Windows runs the tests, not the assembled-app
# probes") and nobody had seen the product's window on either. The forge
# (DocsShots, -Dnmox.shots.dir) paints windows with Swing itself, so it needs
# no screen-recording permission and no person at the machine — which makes
# a CI runner a place a walk can be taken. The pictures are for READING.
#
# Unlike docs-shots.sh this never rebuilds, never stages fixtures, and works
# under Git Bash on a Windows runner (the .exe launcher, Windows paths for
# the JVM). The output dir is owned by the walk: pictures, the app's log, and
# walk.txt, a few facts a picture cannot show.
set -u
cd "$(dirname "$0")/.."

OUT="${1:?usage: platform-walk.sh <output-dir>}"
mkdir -p "$OUT"
OUT_ABS="$(cd "$OUT" && pwd)"
APP_DIR="${NMOX_WALK_APP:-application/target/nmoxstudio}"
TIMEOUT="${NMOX_WALK_TIMEOUT:-420}"

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    OS=windows
    LAUNCHER="$APP_DIR/bin/nmoxstudio64.exe"
    # the JVM is handed Windows paths; Git Bash would otherwise pass /d/a/...
    native() { cygpath -m "$1"; }
    ;;
  *)
    OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
    LAUNCHER="$APP_DIR/bin/nmoxstudio"
    native() { printf '%s' "$1"; }
    ;;
esac
# Paint at the scale the screen has. The forge's 2x is faithful only where
# glyph widths scale exactly (macOS); on Windows and Linux text is hinted, and
# a 2x paint over a 1x layout cuts the last letter off every label — a defect
# of the picture, which the first walk here took for one of the product.
SCALE="${NMOX_WALK_SCALE:-1}"
[ "$OS" = darwin ] && SCALE="${NMOX_WALK_SCALE:-2}"
[ -x "$LAUNCHER" ] || [ -f "$LAUNCHER" ] || { echo "platform-walk: no launcher at $LAUNCHER — build the app first"; exit 2; }

WORK="$(mktemp -d "${TMPDIR:-/tmp}/nmox-walk.XXXXXX")"
UD="$WORK/userdir"
CD="$WORK/cachedir"
# A home of its own (3.5.1). A first launch is what a walk is for, and the
# walker's own workspace hides first-launch defects: real connection names
# made DB Studio's reset tree look the designed width for three weeks. It
# also lets the walk say afterwards whether its dialog pictures created
# anything, which in a real home nobody could tell.
HOME_DIR="$WORK/home"
mkdir -p "$UD" "$CD" "$HOME_DIR"

set --
if [ -n "${JAVA_HOME:-}" ]; then
  set -- --jdkhome "$(native "$JAVA_HOME")"
fi

# NMOX_WALK_STAGED=1: the documentation's staged scenes as well (3.5.2). The
# plain walk photographs a first launch, where every window is empty. The
# staged scenes do things: rack a preset, open a source file in the editor,
# run a query against SQLite, send a request to a loopback endpoint, fill a
# task board, design a small stack. On Windows and Linux none of that had
# been run. The fixtures are the forge's own (docs/i18n/forge-fixtures.json),
# written under the walk's throwaway home. KVASIR's scene needs a key and is
# skipped without one; the Docker, chain and DevTools scenes need services
# this script does not start, and say so in the log.
STAGED="${NMOX_WALK_STAGED:-0}"
FIXTURE_PID=""
if [ "$STAGED" = 1 ]; then
  FIXTURES="$(pwd)/docs/i18n/forge-fixtures.json"
  [ -f "$FIXTURES" ] || { echo "platform-walk: no forge fixtures at $FIXTURES"; exit 2; }
  set -- "$@" -J-Dnmox.shots.staged=1 \
    "-J-Dnmox.shots.fixtures=$(native "$FIXTURES")" -J-Dnmox.shots.lang=en \
    -J-Dnmox.shots.dialogs=File/org.nmox.studio.ui.actions.ManageLearningSpacesAction=spaces-shelf.png
  # API Studio's picture is a response: something must answer /health on
  # loopback for the length of the run
  PY="$(command -v python3 || command -v python || true)"
  if [ -n "$PY" ]; then
    # (its second argument is the shop front the DevTools scene picks from,
    # which that scene writes under the walk's home during the run)
    "$PY" scripts/docs-fixture-server.py 3000 "$(native "$HOME_DIR/NMOX/storefront/site")" >/dev/null 2>&1 &
    FIXTURE_PID=$!
  else
    echo "platform-walk: no python here; API Studio's request will find nothing listening"
  fi
  TIMEOUT="${NMOX_WALK_TIMEOUT:-900}"
fi

# netbeans.keyring.no.master (below): a Linux runner has no Secret Service,
# so the platform's keyring falls back to asking for a master password, and
# its dialog was photographed where the learning-space picker should have
# been (the third walk). The walk photographs the product's windows; with
# the fallback off the keyring is an in-memory one for the run. A machine
# with a real keyring never reaches the fallback and is unaffected.
echo "platform-walk: $OS, launcher $LAUNCHER"
START=$(date +%s)
# timeout(1) is GNU: a stock Mac has none, Homebrew's is gtimeout. With
# neither, the walk runs unleashed and the job's own timeout is the leash.
LEASH="$(command -v timeout || command -v gtimeout || true)"
if [ -n "$LEASH" ]; then
  set -- "$LEASH" --kill-after=30 "$TIMEOUT" "$LAUNCHER" --nosplash "$@"
else
  echo "platform-walk: no timeout(1) here; running without a leash"
  set -- "$LAUNCHER" --nosplash "$@"
fi
"$@" \
  --userdir "$(native "$UD")" --cachedir "$(native "$CD")" \
  -J-Duser.home="$(native "$HOME_DIR")" \
  -J-Dnmox.shots.dir="$(native "$OUT_ABS")" \
  -J-Dnmox.shots.scale="$SCALE" \
  -J-Dplugin.manager.check.updates=false \
  -J-Dnmox.update.check=false \
  -J-Dnetbeans.keyring.no.master=true \
  > "$OUT_ABS/launcher-output.txt" 2>&1
RC=$?
END=$(date +%s)
[ -n "$FIXTURE_PID" ] && kill "$FIXTURE_PID" 2>/dev/null

LOG="$UD/var/log/messages.log"
[ -f "$LOG" ] && cp "$LOG" "$OUT_ABS/messages.log"
SHOTS=$(find "$OUT_ABS" -name '*.png' | wc -l | tr -d ' ')
# What the dialog pictures left behind. Each dialog is photographed and then
# closed the way its close box closes it; a learning space on disk, or the
# Standards Kit's files in the workspace, means a dialog was ACCEPTED.
ACCEPTED=""
# (a staged walk seeds its own shelf of learning spaces and its scenes write
# their projects into the workspace, so there the check has nothing to say)
if [ "$STAGED" != 1 ] && [ -d "$HOME_DIR/.nmox/learn" ]; then
  for made in "$HOME_DIR/.nmox/learn"/*; do
    [ -e "$made" ] && ACCEPTED="$ACCEPTED learning-space:$(basename "$made")"
  done
fi
for made in robots.txt sitemap.xml site.webmanifest humans.txt .well-known; do
  [ "$STAGED" != 1 ] && [ -e "$HOME_DIR/NMOX/$made" ] && ACCEPTED="$ACCEPTED standards-kit:$made"
done
{
  echo "os: $OS ($(uname -a))"
  echo "launcher: $LAUNCHER"
  echo "exit code: $RC (124 = the walk's own timeout)"
  echo "seconds: $((END - START))"
  echo "pictures: $SHOTS (painted at ${SCALE}x)$([ "$STAGED" = 1 ] && echo ', staged scenes included')"
  echo "created by the dialog pictures:${ACCEPTED:- nothing}"
  if [ -f "$LOG" ]; then
    echo "SEVERE lines: $(grep -c 'SEVERE' "$LOG")"
    echo "WARNING lines: $(grep -c 'WARNING' "$LOG")"
    echo "java: $(grep -m1 'Java; VM; Vendor' "$LOG" | sed 's/^ *//')"
    echo "system locale: $(grep -m1 'System Locale' "$LOG" | sed 's/^ *//')"
  else
    echo "no messages.log: the app did not boot far enough to write one"
  fi
} > "$OUT_ABS/walk.txt"
cat "$OUT_ABS/walk.txt"
rm -rf "$WORK"

# a walk that produced no picture did not happen
[ "$SHOTS" -gt 0 ] || { echo "platform-walk: FAIL — no pictures"; exit 1; }
[ "$RC" = 0 ] || { echo "platform-walk: FAIL — the app exited $RC"; exit 1; }
# (NMOX_WALK_ACCEPTED_OK=1 turns that into a note, for walking a release
# older than 3.5.1, whose forge still accepted its dialogs.)
if [ -n "$ACCEPTED" ] && [ "${NMOX_WALK_ACCEPTED_OK:-0}" != 1 ]; then
  echo "platform-walk: FAIL — a photographed dialog was accepted:$ACCEPTED"
  exit 1
fi
