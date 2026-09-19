package org.nmox.studio.ui.browser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Browser's empty state is ours, and it fetches nothing.
 *
 * <p>A bare {@code ⌥⌘4} used to load {@code news.ycombinator.com}: a
 * third-party website inside a work IDE, and an outbound request the user
 * never asked for, made every time they opened a pane to look at their own
 * running app. The laws below are what "fetches nothing" means in code — the
 * page names no remote host, and the component no longer carries a remote
 * address to fall back to.
 */
class StartPageTest {

    /** Any absolute URL, and any protocol-relative one, anywhere in the document. */
    private static final Pattern REMOTE = Pattern.compile("(?i)(https?:)?//[a-z0-9.-]+\\.[a-z]{2,}");

    @Test
    @DisplayName("the page names no remote host — no stylesheet, font, image or beacon to fetch")
    void thePageFetchesNothing() {
        Matcher m = REMOTE.matcher(StartPage.html());
        assertThat(m.find())
                .as("a start page that loads anything from the network is the defect this replaced; "
                        + "found: %s", m.hitEnd() ? "" : m.group())
                .isFalse();
    }

    @Test
    @DisplayName("every sentence the page promises to show is in it")
    void thePageSaysItsPiece() {
        String html = StartPage.html();
        assertThat(html)
                .contains(Bundle.StartPage_nothingServing())
                .contains(Bundle.StartPage_whatHappens())
                .contains(Bundle.StartPage_offline());
        assertThat(html).as("a document the engine can parse").startsWith("<!DOCTYPE html>");
    }

    @Test
    @DisplayName("a string carrying markup is shown as text, not rendered — our own bundles are still input")
    void everyPartIsEscaped() {
        String html = StartPage.document(java.util.Locale.ENGLISH, "t",
                "<img src=x onerror=alert(1)>", "a & b", "\"quoted\"", "o");
        assertThat(html)
                .as("the markup-render class: our own text is written by fifteen translators, "
                        + "and 'our text cannot contain markup' is the assumption that keeps failing")
                .doesNotContain("<img")
                .contains("&lt;img")
                .contains("a &amp; b");
    }

    @Test
    @DisplayName("the document declares its language AND its direction — without dir, a Hebrew sentence draws its full stop at the wrong end")
    void theDocumentDeclaresItsDirection() {
        // measured by the translator with java.text.Bidi: under a base LTR
        // direction the Hebrew door sentence splits and the sentence-final
        // period is drawn at the far right, before the first word. No
        // translation can fix that; only the document can.
        assertThat(StartPage.document(java.util.Locale.forLanguageTag("he"),
                "t", "h", "w", "d", "o"))
                .contains("lang=\"he\"").contains("dir=\"rtl\"");
        assertThat(StartPage.document(java.util.Locale.forLanguageTag("ar"),
                "t", "h", "w", "d", "o")).contains("dir=\"rtl\"");
        assertThat(StartPage.document(java.util.Locale.ENGLISH, "t", "h", "w", "d", "o"))
                .contains("lang=\"en\"").contains("dir=\"ltr\"");
    }

    @Test
    @DisplayName("the door names a control that exists — GO on a rack device, the product's own words")
    void theDoorNamesARealControl() {
        // the rack's faceplates carry DEV/STOP/BUILD/PREVIEW/CHECK and GO.
        // There is no play glyph on any of them; the product's own
        // GettingStarted_runGesture says "▶ (F6), or GO on a rack device",
        // where the play glyph is the IDE TOOLBAR's button. The first draft of
        // this page said "press a console's ▶ on the rack" and would have
        // shipped that wrong door in fifteen languages.
        assertThat(Bundle.StartPage_door()).contains("GO").doesNotContain("▶");
    }

    @Test
    @DisplayName("no value carries a doubled apostrophe — these keys never reach MessageFormat")
    void noValueIsWrittenForMessageFormat() {
        // NbBundle.getMessage(Class, String) with no arguments is
        // getBundle(clazz).getString(key) — read from the platform's bytecode.
        // Nothing formats, so a doubled apostrophe written for MessageFormat's
        // sake ships on screen as two characters.
        for (String value : new String[]{Bundle.StartPage_title(), Bundle.StartPage_nothingServing(),
            Bundle.StartPage_whatHappens(), Bundle.StartPage_door(), Bundle.StartPage_offline()}) {
            assertThat(value).doesNotContain("''");
        }
    }

    @Test
    @DisplayName("the component keeps no remote address to fall back to")
    void theComponentCarriesNoRemoteHome() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/browser",
                "WebBrowserTopComponent.java"), StandardCharsets.UTF_8);
        // strip comments: the history is deliberately recorded in the javadoc,
        // and a gate that reads prose is wrong in both directions (v2.182.0)
        StringBuilder code = new StringBuilder();
        boolean block = false;
        for (String line : src.split("\n", -1)) {
            String t = line.strip();
            if (block) {
                block = !t.contains("*/");
                continue;
            }
            if (t.startsWith("/*")) {
                block = !t.contains("*/");
                continue;
            }
            if (!t.startsWith("//") && !t.startsWith("*")) {
                code.append(line).append('\n');
            }
        }
        assertThat(code.toString())
                .as("the empty state must not be somebody else's page")
                .doesNotContain("http://")
                .doesNotContain("https://");
    }
}
