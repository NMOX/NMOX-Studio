package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The a11y-100 census as a build law (v2.38.0, David's ask): the four
 * studios' label-less Swing inputs all carry accessible names — forty
 * sites were bare when the sweep ran. The gate is deliberately coarse
 * (per-module counting, not per-site matching): a module may not grow
 * a new input creation without at least one new accessible name, so a
 * bare input fails the build unless someone ALSO names something —
 * at which point review sees both lines together. Array allocations
 * ({@code new JPasswordField[n]}) are not components and don't count.
 */
class A11yInputNamesGateTest {

    private static final List<String> MODULES =
            List.of("apiclient", "dbstudio", "web3", "infra");

    @Test
    @DisplayName("every studio's input census is fully named (creations <= names)")
    void inputsAreNamed() throws IOException {
        for (String module : MODULES) {
            Path src = Path.of("..", module, "src", "main", "java");
            int inputs = 0;
            int names = 0;
            try (Stream<Path> walk = Files.walk(src)) {
                for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String s = Files.readString(p);
                    inputs += count(s, "new JTextField(") + count(s, "new JComboBox<")
                            + count(s, "new JPasswordField(") + count(s, "new JList<")
                            + count(s, "new JTextArea(");
                    names += count(s, "setAccessibleName(");
                }
            }
            assertThat(names)
                    .as("%s: %d input creations but only %d accessible names — "
                            + "name the new input (the v2.38.0 census law)",
                            module, inputs, names)
                    .isGreaterThanOrEqualTo(inputs);
        }
    }

    private static int count(String s, String needle) {
        int n = 0;
        int at = 0;
        while ((at = s.indexOf(needle, at)) >= 0) {
            n++;
            at += needle.length();
        }
        return n;
    }
}
