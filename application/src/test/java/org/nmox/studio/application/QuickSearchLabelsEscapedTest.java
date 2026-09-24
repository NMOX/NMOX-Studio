package org.nmox.studio.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quick Search renders every result through the platform's
 * {@code HtmlRenderer} with {@code setHtml(true)}
 * ({@code SearchResultRender}, read from the RELEASE310 bytecode), so a
 * result label is markup. Block Studio's label was
 * {@code Block Studio — <my-card> (3 pieces)} and every row read
 * "Block Studio — (3 pieces)": the tag name was eaten as a tag. The
 * other labels carry a project's own words (request names, table names,
 * card titles, contract names, folder names, a drop-in device's title),
 * and a project is somebody else's text. The markup-render law, one sink
 * over: every label is escaped where it is handed to the platform.
 *
 * <p>The population is derived: every main-source class that implements
 * {@code SearchProvider}. Each {@code response.addResult(...)} call must
 * escape inside its own parentheses; a provider that escapes earlier,
 * piece by piece, is blessed below with the reason.
 */
class QuickSearchLabelsEscapedTest {

    private static final Map<String, String> BLESSED = Map.of(
            "NpmScriptSearchProvider.java",
            "label() escapes the script's name and command separately before they fill the bundle template");

    @Test
    @DisplayName("every Quick Search result label is escaped where it reaches the HTML renderer")
    void everyLabelIsEscaped() throws Exception {
        Path root = Path.of("..").toRealPath();
        List<Path> providers = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("/src/main/java/"))
                    .filter(p -> !root.relativize(p).toString().startsWith("."))
                    .forEach(p -> {
                        try {
                            if (Files.readString(p).contains("implements SearchProvider")) {
                                providers.add(p);
                            }
                        } catch (java.io.IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });
        }
        assertThat(providers).as("the census found the providers").hasSizeGreaterThanOrEqualTo(12);
        List<String> bare = new ArrayList<>();
        for (Path p : providers) {
            String name = p.getFileName().toString();
            if (BLESSED.containsKey(name)) {
                continue;
            }
            String src = Files.readString(p);
            int at = 0;
            int calls = 0;
            while ((at = src.indexOf("response.addResult(", at)) >= 0) {
                int open = src.indexOf('(', at);
                int depth = 0;
                int end = open;
                for (int i = open; i < src.length(); i++) {
                    char c = src.charAt(i);
                    if (c == '(') {
                        depth++;
                    } else if (c == ')' && --depth == 0) {
                        end = i;
                        break;
                    }
                }
                calls++;
                String call = src.substring(at, end + 1);
                if (!call.contains("PlainText.escape(")) {
                    bare.add(root.relativize(p) + " line " + (src.substring(0, at).split("\n", -1).length));
                }
                at = end;
            }
            assertThat(calls).as("%s hands its results to the platform", name).isPositive();
        }
        assertThat(bare).as("Quick Search labels handed to the HTML renderer unescaped").isEmpty();
    }
}
