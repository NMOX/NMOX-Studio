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
# and this runs on macOS (iconutil, hdiutil). By default the bundle is
# ad-hoc signed (a valid but unattributed signature - NOT notarized); see
# INSTALL.md for the Gatekeeper note shipped to users.
#
# Set MACOS_SIGN_IDENTITY in the environment and the same run signs with a
# Developer ID instead, under the hardened runtime, and notarizes+staples
# when notary credentials are present too. Unset (the local default, and
# the release default until the account exists) nothing changes at all.
# The whole switch is documented in docs/engineering/release-signing.md.
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
# Bounded probe: Gatekeeper quarantine makes exec of the bundled java
# neither succeed nor fail - it hangs the assessment, and an unbounded
# probe turns that into "the app does nothing" with no window and no
# error (observed live on a quarantined brew install). Run the probe in
# the background and give it 10s; treat a hang exactly like a failure.
"$RES/jre/bin/java" -version >/dev/null 2>&1 &
PROBE=$!
i=0
while kill -0 "$PROBE" 2>/dev/null && [ $i -lt 100 ]; do
    sleep 0.1
    i=$((i+1))
done
if kill -0 "$PROBE" 2>/dev/null; then
    kill -9 "$PROBE" 2>/dev/null
    wait "$PROBE" 2>/dev/null
    PROBE_OK=1   # nonzero = probe hung
else
    wait "$PROBE"
    PROBE_OK=$?
fi
if [ "$PROBE_OK" = "0" ]; then
    exec "$RES/bin/nmoxstudio" -J-Xdock:name="NMOX Studio" "$@"
fi
# A quarantined bundle is the common cause of a hung/blocked probe -
# name the actual fix instead of blaming a missing JDK.
APP=$(cd "$DIR/../.." && pwd)
if xattr -p com.apple.quarantine "$APP" >/dev/null 2>&1; then
    osascript -e 'display dialog "macOS Gatekeeper has quarantined NMOX Studio, which blocks its bundled Java runtime from starting.\n\nFix (one time): right-click NMOX Studio in Applications and choose Open - or run:\n\nxattr -d com.apple.quarantine \"/Applications/NMOX Studio.app\"" buttons {"OK"} default button 1 with title "NMOX Studio" with icon caution' >/dev/null 2>&1 || true
    exit 1
fi
JDK=$(/usr/libexec/java_home -v 21+ 2>/dev/null || true)
if [ -n "$JDK" ]; then
    exec "$RES/bin/nmoxstudio" --jdkhome "$JDK" -J-Xdock:name="NMOX Studio" "$@"
fi
osascript -e 'display dialog "NMOX Studio could not start its bundled Java runtime on this machine, and no Java 21+ installation was found.\n\nInstall a JDK 21 or newer (for example Temurin from adoptium.net) and launch again." buttons {"OK"} default button 1 with title "NMOX Studio" with icon caution' >/dev/null 2>&1 || true
exit 1
LAUNCHER
chmod +x "$BUNDLE/Contents/MacOS/nmox-studio"

# NO CFBundleLocalizations here, on purpose. Declaring it would list the
# app under System Settings > Language & Region > Applications and let a
# user pick a language the IDE would then ignore: measured 2026-09-10
# with a probe bundle of this exact shape (a shell wrapper exec'ing java)
# — the per-app AppleLanguages preference never reaches the JVM, whose
# default stayed en_US. Options > General > Language is the control that
# works. See ledger 94 in docs/engineering/tech-debt.md.
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

# ---------------------------------------------------------------------------
# Signing: two paths, chosen by ONE environment variable.
#
#   MACOS_SIGN_IDENTITY unset  -> ad-hoc, byte-for-byte what this script has
#                                 always done. Every local build and every
#                                 release without the secrets takes this path.
#   MACOS_SIGN_IDENTITY set    -> Developer ID Application + hardened runtime
#                                 + entitlements + secure timestamp, then
#                                 notarized and stapled if notary credentials
#                                 are present too.
#
# NOTHING IN THE DEVELOPER ID PATH HAS EVER RUN. There is no Apple Developer
# account (ledger 86 - a purchase and an identity, not an engineering
# decision), so it is written from Apple's documentation and reviewed, never
# executed. The first release carrying the secrets is its own first test;
# docs/engineering/release-signing.md says what to watch.
# ---------------------------------------------------------------------------
ENTITLEMENTS="packaging/macos/entitlements.plist"

# Mach-O binaries INSIDE jars are invisible to `codesign`: it signs FILES,
# and a jar is a zip. The notary service unpacks archives and judges what it
# finds, so these are the one class the bundle scan cannot reach.
#
# MEASURED, and it is the reason v2.188.3 was refused - Apple's log named
# ten binaries across five jars (jna, flatlaf, truffle-runtime,
# junixsocket-native-common, sqlite-jdbc) with three complaints between
# them: "not signed", "not signed with a valid Developer ID certificate"
# (vendor-signed, but not by US), and "does not include a secure timestamp".
#
# The population is DERIVED, never listed: a platform bump can add a jar
# carrying a native, and a hand-kept list would ship the next one unsigned.
# Every jar in the bundle is asked what it contains; only entries that are
# really Mach-O are signed, so the Linux .so and Windows .dll siblings these
# same jars carry are left alone.
#
# The jar is UPDATED ENTRY BY ENTRY (`zip <jar> <entry>`) rather than
# unpacked and rebuilt: every other byte of the archive stays as it was.
# Checked first - none of the affected jars is jarsigner-signed, so there is
# no manifest digest to invalidate.
sign_natives_inside_jars() {
    local keychain_args=()
    if [ -n "${MACOS_KEYCHAIN:-}" ]; then
        keychain_args=(--keychain "$MACOS_KEYCHAIN")
    fi
    # DEDICATED FILE DESCRIPTORS, not stdin. `codesign` READS STDIN, so in a
    # `while read` loop fed by process substitution it swallows the lines the
    # loop has not consumed yet. MEASURED: signing ad-hoc against a real
    # bundle produced 8 of Apple's 10 binaries signed - exactly ONE per jar,
    # the first, with every later entry eaten. Both jars carrying two
    # architectures lost their second one, which is the arch a user on Intel
    # would run. Silent, and it would have failed notarization again.
    local signed=0 jar abs entry tmp entries
    while IFS= read -r -d '' -u 3 jar; do
        case "$jar" in /*) abs="$jar" ;; *) abs="$PWD/$jar" ;; esac
        # MATERIALIZE the listing before touching the archive. `zip` rewrites
        # the jar in place, and a streaming `unzip -Z1` on that same file
        # loses its tail when the rewrite lands mid-read. MEASURED: the very
        # first jar processed signed one of its two entries and silently
        # dropped the other, every later jar being fine only because its
        # listing had already drained into the pipe - a race whose victim is
        # whichever jar is slowest, which is not a thing to ship.
        entries=$(unzip -Z1 "$abs" 2>/dev/null | grep -E '\.(dylib|jnilib)$' || true)
        [ -n "$entries" ] || continue
        while IFS= read -r entry; do
            [ -n "$entry" ] || continue
            tmp=$(mktemp -d)
            # </dev/null on both: `codesign` READS STDIN and would otherwise
            # swallow the rest of this loop's input (measured - it ate every
            # entry after the first).
            if unzip -q -o "$abs" "$entry" -d "$tmp" 2>/dev/null \
               && file -b "$tmp/$entry" | grep -q 'Mach-O'; then
                codesign --force --options runtime --timestamp \
                         ${keychain_args[@]+"${keychain_args[@]}"} \
                         --sign "$MACOS_SIGN_IDENTITY" "$tmp/$entry" </dev/null
                ( cd "$tmp" && zip -q "$abs" "$entry" </dev/null )
                signed=$((signed + 1))
            fi
            rm -rf "$tmp"
        done <<< "$entries"
    done 3< <(find "$BUNDLE" -name '*.jar' -print0)
    echo "    signed $signed native libraries inside jars"
}

# Every Mach-O in the bundle, deepest first. `codesign --deep` is
# Apple-deprecated FOR SIGNING (it re-uses the top-level entitlements for
# nested code and skips shapes it does not recognise), and the notary
# service rejects a bundle holding one unsigned nested binary - so the
# nested code is sealed explicitly and the bundle is sealed last.
sign_bundle_with_identity() {
    # before ANY sealing: rewriting a jar changes bytes the bundle seal covers
    sign_natives_inside_jars
    [ -f "$ENTITLEMENTS" ] || { echo "ERROR: $ENTITLEMENTS missing - cannot sign for notarization"; exit 1; }
    # codesign's entitlements parser is AMFI's, and AMFI is STRICTER than
    # plutil: it rejects XML COMMENTS outright. entitlements.plist is full
    # of them by house law - every entitlement carries the reason it is
    # open - and `plutil -lint` calls the file OK, so nothing caught this
    # until a real signature was attempted. MEASURED: v2.188.0's release
    # died exactly here with
    #     Failed to parse entitlements: AMFIUnserializeXML: syntax error near line 6
    # line 6 being inside the header comment. plutil normalises the plist
    # and drops the comments, so the reasons stay where they belong and
    # codesign gets a file it can read. Never pass $ENTITLEMENTS itself.
    local ent="${TMPDIR:-/tmp}/nmox-entitlements-signing.plist"
    plutil -convert xml1 -o "$ent" "$ENTITLEMENTS"
    # ${arr[@]+"${arr[@]}"}, not "${arr[@]}", everywhere an array can be
    # EMPTY: macOS ships bash 3.2 (measured here and on the runner), where
    # expanding an empty array under `set -u` is an "unbound variable"
    # fatal. The + form expands to nothing at all when the array is unset.
    local keychain_args=()
    if [ -n "${MACOS_KEYCHAIN:-}" ]; then
        keychain_args=(--keychain "$MACOS_KEYCHAIN")
    fi
    local signed=0 f
    # Narrowed before `file` runs: a cluster is ~500 jars and thousands of
    # resources, and forking `file` per file would cost minutes for nothing.
    # Mach-O arrives either executable (jre/bin/*, nbexec) or suffixed.
    while IFS= read -r f; do
        file -b "$f" | grep -q 'Mach-O' || continue
        codesign --force --options runtime --timestamp \
                 --entitlements "$ent" \
                 ${keychain_args[@]+"${keychain_args[@]}"} \
                 --sign "$MACOS_SIGN_IDENTITY" "$f"
        signed=$((signed + 1))
    done < <(find "$BUNDLE" -type f \( -perm -u+x -o -name '*.dylib' -o -name '*.so' \
                 -o -name '*.jnilib' -o -name '*.bundle' \) \
             | awk -F/ '{ print NF "\t" $0 }' | sort -rn | cut -f2-)
    echo "    sealed $signed nested binaries"
    codesign --force --options runtime --timestamp \
             --entitlements "$ent" \
             ${keychain_args[@]+"${keychain_args[@]}"} \
             --sign "$MACOS_SIGN_IDENTITY" "$BUNDLE"
    codesign --verify --deep --strict --verbose=2 "$BUNDLE"
}

# notarytool credentials, in whichever form the secrets arrived as. An
# empty array means no credentials were given, which is not an error: a
# Developer-ID-signed-but-unnotarized build is a legitimate intermediate
# state (it is what an account gets on day one, before the App Store
# Connect key exists), and it still beats ad-hoc.
NOTARY_ARGS=()
if [ -n "${MACOS_NOTARY_KEY_FILE:-}" ]; then
    NOTARY_ARGS=(--key "$MACOS_NOTARY_KEY_FILE"
                 --key-id "${MACOS_NOTARY_KEY_ID:?MACOS_NOTARY_KEY_ID required beside the key}"
                 --issuer "${MACOS_NOTARY_ISSUER_ID:?MACOS_NOTARY_ISSUER_ID required beside the key}")
elif [ -n "${MACOS_NOTARY_APPLE_ID:-}" ]; then
    NOTARY_ARGS=(--apple-id "$MACOS_NOTARY_APPLE_ID"
                 --password "${MACOS_NOTARY_PASSWORD:?MACOS_NOTARY_PASSWORD required beside the Apple ID}"
                 --team-id "${MACOS_NOTARY_TEAM_ID:?MACOS_NOTARY_TEAM_ID required beside the Apple ID}")
fi

# Submit one artifact and wait for Apple's verdict. --wait makes the
# release lane fail loudly on a rejection instead of publishing something
# Gatekeeper will refuse; `notarytool log` prints WHY, which is the only
# way to debug a rejection (the verdict alone never says).
notarize() {
    local artifact="$1" out status id
    echo "==> Notarizing $(basename "$artifact") (Apple's queue decides how long this takes)"
    # `notarytool submit --wait` EXITS ZERO ON A REJECTED SUBMISSION. Its
    # exit code reports whether the round trip worked, not what Apple
    # decided - the VERDICT is in the output, exactly as `gofmt -l` puts
    # its verdict in the output rather than its exit code (v1.352.0).
    # MEASURED: v2.188.1 came back `status: Invalid`, this guard did not
    # fire, and the run sailed into `stapler`, which failed two steps
    # later with a CloudKit "Record not found" that named neither the
    # bundle nor the reason. Read the status.
    out=$(xcrun notarytool submit "$artifact" ${NOTARY_ARGS[@]+"${NOTARY_ARGS[@]}"} \
              --wait --timeout 45m 2>&1) || true
    echo "$out"
    status=$(printf '%s\n' "$out" | awk '/^[[:space:]]*status:/ { print $2; exit }')
    id=$(printf '%s\n' "$out" | awk '/^[[:space:]]*id:/ { print $2; exit }')
    if [ "$status" != "Accepted" ]; then
        echo "ERROR: notarization was refused for $artifact (status: ${status:-unknown})"
        # A refusal that does not say why is not a refusal anyone can act
        # on. The log names the offending file; fetch it here rather than
        # telling a human to re-run a command with credentials they may
        # not have locally.
        if [ -n "$id" ]; then
            echo "==> Apple's notary log for submission $id"
            xcrun notarytool log "$id" ${NOTARY_ARGS[@]+"${NOTARY_ARGS[@]}"} || \
                echo "       (could not fetch the log for $id)"
        else
            echo "       (no submission id in the output; nothing to look up)"
        fi
        exit 1
    fi
}

if [ -n "${MACOS_SIGN_IDENTITY:-}" ]; then
    # ----------------------------------------------------------------------
    # Make every cluster READ-ONLY before sealing the bundle.
    #
    # A signed bundle and an in-place updater cannot both be right: the
    # updater rewrites module jars inside Contents/, which breaks the seal
    # the notarization ticket vouches for. MEASURED on a real 2.187.0 app
    # updating to 2.187.1:
    #   writable cluster  -> 1068 files written INSIDE the bundle, and
    #                        `codesign --verify --deep --strict` exits 1
    #                        with "a sealed resource is missing or invalid"
    #   read-only cluster -> 0 files inside the bundle, 955 jars in the
    #                        USERDIR, boot RC=0 with zero SEVERE running
    #                        2.187.1 while the bundle's own jars stay
    #                        2.187.0, and codesign exits 0
    #
    # No platform change was needed: `Utilities.canWriteInCluster` gates on
    # plain `File.canWrite()` (read from the shipped bytecode), and
    # `InstallManager.checkTargetCluster` WARNS and falls through to the
    # userdir rather than throwing - it only throws WRITE_PERMISSION when a
    # caller forces a global install. The shadow path was always there;
    # this just stops the writable path from winning.
    #
    # Only the SIGNED path does this. An unsigned local build keeps its
    # writable cluster, so nothing about the portable zip or a dev build
    # changes. -w only: execute bits stay, so bin/nmoxstudio and jre/bin/*
    # still run.
    echo "==> Signing bundle with Developer ID: $MACOS_SIGN_IDENTITY"
    sign_bundle_with_identity
    # AFTER signing, never before: `codesign` WRITES the signature into each
    # Mach-O, so a read-only binary cannot be signed. v2.188.2 did this
    # first and died on the first nested library with
    #   libjnidispatch-nb.jnilib: internal error in Code Signing subsystem
    # - a permission error wearing a scary name.
    #
    # Safe in this order because POSIX modes are NOT part of what the
    # signature seals. MEASURED on a real bundle: `codesign --verify --deep
    # --strict` exits 0 before the chmod and 0 after it. The verify below
    # re-proves that on every release rather than trusting the measurement.
    echo "==> Making clusters read-only (a signed bundle must not be updated in place)"
    for cluster in "$BUNDLE/Contents/Resources/nmoxstudio"/*/; do
        case "$(basename "$cluster")" in
            bin|etc|jre) continue ;;   # not clusters; jre must stay as linked
        esac
        [ -d "$cluster/modules" ] || continue
        chmod -R a-w "$cluster"
    done
    codesign --verify --deep --strict --verbose=2 "$BUNDLE"
    if [ ${#NOTARY_ARGS[@]} -gt 0 ]; then
        # notarytool takes an archive, never a bundle. ditto's zip is the
        # one Apple documents for this (it preserves the symlinks and the
        # signature's resource forks that /usr/bin/zip flattens).
        APP_ZIP="$STAGE/NMOX-Studio-notarize.zip"
        ditto -c -k --keepParent "$BUNDLE" "$APP_ZIP"
        notarize "$APP_ZIP"
        rm -f "$APP_ZIP"
        # Staple the .app as well as the .dmg below: the DMG's ticket
        # covers the download, but once the user drags the app out, only
        # the app's OWN ticket lets a FIRST launch succeed with no network.
        xcrun stapler staple "$BUNDLE"
        xcrun stapler validate "$BUNDLE"
    else
        echo "==> Notarization skipped (no notary credentials); signed but not notarized"
    fi
elif command -v codesign >/dev/null; then
    # Ad-hoc sign the bundle. Unsigned arm64 Mach-O binaries do not execute at
    # all on Apple silicon (the hard "app is damaged" failure); an ad-hoc
    # signature is valid though unattributed, which downgrades that to the
    # ordinary "unidentified developer" prompt a right-click > Open clears.
    # This is NOT notarization and does NOT remove the quarantine xattr - the
    # launcher's Gatekeeper dialog above still stands. Guarded like iconutil so
    # the script degrades cleanly if codesign is somehow absent.
    echo "==> Ad-hoc signing bundle"
    codesign --force --deep --sign - "$BUNDLE"
    codesign --verify --deep --strict "$BUNDLE" || { echo "ERROR: codesign verify failed"; exit 1; }
else
    echo "==> Skipping ad-hoc signing (codesign not found)"
fi

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

# The DMG is a distribution unit of its own: Gatekeeper evaluates it before
# the user ever opens it, so it gets its own signature and its own ticket.
# (The .app inside was stapled above, which is what makes a first launch work
# offline after it has been dragged out.) Both halves are skipped entirely
# when MACOS_SIGN_IDENTITY is unset - the unsigned lane ends at hdiutil,
# exactly as it always has.
if [ -n "${MACOS_SIGN_IDENTITY:-}" ]; then
    echo "==> Signing DMG"
    if [ -n "${MACOS_KEYCHAIN:-}" ]; then
        codesign --force --timestamp --keychain "$MACOS_KEYCHAIN" --sign "$MACOS_SIGN_IDENTITY" "$DMG"
    else
        codesign --force --timestamp --sign "$MACOS_SIGN_IDENTITY" "$DMG"
    fi
    if [ ${#NOTARY_ARGS[@]} -gt 0 ]; then
        notarize "$DMG"
        xcrun stapler staple "$DMG"
        xcrun stapler validate "$DMG"
        echo "==> Done: $DMG"
        echo "    Developer ID signed, notarized and stapled - no Gatekeeper prompt,"
        echo "    and no quarantine attribute to clear."
        exit 0
    fi
    echo "==> Done: $DMG"
    echo "    Developer ID signed but NOT notarized (no notary credentials) -"
    echo "    Gatekeeper still refuses a quarantined copy on first launch."
    exit 0
fi

echo "==> Done: $DMG"
echo "    Ad-hoc signed, not notarized - first launch needs: right-click > Open,"
echo "    or: xattr -dr com.apple.quarantine '/Applications/NMOX Studio.app'"
