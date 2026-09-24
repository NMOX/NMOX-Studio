package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gates index is complete, and names nothing that is not there (v3.1.0).
 *
 * <p>The build carried more than a hundred gate and ledger tests with no
 * index, so a contributor whose build failed on {@code PlainLabelGateTest}
 * had to read the test to learn what law it holds. {@code docs/engineering/gates.md}
 * is that index. An index kept by hand goes stale the day a gate is added
 * (the {@code DocsIndexGateTest} lesson, one directory over), so the
 * population here is DERIVED: every test class in the tree whose simple name
 * ends in {@code GateTest}, {@code LedgerTest}, {@code ParityTest} or
 * {@code CensusTest}. A new one fails this test by name until its line is
 * written.
 *
 * <p>The other direction matters as much: every test the page links must
 * exist at the path it links, or the page sends a reader to a class that was
 * renamed or deleted.
 */
class GatesIndexGateTest {

    private static final Path REPO = Path.of("..").toAbsolutePath().normalize();
    private static final Path INDEX = REPO.resolve(Path.of("docs", "engineering", "gates.md"));

    private static final Pattern GATE_NAME = Pattern.compile("(GateTest|LedgerTest|ParityTest|CensusTest)\\.java$");

    /** {@code [`Name`](relative/path/Name.java)} — how the index names a test. */
    private static final Pattern LINK = Pattern.compile("\\[`([A-Za-z0-9_]+)`\\]\\(([^)\\s]+)\\)");

    @Test
    @DisplayName("every gate, ledger, parity and census test in the tree has its line in gates.md")
    void everyGateIsIndexed() throws IOException {
        Map<String, Path> gates = derivedGates();
        assertThat(gates).as("the derived gate population (a walk that finds nothing proves nothing)")
                .hasSizeGreaterThan(100)
                .containsKey("SpawnSiteTrustLedgerTest")
                .containsKey(getClass().getSimpleName());

        Map<String, String> linked = linkedTests();
        List<String> missing = new ArrayList<>();
        gates.forEach((name, file) -> {
            if (!linked.containsKey(name)) {
                missing.add(name + " (" + REPO.relativize(file).toString().replace('\\', '/') + ")");
            }
        });
        assertThat(missing).as("gates with no line in docs/engineering/gates.md — write one: "
                + "the law it holds and where it came from").isEmpty();
    }

    @Test
    @DisplayName("every test gates.md links exists at the path it links, and is listed once")
    void noGhosts() throws IOException {
        String text = Files.readString(INDEX, StandardCharsets.UTF_8).replace("\r\n", "\n");
        Matcher m = LINK.matcher(text);
        Map<String, Integer> seen = new TreeMap<>();
        List<String> ghosts = new ArrayList<>();
        while (m.find()) {
            String name = m.group(1);
            String target = m.group(2);
            if (!target.endsWith(".java")) {
                continue;
            }
            seen.merge(name, 1, Integer::sum);
            Path file = INDEX.getParent().resolve(target).normalize();
            if (!Files.isRegularFile(file) || !file.getFileName().toString().equals(name + ".java")) {
                ghosts.add(name + " -> " + target);
            }
        }
        assertThat(seen).as("the links the index carries").hasSizeGreaterThan(100);
        assertThat(ghosts).as("gates.md links tests that are not where it says").isEmpty();
        List<String> twice = new ArrayList<>();
        seen.forEach((name, n) -> {
            if (n > 1) {
                twice.add(name + " x" + n);
            }
        });
        assertThat(twice).as("a gate listed twice has two lines that will drift apart").isEmpty();
    }

    @Test
    @DisplayName("the engineering index and CONTRIBUTING both lead to gates.md")
    void theIndexIsReachable() throws IOException {
        String engineering = Files.readString(REPO.resolve(Path.of("docs", "engineering", "README.md")),
                StandardCharsets.UTF_8);
        String contributing = Files.readString(REPO.resolve("CONTRIBUTING.md"), StandardCharsets.UTF_8);
        assertThat(engineering).contains("(./gates.md)");
        assertThat(contributing).contains("(docs/engineering/gates.md)");
    }

    /** Test name to file, for every gate-shaped test class under a module's src/test/java. */
    private static Map<String, Path> derivedGates() throws IOException {
        Map<String, Path> found = new TreeMap<>();
        Files.walkFileTree(REPO, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(REPO)) {
                    return FileVisitResult.CONTINUE;
                }
                String name = dir.getFileName().toString();
                // judged per segment INSIDE the repo, so a worktree that itself
                // lives under a dot directory (.claude/worktrees/…) still sees its sources
                return name.equals("target") || name.equals("node_modules") || name.startsWith(".")
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
                if (GATE_NAME.matcher(p.getFileName().toString()).find() && isTestSource(p)) {
                    String name = p.getFileName().toString().replaceFirst("\\.java$", "");
                    Path previous = found.put(name, p);
                    assertThat(previous).as("two gates share the simple name " + name
                            + " — the index could not tell them apart").isNull();
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return found;
    }

    /** A file under some module's {@code src/test/java}, judged on the path relative to the repository. */
    private static boolean isTestSource(Path p) {
        return REPO.relativize(p).toString().replace('\\', '/').contains("/src/test/java/");
    }

    private static Map<String, String> linkedTests() throws IOException {
        String text = Files.readString(INDEX, StandardCharsets.UTF_8);
        Map<String, String> linked = new LinkedHashMap<>();
        Matcher m = LINK.matcher(text);
        while (m.find()) {
            linked.put(m.group(1), m.group(2));
        }
        return linked;
    }
}
