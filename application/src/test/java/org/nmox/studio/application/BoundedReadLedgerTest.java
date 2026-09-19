package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bounded-read ledger (v2.180.0): every whole-file read in the
 * product's main sources either goes through {@link
 * org.nmox.studio.core.util.BoundedReads} — which measures the file
 * before reading a byte — or is CLASSIFIED here with a reason a person
 * can disagree with. A new raw read fails this test by name until
 * somebody decides, because the alternative is what a sweep on
 * 2026-09-18 measured: 73 unguarded whole-file reads across ten
 * modules, most of them on files a {@code git clone} brings and a
 * project AIM reads without anyone asking. Enumeration beats
 * recollection — the {@code SpawnSiteTrustLedgerTest} shape, applied
 * to memory instead of execution.
 *
 * <p><b>The rule this ledger applies.</b> A read is CAPPED when the file
 * ARRIVES WITH THE PROJECT and the read RUNS WITHOUT BEING ASKED FOR —
 * on aim, on paint, on save, on file-open, on a drop-in scan. A read is
 * blessed when one of three things is true:
 *
 * <ul>
 *   <li>the caller already measures the file before reading it (its own
 *       ceiling, chosen for its own reason — those route through
 *       BoundedReads only where the spelling was already shared);</li>
 *   <li>the product itself wrote or ships the file (its own journal, its
 *       own marker, its own bundled website, its own launcher conf);</li>
 *   <li>the user named that exact file in a gesture this instant (a
 *       chooser, a kit wizard's target, the source line an error names).</li>
 * </ul>
 *
 * <p>The third is the softest and is stated as a judgement, not a proof:
 * a press is a person deciding to spend the memory, and refusing a file
 * they chose would be its own defect.
 */
class BoundedReadLedgerTest {

    /**
     * A whole-file read, however the formatter broke the line — and
     * whichever API spells it. {@code Files.readString} is the JDK's way and
     * {@code FileObject.asText()} is the platform's; both hand back the
     * entire file as one String, so a population written as the first
     * spelling alone is a gate that a one-word change walks past. It did:
     * this ledger shipped in v2.180.0 naming only the JDK three, while seven
     * {@code asText()} reads of a project's own files sat outside it
     * unclassified — the v2.19.1 law (gate the OUTCOME, not the spelling)
     * turned on the gate that quotes it.
     */
    private static final Pattern RAW_READ = Pattern.compile(
            "Files\\s*\\.\\s*(readString|readAllBytes|readAllLines)\\s*\\(|"
            + "\\.\\s*as(Text|Lines)\\s*\\(\\s*\\)");

    /**
     * A call to the bounded reader, however the formatter broke the line.
     * A plain substring would miss {@code BoundedReads\n    .read(} — and
     * did, on this gate's first run, in the very file this ledger names as
     * read-on-a-paint (the v2.85.0 {@code ProcessSupport\n.runBounded(}
     * scar, one class over). Gate the outcome, not the spelling.
     */
    private static final Pattern BOUNDED_CALL = Pattern.compile(
            "BoundedReads\\s*\\.\\s*(read|readLines)\\s*\\(");

    private static final List<String> MODULES = List.of("core", "editor", "tools", "project",
            "ui", "rack", "apiclient", "dbstudio", "web3", "infra");

    /**
     * file basename → why this file may read whole files raw. A file
     * that routes EVERY read through {@code BoundedReads} is not in this
     * ledger at all; it simply leaves the population.
     */
    private static final Map<String, String> LEDGER = Map.ofEntries(
            // --- the caller measures the file itself, for its own reason ---
            Map.entry("CssTokens.java",
                "MEASURED ELSEWHERE: every file it reads came from BoundedWalk.collect, "
                + "which skips anything over MAX_FILE_BYTES at collection — the census is "
                + "the ceiling, and a second one here would only disagree with it"),
            Map.entry("CssClasses.java",
                "MEASURED ELSEWHERE: reads the same BoundedWalk census CssTokens collects, "
                + "for declarations, usages and the rename survey"),
            Map.entry("RenameClassAction.java",
                "MEASURED ELSEWHERE: rewrites exactly the files the rename survey named, "
                + "which are the BoundedWalk census and nothing else"),
            Map.entry("Routes.java",
                "MEASURED AT COLLECTION: its own walk admits a source file only when "
                + "f.length() <= MAX_FILE_BYTES, so the read is already bounded"),
            Map.entry("TestIndex.java",
                "MEASURED: indexes a file only after Files.size(file) <= MAX_FILE_BYTES"),
            Map.entry("ProjectSymbols.java",
                "MEASURED: the same size gate before the read, with the over-cap file "
                + "recorded as contributing nothing"),
            Map.entry("SymbolIndexProvider.java",
                "MEASURED: refuses over ProjectSymbols.MAX_FILE_BYTES and SAYS the size "
                + "in the outline's own message"),
            Map.entry("EnvKeys.java",
                "MEASURED: skips an .env family file over MAX_BYTES before reading it"),
            Map.entry("I18nCatalogs.java",
                "MEASURED: a catalog over MAX_FILE_BYTES becomes a ParseFailure naming the "
                + "cap, and readSmall applies the same ceiling to its small helpers"),
            Map.entry("ImportMaps.java",
                "MEASURED: skips a page over MAX_PAGE_BYTES before parsing its import map"),
            Map.entry("DebugEntries.java",
                "MEASURED: Files.size against MAX_PACKAGE_JSON_BYTES before the read — the "
                + "javadoc says 'read bounded' and the code agrees"),
            Map.entry("TextSearch.java",
                "MEASURED: the Agent Port's search refuses a file over MAX_FILE_BYTES and "
                + "reads bytes so it can also refuse a binary one"),
            Map.entry("EnvFiles.java",
                "MEASURED: an .env over MAX_BYTES yields an empty environment rather than "
                + "a read"),
            Map.entry("SpaceExporter.java",
                "MEASURED, AND ITS OWN CAPS SPEAK: the exporter skips a file over FILE_CAP "
                + "and a run over TOTAL_CAP, naming each skip in the outcome the teacher "
                + "reads — and the export is an explicit gesture over the aimed project"),

            // --- the product's own file, which the product itself wrote or ships ---
            Map.entry("FlightRecorder.java",
                "OUR OWN FILE: the JSONL journal under the userdir, written by this class "
                + "and rotated by it at JOURNAL_MAX_BYTES — nothing a clone can bring"),
            Map.entry("RackService.java",
                "OUR OWN FILE: the session snapshot at netbeans.user/var/nmox/sessions, "
                + "written by startSessionSnapshots every few seconds and disposable by "
                + "design — a corrupt one is already shrugged off"),
            Map.entry("Experiments.java",
                "OUR OWN FILE: the experiment marker this class writes at creation"),
            Map.entry("LearningSpace.java",
                "OUR OWN FILE: the learning-space marker this class writes at creation"),
            Map.entry("SiteServer.java",
                "OUR OWN FILE: serves the website bundled in the ui module's own release "
                + "directory, containment-checked, with no project path reachable"),
            Map.entry("DocsShots.java",
                "OUR OWN FILE: the docs forge reads what it staged, and only when "
                + "-Dnmox.shots.dir asked for a forge run at all"),
            Map.entry("GeneralOptionsPanelController.java",
                "OUR OWN FILE: the per-user launcher conf in the userdir, which this panel "
                + "is the thing that writes"),

            // --- the user named this exact file in a gesture, this instant ---
            Map.entry("ApiClientTopComponent.java",
                "CHOSEN: every one of these reads is an Import… the user just picked in a "
                + "file chooser — curl, .http, OpenAPI, Postman, HAR, Insomnia"),
            Map.entry("BlockStudioTopComponent.java",
                "CHOSEN: Open Component… reads the file the user just picked in a chooser"),
            Map.entry("NgSwitchActions.java",
                "CHOSEN: the component ↔ template switcher reads the one sibling the "
                + "gesture names, to answer where it should jump"),
            Map.entry("DevToolsPanel.java",
                "CHOSEN: Open Source and Edit Style… read the page's own source, resolved "
                + "through the serving registry at the press, for the element the user picked"),
            Map.entry("BrowserErrorDisclosure.java",
                "CHOSEN: Explain error… reads the file the stack trace names, at the press, "
                + "to show seven lines around it"),
            Map.entry("Checkpoints.java",
                "CHOSEN: Check My Work reads the learner's own file when they press it"),
            Map.entry("CheckDisclosure.java",
                "CHOSEN: the same file as Checkpoints, when the learner asks KVASIR why a "
                + "check failed"),
            Map.entry("KitFiles.java",
                "CHOSEN: compares the file a kit wizard is about to write against what is "
                + "there, so the never-clobber law can answer — inside the wizard's press"),
            Map.entry("ClassicKit.java",
                "CHOSEN: the kit reads the entry page and package.json it is about to "
                + "rewrite, at the wizard's press"),
            Map.entry("PwaKit.java",
                "CHOSEN: the kit reads the entry page it is about to wire, at the press"),
            Map.entry("A11yKit.java",
                "CHOSEN: the kit reads the entry page it is about to wire, at the press"),
            Map.entry("I18nKit.java",
                "CHOSEN: the kit reads the entry page it is about to wire, at the press"),

            // --- the platform's spelling of the same gesture: the file the
            // click is on its way to OPEN, read once to turn an offset into
            // a line number. The editor loads that file whole a moment
            // later, so a ceiling here would refuse what the platform is
            // about to read anyway — and refuse a file the user picked.
            Map.entry("CssClassHyperlink.java",
                "CHOSEN: ⌘-click a class → openAt reads the rule's file to count lines to "
                + "the offset, and the editor opens that same file in the next breath"),
            Map.entry("CssClassUsageHyperlink.java",
                "CHOSEN: the reverse jump's openAt reads the markup file it is opening, to "
                + "turn the usage's offset into a line"),
            Map.entry("CssVarHyperlink.java",
                "CHOSEN: the design-token jump's openAt reads the declaring stylesheet it "
                + "is opening, to turn the declaration's offset into a line"),
            Map.entry("JsClassHyperlink.java",
                "CHOSEN: the class jump from JavaScript reads the stylesheet it is opening, "
                + "to turn the rule's offset into a line"),
            Map.entry("NgSelectorHyperlink.java",
                "CHOSEN: the Angular selector jump reads the component file it is opening, "
                + "to turn the selector's offset into a line"),
            Map.entry("ProjectJumpHyperlink.java",
                "CHOSEN: the shared fetch→route and env-key jumps read the file they are "
                + "opening, to turn the target's offset into a line"),
            Map.entry("CopyTsTypesAction.java",
                "CHOSEN: Copy TS Types reads the .json the user right-clicked, and only "
                + "when it is not already open — an open buffer wins (v2.34.1)")
    );

    /**
     * Readers that must measure before reading, checked by name because
     * "it is not in the population" is a weaker claim than "it calls the
     * bounded reader". These are the aim-time and save-time paths the
     * v2.180.0 sweep found: a clone brings the file and nobody asked.
     */
    private static final Map<String, String> CAPPED = Map.ofEntries(
            Map.entry("rack/src/main/java/org/nmox/studio/rack/devices/ProjectInspector.java",
                    "every toolchain answer starts here, on aim"),
            Map.entry("rack/src/main/java/org/nmox/studio/rack/devices/LegacyWeb.java",
                    "classic-library detection, on aim"),
            Map.entry("rack/src/main/java/org/nmox/studio/rack/devices/Workspaces.java",
                    "monorepo detection, on aim"),
            Map.entry("rack/src/main/java/org/nmox/studio/rack/devices/NpmScriptDevice.java",
                    "the SCRIPT knob's options, on aim and on every manifest pulse"),
            Map.entry("rack/src/main/java/org/nmox/studio/rack/devices/DynamoDevice.java",
                    "the TASK knob parses the project's Gruntfile/gulpfile, on aim"),
            Map.entry("rack/src/main/java/org/nmox/studio/rack/devices/UserDevices.java",
                    "a drop-in device file"),
            Map.entry("rack/src/main/java/org/nmox/studio/rack/docker/DockerRecipes.java",
                    "a drop-in Dockerize recipe"),
            Map.entry("rack/src/main/java/org/nmox/studio/rack/projectstudio/UserProbes.java",
                    "a drop-in Doctor probe"),
            Map.entry("rack/src/main/java/org/nmox/studio/rack/projectstudio/UserTemplates.java",
                    "a drop-in project template"),
            Map.entry("rack/src/main/java/org/nmox/studio/rack/projectstudio/LearningCatalog.java",
                    "a drop-in learning catalogue"),
            Map.entry("rack/src/main/java/org/nmox/studio/rack/projectstudio/PackageJsonFile.java",
                    "Project Studio's manifest editor loads the project's package.json"),
            Map.entry("rack/src/main/java/org/nmox/studio/rack/blockstudio/BlockIO.java",
                    ".nmoxblocks.json, beside the project"),
            Map.entry("rack/src/main/java/org/nmox/studio/rack/model/RackIO.java",
                    ".nmoxrack.json and a shared rack from another machine"),
            Map.entry("tools/src/main/java/org/nmox/studio/tools/npm/WebProject.java",
                    "the project tree's label, read on a PAINT"),
            Map.entry("tools/src/main/java/org/nmox/studio/tools/npm/NpmExplorerTopComponent.java",
                    "the Explorer's script list, on aim"),
            Map.entry("tools/src/main/java/org/nmox/studio/tools/npm/InstallGuard.java",
                    "the dependencies check before a Run"),
            Map.entry("editor/src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java",
                    "package.json probes, on every file open"),
            Map.entry("editor/src/main/java/org/nmox/studio/editor/format/PrettierFormatter.java",
                    "the package.json probe, on every save"),
            Map.entry("editor/src/main/java/org/nmox/studio/editor/standards/EditorConfig.java",
                    ".editorconfig, on every save"),
            Map.entry("editor/src/main/java/org/nmox/studio/editor/classic/ClassicLibraryDetector.java",
                    "manifest detection behind completion"),
            Map.entry("ui/src/main/java/org/nmox/studio/ui/tasks/TasksIO.java",
                    ".nmoxtasks.json, beside the project"),
            Map.entry("apiclient/src/main/java/org/nmox/studio/apiclient/api/WorkspaceIO.java",
                    ".nmoxapi.json, beside the project"),
            Map.entry("dbstudio/src/main/java/org/nmox/studio/dbstudio/io/DbWorkspaceIO.java",
                    ".nmoxdb.json, beside the project"),
            Map.entry("dbstudio/src/main/java/org/nmox/studio/dbstudio/ui/DbStudioTopComponent.java",
                    "the .env connection offer, on aim"),
            Map.entry("web3/src/main/java/org/nmox/studio/web3/io/Web3WorkspaceIO.java",
                    ".nmoxweb3.json, beside the project"),
            Map.entry("web3/src/main/java/org/nmox/studio/web3/engine/ArtifactScanner.java",
                    "build artifacts, on aim and on every artifact pulse"),
            Map.entry("infra/src/main/java/org/nmox/studio/infra/model/GraphIO.java",
                    ".nmoxinfra.json, beside the project")
    );

    @Test
    @DisplayName("every whole-file read is bounded or classified in the ledger")
    void everyWholeFileReadIsClassified() throws IOException {
        Set<String> found = new TreeSet<>();
        List<String> where = new ArrayList<>();
        for (Path p : sources()) {
            String body = stripComments(read(p));
            Matcher m = RAW_READ.matcher(body);
            while (m.find()) {
                found.add(p.getFileName().toString());
                where.add(p.getFileName() + ":" + line(body, m.start()));
            }
        }
        // BoundedReads.java hosts the one measured read every other site
        // is supposed to reach; it is the primitive, not a call site.
        found.remove("BoundedReads.java");
        assertThat(found)
                .as("an unclassified whole-file read: route it through "
                        + "core.util.BoundedReads, or classify it in this ledger with a "
                        + "reason a person can disagree with. Sites seen: " + where)
                .isEqualTo(new TreeSet<>(LEDGER.keySet()));
    }

    @Test
    @DisplayName("every ledger reason is written down, and the ledger holds no ghosts")
    void reasonsAreRealAndCurrent() throws IOException {
        List<String> names = sources().stream().map(p -> p.getFileName().toString()).toList();
        List<String> ghosts = new ArrayList<>();
        List<String> reasonless = new ArrayList<>();
        for (Map.Entry<String, String> e : LEDGER.entrySet()) {
            if (!names.contains(e.getKey())) {
                ghosts.add(e.getKey());
            }
            if (e.getValue().length() < 40) {
                reasonless.add(e.getKey());
            }
        }
        assertThat(ghosts).as("named in the ledger but no longer in the product").isEmpty();
        assertThat(reasonless).as("a blessing without a reason is a guess").isEmpty();
    }

    @Test
    @DisplayName("the readers a clone feeds without being asked really measure first")
    void theAimTimeReadersAreCapped() throws IOException {
        List<String> uncapped = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> e : CAPPED.entrySet()) {
            Path p = Path.of("..", e.getKey());
            if (!Files.isRegularFile(p)) {
                missing.add(e.getKey());
                continue;
            }
            if (!BOUNDED_CALL.matcher(stripComments(read(p))).find()) {
                uncapped.add(e.getKey() + " — " + e.getValue());
            }
        }
        assertThat(missing).as("a CAPPED entry naming a file that no longer exists").isEmpty();
        assertThat(uncapped)
                .as("this file's reads are fed by a clone with nobody asking — they must "
                        + "go through core.util.BoundedReads")
                .isEmpty();
    }

    @Test
    @DisplayName("BoundedReads measures the file before it reads a byte, and says so")
    void theReaderRefusesBeforeReading() throws IOException {
        String body = read(Path.of("..", "core", "src", "main", "java", "org", "nmox",
                "studio", "core", "util", "BoundedReads.java"));
        int size = body.indexOf("Files.size(file)");
        int read = body.indexOf("Files.readString(file");
        assertThat(size).as("BoundedReads must measure the file").isGreaterThan(0);
        assertThat(read).as("BoundedReads must read the file").isGreaterThan(0);
        assertThat(size)
                .as("the measurement has to come BEFORE the read, or the cap is decoration")
                .isLessThan(read);
        assertThat(body)
                .as("a refusal speaks: it names the file and its size")
                .contains("KiB, over the");
    }

    /** Comment-stripped source: a gate that matches a literal must read the CODE. */
    private static String stripComments(String src) {
        StringBuilder sb = new StringBuilder();
        boolean inBlock = false;
        for (String lineText : src.split("\n", -1)) {
            String t = lineText.strip();
            if (inBlock) {
                if (t.contains("*/")) {
                    inBlock = false;
                }
                sb.append('\n');
                continue;
            }
            if (t.startsWith("/*")) {
                inBlock = !t.contains("*/");
                sb.append('\n');
                continue;
            }
            sb.append(t.startsWith("//") ? "" : lineText).append('\n');
        }
        return sb.toString();
    }

    /** 1-based line of an offset, so a finding names its place. */
    private static int line(String body, int offset) {
        int n = 1;
        for (int i = 0; i < offset && i < body.length(); i++) {
            if (body.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    /** CRLF folded: the Windows lane checks out the same sources differently. */
    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static List<Path> sources() throws IOException {
        List<Path> out = new ArrayList<>();
        for (String module : MODULES) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                out.addAll(files.filter(f -> f.toString().endsWith(".java")).toList());
            }
        }
        return out;
    }
}
