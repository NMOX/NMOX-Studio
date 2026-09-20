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
    @DisplayName("the cask keeps its quarantine-clear until a notarized build exists, and says what comes out on the day")
    void theCaskRecordsWhatComesOutOnTheDay() throws IOException {
        String wf = read(WORKFLOW);
        String cask = read(Path.of("..", "Casks", "nmox-studio.rb"));
        // NOT removed now: no notarized build exists, and a cask that stops
        // clearing quarantine before one does breaks every install.
        assertThat(cask).as("the consented quarantine-clear still ships").contains("postflight_steps do");
        String write = step(wf, "Write cask");
        assertThat(write)
                .as("the removal must be written where the next reader is already standing — "
                        + "the generator, not a document they would have to know to open")
                .contains("WHEN THE BUILD IS NOTARIZED, THIS CASK LOSES THREE THINGS.")
                .contains("postflight_steps")
                .contains("CaskGeneratorParityTest");
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
