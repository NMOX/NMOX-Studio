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
 * The Browser's two empty states are ours, and they fetch nothing.
 *
 * <p>A bare {@code ⌥⌘4} used to load {@code news.ycombinator.com}: a
 * third-party website inside a work IDE, and an outbound request the user
 * never asked for, made every time they opened a pane to look at their own
 * running app. The laws below are what "fetches nothing" means in code — the
 * page names no remote host, and the component no longer carries a remote
 * address to fall back to.
 *
 * <p>The second empty state is the one that said NOTHING: a registered serving
 * that does not answer failed at the socket and left a blank pane. Its page
 * must name the address that failed, which means it legitimately CONTAINS a
 * URL — so "fetches nothing" is checked there the only way that survives that:
 * against the parsed document, where a URL printed as text and a URL in an
 * {@code href} are different things.
 */
class StartPageTest {

    /** Any absolute URL, and any protocol-relative one, anywhere in the document. */
    private static final Pattern REMOTE = Pattern.compile("(?i)(https?:)?//[a-z0-9.-]+\\.[a-z]{2,}");

    /** Elements that exist to pull something in, or to send somewhere. */
    private static final java.util.Set<String> FETCHING_ELEMENTS = java.util.Set.of(
            "a", "applet", "audio", "base", "embed", "form", "frame", "iframe",
            "img", "link", "object", "script", "source", "track", "video");

    /** Attributes that name something to pull in, or to send to. */
    private static final java.util.Set<String> FETCHING_ATTRS = java.util.Set.of(
            "action", "background", "codebase", "data", "formaction", "href",
            "http-equiv", "longdesc", "manifest", "ping", "poster", "src", "srcset");

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
    @DisplayName("the document is well formed — a page we generate should survive a strict parser, not only a forgiving one")
    void theDocumentIsWellFormed() throws Exception {
        // WebKit would forgive an unclosed tag; a strict parse is the cheap
        // way to prove the template never drifts into something malformed
        // while still looking fine on screen.
        parse(StartPage.html());
        parse(StartPage.noAnswerHtml("http://localhost:4321/"));
    }

    /** The document, parsed strictly with entity resolution off. */
    private static org.w3c.dom.Document parse(String html) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory f =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        String xml = html.replace("<!DOCTYPE html>", "");
        return f.newDocumentBuilder().parse(
                new org.xml.sax.InputSource(new java.io.StringReader(xml)));
    }

    @Test
    @DisplayName("neither page can fetch: no element, attribute or stylesheet rule names anything to load")
    void neitherPageCanFetch() throws Exception {
        // The regex law above cannot serve the failure page, because that page
        // NAMES the address that did not answer and naming is the whole point.
        // So the law is read off the parsed document instead, where the
        // difference the regex cannot see — a URL in a text node against a URL
        // in an href — is the difference that decides whether anything is
        // fetched. This is the one law both pages share.
        for (String html : new String[]{StartPage.html(),
            StartPage.noAnswerHtml("http://evil.example.com/beacon.png")}) {
            walk(parse(html).getDocumentElement());
        }
    }

    private static void walk(org.w3c.dom.Element el) {
        assertThat(FETCHING_ELEMENTS)
                .as("<%s> exists to pull something in; a page that asks the network "
                        + "for nothing has none", el.getTagName())
                .doesNotContain(el.getTagName().toLowerCase(java.util.Locale.ROOT));
        org.w3c.dom.NamedNodeMap attrs = el.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            org.w3c.dom.Node a = attrs.item(i);
            assertThat(FETCHING_ATTRS)
                    .as("%s=\"%s\" names something to load or somewhere to send",
                            a.getNodeName(), a.getNodeValue())
                    .doesNotContain(a.getNodeName().toLowerCase(java.util.Locale.ROOT));
            assertThat(a.getNodeValue()).doesNotContain("url(").doesNotContain("@import");
        }
        org.w3c.dom.NodeList kids = el.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            org.w3c.dom.Node k = kids.item(i);
            if (k instanceof org.w3c.dom.Element child) {
                walk(child);
            } else if (k.getNodeValue() != null) {
                // a stylesheet is a text node: url() and @import fetch from CSS
                assertThat(k.getNodeValue()).doesNotContain("url(").doesNotContain("@import");
            }
        }
    }

    @Test
    @DisplayName("the failure page names the address that did not answer")
    void theFailurePageNamesTheAddress() {
        String html = StartPage.noAnswerHtml("http://localhost:4321/");
        assertThat(html)
                .as("a blank pane is the defect this replaced; the reader needs to know "
                        + "WHICH address went unanswered — the one they are about to retry")
                .contains("http://localhost:4321/")
                .contains(Bundle.StartPage_noAnswer())
                .contains(Bundle.StartPage_noAnswerWhy())
                .contains(Bundle.StartPage_noAnswerRetry());
    }

    @Test
    @DisplayName("the address is escaped — it is the one part of the page nobody here wrote")
    void theAddressIsEscaped() {
        String html = StartPage.noAnswerDocument(java.util.Locale.ENGLISH, "t", "h",
                "http://x/\"><img src=y onerror=alert(1)>", "w", "d", "o");
        assertThat(html).doesNotContain("<img").contains("&lt;img");
    }

    @Test
    @DisplayName("a very long address is clipped by CODE POINTS — a cut between a surrogate pair strands half a character")
    void theAddressIsClippedWholeCharacters() {
        // "😀" is one code point in two UTF-16 units. Clipping by units can
        // stop between them and leave a lone high surrogate on screen — the
        // v1.287.0 class, in a value that arrives from the address bar.
        String url = "http://x/" + "😀".repeat(StartPage.URL_CAP);
        String html = StartPage.noAnswerDocument(java.util.Locale.ENGLISH, "t", "h",
                url, "w", "d", "o");
        for (int i = 0; i < html.length(); i++) {
            char c = html.charAt(i);
            assertThat(Character.isHighSurrogate(c) && (i + 1 >= html.length()
                    || !Character.isLowSurrogate(html.charAt(i + 1))))
                    .as("a lone high surrogate at index %s", i).isFalse();
        }
        assertThat(html).as("and it says it cut rather than pretending that is the address")
                .contains("…");
    }

    @Test
    @DisplayName("the address keeps its own direction — in Hebrew a bare URL draws its http:// at the wrong end")
    void theAddressIsIsolatedFromTheDocumentDirection() {
        // v2.181.0 measured this exact shape: under an RTL base direction the
        // neutrals around a Latin URL reorder, and http://localhost:8080/ draws
        // its slash at the front. An element's own dir is the HTML isolate.
        String html = StartPage.noAnswerDocument(java.util.Locale.forLanguageTag("he"),
                "t", "h", "http://localhost:4321/", "w", "d", "o");
        assertThat(html).contains("dir=\"rtl\"").contains("class=\"url\" dir=\"ltr\"");
    }

    @Test
    @DisplayName("every shipped language carries the failure page's keys — a blank pane in English is a blank pane in Hebrew")
    void everyLanguageSaysIt() throws Exception {
        // the population is the product's own language list, not a copy of it:
        // a sixteenth language fails here on the commit that adds it
        for (org.nmox.studio.core.util.UiLocale.Choice c
                : org.nmox.studio.core.util.UiLocale.SUPPORTED) {
            // "" is the System default row; "en" is the SOURCE, whose values
            // the @Messages processor merges into the base Bundle.properties
            // at compile time and which the assertions above read directly
            if (c.code().isEmpty() || "en".equals(c.code())) {
                continue;
            }
            Path bundle = Path.of("src/main/resources/org/nmox/studio/ui/browser",
                    "Bundle_" + c.code() + ".properties");
            assertThat(bundle).as("no bundle for %s", c.code()).isRegularFile();
            java.util.Properties p = new java.util.Properties();
            try (java.io.Reader r = Files.newBufferedReader(bundle, StandardCharsets.UTF_8)) {
                p.load(r);
            }
            assertThat(p.stringPropertyNames())
                    .as("%s is missing part of the failure page", c.code())
                    .contains("StartPage_noAnswerTitle", "StartPage_noAnswer",
                            "StartPage_noAnswerWhy", "StartPage_noAnswerRetry");
            for (String k : new String[]{"StartPage_noAnswerTitle", "StartPage_noAnswer",
                "StartPage_noAnswerWhy", "StartPage_noAnswerRetry"}) {
                assertThat(p.getProperty(k)).as("%s/%s is blank", c.code(), k).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("and the page is actually SHOWN — a payload with no gate is the v1.321.0 class")
    void theFailurePageIsWired() throws Exception {
        // Two proofs, because a page nobody renders is not a fix: the panel
        // must offer the failure, and the component must take it. Read from
        // source, since the engine only fails against a real socket.
        String panel = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/browser/fx",
                "FxBrowserPanel.java"), StandardCharsets.UTF_8);
        assertThat(panel)
                .as("the engine's own FAILED state is the signal — never a timer")
                .contains("Worker.State.FAILED")
                .contains("l.loadFailed(failed)");
        String tc = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/browser",
                "WebBrowserTopComponent.java"), StandardCharsets.UTF_8);
        assertThat(tc)
                .as("the component must install the listener and answer it with OUR page")
                .contains("setLoadFailedListener(this::showNoAnswer)")
                .contains("StartPage.noAnswerHtml(url)");
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
