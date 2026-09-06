package org.nmox.studio.application;

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
 * Every text area in the product carries an accessible name (v2.85.0,
 * the Task Board walk read the Standup through accessibility and heard
 * "text entry area" — the role, not the thing). The v2.37.2 sweep named
 * the dialogs' label-less inputs; the report areas (Standup, Sprint),
 * the DevTools details panes, the Image Kit report and the Docker
 * previews had none. A text area is an input or a document; either way
 * assistive technology needs to know which one. The rule reads the
 * whole file for the variable's name (a field constructed at the top
 * and named in a block below is named); a factory names what it builds.
 */
class TextAreasNamedGateTest {

    private static final Pattern AREA = Pattern.compile("JTextArea\\s+(\\w+)\\s*=\\s*new\\s+JTextArea\\s*\\(");

    @Test
    @DisplayName("every JTextArea constructed in the product gets an accessible name somewhere in its file")
    void everyTextAreaIsNamed() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String module : new String[]{"core", "editor", "tools", "rack", "project",
            "ui", "apiclient", "dbstudio", "web3", "infra"}) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String body = Files.readString(p);
                    Matcher m = AREA.matcher(body);
                    while (m.find()) {
                        String var = m.group(1);
                        if (!body.contains(var + ".getAccessibleContext().setAccessibleName(")) {
                            int line = 1 + (int) body.chars().limit(m.start()).filter(c -> c == '\n').count();
                            offenders.add(module + "/" + p.getFileName() + ":" + line + " (" + var + ")");
                        }
                    }
                }
            }
        }
        assertThat(offenders)
                .as("a JTextArea without an accessible name — a screen reader hears only the role")
                .isEmpty();
    }
}
