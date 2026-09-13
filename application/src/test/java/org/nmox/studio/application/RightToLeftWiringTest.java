package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The orientation seam stays ONE seam, and stays wired.
 *
 * <p>The product builds dialogs at 160 call sites. Orienting each one would
 * make a right-to-left build correct only until the next author forgets —
 * the shape of most defects this codebase has a law about. So orientation is
 * applied from a single place, to every window the toolkit opens and every
 * window the platform's registry announces, and this gate keeps it that way.
 *
 * <p>The registry half is here because the WALK found it missing: a window
 * listener alone left every panel opened after startup unmirrored, while the
 * platform's own toolbar mirrored correctly. A TopComponent opening inside an
 * already-open window fires no {@code WINDOW_OPENED}.
 */
class RightToLeftWiringTest {

    private static final Path SEAM = Path.of("..",
            "ui/src/main/java/org/nmox/studio/ui/rtl/RightToLeft.java");

    @Test
    @DisplayName("one seam orients windows, new windows, and windows opened later")
    void theSeamIsWired() throws IOException {
        String src = Files.readString(SEAM, StandardCharsets.UTF_8);
        assertThat(src).as("the startup hook").contains("@OnShowing");
        assertThat(src).as("windows the toolkit opens after startup")
                .contains("AWTEvent.WINDOW_EVENT_MASK");
        assertThat(src).as("windows the platform opens inside an open window — the walk's find")
                .contains("PROP_TC_OPENED");
        assertThat(src).as("orientation changes layout, so the tree is laid out again")
                .contains("revalidate()");
        assertThat(src).as("the live language switch can change direction too")
                .contains("UiLocale.addListener");
    }

    @Test
    @DisplayName("orientation is applied from that seam and nowhere else")
    void nobodyElseOrients() throws IOException {
        Path repo = Path.of("..");
        java.util.List<String> offenders = new java.util.ArrayList<>();
        for (String module : java.util.List.of("core", "editor", "tools", "rack",
                "project", "ui", "infra", "apiclient", "dbstudio", "web3")) {
            Path src = repo.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (var walk = Files.walk(src)) {
                for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                    if (p.getFileName().toString().startsWith("RightToLeft")
                            || p.getFileName().toString().startsWith("PaintedSurfaces")) {
                        continue;
                    }
                    // read CODE, not prose: this gate's own first run flagged
                    // TextDirection, which only NAMES the method in its javadoc
                    // while calling nothing. A gate that scans comments can be
                    // talked out of a verdict by a sentence.
                    if (code(Files.readString(p, StandardCharsets.UTF_8))
                            .contains("applyComponentOrientation")) {
                        offenders.add(repo.relativize(p).toString());
                    }
                }
            }
        }
        assertThat(offenders)
                .as("a second place that orients components is a second place to forget; "
                        + "the seam is org.nmox.studio.ui.rtl.RightToLeft")
                .isEmpty();
    }

    /** The file with its comments removed — block, line, and javadoc alike. */
    private static String code(String java) {
        String noBlocks = java.replaceAll("(?s)/\\*.*?\\*/", " ");
        StringBuilder out = new StringBuilder();
        for (String line : noBlocks.split("\n", -1)) {
            int slash = line.indexOf("//");
            out.append(slash >= 0 ? line.substring(0, slash) : line).append('\n');
        }
        return out.toString();
    }
}
