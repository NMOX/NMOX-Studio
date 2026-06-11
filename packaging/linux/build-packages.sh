#!/bin/bash
# Builds the Linux distribution artifacts from the Maven output:
#   - NMOX-Studio-<version>-linux.tar.gz   (universal: extract & run)
#   - nmox-studio_<version>_all.deb        (Debian/Ubuntu, when dpkg-deb exists)
#
#   ./packaging/linux/build-packages.sh [version]
set -euo pipefail

cd "$(dirname "$0")/../.."
VERSION="${1:-$(date +%Y.%m.%d)}"
APP_INPUT="application/target/nmoxstudio"
DIST="application/target/dist"

[ -d "$APP_INPUT" ] || { echo "ERROR: $APP_INPUT missing - run 'mvn package -DskipTests' first"; exit 1; }
mkdir -p "$DIST"

echo "==> tar.gz"
TAR_STAGE="$DIST/linux-tar"
rm -rf "$TAR_STAGE"
mkdir -p "$TAR_STAGE/nmox-studio-$VERSION"
cp -R "$APP_INPUT"/. "$TAR_STAGE/nmox-studio-$VERSION/"
chmod +x "$TAR_STAGE/nmox-studio-$VERSION/bin/nmoxstudio" \
         "$TAR_STAGE/nmox-studio-$VERSION/platform/lib/nbexec" 2>/dev/null || true
tar -czf "$DIST/NMOX-Studio-${VERSION}-linux.tar.gz" -C "$TAR_STAGE" "nmox-studio-$VERSION"
echo "    $DIST/NMOX-Studio-${VERSION}-linux.tar.gz"

if ! command -v dpkg-deb >/dev/null; then
    echo "==> dpkg-deb not available (not on Debian/Ubuntu); skipping .deb"
    exit 0
fi

echo "==> .deb"
DEB_STAGE="$DIST/deb"
rm -rf "$DEB_STAGE"
mkdir -p "$DEB_STAGE/DEBIAN" \
         "$DEB_STAGE/opt/nmox-studio" \
         "$DEB_STAGE/usr/bin" \
         "$DEB_STAGE/usr/share/applications" \
         "$DEB_STAGE/usr/share/icons/hicolor/512x512/apps" \
         "$DEB_STAGE/usr/share/icons/hicolor/128x128/apps"

cp -R "$APP_INPUT"/. "$DEB_STAGE/opt/nmox-studio/"
chmod +x "$DEB_STAGE/opt/nmox-studio/bin/nmoxstudio" \
         "$DEB_STAGE/opt/nmox-studio/platform/lib/nbexec" 2>/dev/null || true
cp packaging/icons/nmox-studio-512.png "$DEB_STAGE/usr/share/icons/hicolor/512x512/apps/nmox-studio.png"
cp packaging/icons/nmox-studio-128.png "$DEB_STAGE/usr/share/icons/hicolor/128x128/apps/nmox-studio.png"

cat > "$DEB_STAGE/usr/bin/nmox-studio" <<'WRAPPER'
#!/bin/sh
exec /opt/nmox-studio/bin/nmoxstudio "$@"
WRAPPER
chmod 755 "$DEB_STAGE/usr/bin/nmox-studio"

cat > "$DEB_STAGE/usr/share/applications/nmox-studio.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=NMOX Studio
Comment=The web development task rack
Exec=/usr/bin/nmox-studio %F
Icon=nmox-studio
Terminal=false
Categories=Development;IDE;WebDevelopment;
StartupWMClass=NMOX Studio
DESKTOP

INSTALLED_SIZE=$(du -sk "$DEB_STAGE/opt" "$DEB_STAGE/usr" | awk '{s+=$1} END {print s}')
cat > "$DEB_STAGE/DEBIAN/control" <<CONTROL
Package: nmox-studio
Version: ${VERSION}
Section: devel
Priority: optional
Architecture: all
Installed-Size: ${INSTALLED_SIZE}
Depends: bash
Recommends: openjdk-17-jre | java17-runtime, nodejs, npm, git
Maintainer: NMOX <david.liedle@gmail.com>
Homepage: https://github.com/NMOX/NMOX-Studio
Description: Web development IDE with a Reason-style task rack
 NMOX Studio is a NetBeans Platform IDE for modern web development.
 Tasks - build, test, serve, lint, deploy - are hardware-style rack
 devices patched together with cables. Requires Java 17 or newer.
CONTROL

dpkg-deb --build --root-owner-group "$DEB_STAGE" \
    "$DIST/nmox-studio_${VERSION}_all.deb" >/dev/null
echo "    $DIST/nmox-studio_${VERSION}_all.deb"
