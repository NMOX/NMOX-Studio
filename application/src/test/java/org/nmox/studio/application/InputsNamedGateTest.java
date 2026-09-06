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
 * Every input in the product speaks its name (v2.85.0): the walk read
 * the New Project wizard through accessibility and its name field was
 * "text field" — the census found 46 fields, combos, spinners and one
 * password field with neither {@code setAccessibleName} nor a label's
 * {@code setLabelFor} (Swing derives the name from either). The
 * TextAreasNamedGateTest sibling for the rest of the input family; a
 * JCheckBox constructed with its own text names itself and is out.
 */
class InputsNamedGateTest {

    private static final Pattern INPUT = Pattern.compile(
            "(JTextField|JPasswordField|JComboBox<[^>]*>|JComboBox|JSpinner|JCheckBox)\\s+(\\w+)\\s*=\\s*"
            + "new\\s+(?:JTextField|JPasswordField|JComboBox|JSpinner|JCheckBox)\\b(?:<[^>]*>)?\\s*\\(([^;]*)");

    @Test
    @DisplayName("every text field, password field, combo, spinner and text-less checkbox is named or labelled")
    void everyInputIsNamed() throws IOException {
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
                    Matcher m = INPUT.matcher(body);
                    while (m.find()) {
                        String kind = m.group(1);
                        String var = m.group(2);
                        if (kind.equals("JCheckBox") && !m.group(3).strip().startsWith(")")) {
                            continue; // constructed with its own text
                        }
                        boolean named = body.contains(var + ".getAccessibleContext().setAccessibleName(")
                                || Pattern.compile("setLabelFor\\(\\s*" + Pattern.quote(var) + "\\s*\\)").matcher(body).find();
                        if (!named) {
                            int line = 1 + (int) body.chars().limit(m.start()).filter(c -> c == '\n').count();
                            offenders.add(module + "/" + p.getFileName() + ":" + line + " (" + var + ")");
                        }
                    }
                }
            }
        }
        assertThat(offenders)
                .as("an input with neither an accessible name nor a labelFor label — a screen reader hears only the role")
                .isEmpty();
    }
}
