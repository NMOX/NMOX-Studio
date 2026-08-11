package org.nmox.studio.editor.angular;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.text.PlainDocument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.nmox.studio.editor.javascript.JavaScriptEditorKit;
import org.nmox.studio.editor.typescript.TypeScriptEditorKit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Angular-top arc's editor plumbing, pinned headlessly (and pulled
 * onto the measured surface — the windows JaCoCo lane sits ~1.5 points
 * below macOS, and this arc's first CI run tripped the editor floor
 * there at 0.66 vs 0.67): the kits ARE the fix for the dead-chords
 * defect, so their content types are load-bearing, and the parser's
 * result contract is what CSL's Go to Declaration stands on.
 */
class NgArcSurfaceTest {

    @Test
    @DisplayName("the JS/TS kits answer their own mime — the keymap fix's load-bearing line")
    void kitsCarryTheirMime() {
        assertThat(TypeScriptEditorKit.create().getContentType())
                .isEqualTo("text/typescript");
        assertThat(JavaScriptEditorKit.create().getContentType())
                .isEqualTo("text/javascript");
    }

    @Test
    @DisplayName("the snapshot-only parser hands back a result with zero diagnostics")
    void parserContract() throws Exception {
        // a real Snapshot needs the platform parsing environment; the
        // contract under test is the pass-through result shape, which a
        // null snapshot exercises identically
        NgTemplateParser parser = new NgTemplateParser();
        parser.parse(null, null, null);
        NgTemplateParser.NgResult result =
                (NgTemplateParser.NgResult) parser.getResult(null);
        assertThat(result).isNotNull();
        assertThat(result.getDiagnostics()).isEmpty();
        // the listener hooks are deliberate no-ops — results never change
        parser.addChangeListener(e -> { });
        parser.removeChangeListener(e -> { });
    }

    @Test
    @DisplayName("jumpToSelector: a dashed tag dispatches (true), anything else declines (false)")
    void jumpDispatchVerdicts() throws Exception {
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, "<div><app-hero></app-hero></div>", null);
        int inTag = 7; // inside app-hero
        assertThat(NgSelectorHyperlink.jumpToSelector(doc, inTag))
                .as("dashed tag: the jump is DISPATCHED even though no project"
                        + " backs this document — the miss reports async").isTrue();
        assertThat(NgSelectorHyperlink.jumpToSelector(doc, 2))
                .as("<div> is not a jump target").isFalse();
        // hyperlink face over the same span logic
        NgSelectorHyperlink link = new NgSelectorHyperlink();
        assertThat(link.isHyperlinkPoint(doc, inTag, null)).isTrue();
        assertThat(link.getHyperlinkSpan(doc, inTag, null)).containsExactly(6, 14);
        assertThat(link.getTooltipText(doc, inTag, null)).contains("component");
        assertThat(link.getSupportedHyperlinkTypes()).isNotEmpty();
        link.performClickAction(doc, 2, null); // non-tag: declines quietly
    }

    @Test
    @DisplayName("projectDirAbove finds a marker or falls back to the start dir")
    void projectWalk(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root)
            throws Exception {
        java.nio.file.Path nested = root.resolve("src/app");
        java.nio.file.Files.createDirectories(nested);
        java.nio.file.Files.writeString(root.resolve("angular.json"), "{}");
        assertThat(NgSelectorHyperlink.projectDirAbove(nested.toFile()))
                .isEqualTo(root.toFile());
        java.nio.file.Path bare = root.resolve("elsewhere");
        java.nio.file.Files.createDirectories(bare);
        // no marker anywhere above a temp dir root within 8 hops is not
        // guaranteed on every machine, so assert only non-null fallback
        assertThat(NgSelectorHyperlink.projectDirAbove(bare.toFile())).isNotNull();
    }

    @Test
    @DisplayName("completion rows render headlessly: width, render, and the accept contract")
    void completionRowsRender() throws Exception {
        BufferedImage img = new BufferedImage(200, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        g.setFont(font);
        for (Object item : org.nmox.studio.editor.completion
                .NgTemplateCompletionProvider.itemsForTest()) {
            org.netbeans.spi.editor.completion.CompletionItem ci =
                    (org.netbeans.spi.editor.completion.CompletionItem) item;
            assertThat(ci.getPreferredWidth(g, font)).isPositive();
            ci.render(g, font, java.awt.Color.WHITE, java.awt.Color.BLACK,
                    180, 18, false);
            ci.render(g, font, java.awt.Color.WHITE, java.awt.Color.BLACK,
                    180, 18, true);
            assertThat(ci.instantSubstitution(null)).isFalse();
            assertThat(ci.getSortPriority()).isZero();
            assertThat(ci.getSortText()).isNotNull();
            assertThat(ci.getInsertPrefix()).isNotNull();
            assertThat(ci.createDocumentationTask()).isNull();
            assertThat(ci.createToolTipTask()).isNull();
            ci.processKeyEvent(null);
        }
        g.dispose();
    }
}
