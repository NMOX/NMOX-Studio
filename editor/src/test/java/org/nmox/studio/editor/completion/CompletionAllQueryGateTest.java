package org.nmox.studio.editor.completion;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.JEditorPane;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every NMOX completion provider must answer {@code COMPLETION_ALL_QUERY_TYPE}
 * (9) exactly like {@code COMPLETION_QUERY_TYPE} (1).
 *
 * <p>The v2.58.1 walk find, decompiled from the platform's CompletionImpl: a
 * second Ctrl+Space while a completion popup is showing re-queries every
 * provider as COMPLETION_ALL ("show all"), and an empty type-1 result is
 * auto-upgraded the same way. All eleven providers gated {@code createTask}
 * on {@code queryType != COMPLETION_QUERY_TYPE}, so that press dropped every
 * NMOX item at once and the popup collapsed to "No suggestions" — the
 * platform's own items survived, ours vanished. The lawful gate is the bit
 * mask {@code (queryType & COMPLETION_QUERY_TYPE) == 0}, which still refuses
 * DOCUMENTATION (2) and TOOLTIP (4).
 *
 * <p>Two laws, so a new provider fails the build either way: the source law
 * finds the equality shape by name; the behavioral law drives the real
 * {@code createTask} with 1, 9 and 2 on a bare pane — 9 must match 1's
 * answer (null or not, whatever the provider's own document gating says),
 * and 2 must be refused.
 */
class CompletionAllQueryGateTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    private static List<Path> providerSources() throws IOException {
        try (Stream<Path> walk = Files.walk(SOURCE_ROOT)) {
            return walk.filter(p -> p.getFileName().toString().endsWith("CompletionProvider.java"))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains("CompletionTask createTask(");
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .sorted()
                    .toList();
        }
    }

    private static String className(Path source) {
        String rel = SOURCE_ROOT.relativize(source).toString();
        return rel.substring(0, rel.length() - ".java".length()).replace(java.io.File.separatorChar, '.');
    }

    @Test
    @DisplayName("Source law: every createTask gates on the COMPLETION bit mask, never equality")
    void everyProviderUsesTheMask() throws IOException {
        List<Path> sources = providerSources();
        assertThat(sources).as("the census must see the providers").hasSizeGreaterThanOrEqualTo(11);
        for (Path p : sources) {
            String src = Files.readString(p);
            assertThat(src)
                    .as("%s must not gate createTask on queryType equality (drops COMPLETION_ALL)", p.getFileName())
                    .doesNotContain("queryType != ");
            assertThat(src.contains("(queryType & COMPLETION_QUERY_TYPE) == 0")
                    || src.contains("(queryType & CompletionProvider.COMPLETION_QUERY_TYPE) == 0"))
                    .as("%s must gate createTask on the COMPLETION bit mask", p.getFileName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Behavioral law: COMPLETION_ALL answers like COMPLETION; DOCUMENTATION is refused")
    void completionAllAnswersLikeCompletion() throws Exception {
        for (Path p : providerSources()) {
            Class<?> type = Class.forName(className(p));
            Object provider = type.getDeclaredConstructor().newInstance();
            Method createTask = type.getMethod("createTask", int.class, JTextComponent.class);
            JEditorPane pane = new JEditorPane();
            Object one = createTask.invoke(provider, 1, pane);
            Object all = createTask.invoke(provider, 9, pane);
            Object doc = createTask.invoke(provider, 2, pane);
            assertThat(all == null)
                    .as("%s: COMPLETION_ALL (9) must be answered exactly like COMPLETION (1)", type.getSimpleName())
                    .isEqualTo(one == null);
            assertThat(doc).as("%s: DOCUMENTATION (2) stays refused", type.getSimpleName()).isNull();
        }
    }
}
