package org.nmox.studio.ui.actions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A text-bearing JLabel's accessible NAME is its text (v2.85.0, the
 * learning-space walk read the shelf through accessibility). A JLabel
 * already names itself by its text; a constant set on top of that is
 * what a screen reader hears INSTEAD of the content — the Learning
 * Spaces manager's header said "Learning spaces shelf summary" to
 * VoiceOver forever while sighted users read the count, the disk cost
 * and the lifecycle sentence it painted. The role explanation belongs
 * in the accessible DESCRIPTION; the name follows the text. Inputs
 * (fields, boxes, buttons without text) still need names — this gate
 * reads only labels constructed with text.
 */
class LabelNamesAreTheirTextGateTest {

    private static final Pattern TEXT_LABEL = Pattern.compile(
            "JLabel\\s+(\\w+)\\s*=\\s*new\\s+JLabel\\s*\\(\\s*\"");

    @Test
    @DisplayName("no text-bearing JLabel in ui gets a constant accessible name")
    void textLabelsKeepTheirTextAsTheirName() throws IOException {
        List<String> offenders = new ArrayList<>();
        Path src = Path.of("src", "main", "java");
        try (Stream<Path> files = Files.walk(src)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String body = Files.readString(p);
                Matcher m = TEXT_LABEL.matcher(body);
                while (m.find()) {
                    String var = m.group(1);
                    Pattern named = Pattern.compile(
                            "\\b" + Pattern.quote(var) + "\\.getAccessibleContext\\(\\)\\.setAccessibleName\\(\\s*\"");
                    Matcher n = named.matcher(body);
                    if (n.find()) {
                        int line = 1 + (int) body.chars().limit(n.start()).filter(c -> c == '\n').count();
                        offenders.add(p.getFileName() + ":" + line + " (" + var + ")");
                    }
                }
            }
        }
        assertThat(offenders)
                .as("a text-bearing JLabel given a constant accessible name — a screen reader "
                        + "hears the constant, never the text; use setAccessibleDescription for "
                        + "the role and let the name follow the text")
                .isEmpty();
    }
}
