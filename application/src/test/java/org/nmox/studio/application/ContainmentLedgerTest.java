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
 * The containment ledger: every place in the product's main sources that
 * decides "which file does this caller-supplied string name inside this
 * root?" either routes through {@link org.nmox.studio.core.util.Containment}
 * — the one home ledger 111 gave that decision — or is CLASSIFIED here with
 * a reason a person can disagree with.
 *
 * <p><b>Why a derived census and not a list.</b> Ledger 111 closed four
 * copies of one rule, three of whose javadocs called themselves the
 * canonical one, and the two gates it left behind ({@code
 * ContainmentSingleHomeTest}, rack and ui) each carried a HAND-KEPT
 * population. A hand-kept population cannot prove itself complete, and it
 * was not: three more copies were sitting outside it the day those gates
 * shipped — {@code DebugEntries} on the debug-launch path, {@code
 * DocsStaging} in the forge, {@code NgSchematic} in front of a spawn.
 * Deriving the population found two more nobody had named. A gate whose
 * population is smaller than its claim is the defect it exists to catch.
 *
 * <p><b>What the derivation looks for</b> is the RESOLVE shape, in either
 * flavour the product writes it: build a child from a root and a
 * caller-supplied string ({@code new File(root, rel)} or {@code
 * root.resolve(rel)}), canonicalize or realpath or normalize it, and judge
 * it with a path prefix test. Comments are stripped first, because a source
 * gate that matches a literal is one comment away from decorative
 * (v2.178.0). A file that routes through {@code Containment} joins the
 * census by its CALL instead, so routing a site keeps it visible here
 * rather than letting it vanish from the population it belongs to.
 *
 * <p>The sibling gates keep their own half: they pin that each routed
 * surface still CALLS the guard at the right site, and that each still
 * speaks its own refusal. This one answers the other question — whether
 * anything is deciding containment outside the guard at all.
 */
class ContainmentLedgerTest {

    /** Canonicalize, realpath, or normalize — the judging half. */
    private static final Pattern JUDGED = Pattern.compile(
            "getCanonicalPath\\s*\\(\\s*\\)|getCanonicalFile\\s*\\(\\s*\\)"
            + "|toRealPath\\s*\\(|\\.\\s*normalize\\s*\\(\\s*\\)");

    /**
     * A PATH prefix test, not a string-content one: {@code
     * line.startsWith("slug=")} is parsing, not containment, so a literal
     * argument does not count. Without this the census swept in every
     * marker parser in the product.
     */
    private static final Pattern PREFIX_TEST = Pattern.compile(
            "\\.\\s*startsWith\\s*\\(\\s*[^\"\\s)]");

    /**
     * Building a child from a root and something that is not a fixed name.
     * {@code new File(gitDir, "HEAD")} names a file the product chose;
     * {@code new File(dir, rel)} resolves a string somebody else supplied,
     * and only the second is a containment question.
     */
    private static final Pattern CHILD_OF_ROOT = Pattern.compile(
            "new File\\s*\\([^,()]+,\\s*[^\"()]|\\.\\s*resolve\\s*\\(\\s*[^\"\\s)]");

    /** A call into the one home, however the formatter broke the line. */
    private static final Pattern GUARD_CALL = Pattern.compile(
            "Containment\\s*\\.\\s*(resolve|resolvePath)\\s*\\(");

    private static final List<String> MODULES = List.of("core", "editor", "tools", "project",
            "ui", "rack", "apiclient", "dbstudio", "web3", "infra");

    /**
     * The census must not be able to go quiet. If a refactor renames the
     * shapes above out of existence, an empty derivation would make this
     * gate pass while proving nothing — so the total is floored at the
     * sites known to exist when the sweep ran (9, less the home itself).
     */
    private static final int FLOOR = 8;

    /**
     * file basename → why this file decides containment for itself. A file
     * that routes through {@code Containment} is not in this map; it is in
     * the census by its call.
     */
    private static final Map<String, String> CLASSIFIED = Map.ofEntries(
            Map.entry("GitFacts.java",
                "A DIFFERENT QUESTION: its confinement (ledger 43) asks whether a "
                + "gitdir: pointer lands inside A .git directory — any of them, including "
                + "one outside the repo root for a worktree or submodule, which is the "
                + "whole point. Containment answers 'inside THIS root', which would "
                + "refuse exactly the layouts that pointer exists to support."),

            // --- real copies of the rule, named rather than hidden ---
            Map.entry("TerminalLinks.java",
                "A DIFFERENT QUESTION (3.2.0): a location the user CLICKED in their own "
                + "terminal output is opened where it is, because a stack frame names files "
                + "anywhere on disk — node_modules, /usr/lib/python3, ~/.cargo — and refusing "
                + "those would refuse the frames people click most. It asks no 'inside this "
                + "root?' question; the relative case joins the aimed project only to FIND the "
                + "file, the click is the user's own gesture, and only an existing regular file "
                + "is opened, read-only, in the editor. Nothing is written or run."),
            Map.entry("SymbolIndexProvider.java",
                "A DIFFERENT QUESTION, measured (ledger 117, which swept its sibling "
                + "McpSubscriptions and left this one): outline() must accept an "
                + "ABSOLUTE path that names a file inside the root, because the Agent "
                + "Port itself hands agents absolute paths — EditorState reports every "
                + "open tab as getAbsolutePath(), so editor_state.activeFile and "
                + "ide_context.activeFile are absolute, and 'outline what I am editing' "
                + "is the next call an agent makes (pinned by SymbolIndexProviderTest). "
                + "Containment JOINS an absolute-looking name under the root — its own "
                + "deliberate, test-pinned policy — which would answer 'no such file' "
                + "about a file this server had just named. DebugEntries chose the "
                + "joining side in v2.186.0 because npm's spec says `main` is relative; "
                + "that reason does not reach a string the product itself emitted. "
                + "Second, this surface speaks FOUR refusals where the guard answers one "
                + "null for two of them, so routing would make the root itself read "
                + "'outside the aimed project', which is false.")
    );

    @Test
    @DisplayName("every containment decision routes through the one guard or is classified here")
    void everyContainmentSiteIsRoutedOrClassified() throws IOException {
        Set<String> census = new TreeSet<>();
        Set<String> deciding = new TreeSet<>();
        List<String> where = new ArrayList<>();
        for (Path p : sources()) {
            String body = GateSources.stripComments(read(p));
            String name = p.getFileName().toString();
            boolean routed = GUARD_CALL.matcher(body).find();
            Matcher judged = JUDGED.matcher(body);
            boolean shaped = judged.find()
                    && PREFIX_TEST.matcher(body).find()
                    && CHILD_OF_ROOT.matcher(body).find();
            if (routed || shaped) {
                census.add(name);
            }
            // Containment.java IS the decision; it is the home, not a site
            if (shaped && !routed && !"Containment.java".equals(name)) {
                deciding.add(name);
                where.add(name + ":" + line(body, judged.start()));
            }
        }
        census.remove("Containment.java");

        assertThat(census)
                .as("the containment census went quiet — if the shapes it hunts were "
                        + "renamed away, this gate proves nothing until it is taught the "
                        + "new spelling (gate the outcome, not the spelling)")
                .hasSizeGreaterThanOrEqualTo(FLOOR);
        assertThat(deciding)
                .as("a containment decision outside the one home: route it through "
                        + "core.util.Containment, or classify it in this ledger with a "
                        + "reason a person can disagree with. Sites seen: " + where)
                .isEqualTo(new TreeSet<>(CLASSIFIED.keySet()));
    }

    @Test
    @DisplayName("the surfaces these sweeps routed really call the guard")
    void theSweptSitesCallTheGuard() throws IOException {
        // the other half of the two-proof law (v1.321.0): the census above
        // can only see that nothing decides containment on its own, which a
        // site that decides NOTHING AT ALL would also satisfy
        assertThat(guardCalls("rack/src/main/java/org/nmox/studio/rack/mcp/McpSubscriptions.java"))
                .as("the Agent Port's file subscription — ledger 117 — rides the ONE guard").isTrue();
        assertThat(guardCalls("tools/src/main/java/org/nmox/studio/tools/npm/DebugEntries.java"))
                .as("the debug entry — a spawn path — rides the ONE guard").isTrue();
        assertThat(guardCalls("rack/src/main/java/org/nmox/studio/rack/service/DocsStaging.java"))
                .as("the forge's staged sample files ride the ONE guard").isTrue();
        assertThat(guardCalls("ui/src/main/java/org/nmox/studio/ui/actions/NgSchematic.java"))
                .as("the schematic's target folder — a spawn's cwd — rides the ONE guard").isTrue();
        assertThat(guardCalls("tools/src/main/java/org/nmox/studio/tools/vscode/VsCodeTasks.java"))
                .as("a VS Code task's options.cwd — a spawn's cwd — rides the ONE guard").isTrue();
        // ledger 117's first: the product's one templated WRITE path, and the
        // only one of that entry's three that creates files
        assertThat(guardCalls(
                "rack/src/main/java/org/nmox/studio/rack/projectstudio/UserTemplates.java"))
                .as("a drop-in template's files — a WRITE — ride the ONE guard").isTrue();
    }

    @Test
    @DisplayName("each swept surface still speaks its own refusal — the guard decides, they say")
    void refusalsStayWithTheirSurfaces() throws IOException {
        // one decision, three sentences: they are about different things and
        // a shared one would be wrong in at least two places
        assertThat(read(Path.of("..", "rack", "src", "main", "java", "org", "nmox", "studio",
                "rack", "service", "DocsStaging.java")))
                .as("a skipped sample must SAY so — it used to be a bare continue")
                .contains("resolves outside");
        assertThat(read(Path.of("..", "ui", "src", "main", "java", "org", "nmox", "studio",
                "ui", "actions", "NgSchematicAction.java")))
                .as("the folder field's own words")
                .contains("NgSchematicAction_folderMissing=");
    }

    private static boolean guardCalls(String relative) throws IOException {
        Path p = Path.of("..", relative.replace("/", java.io.File.separator));
        return GUARD_CALL.matcher(GateSources.stripComments(read(p))).find();
    }

    private static int line(String body, int offset) {
        return (int) body.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
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
