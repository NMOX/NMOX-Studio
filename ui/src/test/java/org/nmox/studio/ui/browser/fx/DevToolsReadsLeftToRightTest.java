package org.nmox.studio.ui.browser.fx;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * What DevTools shows is the page's own code, and code runs left to right
 * in every language (3.5.3).
 *
 * <p>The right-to-left walk photographed the Browser's address as
 * {@code /http://127.0.0.1:3000}, its last slash leading the line, and the
 * computed-style pane as {@code :Computed style} over right-aligned
 * declarations. A window's orientation reaches every text component in it;
 * the ones holding an address, a selector, a declaration or a log line have
 * to say they are not prose. DevTools holds nothing else, so every text
 * field, text area, tree, table and list it creates is marked, and the
 * population is read from its source: a new pane's field fails here.
 */
class DevToolsReadsLeftToRightTest {

    private static final Pattern CREATED = Pattern.compile(
            "new (JTextField|JTextArea|JEditorPane|JTextPane|JTree|JTable|JList)\\b");

    private static List<String> unmarked(Path source) throws IOException {
        List<String> out = new ArrayList<>();
        String[] lines = Files.readString(source, StandardCharsets.UTF_8).split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String code = line.strip().startsWith("//") || line.strip().startsWith("*") ? "" : line;
            Matcher m = CREATED.matcher(code);
            while (m.find()) {
                if (!code.substring(0, m.start()).contains("keepLeftToRight(")) {
                    out.add(source.getFileName() + ":" + (i + 1) + " " + m.group());
                }
            }
        }
        return out;
    }

    @Test
    @DisplayName("every text component DevTools creates reads left to right")
    void devToolsIsCode() throws IOException {
        Path panel = Path.of("src/main/java/org/nmox/studio/ui/browser/fx/DevToolsPanel.java");
        assertThat(unmarked(panel))
                .as("code, values and addresses, never prose: wrap it in TextDirection.keepLeftToRight")
                .isEmpty();
        assertThat(Files.readString(panel, StandardCharsets.UTF_8).split("keepLeftToRight\\(", -1).length - 1)
                .as("and there are such components to mark, or this reads the wrong file").isGreaterThanOrEqualTo(8);
    }

    @Test
    @DisplayName("the Browser's address reads left to right")
    void theAddressIsAnAddress() throws IOException {
        Path browser = Path.of("src/main/java/org/nmox/studio/ui/browser/fx/FxBrowserPanel.java");
        assertThat(unmarked(browser)).isEmpty();
        assertThat(Files.readString(browser, StandardCharsets.UTF_8))
                .contains("urlField = org.nmox.studio.core.util.TextDirection.keepLeftToRight(");
    }
}
