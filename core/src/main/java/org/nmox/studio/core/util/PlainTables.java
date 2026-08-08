package org.nmox.studio.core.util;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Tables that show text from outside the IDE must show it as TEXT.
 *
 * <p>Swing's {@code JLabel} — and therefore every
 * {@code DefaultTableCellRenderer} — RENDERS a value that starts with
 * {@code <html>}: markup executes at paint time, including
 * {@code <img src="http://…">}, which makes the IDE's own JVM fetch an
 * attacker-chosen URL just for displaying a row. That is the v1.208.0
 * bug class (a page naming a DOM component {@code <html><img …>} made
 * DevTools fetch the URL), and the v1.306.0 review found it standing
 * open in every OTHER table that renders external text: API response
 * headers, DB result cells, IRC channel topics, docker names, probed
 * tool output, port-scan process names, flight-recorder commands.
 *
 * <p>The fix is Swing's own switch: the {@code "html.disable"} client
 * property on the renderer label, which {@code BasicHTML} consults
 * before installing an HTML view. This helper is the ONE place that
 * property is spelled, and {@code PlainTableGateTest} fails the build
 * on any production {@code new JTable} file that doesn't route
 * through it — a new table starts safe or names its reason.
<<<<<<< HEAD
=======
 *
 * <p>The {@link #plain(JComponent)} primitive is not table-specific: any
 * JLabel-based cell renderer (list, tree) or a bare {@code JLabel} that
 * shows external text renders {@code <html>} the same way. The IRC
 * client's nick list, channel tree, and topic label route through it
 * too (v1.307.0), because a hostile IRC server chooses those strings.
>>>>>>> 349f6278 (v1.307.0: the IRC client can't be made to fetch a URL by a hostile server)
 */
public final class PlainTables {

    private PlainTables() {
    }

    /**
     * Installs an html-disabled default renderer on the table (covers
     * Object- and String-classed columns; Boolean/Number renderers
     * never interpret markup). Returns the table for call-site chaining.
     */
    public static JTable disableHtml(JTable table) {
        table.setDefaultRenderer(Object.class, plainRenderer());
        return table;
    }

    /** A fresh default renderer that shows {@code <html>} literally. */
    public static DefaultTableCellRenderer plainRenderer() {
        return plain(new DefaultTableCellRenderer());
    }

    /**
     * Marks one renderer (typically a site's custom
     * {@code DefaultTableCellRenderer} subclass) as plain-text. The
     * property lives on the renderer component instance, so setting it
     * once at construction covers every cell it ever paints.
     */
    public static <T extends JComponent> T plain(T renderer) {
        renderer.putClientProperty("html.disable", Boolean.TRUE);
        return renderer;
    }
}
