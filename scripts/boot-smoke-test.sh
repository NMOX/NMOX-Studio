#!/bin/sh
# Boot smoke test: start the ASSEMBLED application once, headless, from a
# throwaway userdir, and fail if the module system reported any module
# that could not be installed/enabled.
#
# Why this exists: CI builds and packages the app but never booted it, so
# a broken module graph (the commons_codec duplicate-bundle collision that
# rode several releases) stayed green all the way to a tag. This is the
# cheapest check that exercises the real cluster the user runs.
#
# Design notes, each paid for in a past escape:
#   * Runs against application/target/nmoxstudio (the real launcher +
#     cluster), never a maven test harness.
#   * Fresh userdir AND cachedir every run, removed on exit. A stale
#     disable-flag from a prior "Disable Modules and Continue" hides a
#     broken module and fakes a clean boot — never reuse either.
#   * -Dnetbeans.close=true makes the platform quit as soon as the window
#     system is up, so a healthy boot exits on its own in a few seconds.
#   * A broken module graph does NOT exit cleanly: headless, the platform
#     blocks on the "Disable Modules and Continue" dialog nobody can click.
#     So we poll the module log for the failure phrase and fail the moment
#     it appears, and a watchdog kills a boot that hangs for any other
#     reason. Detection reads the authoritative module-system output, not
#     the screen.
#
# Usage: scripts/boot-smoke-test.sh [path-to-nmoxstudio-app-dir]
# Exit:  0 healthy boot, non-zero otherwise (with the offending log lines).
set -eu

APP_DIR="${1:-application/target/nmoxstudio}"
LAUNCHER="$APP_DIR/bin/nmoxstudio"
BOOT_TIMEOUT="${BOOT_TIMEOUT:-180}"

# Authoritative failure signals from the NetBeans module system. These are
# the exact message templates org.netbeans.core.startup emits (verified
# against its Bundle.properties), not guesses:
#   MSG_failed_install_new            "could not install some modules:"
#   MSG_failed_install_new_unexpected "could not install module {0}"
#   TEXT_missing_jar_file             "the module {0} could not be found, ignoring"
#   MSG_problem_*_not_found           "was needed and not found" / "could be found"
#   MSG_start_disable_modules         "Turning off modules"  <-- the key one:
#       a HEALTHY boot only ever turns modules ON. The platform turns them
#       OFF only when it is disabling something that failed, so this single
#       phrase catches the whole "a module broke" class regardless of the
#       specific InvalidException wording (ClassNotFound, version mismatch,
#       the commons_codec Netigso "state remains INSTALLED", ...).
# Plus the Netigso/OSGi wording for a module stuck at start.
# Matched case-insensitively and anchored to whole phrases so module
# *names* that merely contain "error" (editor.errorstripe, errorprone) or
# "install" do not trip it.
FAILURE_RE='could not install|could not be found, ignoring|Turning off modules|state remains INSTALLED|the following modules could not be|cannot be installed|was needed and not found|could not be installed due to'

if [ ! -x "$LAUNCHER" ]; then
    echo "boot-smoke: launcher not found at $LAUNCHER — build the app first" >&2
    echo "  (mvn -B -DskipTests package)" >&2
    exit 2
fi

WORK="$(mktemp -d "${TMPDIR:-/tmp}/nmox-boot.XXXXXX")"
USERDIR="$WORK/userdir"
CACHEDIR="$WORK/cachedir"
LOG="$USERDIR/var/log/messages.log"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT INT TERM

# Prefer JAVA_HOME so the check does not lean on the launcher's own JDK
# fallback (the very logic the launcher fix hardened).
JDK_ARGS=""
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JDK_ARGS="--jdkhome $JAVA_HOME"
fi

echo "boot-smoke: booting $LAUNCHER"
echo "boot-smoke: userdir  $USERDIR (fresh, removed on exit)"

# shellcheck disable=SC2086
"$LAUNCHER" \
    --userdir "$USERDIR" \
    --cachedir "$CACHEDIR" \
    $JDK_ARGS \
    --nosplash \
    -J-Dnetbeans.close=true \
    > "$WORK/stdout.log" 2>&1 &
APP_PID=$!

fail_with_log() {
    echo "boot-smoke: FAIL — $1" >&2
    if [ -f "$LOG" ]; then
        echo "----- module-system failures -----" >&2
        grep -niE -A4 "$FAILURE_RE" "$LOG" | head -60 >&2 || true
        echo "----- tail of messages.log -----" >&2
        tail -25 "$LOG" >&2
    else
        echo "----- launcher stdout/stderr (no messages.log) -----" >&2
        cat "$WORK/stdout.log" >&2
    fi
    kill -TERM "$APP_PID" 2>/dev/null || true
    sleep 3
    kill -KILL "$APP_PID" 2>/dev/null || true
    exit 1
}

# Wait for one of: the failure phrase appears in the log (broken graph,
# which hangs headless), the process exits (healthy or crashed), or the
# watchdog timeout.
waited=0
while kill -0 "$APP_PID" 2>/dev/null; do
    if [ -f "$LOG" ] && grep -qiE "$FAILURE_RE" "$LOG"; then
        fail_with_log "the module system reported install/enable failures"
    fi
    if [ "$waited" -ge "$BOOT_TIMEOUT" ]; then
        fail_with_log "boot did not finish within ${BOOT_TIMEOUT}s (hung?)"
    fi
    sleep 2
    waited=$((waited + 2))
done
wait "$APP_PID" 2>/dev/null && APP_EXIT=0 || APP_EXIT=$?

# Process exited on its own — re-check the log (the phrase may have landed
# in the final flush) and confirm a clean, complete boot.
[ -f "$LOG" ] || fail_with_log "no messages.log written; app did not boot far enough"
grep -qiE "$FAILURE_RE" "$LOG" && fail_with_log "the module system reported install/enable failures"
grep -q "Turning on modules" "$LOG" || fail_with_log "module system never started (no 'Turning on modules')"
[ "$APP_EXIT" -eq 0 ] || fail_with_log "launcher exited $APP_EXIT (expected a clean shutdown)"

echo "boot-smoke: OK — module system came up and quit cleanly (exit 0)"
echo "boot-smoke: no install/enable failures in the module log"
exit 0
