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

    @Test
    @DisplayName("plain() on a list-cell renderer, set BEFORE render: hostile nick is text")
    void plainCoversListRenderers() throws Exception {
        // the exact shape the IRC nick list uses (v1.307.0): plain() is set
        // on the renderer at construction, BEFORE it ever renders a value,
        // because BasicHTML installs the view at setText time — setting the
        // property after super's setText would be too late.
        javax.swing.DefaultListCellRenderer renderer =
                PlainTables.plain(new javax.swing.DefaultListCellRenderer());
        javax.swing.JList<String> list = new javax.swing.JList<>(
                new String[]{HOSTILE});
        JComponent[] out = new JComponent[1];
        SwingUtilities.invokeAndWait(() -> out[0] = (JComponent)
                renderer.getListCellRendererComponent(list, HOSTILE, 0, false, false));
        assertThat(out[0].getClientProperty(BasicHTML.propertyKey))
                .as("a nick chosen by a hostile server must be text, not markup")
                .isNull();
    }

    @Test
    @DisplayName("the ordering trap: plain() AFTER a render does NOT undo an installed view")
    void plainAfterRenderIsTooLate() throws Exception {
        // pins WHY the IRC fix sets the property at construction: once
        // setText has built the HTML view, disabling html later can't remove
        // it. A future refactor moving plain() into the render method would
        // reintroduce the fetch — this test fails first if that happens.
        javax.swing.DefaultListCellRenderer renderer =
                new javax.swing.DefaultListCellRenderer();
        javax.swing.JList<String> list = new javax.swing.JList<>(
                new String[]{HOSTILE});
        JComponent[] out = new JComponent[1];
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Component c = renderer.getListCellRendererComponent(
                    list, HOSTILE, 0, false, false);
            PlainTables.plain((JComponent) c);
            out[0] = (JComponent) c;
        });
        assertThat(out[0].getClientProperty(BasicHTML.propertyKey))
                .as("view already built at setText time; late plain() cannot undo it")
                .isNotNull();
    }
}
