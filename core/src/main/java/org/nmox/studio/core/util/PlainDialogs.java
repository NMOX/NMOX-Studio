package org.nmox.studio.core.util;

import javax.swing.JTextArea;
import javax.swing.UIManager;

/**
 * Dialog messages that show external text as TEXT (v2.86.0, the
 * v2.85.0 review's find made a law): a {@code String} handed to a
 * NotifyDescriptor is laid out by Swing's option pane as one
 * label per line and per wrapped fragment, and a {@code JLabel} whose
 * text starts with {@code <html>} RENDERS it — the v1.208.0 class. Any
 * message carrying an exception's words, a file name, a tool's output
 * or a catalog's prose is one line break away from painting markup, so
 * every such message rides a read-only wrapping text area instead: it
 * never interprets, it reads whole to a screen reader, and it looks
 * like the label it replaces. Pure Swing (core carries no dialogs
 * dependency): a site writes
 * {@code new NotifyDescriptor.Message(PlainDialogs.plain(text, "Message"), type)}
 * and PlainMessageGateTest refuses any other non-literal String. The
 * one place the shape is spelled — the html-render law's dialog half,
 * {@link PlainTables} its table half.
 */
public final class PlainDialogs {

    private PlainDialogs() {
    }

    /** A read-only, wrapping, named text area sized like a message label. */
    public static JTextArea plain(String text, String accessibleName) {
        JTextArea area = new JTextArea(text == null ? "" : text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setColumns(Math.min(72, Math.max(24, longestLine(area.getText()))));
        area.setFont(UIManager.getFont("Label.font"));
        area.setBorder(null);
        area.getAccessibleContext().setAccessibleName(accessibleName);
        return area;
    }


    static int longestLine(String text) {
        int longest = 0;
        for (String line : text.split("\n", -1)) {
            longest = Math.max(longest, line.length());
        }
        return longest;
    }
}
