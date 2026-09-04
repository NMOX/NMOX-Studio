#!/bin/bash
# The update-center gauntlet, in-repo since v2.69.2: a stock portable of an
# OLDER release updates itself in-app through the real update center to the
# newest, and the result is proven three ways — the platform's own updater
# wrote update_tracking for every module, every cluster jar reads the new
# spec version, and a fresh-cache boot turns every module on with zero
# SEVERE. The laws it carries (measured 2026-09-02/03):
#   - the update run outlives a short leash (download + install of 11 NBMs is
#     ~15 min); RC 124 from `timeout` is the leash, not a hang;
#   - wait for the updater's update_tracking writes, never for the process —
#     a reboot in the updater's same second loaded the OLD modules;
#   - boot the proof with a FRESH cachedir (a stale module cache fakes the
#     old versions);
#   - the update-catalog cache lives in the CACHEDIR: a second sequential
#     update on the same userdir needs `--refresh` and the SAME cachedir;
#   - `netbeans.close` reboots exit before the platform's post-UI hooks —
#     first-boot UI (What's New) is proven only by a live boot + orderly quit,
#     which this script prints the recipe for and does not attempt.
# Usage: scripts/update-gauntlet.sh <from-tag vX.Y.Z> [work-dir]
# A live window appears for ~45s during the first-boot half (desktop only).
set -u
set -o pipefail
FROM=${1:?usage: update-gauntlet.sh <from-tag vX.Y.Z> [work-dir]}
G=${2:-/tmp/nmox-gauntlet}
JH=${JAVA_HOME:?JAVA_HOME must point at a JDK 21+}
rm -rf "$G"; mkdir -p "$G"; cd "$G" || exit 2
gh release download "$FROM" --repo NMOX/NMOX-Studio --pattern '*.zip' --dir "$G" || { echo DOWNLOAD-FAILED; exit 1; }
Z=$(ls "$G"/*.zip | head -1); unzip -q "$Z" -d "$G/app" || { echo UNZIP-FAILED; exit 1; }
BIN=$(find "$G/app" -name nmoxstudio -path '*/bin/*' -type f | head -1); CL=$(dirname "$(dirname "$BIN")")
census() { for j in "$CL"/nmoxstudio/modules/org-nmox-*.jar; do unzip -p "$j" META-INF/MANIFEST.MF | grep -m1 Specification-Version; done | sort | uniq -c | tr -s ' ' | tr '\n' ';'; }
echo "before: $(census)"
LATEST=$(curl -sL https://github.com/NMOX/NMOX-Studio/releases/latest/download/updates.xml | grep -oE 'OpenIDE-Module-Specification-Version="[0-9.]+"' | head -1 | grep -oE '[0-9.]+')
echo "catalog offers: $LATEST"
timeout 2400 "$BIN" --jdkhome "$JH" --userdir "$G/ud" --cachedir "$G/cd" --nosplash --modules --refresh --update-all -J-Dnetbeans.close=true > "$G/update.log" 2>&1; echo "update RC=$? (124 = leash, not a hang)"
grep -E 'updates=|Will update' "$G/update.log" | head -3
for i in $(seq 1 120); do n=$(grep -l "$LATEST" "$CL"/nmoxstudio/update_tracking/org-nmox-*.xml 2>/dev/null | wc -l | tr -d ' '); [ "$n" -ge 11 ] && break; sleep 5; done; echo "update_tracking at $LATEST: $n of 11"; sleep 10
echo "after: $(census)"
[ "$n" -ge 11 ] || { echo "GAUNTLET-FAIL: the updater did not track 11 modules at $LATEST"; exit 1; }
rm -rf "$G/cd2"; timeout 300 "$BIN" --jdkhome "$JH" --userdir "$G/ud" --cachedir "$G/cd2" --nosplash -J-Dplugin.manager.check.updates=false -J-Dnetbeans.close=true > "$G/boot.log" 2>&1; echo "boot RC=$?"
L="$G/ud/var/log/messages.log"; ON=$(grep -oE "org\.nmox\.NMOX\.Studio\.[a-z0-9]+ \[$LATEST" "$L" | sort -u | wc -l | tr -d ' '); SEV=$(grep -c SEVERE "$L")
echo "modules on at $LATEST: $ON of 11; SEVERE: $SEV"
[ "$ON" -ge 11 ] && [ "$SEV" -eq 0 ] || { echo "GAUNTLET-FAIL: boot"; exit 1; }
echo "GAUNTLET-PASS $FROM -> $LATEST"
# The first-boot half (v2.69.3): `netbeans.close` exits before the platform's
# post-UI hooks, so What's New's first-boot rule (an @OnShowing hook) is proven
# only by a LIVE boot. NbPreferences persist under SIGTERM (measured v2.67.1),
# so a timed boot + TERM is an honest proof and needs no orderly quit — the
# updated install must record the new version as seen (RECORD_ONLY when the
# old build never wrote the key; SHOW when it did — either way the key lands).
# Skipped when no display can host the window (headless CI): said out loud.
if [ -n "${DISPLAY:-}" ] || [ "$(uname)" = Darwin ]; then
  rm -rf "$G/cd3"; "$BIN" --jdkhome "$JH" --userdir "$G/ud" --cachedir "$G/cd3" --nosplash -J-Dplugin.manager.check.updates=false > "$G/first-boot.log" 2>&1 &
  LIVE=$!; sleep 45; pkill -TERM -f "cachedir $G/cd3" 2>/dev/null; for i in $(seq 1 30); do kill -0 "$LIVE" 2>/dev/null || break; sleep 1; done; kill -0 "$LIVE" 2>/dev/null && { kill -KILL "$LIVE"; echo "first boot: KILLED after TERM grace (pref may be absent)"; }
  P="$G/ud/config/Preferences/org/nmox/NMOX/Studio/ui.properties"; SEEN=$(grep -h lastSeenVersion "$P" 2>/dev/null | tail -1)
  echo "first boot: ${SEEN:-lastSeenVersion ABSENT — the first-boot hook never ran}"
  echo "$SEEN" | grep -q "$LATEST" || { echo "GAUNTLET-FAIL: first-boot preference"; exit 1; }
  echo "FIRST-BOOT-PASS $LATEST recorded as seen"
else
  echo "first boot: SKIPPED (no display) — run on a desktop for the What's New half"
fi
