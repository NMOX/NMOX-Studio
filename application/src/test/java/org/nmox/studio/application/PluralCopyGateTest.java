package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plural law (v2.85.0): a user-visible count never reads "1 cards".
 * The sweep after the first-show walk's "1 pieces" found the shape in
 * Tasks, the IRC find bar, DB Studio's row count and Check My Work; all
 * ride core.util.Plural now. This gate names the nouns the sweep fixed
 * and fails on a bare {@code + " cards"} shape returning for any of them.
 */
class PluralCopyGateTest {

    // a NAME followed by the noun ("To Do cards", a list's accessible
    // name) is not a count — only a count's operand before the plural is
    private static final Pattern BARE = Pattern.compile(
            "(?<!name\\(\\) |label\\(\\) |title\\(\\) )\\+ \" (cards|rows|matches|checks|pieces)(\"| )");

    @Test
    @DisplayName("no user-visible count concatenates a bare plural for the nouns the sweep fixed")
    void noBarePlurals() throws IOException {
        List<String> offenders = new java.util.ArrayList<>();
        for (String module : List.of("core", "editor", "tools", "rack", "project", "ui", "apiclient", "dbstudio", "web3", "infra")) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(p);
                    if (BARE.matcher(text).find()) {
                        offenders.add(module + "/" + src.relativize(p));
                    }
                }
            }
        }
        assertThat(offenders).as("count strings without a singular branch").isEmpty();
    }
}
