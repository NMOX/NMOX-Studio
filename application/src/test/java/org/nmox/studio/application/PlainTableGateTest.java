package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plain-text table gate (v1.306.0). Swing's
 * {@code DefaultTableCellRenderer} RENDERS a cell value that starts
 * with {@code <html>} — markup executes at paint time, and an
 * {@code <img src="http://…">} makes the IDE's own JVM fetch an
 * attacker-chosen URL just for displaying a row (the v1.208.0 bug
 * class, found then in DevTools and fixed only there). Tables all over
 * the product show text from outside the IDE — API response headers,
 * DB result cells, IRC channel topics, docker container names, probed
 * tool output, port-scan process names, flight-recorder commands — and
 * every one was a standing instance.
 *
 * <p>The fix is {@code core.util.PlainTables} (the one place the
 * {@code "html.disable"} property is spelled). This gate makes it a
 * build law: every main source that constructs a {@code new JTable}
 * (or, since v2.70.0, a {@code new JTree} — the default tree renderer is
 * the same JLabel) must also reference {@code PlainTables}, so a NEW table starts safe
 * or names its reason. The scan runs from the application module — the
 * last reactor member — so it sees every sibling module's sources.
 *
 * <p>One shape is exempt, in writing: a pane that predates this helper
 * and carries its OWN html-disable idiom plus its own gate
 * (DevToolsPanel, v1.206.0's {@code DevToolsHtmlSafetyTest}). Such a
 * site carries a {@code PLAIN-TABLE-EXEMPT:} comment stating the claim
 * beside the {@code new JTable}; the marker is the blessing.
 */
class PlainTableGateTest {

    @Test
    @DisplayName("every production new JTable / new JTree file references PlainTables or carries the written exemption")
    void everyTableIsPlainText() throws IOException {
        Path root = Path.of("..").toRealPath();
        List<String> offenders = new ArrayList<>();
        int sites = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                // Windows walks yield backslash paths — normalize before
                // matching or the filter passes NOTHING and the subject
                // floor fails on exactly one OS (the v1.63.2 class).
                // Filter the path RELATIVE to the repo root: from a git
                // worktree (…/.claude/worktrees/<name>/) the ABSOLUTE
                // path carries "/.claude/", so an absolute-path dot
                // filter excluded every file and the subject floor
                // tripped (caught shipping v2.15.0 from a worktree).
                // The leading "/" keeps a top-level dot-dir matching.
                String s = "/" + root.relativize(p).toString().replace('\\', '/');
                // dot-dirs hold non-build copies (.claude worktrees, .git);
                // only reactor members' main sources are the gate's subjects
                if (!s.endsWith(".java") || !s.contains("src/main/java")
                        || s.contains("/target/") || s.contains("/.")) {
                    continue;
                }
                String src = Files.readString(p);
                // v2.70.0: a JTree's DefaultTreeCellRenderer is a JLabel and
                // renders <html> exactly as a table cell does — NPM Explorer
                // painted package.json script names through it since v0.1
                if (!src.contains("new JTable(") && !src.contains("new JTree(")) {
                    continue;
                }
                sites += src.split("new JTable\\(", -1).length - 1;
                sites += src.split("new JTree\\(", -1).length - 1;
                if (!src.contains("PlainTables")
                        && !src.contains("PLAIN-TABLE-EXEMPT:")) {
                    offenders.add(root.relativize(p).toString());
                }
            }
        }
        assertThat(offenders)
                .as("a JTable that shows external text without PlainTables will "
                        + "RENDER a <html><img> cell and fetch its URL at paint "
                        + "time (v1.208.0) — route the table through "
                        + "PlainTables.disableHtml / PlainTables.plain")
                .isEmpty();
        assertThat(sites)
                .as("the gate has subjects (the swept table + tree sites exist)")
                .isGreaterThanOrEqualTo(16);
    }
}
