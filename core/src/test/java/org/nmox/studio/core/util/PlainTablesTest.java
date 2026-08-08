package org.nmox.studio.core.util;

import java.awt.Component;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plain-text table law (v1.306.0): a cell value starting with
 * {@code <html>} must display as text, never render as markup — a
 * rendered {@code <img src>} makes the IDE fetch an attacker-chosen
 * URL at paint time (the v1.208.0 class). The divergent input is real
 * markup in a real headless table: the mutant that drops the
 * {@code html.disable} property gets a live HTML view installed.
 */
class PlainTablesTest {

    private static final String HOSTILE = "<html><img src='http://evil/x'>";

    /** The renderer component a table would actually paint for cell (0,0). */
    private static JComponent painted(JTable table) throws Exception {
        JComponent[] out = new JComponent[1];
        SwingUtilities.invokeAndWait(() -> {
            Component c = table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0);
            out[0] = (JComponent) c;
        });
        return out[0];
    }

    private static JTable tableWithHostileCell() {
        DefaultTableModel m = new DefaultTableModel(new Object[]{"Status"}, 0);
        m.addRow(new Object[]{HOSTILE});
        return new JTable(m);
    }

    @Test
    @DisplayName("negative control: a stock JTable really does install an HTML view")
    void stockTableRendersMarkup() throws Exception {
        // this pins the HAZARD, so the fix below is proven against a
        // live failure mode rather than an assumption about Swing
        JComponent c = painted(tableWithHostileCell());
        assertThat(c.getClientProperty(BasicHTML.propertyKey))
                .as("Swing interprets <html>-prefixed cell text by default;"
                        + " if this ever changes, the helper is dead weight")
                .isNotNull();
    }

    @Test
    @DisplayName("disableHtml: the hostile cell paints as literal text, no HTML view")
    void disableHtmlShowsMarkupLiterally() throws Exception {
        JTable table = PlainTables.disableHtml(tableWithHostileCell());
        JComponent c = painted(table);
        assertThat(c.getClientProperty(BasicHTML.propertyKey))
                .as("no BasicHTML view may be installed — markup must be text")
                .isNull();
    }

    @Test
    @DisplayName("plain(renderer): a site's custom renderer subclass is covered too")
    void plainCoversCustomRenderers() throws Exception {
        JTable table = tableWithHostileCell();
        table.setDefaultRenderer(Object.class,
                PlainTables.plain(new DefaultTableCellRenderer() {
                }));
        JComponent c = painted(table);
        assertThat(c.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
        assertThat(c.getClientProperty(BasicHTML.propertyKey)).isNull();
    }
}
