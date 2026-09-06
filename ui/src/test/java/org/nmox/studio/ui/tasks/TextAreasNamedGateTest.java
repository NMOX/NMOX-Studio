package org.nmox.studio.ui.tasks;

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
 * Every text area in ui carries an accessible name (v2.85.0, the Task
 * Board walk read the Standup through accessibility and heard "text
 * entry area" — the role, not the thing). The v2.37.2 sweep named the
 * dialogs' label-less inputs; the two report areas (Standup, Sprint
 * report) had none. A text area is an input or a document; either way
 * a screen reader needs to know which one.
 */
class TextAreasNamedGateTest {

    private static final Pattern AREA = Pattern.compile("JTextArea\\s+(\\w+)\\s*=\\s*new\\s+JTextArea\\s*\\(");

    @Test
    @DisplayName("every JTextArea constructed in ui gets an accessible name")
    void everyTextAreaIsNamed() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String body = Files.readString(p);
                Matcher m = AREA.matcher(body);
                while (m.find()) {
                    String var = m.group(1);
                    // the name must follow the construction before the next text area is built
                    int next = body.indexOf("new JTextArea(", m.end());
                    String window = body.substring(m.end(), next < 0 ? body.length() : next);
                    if (!window.contains(var + ".getAccessibleContext().setAccessibleName(")) {
                        int line = 1 + (int) body.chars().limit(m.start()).filter(c -> c == '\n').count();
                        offenders.add(p.getFileName() + ":" + line + " (" + var + ")");
                    }
                }
            }
        }
        assertThat(offenders)
                .as("a JTextArea without an accessible name — a screen reader hears only the role")
                .isEmpty();
    }
}
