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
echo "first-boot UI proof (manual, macOS): $BIN --jdkhome $JH --userdir $G/ud --cachedir $G/cd3 --nosplash ; wait 30s ; ⌘Q ; grep lastSeenVersion $G/ud/config/Preferences/org/nmox/NMOX/Studio/ui.properties"
