#!/bin/bash
# Does the Plugin Manager treat a published release's module NBMs as TRUSTED?
#
# In-repo since v2.154.0 (ledger 86's remainder). The Plugin Installer has no
# "trusted" label to photograph: InstallStep opens its "Verify Certificate"
# panel only for plugins that are NOT trusted, and trusted ones install with no
# certificate step at all. So this runs the platform's own decision,
# autoupdate-services' Utilities.verifyCertificates, the one
# InstallSupportImpl.verifyNbm feeds, on a published NBM, with a portable's
# assembled cluster on the classpath so the product's shipped KeyStoreProvider
# (NmoxTrustedCerts) is the one consulted. A control call with no trusted
# certificates must NOT say TRUSTED, or the probe proves nothing.
#
# Usage: scripts/nbm-trust-probe.sh <release-tag vX.Y.Z> [work-dir]
# Downloads that release's portable zip and its core NBM from GitHub.
set -u
set -o pipefail
TAG=${1:?usage: nbm-trust-probe.sh <release-tag vX.Y.Z> [work-dir]}
G=${2:-/tmp/nmox-trust-probe}
JH=${JAVA_HOME:?JAVA_HOME must point at a JDK 21+}
HERE=$(cd "$(dirname "$0")" && pwd)
rm -rf "$G"; mkdir -p "$G/classes" || exit 2
gh release download "$TAG" --repo NMOX/NMOX-Studio --pattern '*portable.zip' --dir "$G" || { echo DOWNLOAD-FAILED; exit 1; }
gh release download "$TAG" --repo NMOX/NMOX-Studio --pattern '*core*.nbm' --dir "$G" || { echo NBM-DOWNLOAD-FAILED; exit 1; }
unzip -q "$G"/*portable.zip -d "$G/app" || { echo UNZIP-FAILED; exit 1; }
CL=$(dirname "$(dirname "$(find "$G/app" -name nmoxstudio -path '*/bin/*' -type f | head -1)")")
CP=$(find "$CL/platform" "$CL/nmoxstudio" -name '*.jar' | grep -vE '/locale/|/ext/' | paste -sd: -)
"$JH/bin/javac" -d "$G/classes" -cp "$CP" "$HERE/nbm-trust-probe/TrustProbe.java" || { echo COMPILE-FAILED; exit 1; }
OUT=$("$JH/bin/java" -cp "$G/classes:$CP" TrustProbe "$(ls "$G"/*core*.nbm | head -1)" 2>/dev/null)
RC=$?
echo "$OUT"
[ $RC -eq 0 ] || { echo PROBE-FAILED; exit 1; }
echo "$OUT" | grep -Fq 'verdict with shipped provider: TRUSTED' || { echo NOT-TRUSTED; exit 1; }
echo "$OUT" | grep -Fq 'verdict without it (control):  TRUSTED' && { echo CONTROL-TRUSTED-PROBE-PROVES-NOTHING; exit 1; }
echo "TRUSTED: $TAG"
