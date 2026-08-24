package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The A11y Kit's contract (v2.38.0): the wiring is idempotent edit by
 * edit, presence beats position, the zoom warning never becomes an
 * edit, and the kit family's never-clobber law holds on both files.
 */
class A11yKitTest {

    @Test
    @DisplayName("wire(): lang, skip link, and stylesheet land once each — run twice, nothing doubles")
    void wireIsIdempotent() {
        String page = "<html>\n<head>\n<title>t</title>\n</head>\n<body>\n<p>hi</p>\n</body>\n</html>";
        String once = A11yKit.wire(page);
        assertThat(once).contains("<html lang=\"en\">");
        assertThat(once).contains("class=\"skip-link\"");
        assertThat(once).contains("a11y.css");
        assertThat(A11yKit.wire(once))
                .as("the second run is a no-op — every edit is presence-checked")
                .isEqualTo(once);
    }

    @Test
    @DisplayName("an existing lang survives untouched, whatever its value")
    void existingLangWins() {
        String page = "<html lang=\"de\"><head></head><body></body></html>";
        assertThat(A11yKit.wire(page)).contains("lang=\"de\"")
                .doesNotContain("lang=\"en\"");
    }

    @Test
    @DisplayName("no <body> means no skip link; no </head> means no stylesheet — never a guess")
    void missingAnchorsRefuse() {
        String bare = "<p>fragment</p>";
        assertThat(A11yKit.wire(bare)).isEqualTo(bare);
    }

    @Test
    @DisplayName("a zoom-disabling viewport is WARNED about, never rewritten")
    void zoomWarningNeverEdits(@TempDir Path work) throws Exception {
        File dir = work.toFile();
        String page = "<html lang=\"en\"><head><meta name=\"viewport\" content=\""
                + "width=device-width, user-scalable=no\"></head><body>"
                + "<a class=\"skip-link\" href=\"#main\">s</a></body></html>";
        // page already fully wired except a11y.css — write it with the kit
        Files.writeString(new File(dir, "index.html").toPath(), page);
        List<A11yKit.Outcome> out = A11yKit.write(dir,
                new A11yKit.Options(false, false, true));
        assertThat(out.get(0).note()).contains("WARNING")
                .contains("pinch zoom");
        String after = Files.readString(new File(dir, "index.html").toPath());
        assertThat(after).as("the warning is words, not an edit")
                .contains("user-scalable=no");
    }

    @Test
    @DisplayName("never-clobber: existing a11y.css and notes are untouched")
    void neverClobber(@TempDir Path work) throws Exception {
        File dir = work.toFile();
        Files.writeString(new File(dir, "a11y.css").toPath(), "/* mine */");
        List<A11yKit.Outcome> out = A11yKit.write(dir,
                new A11yKit.Options(true, true, false));
        assertThat(out.get(0).written()).isFalse();
        assertThat(Files.readString(new File(dir, "a11y.css").toPath()))
                .isEqualTo("/* mine */");
        assertThat(out.get(1).written()).isTrue();
        assertThat(Files.readString(new File(dir, "A11Y-NOTES.md").toPath()))
                .contains("keyboard walk").contains("VITALS");
    }

    @Test
    @DisplayName("no index.html is an honest outcome, not a stack trace")
    void missingIndexSpeaks(@TempDir Path work) throws Exception {
        List<A11yKit.Outcome> out = A11yKit.write(work.toFile(),
                new A11yKit.Options(false, false, true));
        assertThat(out.get(0).written()).isFalse();
        assertThat(out.get(0).note()).contains("no index.html");
    }
}
