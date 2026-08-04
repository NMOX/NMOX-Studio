#!/bin/bash
# Embeds a jlinked Java runtime inside an NMOX Studio cluster so the
# installed app never depends on the user's Java. Idempotent.
#
#   ./packaging/tools/bundle-jre.sh <cluster-dir>
#
# Produces <cluster-dir>/jre and appends jdkhome="jre" to
# etc/nmoxstudio.conf (the launcher resolves a relative jdkhome
# against the cluster root). Requires a JDK with jmods (21+: the
# NetBeans Platform 30 baseline won't run on anything older).
set -euo pipefail

CLUSTER="${1:?usage: bundle-jre.sh <cluster-dir>}"
[ -d "$CLUSTER" ] || { echo "ERROR: $CLUSTER missing"; exit 1; }

JDK="${JAVA_HOME:-$(/usr/libexec/java_home -v 21+ 2>/dev/null || true)}"
[ -n "$JDK" ] && [ -d "$JDK/jmods" ] || {
    echo "ERROR: need a JDK 21+ with jmods (JAVA_HOME=$JDK)"; exit 1; }

# ---- OpenJFX jmods (v1.199.0: the embedded browser's engine) ----
# Temurin carries no JavaFX; the platform's embedded WebKit browser
# (org.netbeans.core.browser.webview) needs it. Gluon's official jmods
# join the jlink module path, so javafx.* become part of the bundled
# runtime — auto-resolved for classpath apps per JEP 261, no launcher
# flags. Version + per-platform sha256 pinned; a hash mismatch aborts
# the build rather than shipping unverified bytes.
FX_VERSION="26.0.2"
case "$(uname -s)-$(uname -m)" in
    Darwin-arm64)  FX_PLATFORM="osx-aarch64"
                   FX_SHA256="ed6ac7d8d056b29fa221edb029ed232eb54f3a7068c4d4e1304faf99f8d93285" ;;
    Darwin-x86_64) FX_PLATFORM="osx-x64"
                   FX_SHA256="3cb67bcc4be73f422010cca06618b51a57975ace429d14e6c97f08ea66d8a3cd" ;;
    Linux-x86_64)  FX_PLATFORM="linux-x64"
                   FX_SHA256="7c32eee96c4f992cea43cecee77420a660478ee2776f0b7475e03fb40cbfae84" ;;
    *) echo "ERROR: no pinned OpenJFX jmods for $(uname -s)-$(uname -m)"; exit 1 ;;
esac
FX_CACHE="${FX_JMODS_CACHE:-$HOME/.cache/nmox-openjfx}"
FX_DIR="$FX_CACHE/javafx-jmods-$FX_VERSION-$FX_PLATFORM"
if [ ! -d "$FX_DIR" ] || [ -z "$(ls "$FX_DIR"/*.jmod 2>/dev/null)" ]; then
    echo "==> fetching OpenJFX $FX_VERSION jmods ($FX_PLATFORM)"
    mkdir -p "$FX_CACHE"
    FX_ZIP="$FX_CACHE/openjfx-$FX_VERSION-$FX_PLATFORM.zip"
    # --retry covers the transient CDN failures a release must survive:
    # v1.207.0's first release run died on curl exit 18 (partial file)
    # 44s into this very download, taking the whole linux artifact with
    # it. Retry transient errors AND connection resets, and demand a
    # minimum trickle so a stalled socket fails fast instead of hanging
    # the job. The sha256 check below is still the correctness gate — a
    # retry that returns a corrupt file is caught there, not here.
    curl -sfL --retry 5 --retry-delay 3 --retry-all-errors \
        --connect-timeout 20 --speed-time 60 --speed-limit 1024 \
        -o "$FX_ZIP" \
        "https://download2.gluonhq.com/openjfx/$FX_VERSION/openjfx-${FX_VERSION}_${FX_PLATFORM}_bin-jmods.zip"
    ACTUAL="$(shasum -a 256 "$FX_ZIP" | cut -d' ' -f1)"
    [ "$ACTUAL" = "$FX_SHA256" ] || {
        echo "ERROR: OpenJFX jmods sha256 mismatch ($ACTUAL != $FX_SHA256)"; exit 1; }
    rm -rf "$FX_CACHE/unpack.$$" && mkdir -p "$FX_CACHE/unpack.$$"
    unzip -q "$FX_ZIP" -d "$FX_CACHE/unpack.$$"
    rm -rf "$FX_DIR"
    mv "$FX_CACHE/unpack.$$/javafx-jmods-$FX_VERSION" "$FX_DIR"
    rmdir "$FX_CACHE/unpack.$$"
fi
echo "==> OpenJFX jmods: $FX_DIR"

if [ -x "$CLUSTER/jre/bin/java" ]; then
    echo "==> bundled jre already present"
else
    echo "==> jlinking runtime from $JDK (+ OpenJFX)"
    rm -rf "$CLUSTER/jre"
    "$JDK/bin/jlink" \
        --module-path "$JDK/jmods:$FX_DIR" \
        --add-modules ALL-MODULE-PATH \
        --strip-debug --no-header-files --no-man-pages \
        --output "$CLUSTER/jre"
    "$CLUSTER/jre/bin/java" --list-modules | grep -q "javafx.web" || {
        echo "ERROR: bundled runtime is missing javafx.web"; exit 1; }
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
