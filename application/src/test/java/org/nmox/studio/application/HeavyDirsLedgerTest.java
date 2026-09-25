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
 * The heavy-directory ledger (ledger 110, closed in v2.186.0): every file
 * in the product's main sources that names {@code node_modules} either
 * reaches {@link org.nmox.studio.core.util.HeavyDirs} — the one home for
 * "which directories does a walk skip" — or is CLASSIFIED here with a
 * reason a person can disagree with.
 *
 * <p><b>Why this gate exists is the whole finding.</b> A sweep on
 * 2026-09-19 counted TWELVE skip-set declarations across four modules
 * holding NINE distinct answers, plus a thirteenth written as a bare
 * inline name. Only five names appeared in all twelve. Nobody decided
 * that; the sets drifted apart one honest edit at a time because
 * <em>nothing was watching</em> — there was no compiler error, no test,
 * and no way for a reviewer adding a fourteenth walk to know the other
 * thirteen existed. The merge itself was a one-release job; this gate is
 * the part that keeps it merged.
 *
 * <p><b>The population is derived, not listed.</b> Every {@code .java}
 * under {@code <module>/src/main/java} is read, comments stripped, and any
 * file carrying the literal {@code "node_modules"} joins the population —
 * whatever shape it is written in. That is deliberately broader than "a
 * {@code Set.of(…)} containing node_modules", because the thirteenth copy
 * was NOT a set literal: {@code Workspaces.walk} spelled it
 * {@code "node_modules".equals(dir.getName())}, which a set-shaped gate
 * would have walked straight past. Gate the outcome, not the spelling
 * (v2.19.1).
 *
 * <p>Membership leaves the population the moment a file routes through
 * {@code HeavyDirs}; it does not need an entry here. The {@link #LEDGER}
 * holds only the files that name {@code node_modules} for a reason that
 * is <em>not</em> a walk.
 */
class HeavyDirsLedgerTest {

    /**
     * The thing a walk skips, however it is written. One directory name is
     * enough: no file in this product names {@code node_modules} by
     * accident, and the twelve sets that started this ledger all carried
     * it, so it is the one literal that identifies the whole family
     * without a shape assumption.
     */
    private static final Pattern NAMES_THE_TREE = Pattern.compile("\"node_modules\"");

    /**
     * A reference to the one home, however the formatter broke the line or
     * the caller qualified it. {@code HeavyDirs.NAMES},
     * {@code HeavyDirs.plus(…)} and {@code HeavyDirs.isHeavy(…)} are all
     * lawful arrivals; a bare {@code import} that nothing uses is not, so
     * the pattern requires the dot.
     */
    private static final Pattern REACHES_THE_HOME =
            Pattern.compile("HeavyDirs\\s*\\.\\s*(NAMES|plus|isHeavy)\\b");

    private static final List<String> MODULES = List.of("core", "editor", "tools", "project",
            "ui", "rack", "apiclient", "dbstudio", "web3", "infra");

    /**
     * The file that HOSTS the one home, and the gate that reads it. Neither
     * is a call site.
     *
     * <p>The second entry is not hypothetical bookkeeping. This repository
     * has been bitten three times by a source gate matching its own
     * forbidden literal — most recently in v2.182.0, where a gate flagged
     * the javadoc sentence that DOCUMENTED the contract it was policing.
     * This gate reads {@code src/main} only and lives in {@code src/test},
     * so today it cannot see itself; the exclusion is written down anyway,
     * so that widening the scan later fails loudly on something else
     * instead of quietly on this.
     */
    private static final Set<String> NOT_A_CALL_SITE = Set.of(
            "HeavyDirs.java", "HeavyDirsLedgerTest.java");

    /**
     * file basename → why this file names {@code node_modules} without
     * being a walk that skips it. A file that routes its walk through
     * {@code HeavyDirs} is not in this ledger at all; it simply leaves the
     * population.
     */
    private static final Map<String, String> LEDGER = Map.ofEntries(
            Map.entry("LanguageServers.java",
                "NOT A SKIP — A RESOLVE: builds the PATH to node_modules to find the "
                + "TypeScript the Angular Language Service must load, probing the nested "
                + "install then the hoisted root (v1.216.0). It is looking INTO the "
                + "directory every walk refuses, which is the opposite decision"),
            Map.entry("InstallGuard.java",
                "NOT A SKIP — A PROBE: the third beginner wall asks whether node_modules "
                + "EXISTS beside a package.json that declares dependencies, so that a "
                + "clone's first Run says 'install first' instead of 'Cannot find "
                + "module'. The name is the subject of the question, not a walk's exclusion"),
            Map.entry("WorkspaceDependencies.java",
                "NOT A SKIP — A RESOLVE AND A REFUSAL (3.3): it looks INTO node_modules for "
                + "the link the package manager made to a workspace package, then refuses "
                + "any answer whose resolved path lies under a node_modules segment, because "
                + "that is a third-party install. HeavyDirs would be the wrong rule: it also "
                + "names build/ and dist/, and a workspace package may be called either"),
            Map.entry("DockerizeGenerator.java",
                "NOT A WALK AT ALL: this is the CONTENT of a generated .dockerignore, "
                + "read by docker build inside the user's own container context. It "
                + "answers to Docker's rules and to what each base image needs — the Go "
                + "recipe ignores bin/, the Rust one target/ — not to what an IDE scan "
                + "should elide, and binding the two would make a HeavyDirs edit silently "
                + "change what ships in a user's image")
    );

    @Test
    @DisplayName("every walk's skip set is the one home, or is classified here")
    void everySkipSetIsRoutedOrClassified() throws IOException {
        Set<String> unrouted = new TreeSet<>();
        List<String> where = new ArrayList<>();
        for (Path p : sources()) {
            String name = p.getFileName().toString();
            if (NOT_A_CALL_SITE.contains(name)) {
                continue;
            }
            String body = GateSources.stripComments(read(p));
            Matcher m = NAMES_THE_TREE.matcher(body);
            if (!m.find() || REACHES_THE_HOME.matcher(body).find()) {
                continue;
            }
            unrouted.add(name);
            where.add(name + ":" + line(body, m.start()));
        }
        assertThat(unrouted)
                .as("a walk with its own private answer to which directories it skips: "
                        + "route it through core.util.HeavyDirs — HeavyDirs.plus(\"…\") "
                        + "keeps any extras this walk legitimately needs — or classify it "
                        + "in this ledger with a reason a person can disagree with. "
                        + "Sites seen: " + where)
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

    /**
     * The sharpest instance of ledger 110, pinned so it cannot come back:
     * the class whose javadoc called itself the one home was the ONLY one
     * of the twelve that did not skip build output. Eleven walks refused a
     * Rust or Maven {@code target/}; the Project Studio file tree, which
     * reads its names from here, expanded it.
     *
     * <p>Read from the SOURCE, not from the loaded constant, and that is
     * not fussiness: this module's test classpath resolves core from the
     * assembled cluster rather than the reactor, so a linked assertion
     * here reads whichever {@code HeavyDirs} was last packaged and can
     * pass or fail for reasons that have nothing to do with the change
     * under test — it failed exactly that way on this gate's first run.
     * {@code BoundedReadLedgerTest} reads {@code BoundedReads.java} as
     * text for the same reason. The behaviour of the class itself — that
     * {@code plus} only ever adds — is unit-tested where it belongs, in
     * {@code core}'s own {@code HeavyDirsTest}.
     */
    @Test
    @DisplayName("the one home skips build output, which is what it was missing")
    void theHomeSkipsBuildOutput() throws IOException {
        Path home = Path.of("..", "core", "src", "main", "java", "org", "nmox",
                "studio", "core", "util", "HeavyDirs.java");
        String body = GateSources.stripComments(read(home));
        int at = body.indexOf("NAMES");
        assertThat(at).as("HeavyDirs must declare NAMES").isGreaterThan(0);
        String declared = body.substring(at, body.indexOf(';', at));
        Set<String> names = new TreeSet<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(declared);
        while (m.find()) {
            names.add(m.group(1));
        }
        assertThat(names)
                .as("target and out were in eleven of the twelve sets and not in this one")
                .contains("target", "out")
                .contains("node_modules", ".git", "dist", "build", "coverage");
    }

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
