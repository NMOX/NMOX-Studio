package org.nmox.studio.apiclient.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.tree.DefaultMutableTreeNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API Studio's collections tree shows names a collection brought with
 * it — imported from Postman, a HAR capture, or an OpenAPI spec — so
 * they are EXTERNAL text. A JLabel-based cell renderer RENDERS a value
 * starting with {@code <html>}, so a request named
 * {@code <html><img src="http://…">} would make the IDE's own JVM fetch
 * that URL at paint time (the v1.208.0 class, swept through tables in
 * v1.306.0 and IRC in v1.307.0; those sweeps enumerated `new JTable`
 * and missed the cell renderers — v1.311.0 closes that).
 */
class RendererHtmlSafetyTest {

    private static final String HOSTILE = "<html><img src='http://evil/x'>";

    @Test
    @DisplayName("a hostile collection name paints as text, no HTML view installed")
    void requestTreeRendererKeepsMarkupLiteral() throws Exception {
        Collection c = new Collection();
        c.name = HOSTILE;
        JComponent[] out = new JComponent[1];
        SwingUtilities.invokeAndWait(() -> {
            RequestTreeRenderer renderer = new RequestTreeRenderer();
            out[0] = (JComponent) renderer.getTreeCellRendererComponent(
                    new JTree(), new DefaultMutableTreeNode(c),
                    false, false, true, 0, false);
        });
        assertThat(out[0].getClientProperty(BasicHTML.propertyKey))
                .as("an imported collection name must never render as markup")
                .isNull();
    }

    @Test
    @DisplayName("the send-history renderer disables HTML too (URLs can be imported)")
    void historyRendererIsPlain() throws Exception {
        // CRLF checkouts (the windows lane) — normalize before asserting
        String src = Files.readString(Path.of("src", "main", "java", "org", "nmox",
                "studio", "apiclient", "ui", "ApiClientTopComponent.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(src)
                .as("the history row shows a sent METHOD and URL — an imported"
                        + " request carries an external URL, so keep it literal")
                .contains("PlainTables.plain(")
                .contains("historyList.setCellRenderer(");
    }
}
