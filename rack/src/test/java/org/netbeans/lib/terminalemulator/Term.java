package org.netbeans.lib.terminalemulator;

/**
 * A stand-in with the platform Term's exact name (the real one lives in a
 * friend-only module the rack cannot depend on), so the by-name hierarchy
 * match in TerminalLinkClicks can be proven without the module.
 */
public class Term extends javax.swing.JComponent {
}
