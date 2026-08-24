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
 * The Turkish-I law (v2.37.5): a case-fold that feeds MATCHING —
 * completion prefixes, tag and attribute comparison, enum and mime
 * lookups, file-name checks — must name its locale, because under
 * {@code tr-TR} the default fold maps {@code I} to a dotless ı and
 * the match silently fails for exactly the user the fold was meant to
 * serve. {@code String.toLowerCase()} / {@code toUpperCase()} with no
 * argument are therefore banned in the product's main sources; spell
 * {@code toLowerCase(Locale.ROOT)} (or a deliberate display locale
 * with the reason in an inline {@code // locale:} comment on the same
 * line, which this gate honors).
 *
 * <p>Failing-first proof: the gate's first run named all 55
 * pre-sweep sites by file and line.
 */
class CaseFoldLocaleGateTest {

    private static final Pattern BARE = Pattern.compile("\\.to(?:Lower|Upper)Case\\(\\)");

    @Test
    @DisplayName("every case-fold in main sources names its locale")
    void noBareCaseFolds() throws IOException {
        Path root = Path.of("..").toRealPath();
        List<String> offenders = new ArrayList<>();
        for (String module : List.of("core", "editor", "tools", "rack", "apiclient",
                "dbstudio", "web3", "infra", "project", "ui")) {
            Path src = root.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(src)) {
                for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                    List<String> lines = Files.readAllLines(p);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        Matcher m = BARE.matcher(line);
                        if (m.find() && !line.contains("// locale:")) {
                            offenders.add(root.relativize(p) + ":" + (i + 1)
                                    + "  " + line.strip());
                        }
                    }
                }
            }
        }
        assertThat(offenders)
                .as("bare toLowerCase()/toUpperCase() folds with the user's locale — "
                    + "name Locale.ROOT for matching, or justify a display locale "
                    + "with an inline // locale: comment")
                .isEmpty();
    }
}
