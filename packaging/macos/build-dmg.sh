#!/bin/bash
# Builds "NMOX Studio.app" and a distributable DMG from the Maven output.
#
#   ./packaging/macos/build-dmg.sh [--app-only] [version]
#
# --app-only stops after the .app bundle (no DMG). This is the honest way to
# PLAY-TEST with full macOS fidelity: the Cmd-Tab switcher label comes from
# the bundle's Info.plist and only attributes to the app when the JVM binary
# lives INSIDE the bundle (the embedded runtime) — no bin-script launch or
# -Xdock flag can rename the switcher entry for a bare JVM process (it says
# "java").
#
# Prerequisites: `mvn package` has produced application/target/nmoxstudio,
# and this runs on macOS (iconutil, hdiutil). The bundle is unsigned;
# see INSTALL.md for the Gatekeeper note shipped to users.
set -euo pipefail

cd "$(dirname "$0")/../.."
APP_ONLY=no
if [ "${1:-}" = "--app-only" ]; then
    APP_ONLY=yes
    shift
fi
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

echo "==> Bundling Java runtime"
./packaging/tools/bundle-jre.sh "$BUNDLE/Contents/Resources/nmoxstudio"

echo "==> Building icns"
iconutil -c icns packaging/icons/nmox-studio.iconset \
    -o "$BUNDLE/Contents/Resources/nmox-studio.icns"
# The cluster's generated launcher passes
# -J-Xdock:icon=$progdir/../../nmoxstudio.icns (unhyphenated), which resolves
# to Contents/Resources/nmoxstudio.icns here. A dangling -Xdock:icon path
# overrides the bundle's icon attribution and the Dock/Cmd-Tab fall back to
# the default Java icon, so the icns must exist under BOTH names.
cp "$BUNDLE/Contents/Resources/nmox-studio.icns" \
   "$BUNDLE/Contents/Resources/nmoxstudio.icns"

echo "==> Writing launcher"
cat > "$BUNDLE/Contents/MacOS/nmox-studio" <<'LAUNCHER'
#!/bin/sh
DIR=$(cd "$(dirname "$0")" && pwd)
RES="$DIR/../Resources/nmoxstudio"
# The app ships its own Java runtime (jre/, jdkhome in the conf). Probe
# it actually runs on this machine (an arch mismatch must not strand the
# user), else fall back to an installed JDK 21+, else say so plainly.
# -Xdock:name: the menu bar shows the JVM process's own idea of its
# name ("nmoxstudio") unless told otherwise - Info.plist can't reach
# the java child process. macOS-only flag; never in the shared conf.
if "$RES/jre/bin/java" -version >/dev/null 2>&1; then
    exec "$RES/bin/nmoxstudio" -J-Xdock:name="NMOX Studio" "$@"
fi
JDK=$(/usr/libexec/java_home -v 21+ 2>/dev/null || true)
if [ -n "$JDK" ]; then
    exec "$RES/bin/nmoxstudio" --jdkhome "$JDK" -J-Xdock:name="NMOX Studio" "$@"
fi
osascript -e 'display dialog "NMOX Studio could not start its bundled Java runtime on this machine, and no Java 21+ installation was found.\n\nInstall a JDK 21 or newer (for example Temurin from adoptium.net) and launch again." buttons {"OK"} default button 1 with title "NMOX Studio" with icon caution' >/dev/null 2>&1 || true
exit 1
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

if [ "$APP_ONLY" = yes ]; then
    echo "==> Done (app only): $BUNDLE"
    echo "    Launch: open \"$BUNDLE\" --args --userdir <dir>"
    exit 0
fi

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
