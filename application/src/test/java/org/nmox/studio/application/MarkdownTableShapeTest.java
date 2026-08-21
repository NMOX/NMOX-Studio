package org.nmox.studio.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GitHub-table SHAPE law for the live docs (found live 2026-08-21: the
 * README's front page rendered a paragraph as table rows and a caption
 * row as literal pipes — David saw it before any gate did).
 *
 * <p>Two defect shapes, both invisible to content greps because every
 * word survives:
 *
 * <ul>
 * <li><b>Glued paragraph</b> — a non-table line directly after a table
 * line, with no blank between: GitHub absorbs each wrapped paragraph
 * line as a one-cell table row with phantom empty columns.</li>
 * <li><b>Orphan row</b> — a lone {@code | ... |} line with blank lines
 * on both sides: with no header+delimiter it renders as a literal
 * pipe-riddled paragraph.</li>
 * </ul>
 *
 * <p>The checker is pure so the defect shapes themselves are pinned as
 * REJECTED fixtures — the mutation proof is baked in.
 */
class MarkdownTableShapeTest {

    /** Pure checker: line numbers (1-based) of table-shape defects. */
    static List<String> defects(String text) {
        String[] lines = text.split("\n", -1);
        List<String> out = new ArrayList<>();
        boolean inFence = false;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].strip();
            if (l.startsWith("```")) {
                inFence = !inFence;
                continue;
            }
            if (inFence || !l.startsWith("|")) {
                continue;
            }
            String next = i + 1 < lines.length ? lines[i + 1].strip() : "";
            String prev = i > 0 ? lines[i - 1].strip() : "";
            if (!next.isEmpty() && !next.startsWith("|") && !next.startsWith("```")) {
                out.add((i + 1) + ": table line followed by non-table text — "
                        + "GitHub absorbs the paragraph as table rows; add a blank line");
            }
            if (prev.isEmpty() && !next.startsWith("|")) {
                out.add((i + 1) + ": lone table row — no header/delimiter, "
                        + "renders as literal pipes");
            }
        }
        return out;
    }

    @Test
    @DisplayName("the two live-caught defect shapes are rejected by the checker")
    void defectShapesAreRejected() {
        // the glued paragraph (the README's audience text, reduced)
        String glued = "| ![a](a.png) | ![b](b.png) |\n"
                + "**The go-to studio.** paragraph text\n"
                + "wrapped onto more lines.\n";
        assertThat(defects(glued))
                .as("a paragraph glued to a table line is a defect")
                .anySatisfy(d -> assertThat(d).contains("followed by non-table text"));

        // the orphan caption row (the README's stranded captions, reduced)
        String orphan = "paragraph above.\n\n"
                + "| *caption a* | *caption b* |\n\n"
                + "paragraph below.\n";
        assertThat(defects(orphan))
                .as("a lone pipe row with no header is a defect")
                .anySatisfy(d -> assertThat(d).contains("lone table row"));

        // the FIXED shape passes: images, captions, blank, paragraph
        String fixed = "| | |\n|---|---|\n| ![a](a.png) | ![b](b.png) |\n"
                + "| *caption a* | *caption b* |\n\n"
                + "**The go-to studio.** paragraph text.\n";
        assertThat(defects(fixed)).isEmpty();
    }

    @Test
    @DisplayName("the live docs carry no table-shape defects")
    void liveDocsAreClean() throws Exception {
        Path root = Path.of("..").toRealPath();
        List<Path> docs = new ArrayList<>();
        docs.add(root.resolve("README.md"));
        try (Stream<Path> s = Files.list(root.resolve("docs"))) {
            s.filter(p -> p.toString().endsWith(".md")).forEach(docs::add);
        }
        assertThat(docs.size())
                .as("the doc census found the live set").isGreaterThan(10);
        for (Path doc : docs) {
            assertThat(defects(Files.readString(doc)))
                    .as(root.relativize(doc) + " table shapes").isEmpty();
        }
    }
}
