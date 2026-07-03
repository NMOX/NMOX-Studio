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
        assertThat(StandardsKitAction.validate(
                "https://example.com", "My Site", "security@example.com", true))
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
                "https://example.com", "My Site", "not-an-email", false))
                .isNull();
    }

    @Test
    @DisplayName("Name is validated before the URL — blank name wins over a bad URL")
    void nameCheckedBeforeUrl() {
        assertThat(StandardsKitAction.validate(
                "not a url", "", "x", true))
                .contains("Give the site a name");
    }
}
