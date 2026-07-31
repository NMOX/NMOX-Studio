package org.nmox.studio.ui.browser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JLabel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.ui.browser.devtools.DomSnapshotParser;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins for the v1.208.0 arc-review fixes on the Browser DevTools
 * surface. Every string these panes display comes from the inspected
 * page, so the fixes here are about not trusting it.
 */
class DevToolsReviewFixesTest {

    // ---- F1: page text is never rendered as HTML ------------------------

    @Test
    @DisplayName("a JLabel DOES render page-supplied <html> — the vector is real")
    void plainLabelRendersHtml() {
        // Establishes the premise the fix defends against: without
        // html.disable, a component name like <html><img src=...> makes
        // Swing build an HTML view and the IDE's JVM fetch that URL.
        JLabel label = new JLabel("<html><img src=\"http://example.invalid/x\">y");
        // Swing really does build an HTML view for this text — that view
        // is what would fetch the attacker's URL from the IDE's own JVM.
        assertThat(label.getClientProperty("html"))
                .as("the unguarded vector is real, not theoretical")
                .isNotNull();
    }

    @Test
    @DisplayName("html.disable suppresses the HTML view (the defense works)")
    void htmlDisableSuppressesRendering() {
        JLabel label = new JLabel();
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setText("<html><img src=\"http://example.invalid/x\">y");
        assertThat(label.getClientProperty("html"))
                .as("no HTML view is built when html.disable is set")
                .isNull();
    }

    @Test
    @DisplayName("every DevTools pane routes through the no-HTML factories (source gate)")
    void everyPaneDisablesHtml() throws IOException {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/ui/browser/fx/DevToolsPanel.java"),
                StandardCharsets.UTF_8);
        // The only raw constructors allowed are the ones INSIDE the
        // html-safe factories; every pane must go through those, so a
        // second occurrence means a pane was added unguarded.
        assertThat(countOf(src, "new JTree("))
                .as("only safeTree() constructs a JTree")
                .isEqualTo(1);
        assertThat(countOf(src, "new JTable("))
                .as("only safeTable() constructs a JTable")
                .isEqualTo(1);
        assertThat(src).contains("html.disable");
        assertThat(countOf(src, "safeTree(")).isGreaterThanOrEqualTo(4);
        assertThat(countOf(src, "safeTable(")).isGreaterThanOrEqualTo(4);
    }

    private static int countOf(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    // ---- F7: page-supplied DOM labels are capped Java-side --------------

    @Test
    @DisplayName("a megabyte tagName cannot reach a tree label (F7)")
    void domLabelsCappedJavaSide() {
        String huge = "t".repeat(4_000_000);
        String json = "{\"t\":\"" + huge + "\",\"i\":\"" + huge + "\",\"c\":\"" + huge
                + "\",\"a\":[],\"p\":[0],\"ch\":[]}";
        DomSnapshotParser.DomNode n = DomSnapshotParser.parse(json);
        assertThat(n.tag.length()).isLessThanOrEqualTo(200);
        assertThat(n.id.length()).isLessThanOrEqualTo(200);
        assertThat(n.classes.length()).isLessThanOrEqualTo(200);
    }

    // ---- F3 / F5: caps and disposal (source gates) ----------------------

    @Test
    @DisplayName("script results are capped before crossing to the EDT (F3)")
    void scriptResultsCapped() throws IOException {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/ui/browser/fx/FxBrowserPanel.java"),
                StandardCharsets.UTF_8);
        assertThat(src)
                .as("runScript caps on our side of the page border")
                .contains("onResult.accept(cap(String.valueOf(r)))")
                .contains("onError.accept(cap(msg))");
    }

    @Test
    @DisplayName("closing the Browser tab stops the page (F5)")
    void closingTabStopsEngine() throws IOException {
        String tc = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/ui/browser/WebBrowserTopComponent.java"),
                StandardCharsets.UTF_8);
        assertThat(tc)
                .as("componentClosed tears the engine down")
                .contains("protected void componentClosed()")
                .contains("browser.stopEngine()");
        String panel = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/ui/browser/fx/FxBrowserPanel.java"),
                StandardCharsets.UTF_8);
        assertThat(panel)
                .as("stopEngine cancels the load, blanks the document, stops the timers")
                .contains("getLoadWorker().cancel()")
                .contains("about:blank")
                .contains("devTools.stopTimers()");
    }
}
