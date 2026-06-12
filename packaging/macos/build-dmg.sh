#!/bin/bash
# Builds "NMOX Studio.app" and a distributable DMG from the Maven output.
#
#   ./packaging/macos/build-dmg.sh [version]
#
# Prerequisites: `mvn package` has produced application/target/nmoxstudio,
# and this runs on macOS (iconutil, hdiutil). The bundle is unsigned;
# see INSTALL.md for the Gatekeeper note shipped to users.
set -euo pipefail

cd "$(dirname "$0")/../.."
VERSION="${1:-$(date +%Y.%m.%d)}"
APP_INPUT="application/target/nmoxstudio"
STAGE="application/target/dist/macos"
BUNDLE="$STAGE/NMOX Studio.app"
DMG="application/target/dist/NMOX-Studio-${VERSION}-macos.dmg"

[ -d "$APP_INPUT" ] || { echo "ERROR: $APP_INPUT missing - run 'mvn package -DskipTests' first"; exit 1; }
command -v iconutil >/dev/null || { echo "ERROR: iconutil not found - this script needs macOS"; exit 1; }

echo "==> Staging $BUNDLE"
rm -rf "$STAGE"
mkdir -p "$BUNDLE/Contents/MacOS" "$BUNDLE/Contents/Resources"

cp -R "$APP_INPUT" "$BUNDLE/Contents/Resources/nmoxstudio"
chmod +x "$BUNDLE/Contents/Resources/nmoxstudio/bin/nmoxstudio" \
         "$BUNDLE/Contents/Resources/nmoxstudio/platform/lib/nbexec" 2>/dev/null || true

echo "==> Building icns"
iconutil -c icns packaging/icons/nmox-studio.iconset \
    -o "$BUNDLE/Contents/Resources/nmox-studio.icns"

echo "==> Writing launcher"
cat > "$BUNDLE/Contents/MacOS/nmox-studio" <<'LAUNCHER'
#!/bin/sh
DIR=$(cd "$(dirname "$0")" && pwd)
# macOS GUI launches carry no shell env: locate a JDK 17+ the native
# way so users with a JDK installed never see the "Java required" dialog
JDK=$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)
if [ -n "$JDK" ]; then
    exec "$DIR/../Resources/nmoxstudio/bin/nmoxstudio" --jdkhome "$JDK" "$@"
fi
exec "$DIR/../Resources/nmoxstudio/bin/nmoxstudio" "$@"
LAUNCHER
chmod +x "$BUNDLE/Contents/MacOS/nmox-studio"

echo "==> Writing Info.plist"
cat > "$BUNDLE/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key>               <string>NMOX Studio</string>
    <key>CFBundleDisplayName</key>        <string>NMOX Studio</string>
    <key>CFBundleIdentifier</key>         <string>org.nmox.studio</string>
    <key>CFBundleVersion</key>            <string>${VERSION}</string>
    <key>CFBundleShortVersionString</key> <string>${VERSION}</string>
    <key>CFBundlePackageType</key>        <string>APPL</string>
    <key>CFBundleExecutable</key>         <string>nmox-studio</string>
    <key>CFBundleIconFile</key>           <string>nmox-studio</string>
    <key>NSHighResolutionCapable</key>    <true/>
    <key>LSMinimumSystemVersion</key>     <string>11.0</string>
    <key>NSHumanReadableCopyright</key>   <string>© NMOX. Apache License 2.0.</string>
</dict>
</plist>
PLIST

echo "==> Building DMG"
DMG_STAGE="$STAGE/dmg"
mkdir -p "$DMG_STAGE"
cp -R "$BUNDLE" "$DMG_STAGE/"
ln -s /Applications "$DMG_STAGE/Applications"
mkdir -p "$(dirname "$DMG")"
rm -f "$DMG"
hdiutil create -volname "NMOX Studio" -srcfolder "$DMG_STAGE" -ov -format UDZO "$DMG" >/dev/null

echo "==> Done: $DMG"
echo "    Unsigned build - first launch needs: right-click > Open,"
echo "    or: xattr -dr com.apple.quarantine '/Applications/NMOX Studio.app'"
