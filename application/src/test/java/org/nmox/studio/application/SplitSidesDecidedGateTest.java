package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every split pane the product builds says what a right-to-left reader
 * gets (3.5.13).
 *
 * <p>A horizontal split pane changes sides for a Hebrew or Arabic reader
 * only when its author marked it
 * ({@code TextDirection.sidesFollowReader(new JSplitPane(…))}). 3.5.12
 * exchanged every horizontal pane in the JVM instead, and the platform's
 * own, which address a side by its slot, broke. So the mark is a decision
 * somebody has to take per pane, and a pane built tomorrow without one
 * would silently stay left-to-right in a mirrored window: this gate makes
 * the decision visible. Each {@code new JSplitPane(} in a main source is
 * one of:
 * <ul>
 * <li>marked, on the same statement;</li>
 * <li>vertical ({@code VERTICAL_SPLIT} on the statement): top and bottom
 *     never change places;</li>
 * <li>blessed with {@code // SPLIT-STAYS: reason} on its line or the line
 *     above, for a pane whose sides mean something that does not follow
 *     the reader.</li>
 * </ul>
 */
class SplitSidesDecidedGateTest {

    private static final List<String> MODULES = List.of("core", "editor", "tools", "rack", "project",
            "ui", "infra", "apiclient", "dbstudio", "web3");

    static List<String> undecided(Path repo) throws IOException {
        List<String> out = new ArrayList<>();
        for (String module : MODULES) {
            Path src = repo.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String raw = Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
                    String code = GateSources.stripComments(raw);
                    String[] rawLines = raw.split("\n", -1);
                    int from = 0;
                    while (true) {
                        int at = code.indexOf("JSplitPane(", from);
                        if (at < 0) {
                            break;
                        }
                        from = at + 1;
                        int newAt = code.lastIndexOf("new ", at);
                        if (newAt < 0 || !code.substring(newAt, at).matches("new\\s+(javax\\.swing\\.)?")) {
                            continue; // a method named …JSplitPane(, or a cast: not a construction
                        }
                        int statementStart = Math.max(code.lastIndexOf(';', newAt), code.lastIndexOf('{', newAt)) + 1;
                        int statementEnd = code.indexOf(';', at);
                        String statement = code.substring(statementStart, statementEnd < 0 ? code.length() : statementEnd);
                        int line = (int) code.substring(0, newAt).chars().filter(c -> c == '\n').count();
                        boolean blessed = rawLines[line].contains("// SPLIT-STAYS:")
                                || (line > 0 && rawLines[line - 1].contains("// SPLIT-STAYS:"));
                        if (statement.contains("sidesFollowReader(") || statement.contains("VERTICAL_SPLIT") || blessed) {
                            continue;
                        }
                        out.add(module + "/" + p.getFileName() + ":" + (line + 1));
                    }
                }
            }
        }
        return out;
    }

    @Test
    @DisplayName("every split pane the product builds is marked to follow its reader, is vertical, or says why it stays")
    void everySplitIsDecided() throws IOException {
        assertThat(undecided(Path.of("..")))
                .as("wrap the pane in TextDirection.sidesFollowReader(…) so its first side stands on the right for a "
                        + "right-to-left reader, or write `// SPLIT-STAYS: reason` on its line")
                .isEmpty();
    }

    @Test
    @DisplayName("the gate reads what it claims to: an unmarked horizontal pane is named, a marked, a vertical and a blessed one are not")
    void theGateSeesAnUndecidedPane(@TempDir Path repo) throws IOException {
        Path src = Files.createDirectories(repo.resolve("ui/src/main/java/x"));
        Files.writeString(src.resolve("Panes.java"), String.join("\n",
                "class Panes {",
                "    void build() {",
                "        JSplitPane a = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);",
                "        JSplitPane b = TextDirection.sidesFollowReader(new JSplitPane(",
                "                JSplitPane.HORIZONTAL_SPLIT, left, right));",
                "        JSplitPane c = new javax.swing.JSplitPane(",
                "                JSplitPane.VERTICAL_SPLIT, top, bottom);",
                "        // SPLIT-STAYS: before and after are sides of a comparison, not of a line",
                "        JSplitPane d = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, before, after);",
                "        JSplitPane e = new JSplitPane();",
                "        // a comment that says new JSplitPane( proves nothing",
                "    }",
                "}"));
        assertThat(undecided(repo)).containsExactly("ui/Panes.java:3", "ui/Panes.java:10");
    }
}
