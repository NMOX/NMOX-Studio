#!/bin/bash
# Embeds a jlinked Java runtime inside an NMOX Studio cluster so the
# installed app never depends on the user's Java. Idempotent.
#
#   ./packaging/tools/bundle-jre.sh <cluster-dir>
#
# Produces <cluster-dir>/jre and appends jdkhome="jre" to
# etc/nmoxstudio.conf (the launcher resolves a relative jdkhome
# against the cluster root). Requires a JDK with jmods (any 17+).
set -euo pipefail

CLUSTER="${1:?usage: bundle-jre.sh <cluster-dir>}"
[ -d "$CLUSTER" ] || { echo "ERROR: $CLUSTER missing"; exit 1; }

JDK="${JAVA_HOME:-$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)}"
[ -n "$JDK" ] && [ -d "$JDK/jmods" ] || {
    echo "ERROR: need a JDK with jmods (JAVA_HOME=$JDK)"; exit 1; }

if [ -x "$CLUSTER/jre/bin/java" ]; then
    echo "==> bundled jre already present"
else
    echo "==> jlinking runtime from $JDK"
    rm -rf "$CLUSTER/jre"
    "$JDK/bin/jlink" \
        --module-path "$JDK/jmods" \
        --add-modules ALL-MODULE-PATH \
        --strip-debug --no-header-files --no-man-pages \
        --output "$CLUSTER/jre"
fi

CONF="$CLUSTER/etc/nmoxstudio.conf"
if ! grep -q '^jdkhome=' "$CONF"; then
    {
        echo ''
        echo '# bundled runtime: the app runs on its own Java, whatever the host has'
        echo 'jdkhome="jre"'
    } >> "$CONF"
    echo "==> jdkhome=\"jre\" written to etc/nmoxstudio.conf"
fi
echo "==> bundled runtime: $(du -sh "$CLUSTER/jre" | cut -f1)"
