package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every list, tree and table ROW is heard as the words it paints (3.4,
 * question 3). {@link CollectionsNamedGateTest} names the collection; a
 * screen reader then reads each row through the cell renderer's own
 * accessible context. A {@code JLabel} renderer (and the platform's
 * {@code Default*CellRenderer}s, which are labels) is named by its text for
 * free; any other component is not. The 3.4 walk read the Task Board's
 * cards through VoiceOver: every card was an empty text field, because its
 * renderer is a wrapping {@code JTextArea}, and the Infra palette's painted
 * entries were blank the same way. The name laws before this checked
 * controls and collections, never the rows inside them.
 *
 * <p>The population is every class in the product that implements a
 * {@code ListCellRenderer}, {@code TreeCellRenderer} or
 * {@code TableCellRenderer}; one that extends a label is named by
 * construction, and every other must call {@code setAccessibleName} in its
 * body.
 */
class RenderersNamedGateTest {

    private static final Pattern DECL = Pattern.compile(
            "class\\s+(\\w+)\\s+extends\\s+([\\w.<>]+)\\s*(implements\\s+[^{]*)?\\{");
    private static final Pattern RENDERER = Pattern.compile("(List|Tree|Table)CellRenderer");
    private static final Set<String> LABELS = Set.of("JLabel", "DefaultListCellRenderer",
            "DefaultTableCellRenderer", "DefaultTreeCellRenderer");

    /** Measured at three when the gate was written (the Task Board, the Infra palette, the device shelf). */
    private static final int POPULATION_FLOOR = 3;

    record Renderer(String file, String name, String base, boolean named) {
    }

    static List<Renderer> renderers() throws IOException {
        List<Renderer> out = new ArrayList<>();
        for (String module : NamedControlCensus.MODULES) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                    String body = GateSources.stripComments(Files.readString(p));
                    Matcher m = DECL.matcher(body);
                    while (m.find()) {
                        if (m.group(3) == null || !RENDERER.matcher(m.group(3)).find()) {
                            continue;
                        }
                        String base = m.group(2).replaceAll("<.*", "");
                        base = base.substring(base.lastIndexOf('.') + 1);
                        String classBody = body.substring(m.end(), closing(body, m.end() - 1));
                        out.add(new Renderer(src.relativize(p).toString(), m.group(1), base,
                                classBody.contains("setAccessibleName(")));
                    }
                }
            }
        }
        return out;
    }

    /** The index of the brace closing the one at {@code open}. */
    private static int closing(String body, int open) {
        int depth = 0;
        for (int i = open; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return body.length();
    }

    @Test
    @DisplayName("a cell renderer that is not a label names itself with what it paints")
    void everyRendererIsHeard() throws IOException {
        List<Renderer> all = renderers();
        List<Renderer> unlabelled = all.stream().filter(r -> !LABELS.contains(r.base())).toList();
        assertThat(unlabelled)
                .as("the derived population of renderers built on something other than a label — "
                        + "a derivation that finds nothing would pass saying nothing")
                .hasSizeGreaterThanOrEqualTo(POPULATION_FLOOR);
        assertThat(unlabelled.stream().filter(r -> !r.named())
                .map(r -> r.file() + " " + r.name() + " extends " + r.base()).toList())
                .as("a renderer a screen reader reads as blank: call "
                        + "getAccessibleContext().setAccessibleName(<the painted words>) per cell")
                .isEmpty();
    }
}
