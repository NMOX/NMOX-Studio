#!/bin/sh
# Rendering probe: boot the ASSEMBLED app headless and assert the editor's
# RESOLVED colors are the dark scheme — the failure class that shipped in
# v1.10.1, when the IDE ran FlatLaf Dark but every editor rendered
# light-profile colors on a white canvas, green through all of CI.
#
# The assertions live in RenderingProbe (@OnStart, in the editor module):
# under the active font-color profile, the default editor background must
# be dark and the JS keyword must resolve to Phosphor's color. They read
# the runtime FontColorSettings, so they catch both halves of the bug —
# the profile falling back to light AND the palette not being registered
# under the active profile — which a source-layer test (DarkProfileLayerTest)
# cannot. The probe writes PASS / FAIL:<reason> to a result file; this
# script boots with the probe enabled and asserts PASS.
#
# Same fresh-userdir discipline as the boot smoke test: a stale profile
# choice in a reused userdir would mask the shipped default.
#
# Usage: scripts/rendering-probe.sh [path-to-nmoxstudio-app-dir]
# Exit:  0 dark scheme resolved, non-zero otherwise (with the FAIL reason).
set -eu

APP_DIR="${1:-application/target/nmoxstudio}"
LAUNCHER="$APP_DIR/bin/nmoxstudio"
BOOT_TIMEOUT="${BOOT_TIMEOUT:-180}"

if [ ! -x "$LAUNCHER" ]; then
    echo "rendering-probe: launcher not found at $LAUNCHER — build the app first" >&2
    exit 2
fi

WORK="$(mktemp -d "${TMPDIR:-/tmp}/nmox-render.XXXXXX")"
USERDIR="$WORK/userdir"
CACHEDIR="$WORK/cachedir"
RESULT="$WORK/probe-result.txt"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT INT TERM

JDK_ARGS=""
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JDK_ARGS="--jdkhome $JAVA_HOME"
fi

echo "rendering-probe: booting $LAUNCHER with the color probe enabled"

# shellcheck disable=SC2086
"$LAUNCHER" \
    --userdir "$USERDIR" \
    --cachedir "$CACHEDIR" \
    $JDK_ARGS \
    --nosplash \
    -J-Dnetbeans.close=true \
    -J-Dnmox.rendering.probe=true \
    -J-Dnmox.rendering.probe.out="$RESULT" \
    > "$WORK/stdout.log" 2>&1 &
APP_PID=$!

# The probe writes its result at @OnStart, before the window even shows,
# so the file usually lands within a couple seconds. Wait for the file or
# the process exit or the watchdog.
waited=0
while kill -0 "$APP_PID" 2>/dev/null; do
    [ -f "$RESULT" ] && break
    if [ "$waited" -ge "$BOOT_TIMEOUT" ]; then
        echo "rendering-probe: FAIL — no probe result within ${BOOT_TIMEOUT}s (boot hung?)" >&2
        kill -TERM "$APP_PID" 2>/dev/null || true; sleep 3; kill -KILL "$APP_PID" 2>/dev/null || true
        tail -25 "$USERDIR/var/log/messages.log" 2>/dev/null >&2 || cat "$WORK/stdout.log" >&2
        exit 1
    fi
    sleep 2
    waited=$((waited + 2))
done

# The rendering verdict is complete the moment the result file lands —
# whether the app then shuts down cleanly is the boot smoke test's job,
# not this check's. So terminate the app rather than waiting on its exit,
# which would hang CI forever if a PASS were followed by a stalled
# shutdown.
if kill -0 "$APP_PID" 2>/dev/null; then
    kill -TERM "$APP_PID" 2>/dev/null || true
    sleep 3
    kill -KILL "$APP_PID" 2>/dev/null || true
fi

if [ ! -f "$RESULT" ]; then
    echo "rendering-probe: FAIL — the probe never wrote a result" >&2
    echo "  (RenderingProbe not on the classpath, or @OnStart never ran)" >&2
    tail -25 "$USERDIR/var/log/messages.log" 2>/dev/null >&2 || true
    exit 1
fi

VERDICT="$(cat "$RESULT")"
case "$VERDICT" in
    PASS*)
        echo "rendering-probe: OK — $VERDICT"
        exit 0
        ;;
    *)
        echo "rendering-probe: FAIL — the editor is not rendering the dark scheme:" >&2
        echo "  $VERDICT" >&2
        exit 1
        ;;
esac
