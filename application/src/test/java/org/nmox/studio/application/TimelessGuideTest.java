package org.nmox.studio.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The manual describes the product, not its history (3.1.0). The English
 * user guide had grown forty release tags - "since v2.74.0", "(v2.87.0)"
 * in headings, a Browser paragraph that was eleven changelog entries
 * strung together - so a newcomer read the story of how a feature arrived
 * before learning what it does. The history lives in CHANGELOG.md.
 *
 * <p>The rule is the {@code vX.Y.Z} tag form: that is how a release is
 * NAMED in history. A version a reader must act on ("an install of 3.0.0,
 * 3.0.1 or 3.0.2", "older than 2.35.0") is written without the {@code v}
 * and stays.
 */
class TimelessGuideTest {

    private static final Pattern TAG = Pattern.compile("\\bv\\d+\\.\\d+\\.\\d+\\b");

    private static final List<String> DOCS = List.of(
            "docs/user-guide.md", "docs/quickstart.md", "docs/glossary.md", "docs/coming-from-vscode.md");

    @Test
    @DisplayName("the newcomer's documents name no release tag")
    void noReleaseTags() throws Exception {
        Path root = Path.of("..");
        List<String> tags = new ArrayList<>();
        for (String doc : DOCS) {
            List<String> lines = Files.readAllLines(root.resolve(doc));
            for (int i = 0; i < lines.size(); i++) {
                Matcher m = TAG.matcher(lines.get(i));
                while (m.find()) {
                    tags.add(doc + ":" + (i + 1) + " " + m.group());
                }
            }
        }
        assertThat(tags).as("release tags in the manual; history belongs in CHANGELOG.md").isEmpty();
    }
}
