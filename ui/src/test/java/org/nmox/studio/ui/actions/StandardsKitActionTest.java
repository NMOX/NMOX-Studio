package org.nmox.studio.ui.actions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Standards Kit's input gate: the generated files are only as valid
 * as the URL, name, and contact the wizard is handed, so the pure
 * {@code validate} guard is what stands between good inputs and a broken
 * robots/sitemap/security.txt set.
 */
class StandardsKitActionTest {

    @Test
    @DisplayName("Good inputs pass: real name, real URL, real contact with security.txt on")
    void acceptsValidInputs() {
        // a REAL host, because v2.121.0 made the reserved documentation
        // domain a refusal: this test said "real URL" in its own title and
        // then handed over example.com, which is the same substitution the
        // wizard was inviting its users to make
        assertThat(StandardsKitAction.validate(
                "https://my-site.dev", "My Site", "security@my-site.dev", true))
                .isNull();
    }

    @Test
    @DisplayName("A blank site name is rejected — it feeds the manifest and humans.txt")
    void rejectsBlankName() {
        assertThat(StandardsKitAction.validate(
                "https://example.com", "   ", "security@example.com", true))
                .contains("Give the site a name");
    }

    @Test
    @DisplayName("A URL with no scheme is rejected")
    void rejectsSchemelessUrl() {
        String problem = StandardsKitAction.validate(
                "example.com", "My Site", "security@example.com", true);
        assertThat(problem).contains("scheme and host").contains("example.com");
    }

    @Test
    @DisplayName("A URL with a scheme but no host is rejected")
    void rejectsHostlessUrl() {
        assertThat(StandardsKitAction.validate(
                "mailto:someone", "My Site", "security@example.com", true))
                .contains("scheme and host");
    }

    @Test
    @DisplayName("A syntactically unparseable URL is rejected with its own message")
    void rejectsUnparseableUrl() {
        String problem = StandardsKitAction.validate(
                "http://exa mple.com", "My Site", "security@example.com", true);
        assertThat(problem).contains("doesn't parse");
    }

    @Test
    @DisplayName("With security.txt on, a non-email contact is rejected (RFC 9116)")
    void rejectsBadContactWhenSecurityTxtOn() {
        String problem = StandardsKitAction.validate(
                "https://example.com", "My Site", "not-an-email", true);
        assertThat(problem).contains("RFC 9116").contains("not-an-email");
    }

    @Test
    @DisplayName("With security.txt off, a junk contact is tolerated — it isn't written")
    void ignoresContactWhenSecurityTxtOff() {
        assertThat(StandardsKitAction.validate(
                "https://my-site.dev", "My Site", "not-an-email", false))
                .isNull();
    }

    @Test
    @DisplayName("Name is validated before the URL — blank name wins over a bad URL")
    void nameCheckedBeforeUrl() {
        assertThat(StandardsKitAction.validate(
                "not a url", "", "x", true))
                .contains("Give the site a name");
    }

    @Test
    @DisplayName("the untouched example is refused — a placeholder is not a value")
    void theExampleSiteIsRefused() {
        // v2.121.0, the walk of the wizard: both fields arrive pre-filled
        // with example.com, and both pass every other check because they
        // are syntactically perfect. Pressing OK unedited writes a sitemap
        // for a site the user does not own and a security.txt promising
        // that someone answers a mailbox that does not exist.
        assertThat(StandardsKitAction.validate(
                "https://example.com", "NMOX", "security@example.com", true))
                .as("the dialog's own defaults, untouched").isNotNull();
        assertThat(StandardsKitAction.validate(
                "https://example.com", "NMOX", "me@real.dev", true))
                .as("a real contact does not excuse the example site").isNotNull();
        assertThat(StandardsKitAction.validate(
                "https://real.dev", "NMOX", "security@example.com", true))
                .as("a real site does not excuse the example contact").isNotNull();
        assertThat(StandardsKitAction.validate(
                "https://real.dev", "NMOX", "security@real.dev", true))
                .as("both real: nothing to refuse").isNull();
    }

    @Test
    @DisplayName("only the reserved documentation names are refused, and www does not hide them")
    void reservedNamesOnly() {
        // RFC 2606 and RFC 6761 reserve these, which is what makes the
        // refusal safe rather than a guess about what the user meant
        assertThat(StandardsKitAction.isExampleHost("example.com")).isTrue();
        assertThat(StandardsKitAction.isExampleHost("EXAMPLE.ORG")).isTrue();
        assertThat(StandardsKitAction.isExampleHost("www.example.net")).isTrue();
        assertThat(StandardsKitAction.isExampleHost("shop.example")).isTrue();
        // a real site whose name merely CONTAINS the word is not reserved
        assertThat(StandardsKitAction.isExampleHost("example.dev")).isFalse();
        assertThat(StandardsKitAction.isExampleHost("myexample.com")).isFalse();
        assertThat(StandardsKitAction.isExampleHost("counterexample.io")).isFalse();
    }
}
