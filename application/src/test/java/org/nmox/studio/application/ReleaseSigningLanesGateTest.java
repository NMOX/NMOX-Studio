package org.nmox.studio.application;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 86 is a purchase and an identity, not an engineering decision —
 * but it had been recorded as "the engineering half is already done and
 * waiting", and that was only true of NBM signing (v2.42.0) and the
 * in-product TRUST certificate (v2.43.0). The macOS notarization and
 * Windows Authenticode lanes did not exist at all, so the entry was one
 * action plus an unwritten release lane, and the owner could not weigh the
 * decision without knowing the work was ready.
 *
 * <p>Both lanes are written now, and this gate holds the two properties
 * that make writing them safe to do BEFORE the accounts exist:
 *
 * <ol>
 *   <li><b>Every signing step is gated on its own secret</b>, refusing by
 *       name only when a secret is half-present. A lane nobody can test is
 *       only acceptable if it cannot run by accident.
 *   <li><b>The unsigned path is unchanged.</b> A broken release workflow is
 *       not recoverable from a tag, so the ad-hoc branch and the 21-asset
 *       release are pinned exactly as they were.
 * </ol>
 *
 * <p>The third law is the one that keeps the decision actionable: the
 * checklist's secret names are DERIVED from the workflow, so a lane that
 * grows a secret the one-sitting document does not name fails the build
 * rather than leaving the owner a step short on the day.
 *
 * <p>Neither lane has been executed — there is no Apple Developer
 * enrolment and no Windows code-signing account. What can be proven is
 * proven here; what cannot is said plainly in
 * {@code docs/engineering/release-signing.md}.
 */
class ReleaseSigningLanesGateTest {

    private static final Path WORKFLOW = Path.of("..", ".github", "workflows", "release.yml");
    private static final Path BUILD_DMG = Path.of("..", "packaging", "macos", "build-dmg.sh");
    private static final Path ENTITLEMENTS = Path.of("..", "packaging", "macos", "entitlements.plist");
    private static final Path WIN_SIGN = Path.of("..", "packaging", "windows", "authenticode-sign.ps1");
    private static final Path CHECKLIST = Path.of("..", "docs", "engineering", "release-signing.md");

    private static String read(Path p) throws IOException {
        assertThat(p).as("%s exists", p).exists();
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /** The body of a named workflow step, up to the next step's dash. */
    private static String step(String workflow, String name) {
        int at = workflow.indexOf("- name: " + name + "\n");
        assertThat(at).as("release.yml still has a step named %s", name).isNotEqualTo(-1);
        int next = workflow.indexOf("\n      - ", at + 1);
        return next == -1 ? workflow.substring(at) : workflow.substring(at, next);
    }

    @Test
    @DisplayName("the macOS Developer ID step does nothing at all until its secret exists")
    void macosCertificateStepIsGatedOnItsSecret() throws IOException {
        String body = step(read(WORKFLOW), "Import Developer ID certificate (optional)");
        int guard = body.indexOf("if [ -z \"$MACOS_CERT_B64\" ]");
        int exit = body.indexOf("exit 0", guard + 1);
        int firstAction = body.indexOf("security create-keychain");
        assertThat(guard)
                .as("the step must refuse before it does anything: without the guard, an empty "
                        + "secret would create a keychain and fail the import, taking the release with it")
                .isNotEqualTo(-1);
        assertThat(exit).as("… and leave the job entirely").isBetween(guard, firstAction);
        assertThat(firstAction).as("the keychain is created only past the guard").isGreaterThan(exit);
        assertThat(body)
                .as("a skipped lane says so: silence would look like signing that worked")
                .contains("::notice::macOS Developer ID signing skipped");
        // Absent is a decision; HALF present is a mistake, and `security
        // import` reports it as an opaque failure that takes the release with
        // it without naming the missing half.
        assertThat(body)
                .as("a certificate with no password must refuse BY NAME rather than failing opaquely")
                .contains("::error::MACOS_SIGNING_CERTIFICATE_BASE64 is set but "
                        + "MACOS_SIGNING_CERTIFICATE_PASSWORD is not");
    }

    @Test
    @DisplayName("the macOS lane signs with the hardened runtime, notarizes, and staples BOTH the app and the DMG")
    void macosLaneSignsNotarizesAndStaples() throws IOException {
        String dmg = read(BUILD_DMG);
        assertThat(dmg)
                .as("notarization REQUIRES the hardened runtime — a bundle signed without "
                        + "--options runtime is refused by the notary service, not by codesign")
                .contains("--options runtime");
        assertThat(dmg)
                .as("a signature with no secure timestamp expires with the certificate")
                .contains("--timestamp");
        assertThat(dmg)
                .as("the JVM entitlements are the fiddly part ledger 86 names; they must be applied")
                .contains("--entitlements \"$ent\"");
        // v2.188.0 DIED HERE, and this gate had asserted the broken spelling.
        // codesign's entitlements parser is AMFI's, and AMFI rejects XML
        // COMMENTS — which entitlements.plist is full of by house law, every
        // key carrying the reason it is open. `plutil -lint` calls the file
        // OK, so nothing caught it until a real signature was attempted:
        //   Failed to parse entitlements: AMFIUnserializeXML: syntax error near line 6
        // (line 6 being inside the header comment). The documented file stays
        // the source; codesign gets a normalised copy with the comments gone.
        assertThat(dmg)
                .as("the commented plist must be normalised before codesign sees it — AMFI "
                        + "cannot parse XML comments and plutil -lint cannot see that")
                .contains("plutil -convert xml1");
        assertThat(dmg)
                .as("and the RAW commented file must never reach codesign again")
                .doesNotContain("--entitlements \"$ENTITLEMENTS\"");
        assertThat(dmg)
                .as("--wait, so a rejection fails the release instead of publishing something "
                        + "Gatekeeper will refuse")
                .contains("notarytool submit").contains("--wait");
        // v2.188.1 came back `status: Invalid` and the run CONTINUED:
        // `notarytool submit --wait` exits ZERO on a rejected submission,
        // because its exit code reports the round trip rather than Apple's
        // verdict. The failure then surfaced two steps later as a CloudKit
        // "Record not found" from `stapler`, naming neither the bundle nor
        // the reason. Same shape as `gofmt -l` (v1.352.0): read the output.
        assertThat(dmg)
                .as("the verdict is in the OUTPUT — notarytool submit --wait exits 0 on a "
                        + "rejection, so an exit-code check passes a refused bundle straight "
                        + "through to stapling")
                .contains("status:").contains("!= \"Accepted\"");
        // refusals speak: a rejection that does not name the offending file
        // cannot be acted on, and release-signing.md documented the lookup
        // as a manual step for a human who may hold no credentials locally
        assertThat(dmg)
                .as("a refused notarization must fetch Apple's log itself, not tell a human to")
                .contains("notarytool log");
        // The app's own ticket is what makes a FIRST launch work offline once
        // the user has dragged it out of the DMG; the DMG's ticket only covers
        // the download. Both, or the offline case silently regresses.
        assertThat(dmg).as("the .app is stapled").contains("stapler staple \"$BUNDLE\"");
        assertThat(dmg).as("and so is the .dmg").contains("stapler staple \"$DMG\"");
        assertThat(dmg)
                .as("the nested binaries are sealed explicitly: `codesign --deep` is Apple-deprecated "
                        + "for signing and the notary service rejects one unsigned nested binary")
                .contains("sign_bundle_with_identity");
    }

    @Test
    @DisplayName("native libraries INSIDE jars are signed too — codesign cannot reach them")
    void nativesInsideJarsAreSigned() throws IOException {
        String dmg = read(BUILD_DMG);
        // WHY THIS EXISTS: Apple refused v2.188.3 naming TEN binaries across
        // five jars (jna, flatlaf, junixsocket, truffle-runtime, sqlite-jdbc)
        // — "The binary is not signed", "not signed with a valid Developer ID
        // certificate" (vendor-signed, not by us) and "does not include a
        // secure timestamp". codesign signs FILES and a jar is a zip, so the
        // bundle scan can never reach them.
        assertThat(dmg)
                .as("the jar-embedded natives must be signed, or the notary service refuses the "
                        + "whole archive — measured, ten of them across five jars")
                .contains("sign_natives_inside_jars");
        // DERIVED, never listed: a platform bump can add a jar carrying a
        // native, and a hand-kept list ships the next one unsigned.
        assertThat(dmg)
                .as("the population is every jar in the bundle, not a list someone maintains")
                .contains("find \"$BUNDLE\" -name '*.jar' -print0");
        // `codesign` READS STDIN. Inside a `while read` loop it swallows the
        // entries not yet consumed — measured: exactly ONE binary per jar got
        // signed and the rest vanished silently.
        // The FIRST cut of this assertion was a loose regex ("codesign" within
        // 400 chars of "</dev/null") and the mutant SURVIVED it, because the
        // `zip` call two lines below carries its own </dev/null. A gate that
        // matches loosely matches the wrong thing — pin the invocation.
        assertThat(dmg)
                .as("codesign reads stdin and eats the loop's input — THIS call must take "
                        + "/dev/null, not merely some call nearby")
                .contains("--sign \"$MACOS_SIGN_IDENTITY\" \"$tmp/$entry\" </dev/null");
        // `zip` rewrites the jar IN PLACE while a streaming `unzip -Z1` is
        // still reading that same file, so the listing loses its tail. The
        // entry list is materialised before anything is written.
        assertThat(dmg)
                .as("the entry listing must be materialised before the jar is rewritten — a "
                        + "streaming listing loses its tail to the rewrite, silently")
                .contains("entries=$(unzip -Z1");
        // and it must run BEFORE the bundle is sealed: rewriting a jar
        // changes bytes the seal covers
        assertThat(dmg.indexOf("    sign_natives_inside_jars\n"))
                .as("rewriting a jar changes bytes the bundle seal covers, so it happens first")
                .isLessThan(dmg.indexOf("codesign --force --options runtime --timestamp \\\n"
                        + "                 --entitlements"));
    }

    @Test
    @DisplayName("a signed bundle's clusters are read-only, so an update cannot break its own seal")
    void signedBundleClustersAreReadOnly() throws IOException {
        String dmg = read(BUILD_DMG);
        // A signed bundle and an IN-PLACE updater cannot both be right.
        // MEASURED, 2.187.0 -> 2.187.1 on a real bundle: a writable cluster
        // took 1068 files INSIDE Contents/ and left
        // `codesign --verify --deep --strict` exiting 1 with "a sealed
        // resource is missing or invalid"; read-only took 0 files inside,
        // 955 jars into the USERDIR, booted RC=0 with zero SEVERE running
        // 2.187.1 while the bundle's own jars stayed 2.187.0, and codesign
        // exited 0. The platform needed no change: canWriteInCluster gates
        // on File.canWrite() and checkTargetCluster falls through to the
        // userdir rather than throwing.
        assertThat(dmg)
                .as("the signed path must drop write permission on the clusters, or the first "
                        + "in-app update invalidates the notarization this release just bought")
                .contains("chmod -R a-w");
        // -w only. `a-x` would strip the execute bit from bin/nmoxstudio and
        // every jre/bin binary, which is a bundle that cannot start at all —
        // a far worse failure than the one being fixed, and one that only
        // appears on the SIGNED path where no local build would catch it.
        assertThat(dmg)
                .as("execute bits must survive: a-w, never a-wx")
                .doesNotContain("chmod -R a-wx").doesNotContain("chmod -R a-x");
        // Only the signed path. An unsigned local build and the portable zip
        // keep their writable clusters and their in-place updates.
        int signedAt = dmg.indexOf("Signing bundle with Developer ID");
        int chmodAt = dmg.indexOf("chmod -R a-w");
        assertThat(chmodAt)
                .as("the read-only step belongs INSIDE the signed branch — an unsigned build "
                        + "and the portable zip must keep updating in place")
                .isGreaterThan(signedAt);
        // ORDER, and v2.188.2 got it wrong: `codesign` WRITES the signature
        // into each Mach-O, so a read-only binary cannot be signed. Doing
        // the chmod first died on the first nested library with
        //   libjnidispatch-nb.jnilib: internal error in Code Signing subsystem
        // — a permission error wearing a scary name. It is safe afterwards
        // because POSIX modes are not part of what the signature seals
        // (measured: codesign --verify exits 0 before AND after the chmod).
        assertThat(chmodAt)
                .as("the chmod must come AFTER sign_bundle_with_identity — codesign writes into "
                        + "the binaries, so read-only ones cannot be signed at all")
                .isGreaterThan(dmg.indexOf("    sign_bundle_with_identity\n"));
        // and the lane re-proves the modes did not break the seal, rather
        // than carrying my measurement as a comment nobody re-runs
        assertThat(dmg.indexOf("codesign --verify --deep --strict --verbose=2 \"$BUNDLE\"", chmodAt))
                .as("verify the signature AFTER the chmod, so a future macOS that does seal the "
                        + "mode bits fails the release instead of shipping a broken signature")
                .isGreaterThan(chmodAt);
    }

    @Test
    @DisplayName("the entitlements grant exactly the JVM exceptions, each with its reason")
    void entitlementsCarryTheJvmKeys() throws IOException {
        String plist = read(ENTITLEMENTS);
        assertThat(plist).startsWith("<?xml").contains("</plist>");
        // Ledger 86 names these two by name as what a JVM under the hardened
        // runtime cannot start without.
        assertThat(plist).contains("<key>com.apple.security.cs.allow-jit</key>");
        assertThat(plist).contains("<key>com.apple.security.cs.allow-unsigned-executable-memory</key>");
        // Each key is a hole in the hardened runtime. A key with no reason
        // beside it is a hole nobody can review.
        for (String key : plist.split("<key>")) {
            if (!key.startsWith("com.apple.security")) {
                continue;
            }
            String name = key.substring(0, key.indexOf('<'));
            assertThat(key.substring(key.indexOf("</key>")))
                    .as("%s must be followed by <true/> (a listed-but-false entitlement grants nothing)", name)
                    .contains("<true/>");
        }
        // The comments are the REASON the normalisation step exists. If they
        // ever go away, the plutil hop above becomes decorative and the next
        // author will delete it — so the gate states the dependency.
        assertThat(plist)
                .as("every key carries its reason (house law), which is exactly what AMFI "
                        + "cannot parse — build-dmg.sh normalises the file before signing")
                .contains("<!--");
        assertThat(plist.split("<key>").length - 1)
                .as("every granted entitlement weakens the hardened runtime; keep the set small "
                        + "and deliberate rather than copying a template")
                .isLessThanOrEqualTo(4);
    }

    @Test
    @DisplayName("the Windows lane is a no-op without secrets and signs both the launchers and the installer")
    void windowsLaneIsGatedAndCoversBothArtifacts() throws IOException {
        String ps = read(WIN_SIGN);
        int guard = ps.indexOf("if (-not $azure -and -not $pfxB64)");
        assertThat(guard).as("no credentials of either shape → the script must refuse before signtool").isNotEqualTo(-1);
        assertThat(ps.indexOf("exit 0", guard)).as("… by exiting successfully, so the release continues")
                .isBetween(guard, ps.indexOf("Get-ChildItem \"C:\\Program Files (x86)\\Windows Kits"));
        assertThat(ps).as("a skipped lane says so").contains("::notice::Windows Authenticode signing skipped");
        assertThat(ps)
                .as("signing without verifying proves only that the command ran")
                .contains("verify /pa /v");

        String wf = read(WORKFLOW);
        int brand = wf.indexOf("- name: Brand launcher icons");
        int signLaunchers = wf.indexOf("- name: Sign launcher executables (optional)");
        int inno = wf.indexOf("- name: Build installer");
        int signInstaller = wf.indexOf("- name: Sign the installer (optional)");
        assertThat(signLaunchers)
                .as("AFTER rcedit — rewriting an exe's resources invalidates its signature")
                .isGreaterThan(brand);
        assertThat(inno)
                .as("… and BEFORE Inno Setup packages them, or the installed launchers are unsigned")
                .isGreaterThan(signLaunchers);
        assertThat(signInstaller)
                .as("the setup .exe is what the user downloads and what SmartScreen judges, "
                        + "so it is signed after it exists")
                .isGreaterThan(inno);
    }

    @Test
    @DisplayName("the unsigned path is exactly what it was: ad-hoc signing, and the same 21 assets")
    void theUnsignedPathIsUnchanged() throws IOException {
        String dmg = read(BUILD_DMG);
        assertThat(dmg)
                .as("with no identity the bundle is still ad-hoc signed — unsigned arm64 Mach-O "
                        + "binaries do not execute at all on Apple silicon")
                .contains("codesign --force --deep --sign - \"$BUNDLE\"");
        assertThat(dmg)
                .as("and the whole Developer ID branch hangs off ONE variable being non-empty")
                .contains("if [ -n \"${MACOS_SIGN_IDENTITY:-}\" ]");

        // A signing lane must not quietly add or drop a published asset: the
        // ship gate counts 21 and the update center reads the catalog.
        String wf = read(WORKFLOW);
        String files = step(wf, "Create GitHub Release");
        List<String> globs = new ArrayList<>();
        for (String line : files.substring(files.indexOf("files: |")).split("\n")) {
            String t = line.trim();
            if (t.startsWith("artifacts/")) {
                globs.add(t);
            }
        }
        assertThat(globs)
                .as("the published asset set is unchanged by the signing lanes")
                .containsExactly(
                        "artifacts/**/NMOX-Studio-*-portable.zip",
                        "artifacts/**/NMOX-Studio-*-sbom.json",
                        "artifacts/**/NMOX-Studio-*-linux.tar.gz",
                        "artifacts/**/nmox-studio_*.deb",
                        "artifacts/**/NMOX-Studio-*-macos.dmg",
                        "artifacts/**/NMOX-Studio-*-windows-setup.exe",
                        "artifacts/**/updates.xml",
                        "artifacts/**/updates.xml.gz",
                        "artifacts/**/*.nbm",
                        "artifacts/SHA256SUMS",
                        "artifacts/SHA256SUMS.asc");
    }

    @Test
    @DisplayName("a notarized build's cask touches nothing after copying the app")
    void theCaskTouchesNothingAfterCopying() throws IOException {
        String wf = read(WORKFLOW);
        String cask = read(Path.of("..", "Casks", "nmox-studio.rb"));
        // INVERTED in v3.0.0. Until v2.188.4 this gate asserted the
        // quarantine-clear STILL SHIPPED, because removing it before a
        // notarized build existed would have handed every `brew install` a DMG
        // that was neither notarized nor de-quarantined. The build is notarized
        // now — `spctl --assess` answers "accepted, source=Notarized Developer
        // ID" on the published DMG and app — so the opposite is the law: the
        // cask copies the app and does nothing else to it.
        for (String gone : List.of("postflight_steps", "com.apple.quarantine", "xattr", "ad-hoc")) {
            assertThat(cask)
                    .as("a notarized build needs no %s in its cask — rewriting a notarized app "
                            + "after copying is exactly what breaks its seal", gone)
                    .doesNotContain(gone);
            assertThat(step(wf, "Write cask"))
                    .as("and the generator is the other home of that same fact (%s)", gone)
                    .doesNotContain(gone);
        }
        // the one caveat that is still true stays, so the cask keeps saying
        // something useful rather than nothing at all
        assertThat(cask).as("the in-app updater note is still true and still shipped")
                .contains("in-app updater");
    }

    @Test
    @DisplayName("every signing secret the lanes read is named in the one-sitting checklist")
    void theChecklistNamesEverySecretTheLanesRead() throws IOException {
        String wf = read(WORKFLOW);
        String doc = read(CHECKLIST);
        // Derived, not hand-kept: a lane that grows a secret must fail the
        // build rather than leave the owner a step short on the day.
        Set<String> secrets = new LinkedHashSet<>();
        Matcher m = Pattern.compile("secrets\\.([A-Z0-9_]+)").matcher(wf);
        while (m.find()) {
            String name = m.group(1);
            if (name.startsWith("MACOS_") || name.startsWith("WINDOWS_") || name.startsWith("AZURE_")) {
                secrets.add(name);
            }
        }
        assertThat(secrets).as("the lanes read signing secrets at all").isNotEmpty();
        List<String> missing = secrets.stream().filter(s -> !doc.contains(s)).toList();
        assertThat(missing)
                .as("docs/engineering/release-signing.md must name every secret the lanes read — "
                        + "that document IS the action ledger 86 is waiting on")
                .isEmpty();
        assertThat(doc)
                .as("and it must be honest that nothing here has been executed — a checklist that "
                        + "reads as tested would be a claim nobody made")
                .contains("None of it has ever run");
    }

    /**
     * 3.0.0-3.0.2: a brew or browser install answered "NMOX Studio.app Not
     * Opened: Apple could not verify it is free of malware" on a bundle
     * {@code spctl} calls notarized. Gatekeeper judges every quarantined file a
     * process EXECUTES; the bundle launcher exec'd {@code bin/nmoxstudio}
     * through its shebang, and a shell script carries no embedded signature
     * ({@code spctl -t open}: "no usable signature"). Every check before then
     * used a download WITHOUT quarantine, so none could see it. The rule: the
     * launcher hands every nested script to {@code /bin/sh} as input, which is
     * how the platform's own launcher runs {@code nbexec}. The population is
     * every exec line in the launcher, derived, so a new one is judged too.
     */
    @Test
    @DisplayName("the bundle launcher never execs a script inside the bundle — a quarantined script has no signature")
    void theLauncherNeverExecsANestedScript() throws IOException {
        String dmg = read(BUILD_DMG);
        String open = "cat > \"$BUNDLE/Contents/MacOS/nmox-studio\" <<'LAUNCHER'\n";
        int from = dmg.indexOf(open);
        assertThat(from).as("build-dmg.sh still writes the bundle launcher as a heredoc").isNotEqualTo(-1);
        int to = dmg.indexOf("\nLAUNCHER\n", from);
        assertThat(to).as("the launcher heredoc is closed").isGreaterThan(from);
        List<String> execs = new ArrayList<>();
        for (String line : dmg.substring(from + open.length(), to).split("\n")) {
            String t = line.strip();
            if (t.startsWith("exec ")) {
                execs.add(t);
            }
        }
        assertThat(execs).as("the launcher starts the platform through at least its two exec lines").hasSizeGreaterThanOrEqualTo(2);
        for (String e : execs) {
            assertThat(e).as("an exec in the bundle launcher must hand a script to /bin/sh, never run it by "
                    + "its shebang - a quarantined script is refused by Gatekeeper: %s", e)
                    .startsWith("exec /bin/sh ");
        }
    }

    /**
     * The lane that cannot run without its secrets gets a way to run WITHOUT a
     * release: a dispatch builds, signs and notarizes the DMG alone. What makes
     * it safe is structural - linux and windows are push-only, so the release
     * and homebrew jobs, which need them, cannot run - and the Update gauntlet
     * must not treat a dispatched run as a release to update to.
     */
    @Test
    @DisplayName("a dispatched Release run notarizes a DMG and publishes nothing")
    void aDispatchedRunPublishesNothing() throws IOException {
        String wf = read(WORKFLOW);
        assertThat(wf).as("the dry-run door exists").contains("\n  workflow_dispatch:\n");
        for (String job : List.of("linux", "windows")) {
            assertThat(wf).as("%s must be push-only, so release/homebrew (which need it) cannot run on a dispatch", job)
                    .contains("\n  " + job + ":\n    needs: version\n    if: github.event_name == 'push'\n");
        }
        assertThat(wf).as("the release job needs a push-only lane").contains("needs: [version, linux, macos, windows]");
        assertThat(wf).as("macOS runs on a dispatch too - that is the point")
                .doesNotContain("\n  macos:\n    needs: version\n    if:");
        String gauntlet = read(Path.of("..", ".github", "workflows", "update-gauntlet.yml"));
        assertThat(gauntlet).as("the Update gauntlet ignores a dispatched Release run")
                .contains("github.event.workflow_run.event == 'push'");
    }

    /**
     * The release version is every module's OpenIDE spec version, and the
     * module system parses that as dotted numbers only. The first dry run
     * was dispatched as {@code 3.1.0-dryrun1}: notarized, accepted by
     * Gatekeeper, and every NMOX module refused to load
     * ({@code NumberFormatException: "0-dryrun1"}), so the window opened
     * empty. The version job's own script is RUN here, not matched.
     */
    @Test
    @DisplayName("the version job refuses anything but three dotted numbers, from a tag or a dispatch")
    @DisabledOnOs(OS.WINDOWS)
    void versionIsThreeDottedNumbers() throws Exception {
        String wf = read(WORKFLOW);
        Matcher m = Pattern.compile("\\n      - id: v\\n(?:        [^\\n]*\\n)*?        run: \\|\\n((?:          [^\\n]*\\n|\\n)+)")
                .matcher(wf);
        assertThat(m.find()).as("the version job's run script").isTrue();
        StringBuilder script = new StringBuilder();
        for (String line : m.group(1).split("\\n")) {
            script.append(line.length() >= 10 ? line.substring(10) : line.strip()).append('\n');
        }
        Path sh = Files.createTempFile("version-job", ".sh");
        Files.writeString(sh, script);
        record Case(String event, String ref, String input, String expect) { }
        List<Case> cases = List.of(
                new Case("push", "v3.1.0", "", "3.1.0"),
                new Case("push", "v12.0.40", "", "12.0.40"),
                new Case("push", "v3.1.0-rc1", "", null),
                new Case("workflow_dispatch", "claude/dx-3.1", "", "0.0.1"),
                new Case("workflow_dispatch", "claude/dx-3.1", "3.1.0", "3.1.0"),
                new Case("workflow_dispatch", "claude/dx-3.1", "3.1.0-dryrun1", null),
                new Case("workflow_dispatch", "claude/dx-3.1", "3.1", null));
        for (Case c : cases) {
            Path out = Files.createTempFile("github-output", ".txt");
            ProcessBuilder pb = new ProcessBuilder("bash", sh.toString()).redirectErrorStream(true)
                    .redirectOutput(new File(System.getProperty("java.io.tmpdir"), "version-job.out"));
            pb.environment().put("GITHUB_EVENT_NAME", c.event());
            pb.environment().put("GITHUB_REF_NAME", c.ref());
            pb.environment().put("DISPATCH_VERSION", c.input());
            pb.environment().put("GITHUB_OUTPUT", out.toString());
            Process p = pb.start();
            assertThat(p.waitFor(30, TimeUnit.SECONDS)).isTrue();
            String written = Files.readString(out);
            if (c.expect() == null) {
                assertThat(p.exitValue()).as("%s %s/%s must be refused", c.event(), c.ref(), c.input()).isNotZero();
                assertThat(written).as("a refused version reaches no job").doesNotContain("version=");
            } else {
                assertThat(p.exitValue()).as("%s %s/%s is a real version", c.event(), c.ref(), c.input()).isZero();
                assertThat(written.strip()).isEqualTo("version=" + c.expect());
            }
        }
    }

    @Test
    @DisplayName("every packaging shell script parses (bash -n) — the ship-scripts law, one directory over")
    @DisabledOnOs(OS.WINDOWS)
    void packagingScriptsParse() throws Exception {
        List<Path> scripts;
        try (Stream<Path> walk = Files.walk(Path.of("..", "packaging"))) {
            scripts = walk.filter(p -> p.toString().endsWith(".sh")).sorted().toList();
        }
        assertThat(scripts).as("packaging/ still holds shell scripts").isNotEmpty();
        for (Path s : scripts) {
            assertThat(run("bash", "-n", s.toString())).as("%s must parse", s).isZero();
        }
    }

    private static int run(String... argv) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(argv).redirectErrorStream(true)
                .redirectOutput(new File(System.getProperty("java.io.tmpdir"), "release-signing-gate.out"))
                .start();
        assertThat(p.waitFor(30, TimeUnit.SECONDS)).as("script check exits promptly").isTrue();
        return p.exitValue();
    }
}
