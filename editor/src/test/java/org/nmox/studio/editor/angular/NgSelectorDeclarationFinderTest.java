package org.nmox.studio.editor.angular;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.text.PlainDocument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.netbeans.modules.csl.api.OffsetRange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The native-⌘B unit (Angular-top arc): CSL's Go to Declaration rides
 * this finder, so the span half must claim exactly the dashed tags the
 * ⌘-click hyperlink claims — and the wiring gate pins that the CSL
 * language actually registers the parser + finder pair, because a
 * finder without a parser is silently never consulted (the v1.219.0
 * measurement, made structural).
 */
class NgSelectorDeclarationFinderTest {

    @Test
    @DisplayName("the reference span claims dashed tags and nothing else")
    void referenceSpan() throws Exception {
        NgSelectorDeclarationFinder finder = new NgSelectorDeclarationFinder();
        PlainDocument doc = new PlainDocument();
        String t = "<div><app-hero data-x=\"1\"></app-hero></div>";
        doc.insertString(0, t, null);
        int inTag = t.indexOf("app-hero") + 3;
        OffsetRange r = finder.getReferenceSpan(doc, inTag);
        assertThat(r.getStart()).isEqualTo(t.indexOf("app-hero"));
        assertThat(r.getEnd()).isEqualTo(t.indexOf("app-hero") + 8);
        assertThat(finder.getReferenceSpan(doc, 2))
                .as("<div> is a platform tag").isEqualTo(OffsetRange.NONE);
        assertThat(finder.getReferenceSpan(doc, t.indexOf("data-x") + 2))
                .as("a dashed attribute is not a tag").isEqualTo(OffsetRange.NONE);
    }

    @Test
    @DisplayName("identifiers are claimed only when a language server can answer")
    void identifierClaimGated() throws Exception {
        NgSelectorDeclarationFinder finder = new NgSelectorDeclarationFinder();
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, "{{ user.name }}", null);
        // in a unit test no LSP provider is registered for the mime, so
        // the claim must decline — a false claim would make CSL call
        // findDeclaration and dead-end the chord
        assertThat(finder.getReferenceSpan(doc, 4))
                .isEqualTo(org.netbeans.modules.csl.api.OffsetRange.NONE);
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/editor/angular/"
                + "NgSelectorDeclarationFinder.java"));
        assertThat(src)
                .as("the identifier half must route through the LSP provider"
                        + " (ledger 78 remainder)")
                .contains("lspProvider()")
                .contains("performClickAction");
    }

    @Test
    @DisplayName("wiring gate: the ng-template CSL language registers the parser AND the finder")
    void wiringGate() throws Exception {
        String lang = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/editor/languages/NgTemplateLanguage.java"));
        assertThat(lang)
                .as("a DeclarationFinder is only consulted when the language has a"
                        + " Parser — both overrides or the native ⌘B stays dead")
                .contains("NgTemplateParser")
                .contains("NgSelectorDeclarationFinder");
    }
}
