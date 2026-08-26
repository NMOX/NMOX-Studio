package org.nmox.studio.ui.browser.devtools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.spi.LiveServings;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The runtime-error pipeline's laws (v2.39.0): only errors with a
 * REAL location parse (a guessed location is worse than none), only
 * URLs that resolve into a served project land in the editor, and a
 * page load clears the previous page's batch — errors belong to the
 * load that produced them.
 */
class RuntimeErrorsTest {

    @Test
    @DisplayName("the onerror shape parses; rejections and locationless errors refuse")
    void parseShapes() {
        RuntimeErrors.Located l = RuntimeErrors.parse(
                "ReferenceError: Can't find variable: brewCoffee"
                + " (http://127.0.0.1:8080/main.js:12)");
        assertThat(l.message()).startsWith("ReferenceError");
        assertThat(l.url()).isEqualTo("http://127.0.0.1:8080/main.js");
        assertThat(l.line()).isEqualTo(12);

        assertThat(RuntimeErrors.parse("Unhandled rejection: boom")).isNull();
        assertThat(RuntimeErrors.parse("error (:0)")).isNull();
        assertThat(RuntimeErrors.parse("weird (http://x/y.js:0)"))
                .as("line 0 is WebKit's no-location shape — refuse")
                .isNull();
        assertThat(RuntimeErrors.parse(null)).isNull();
    }

    @Test
    @DisplayName("a multi-line message still finds its trailing location")
    void multilineMessage() {
        RuntimeErrors.Located l = RuntimeErrors.parse(
                "SyntaxError: something\nwith detail (http://127.0.0.1:8080/app.js:3)");
        assertThat(l).isNotNull();
        assertThat(l.line()).isEqualTo(3);
    }

    @Test
    @DisplayName("only served-project URLs resolve — a CDN error never lands in the editor")
    void resolveContainment(@TempDir Path work) throws IOException {
        File root = work.toFile();
        Files.writeString(new File(root, "main.js").toPath(), "x");
        List<LiveServings.Serving> servings = List.of(
                new LiveServings.Serving("dev", "Dev Server",
                        "http://127.0.0.1:8080/", LiveServings.Kind.WEB, root));
        File hit = RuntimeErrors.resolveForTest(
                "http://127.0.0.1:8080/main.js", servings);
        assertThat(hit).isNotNull();
        // canonical compare: macOS @TempDir paths ride the /var →
        // /private/var symlink the resolver canonicalizes through
        assertThat(hit.getCanonicalFile())
                .isEqualTo(new File(root, "main.js").getCanonicalFile());
        assertThat(RuntimeErrors.resolveForTest(
                "https://cdn.example.com/lib.js", servings)).isNull();
    }

    @Test
    @DisplayName("a page load clears the previous page's batch THROUGH THE BUS — errors belong to their load")
    void pageLoadClears(@TempDir Path work) throws IOException {
        File f = new File(work.toFile(), "main.js");
        Files.writeString(f.toPath(), "x");
        RuntimeErrors re = new RuntimeErrors();
        re.add(f, 12, "boom");
        assertThat(org.nmox.studio.rack.engine.DiagnosticsBus.problemsFor(f))
                .as("the error reached the bus")
                .isNotEmpty();
        re.onPageLoad();
        assertThat(re.current()).isEmpty();
        assertThat(org.nmox.studio.rack.engine.DiagnosticsBus.problemsFor(f))
                .as("the reload cleared the file ON THE BUS — squiggles"
                    + " and Action Items rows follow it")
                .isEmpty();
    }
}
