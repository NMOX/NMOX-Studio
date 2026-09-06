package org.nmox.studio.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The long documents carry a contents block that is DERIVED from their
 * own chapter headings (v2.89.0): every {@code ## } heading outside a
 * code fence, in order, as a GitHub-anchored link. The block lives
 * between {@code <!-- contents -->} and {@code <!-- /contents -->}; this
 * gate regenerates it from the headings and fails on the first
 * divergence, printing the whole expected block so the fix is a paste.
 * The slug rule is GitHub's: lower-case, everything but letters, digits,
 * spaces and hyphens dropped, spaces to hyphens — so "3. The Workbench
 * (⌥⌘0)" anchors as {@code #3-the-workbench-0}.
 */
class DocsContentsGateTest {

    static final String OPEN = "<!-- contents -->";
    static final String CLOSE = "<!-- /contents -->";

    @ParameterizedTest
    @ValueSource(strings = {"user-guide.md", "kitchen-sink.md"})
    @DisplayName("the contents block equals the block derived from the chapter headings")
    void contentsMatchHeadings(String doc) throws Exception {
        String text = Files.readString(Path.of("..", "docs", doc)).replace("\r\n", "\n");
        int open = text.indexOf(OPEN);
        int close = text.indexOf(CLOSE);
        String expected = block(headings(text));
        assertThat(open).as(doc + " carries a contents block; expected:\n" + expected).isPositive();
        assertThat(close).isGreaterThan(open);
        String actual = text.substring(open + OPEN.length(), close).strip();
        assertThat(actual).as(doc + " contents block is stale; expected:\n" + expected).isEqualTo(expected.strip());
    }

    @org.junit.jupiter.api.Test
    @DisplayName("the slug rule is GitHub's")
    void slugs() {
        assertThat(slug("3. The Workbench (⌥⌘0)")).isEqualTo("3-the-workbench-0");
        assertThat(slug("8. The Browser (⌥⌘4), source-aware")).isEqualTo("8-the-browser-4-source-aware");
        assertThat(slug("The refusals are features")).isEqualTo("the-refusals-are-features");
        assertThat(slug("Break it, check it, export it — the learning loop")).isEqualTo("break-it-check-it-export-it--the-learning-loop");
    }

    /** Level-2 headings outside code fences, in order. */
    static List<String> headings(String text) {
        List<String> out = new ArrayList<>();
        boolean inFence = false;
        for (String line : text.split("\n")) {
            if (line.startsWith("```")) {
                inFence = !inFence;
                continue;
            }
            if (!inFence && line.startsWith("## ")) {
                out.add(line.substring(3).strip());
            }
        }
        return out;
    }

    static String block(List<String> headings) {
        StringBuilder sb = new StringBuilder("**Contents**\n\n");
        for (String h : headings) {
            sb.append("- [").append(h).append("](#").append(slug(h)).append(")\n");
        }
        return sb.toString();
    }

    static String slug(String heading) {
        StringBuilder sb = new StringBuilder();
        heading.toLowerCase(Locale.ROOT).codePoints().forEach(cp -> {
            if (Character.isLetterOrDigit(cp)) {
                sb.appendCodePoint(cp);
            } else if (cp == ' ' || cp == '-') {
                sb.append(cp == ' ' ? '-' : '-');
            }
        });
        return sb.toString();
    }
}
