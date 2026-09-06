package org.nmox.studio.editor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The quieter-boot law's fold/braces half (v2.85.0): every org-nmox row
 * under a mime's FoldManager and BracesMatchers folders carries a
 * position — the v2.85.0 Docker walk's log had two Ordering warnings
 * naming JsFoldManager and JsBracesMatcherFactory beside the platform's
 * positioned rows. Read from the GENERATED layer, so a new unpositioned
 * registration fails here, not on a user's boot.
 */
class EditorRowsPositionedTest {

    @Test
    @DisplayName("every org-nmox FoldManager and BracesMatchers row is positioned")
    void foldAndBracesRowsArePositioned() throws Exception {
        String xml;
        try (InputStream layer = EditorRowsPositionedTest.class.getResourceAsStream("/META-INF/generated-layer.xml")) {
            assertThat(layer).as("generated-layer.xml").isNotNull();
            xml = new String(layer.readAllBytes(), StandardCharsets.UTF_8);
        }
        // <file name="org-nmox-…FoldManager$Factory.instance"> … </file> — the
        // position attr sits inside the element; capture each org-nmox file
        // element under a FoldManager or BracesMatchers folder
        Matcher folder = Pattern.compile("<folder name=\"(FoldManager|BracesMatchers)\">(.*?)</folder>", Pattern.DOTALL).matcher(xml);
        int checked = 0;
        while (folder.find()) {
            Matcher file = Pattern.compile("<file name=\"(org-nmox-[^\"]+)\">(.*?)</file>", Pattern.DOTALL).matcher(folder.group(2));
            while (file.find()) {
                checked++;
                assertThat(file.group(2)).as(folder.group(1) + " row " + file.group(1) + " carries a position")
                        .contains("name=\"position\"");
            }
        }
        assertThat(checked).as("the JS/TS fold and braces rows were seen").isGreaterThanOrEqualTo(4);
    }
}
