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
./packaging/tools/bundle-jre.sh "$TAR_STAGE/nmox-studio-$VERSION"
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
# reuse the runtime already jlinked for the tar.gz; conf line comes with it
cp -R "$TAR_STAGE/nmox-studio-$VERSION/jre" "$DEB_STAGE/opt/nmox-studio/jre"
cp "$TAR_STAGE/nmox-studio-$VERSION/etc/nmoxstudio.conf" \
   "$DEB_STAGE/opt/nmox-studio/etc/nmoxstudio.conf"
cp packaging/icons/nmox-studio-512.png "$DEB_STAGE/usr/share/icons/hicolor/512x512/apps/nmox-studio.png"
cp packaging/icons/nmox-studio-128.png "$DEB_STAGE/usr/share/icons/hicolor/128x128/apps/nmox-studio.png"

cat > "$DEB_STAGE/usr/bin/nmox-studio" <<'WRAPPER'
#!/bin/sh
exec /opt/nmox-studio/bin/nmoxstudio "$@"
WRAPPER
chmod 755 "$DEB_STAGE/usr/bin/nmox-studio"

# The menu entry speaks every language the IDE speaks: freedesktop reads
# Comment[xx]/GenericName[xx] for the session locale and falls back to the
# bare key. The heredoc is unquoted, so no value here may carry $ or a
# backtick — DesktopEntryLanguagesTest holds that along with the coverage.
cat > "$DEB_STAGE/usr/share/applications/nmox-studio.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=NMOX Studio
GenericName=Web IDE
GenericName[es]=IDE web
GenericName[fr]=EDI web
GenericName[de]=Web-IDE
GenericName[ru]=Веб-IDE
GenericName[uk]=Веб-IDE
GenericName[pl]=Webowe IDE
GenericName[pt]=IDE web
GenericName[id]=IDE web
GenericName[tl]=Web IDE
GenericName[vi]=IDE web
GenericName[zh]=Web 集成开发环境
GenericName[hi]=वेब IDE
Comment=The web development task rack
Comment[es]=El rack de tareas para el desarrollo web
Comment[fr]=Le rack de tâches pour le développement web
Comment[de]=Das Task-Rack für die Webentwicklung
Comment[ru]=Стойка задач для веб-разработки
Comment[uk]=Стійка задач для веброзробки
Comment[pl]=Stojak zadań dla programowania webowego
Comment[pt]=O rack de tarefas para o desenvolvimento web
Comment[id]=Rak tugas untuk pengembangan web
Comment[tl]=Ang rack ng gawain para sa web development
Comment[vi]=Giá tác vụ cho phát triển web
Comment[zh]=面向 Web 开发的任务机架
Comment[hi]=वेब विकास के लिए टास्क रैक
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
Architecture: amd64
Installed-Size: ${INSTALLED_SIZE}
Depends: bash
Recommends: nodejs, npm, git
Maintainer: NMOX <david.liedle@gmail.com>
Homepage: https://github.com/NMOX/NMOX-Studio
Description: Web development IDE with a Reason-style task rack
 NMOX Studio is a NetBeans Platform IDE for modern web development.
 Tasks - build, test, serve, lint, deploy - are hardware-style rack
 devices patched together with cables. Ships with its own Java
 runtime; no Java installation is required.
CONTROL

dpkg-deb --build --root-owner-group "$DEB_STAGE" \
    "$DIST/nmox-studio_${VERSION}_amd64.deb" >/dev/null
echo "    $DIST/nmox-studio_${VERSION}_amd64.deb"
