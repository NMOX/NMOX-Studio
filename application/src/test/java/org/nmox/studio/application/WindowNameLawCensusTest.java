package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The name law made TOTAL over the product's windows (v2.77.0). The rack
 * has answered it since v1.41.0 (DeviceContractTest); the windows joined
 * one batch at a time (Workbench v2.73.0, the rack windows and the NPM
 * Explorer v2.75.0, the Welcome/IRC/Tests window v2.76.0, the studios
 * v2.77.0). This gate derives the population from the SOURCE — every
 * class carrying {@code @TopComponent.Registration} — and demands that
 * each is constructed by some {@code *A11yContractTest}, or carries a
 * written exemption here. A window added tomorrow fails the build until
 * it is under contract: gates gate outcomes, and the outcome is that no
 * registered window escapes the law.
 */
class WindowNameLawCensusTest {

    /** Windows the headless contract cannot construct, with the reason. */
    private static final Map<String, String> EXEMPT = Map.of(
            "WebBrowserTopComponent",
            "builds its JavaFX WebView on first show (v1.211.0); its toolbar is "
            + "FX-bound and cannot be constructed under plain JUnit — walked by hand");

    private static final Pattern REGISTRATION = Pattern.compile("@TopComponent\\.Registration");

    private static Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("pom.xml").resolveSibling("CLAUDE.md"))) {
            p = p.getParent();
        }
        return p;
    }

    private static List<Path> javaFiles(Path root, String subtree) throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path m : modules.filter(Files::isDirectory).toList()) {
                Path src = m.resolve(subtree);
                if (!Files.isDirectory(src)) {
                    continue;
                }
                try (Stream<Path> walk = Files.walk(src)) {
                    walk.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
                }
            }
        }
        return out;
    }

    @Test
    @DisplayName("every registered window is under a name-law contract or exempt in writing")
    void everyWindowUnderContract() throws IOException {
        Path root = repoRoot();
        assertThat(root).as("repo root").isNotNull();
        List<String> windows = new ArrayList<>();
        for (Path p : javaFiles(root, "src/main/java")) {
            String text = Files.readString(p);
            Matcher m = REGISTRATION.matcher(text);
            if (m.find()) {
                windows.add(p.getFileName().toString().replace(".java", ""));
            }
        }
        assertThat(windows).as("the census finds the product's windows").hasSizeGreaterThan(10);
        StringBuilder contracts = new StringBuilder();
        for (Path p : javaFiles(root, "src/test/java")) {
            if (p.getFileName().toString().endsWith("A11yContractTest.java")) {
                contracts.append(Files.readString(p)).append('\n');
            }
        }
        List<String> uncovered = new ArrayList<>();
        for (String w : windows) {
            if (EXEMPT.containsKey(w)) {
                continue;
            }
            if (!contracts.toString().contains("new " + w + "()")
                    && !Pattern.compile("new [\\w.]*\\." + w + "\\(\\)").matcher(contracts).find()) {
                uncovered.add(w);
            }
        }
        assertThat(uncovered)
                .as("a registered window no *A11yContractTest constructs — add it to a contract "
                        + "or exempt it here with its reason")
                .isEmpty();
        for (String e : EXEMPT.keySet()) {
            assertThat(windows).as("an exemption for a window that no longer exists is stale").contains(e);
        }
    }
}
