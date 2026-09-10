package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every released version in the changelog is linkable (v2.104.0).
 *
 * <p>Keep a Changelog's version headings are reference links, so a version
 * with no definition at the bottom renders as literal brackets and its
 * "what changed" diff is unreachable. The block dead-ends silently — it is
 * the tail of a file nobody scrolls to — and it has now done so twice: once
 * at v1.2.1, found in v1.176.0 and regenerated for 217 versions, and again
 * at v2.95.0, found here with fourteen versions unlinked.
 *
 * <p>Twice is a class, so it becomes a build law rather than a habit.
 */
class ChangelogLinkBlockTest {

    private static final Pattern HEADING = Pattern.compile("(?m)^## \\[(\\d+\\.\\d+\\.\\d+)\\]");
    private static final Pattern DEFINITION = Pattern.compile("(?m)^\\[(\\d+\\.\\d+\\.\\d+)\\]:");

    @Test
    @DisplayName("every version heading has a link definition, and every definition has a heading")
    void headingsAndLinksAgree() throws IOException {
        String body = Files.readString(Path.of("..", "CHANGELOG.md"), StandardCharsets.UTF_8);

        Set<String> headings = new LinkedHashSet<>();
        Matcher h = HEADING.matcher(body);
        while (h.find()) {
            headings.add(h.group(1));
        }
        Set<String> defined = new LinkedHashSet<>();
        Matcher d = DEFINITION.matcher(body);
        while (d.find()) {
            defined.add(d.group(1));
        }

        assertThat(headings).as("the changelog's own version headings").hasSizeGreaterThan(500);

        List<String> unlinked = new ArrayList<>(headings);
        unlinked.removeAll(defined);
        assertThat(unlinked)
                .as("versions whose heading renders as literal brackets and whose diff is unreachable")
                .isEmpty();

        List<String> ghosts = new ArrayList<>(defined);
        ghosts.removeAll(headings);
        assertThat(ghosts).as("a link definition for a version that has no entry").isEmpty();
    }

    @Test
    @DisplayName("the newest version's link compares against the one below it, so the chain is unbroken")
    void theChainIsUnbroken() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("..", "CHANGELOG.md"), StandardCharsets.UTF_8);
        List<String> order = new ArrayList<>();
        for (String line : lines) {
            Matcher m = HEADING.matcher(line);
            if (m.find()) {
                order.add(m.group(1));
            }
        }
        assertThat(order).hasSizeGreaterThan(2);
        String newest = order.get(0);
        String below = order.get(1);
        assertThat(String.join("\n", lines))
                .as("the head entry must diff against the release before it")
                .contains("[" + newest + "]: https://github.com/NMOX/NMOX-Studio/compare/v"
                        + below + "...v" + newest);
    }
}
