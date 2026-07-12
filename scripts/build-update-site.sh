#!/bin/sh
# Build the NBM update-center catalog: target/netbeans_site/ gets
# updates.xml (+ .xml.gz) and one .nbm per product module, ready to
# upload as release assets next to the six installer artifacts.
#
# Usage:
#   scripts/build-update-site.sh            # relative NBM URLs (local file:// testing)
#   scripts/build-update-site.sh 1.51.0     # absolute URLs pinned to the v1.51.0 release
#
# Run AFTER `mvn package` — the aggregator goal only collects the .nbm
# files the package phase already produced; it does not rebuild them.
#
# Why absolute URLs on release (the /latest/ redirect trick): the app's
# registered catalog URL is
#   https://github.com/NMOX/NMOX-Studio/releases/latest/download/updates.xml
# — GitHub 302s that to the newest release's asset, so shipped apps
# always see the newest catalog with no code change. But the platform
# resolves RELATIVE distribution URLs against the pre-redirect /latest/
# URL (AutoupdateCatalogParser resolves against the configured catalog
# URI; it never sees the 302 target), so relative NBM links would also
# float to "latest" — and a cached older catalog would then download
# newer NBM bytes and fail its own SHA-512 digests. Pinning every NBM to
# its release tag keeps any catalog self-consistent forever.
#
# The catalog must carry exactly the 11 product modules. The aggregator
# walks session.getAllProjects(), which ignores -pl exclusions, and its
# updateSiteIncludes filter only applies to nbm-application projects
# (both verified against the 14.5 mojo) — so the never-shipped
# NMOX-Studio-sample template lands in the generated catalog and is
# pruned right here. The gates below fail the build if that drifts.
set -eu
cd "$(dirname "$0")/.."

VERSION="${1:-}"

# The catalog must describe the NBMs the release actually ships — refuse
# to run against a tree that has not been packaged.
if ! ls core/target/*.nbm >/dev/null 2>&1; then
    echo "ERROR: no NBMs found (core/target/*.nbm). Run 'mvn package' first." >&2
    exit 1
fi

if [ -n "$VERSION" ]; then
    mvn -B nbm:autoupdate \
        -Dmaven.nbm.customDistBase="https://github.com/NMOX/NMOX-Studio/releases/download/v${VERSION}"
else
    mvn -B nbm:autoupdate
fi

SITE=target/netbeans_site
CATALOG="$SITE/updates.xml"
[ -f "$CATALOG" ] || { echo "ERROR: $CATALOG was not generated" >&2; exit 1; }

# Prune the sample template (see the header comment): cut its <module>
# block out of the catalog, drop its NBM, and regenerate the .gz twin
# the mojo wrote from the unpruned XML. The end-match is anchored so it
# cannot eat the closing </module_updates>.
awk '/<module codenamebase="org.nmox.NMOX.Studio.sample"/ { skip = 1 }
     !skip { print }
     skip && $0 == "</module>" { skip = 0 }' "$CATALOG" > "$CATALOG.pruned"
mv "$CATALOG.pruned" "$CATALOG"
rm -f "$SITE"/NMOX-Studio-sample-*.nbm
gzip -c "$CATALOG" > "$CATALOG.gz"

# Gate 1: exactly the 11 product modules, each present by codename.
for m in branding core ui editor project tools rack infra apiclient dbstudio web3; do
    grep -q "codenamebase=\"org.nmox.NMOX.Studio.${m}\"" "$CATALOG" \
        || { echo "ERROR: module '$m' missing from catalog" >&2; exit 1; }
done
COUNT=$(grep -c '<module codenamebase=' "$CATALOG")
[ "$COUNT" -eq 11 ] || { echo "ERROR: expected 11 modules in catalog, found $COUNT" >&2; exit 1; }

# Gate 2: the sample template never ships (matched on its codename so a
# harmless "sample" in some module description cannot false-positive).
if grep -q 'NMOX.Studio.sample' "$CATALOG"; then
    echo "ERROR: NMOX-Studio-sample leaked into the catalog" >&2
    exit 1
fi

# Gate 3: on release, every NBM URL is absolute and pinned to this tag.
if [ -n "$VERSION" ]; then
    STRAY=$(grep -o 'distribution="[^"]*"' "$CATALOG" \
        | grep -cv "^distribution=\"https://github.com/NMOX/NMOX-Studio/releases/download/v${VERSION}/" || true)
    [ "$STRAY" -eq 0 ] || { echo "ERROR: $STRAY catalog URL(s) not pinned to v${VERSION}" >&2; exit 1; }
fi

echo "update site OK: $COUNT modules, $(ls "$SITE"/*.nbm | wc -l | tr -d ' ') NBMs in $SITE"
