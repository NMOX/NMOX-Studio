package org.nmox.studio.editor.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.debug.dap.JsDebugServer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bundled js-debug adapter runs under the user's own Node, and since
 * v1.140.1 it does not load under Node 18. {@code JsDebugServerTest} holds
 * the refusal itself; this holds the two things around it: both doors that
 * start the adapter say the refusal instead of the generic "is the debug
 * adapter installed?", and the sentence carries both facts in every
 * language the package ships.
 */
class NodeFloorRefusalTest {

    private static final Path SOURCES = Path.of("src/main/java/org/nmox/studio/editor/debug");
    private static final Path BUNDLES = Path.of("src/main/resources/org/nmox/studio/editor/debug");
    private static final String KEY = "DapDebugAction_nodeTooOld=";

    @Test
    @DisplayName("the sentence names the Node that was found and the one that is needed")
    void sentenceCarriesBothFacts() {
        String said = DapDebugAction.nodeTooOld(new JsDebugServer.NodeTooOld("v18.20.8"));
        assertThat(said).contains("v18.20.8")
                .contains("Node.js " + JsDebugServer.MIN_NODE_MAJOR)
                .doesNotContain("{0}").doesNotContain("{1}");
    }

    @Test
    @DisplayName("both doors that start the adapter catch the refusal before their generic failure")
    void bothDoorsSayTheRefusal() throws IOException {
        for (String door : List.of("DapDebugAction.java", "BrowserDebugAction.java")) {
            String src = Files.readString(SOURCES.resolve(door), StandardCharsets.UTF_8);
            int refusal = src.indexOf("catch (org.nmox.studio.editor.debug.dap.JsDebugServer.NodeTooOld");
            int generic = src.indexOf("catch (Exception ex)", Math.max(refusal, 0));
            assertThat(refusal).as(door + " catches NodeTooOld").isPositive();
            assertThat(generic).as(door + " catches it before the generic failure").isGreaterThan(refusal);
            assertThat(src.substring(refusal, generic)).as(door + " says the sentence")
                    .contains("nodeTooOld(old)");
        }
    }

    @Test
    @DisplayName("every language of this package carries the sentence with both arguments")
    void everyLanguageCarriesTheSentence() throws IOException {
        List<Path> bundles;
        try (Stream<Path> files = Files.list(BUNDLES)) {
            bundles = files.filter(p -> p.getFileName().toString().matches("Bundle(_[a-z]{2})?\\.properties"))
                    .sorted().toList();
        }
        assertThat(bundles).as("the base bundle and its translations").hasSizeGreaterThan(10);
        for (Path bundle : bundles) {
            String value = Files.readAllLines(bundle, StandardCharsets.UTF_8).stream()
                    .filter(line -> line.startsWith(KEY)).map(line -> line.substring(KEY.length()))
                    .findFirst().orElse(null);
            assertThat(value).as(bundle.getFileName() + " has " + KEY).isNotNull();
            assertThat(value).as(bundle.getFileName() + " names both versions")
                    .contains("{0}").contains("{1}").contains("Node.js")
                    // MessageFormat: a bare ASCII apostrophe would open a quote and eat an argument
                    .doesNotContain("'");
        }
    }
}
