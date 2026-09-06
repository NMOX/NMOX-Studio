package org.nmox.studio.rack.mcp;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;

/**
 * Tools ▸ Agent Port… — the explicit gesture that is the Agent Port's
 * whole consent model: nothing listens until the user starts it here,
 * the dialog names EXACTLY what a token-holder can read (the tool
 * roster, by name, read-only), and the token exists only in this
 * dialog and the caller's config — never a log, never a file of ours.
 * Zero boot cost: a menu item. Stop is one click and the port dies
 * with the JVM regardless.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.rack.mcp.AgentPortAction")
@ActionRegistration(displayName = "#CTL_AgentPortAction", lazy = true)
@ActionReference(path = "Menu/Tools", position = 95)
@Messages("CTL_AgentPortAction=Agent Port (MCP)…")
public final class AgentPortAction implements ActionListener {

    // EDT-confined single-window state; an AtomicReference so the write
    // is a method call, not a static-field assignment from an instance
    // method (and harmless if ever touched off the EDT)
    private static final java.util.concurrent.atomic.AtomicReference<AgentPort> RUNNING =
            new java.util.concurrent.atomic.AtomicReference<>();

    /**
     * The token as the dialog shows it: the real one, except under the docs
     * forge ({@code nmox.shots.dir}, v2.84.0), where the placeholder stands
     * in — a screenshot is a file that outlives the port, and a secret in a
     * file is a secret leaked, dead or not.
     */
    /** The disclosure label's wrap width — under the 0.8-screen clamp of every laptop the product supports. */
    static final int LABEL_WIDTH = 720;

    /**
     * The dialog's disclosure, as HTML that WRAPS (the second walk's find): a
     * bare {@code <html>} label lays its whole text on one line, and once
     * that line outgrew the 0.8-screen clamp DialogFit put the dialog in a
     * scroll pane with the disclosure clipped mid-word — the one sentence
     * that must never be clipped. A width-bounded body wraps it.
     */
    static String disclosureHtml(int port, String tools) {
        // UNITLESS on purpose: Swing's CSS reads "width: 720" and ignores
        // "width: 720px" for a body (probed headless — 935 px one-line vs 720
        // wrapped); a units-bearing value would silently restore the bug
        return "<html><body style='width: " + LABEL_WIDTH + "'><b>The Agent Port is listening on "
                + "127.0.0.1:" + port + "</b> — loopback only.<br><br>"
                + "Any program holding the token below can READ, and only read: "
                + "<i>" + tools + "</i> — "
                + "and be told when a run starts, a server goes live, or a file you edit changes, "
                + "and hear a run's own output at the level it asks for.<br>"
                + "Nothing it says can run a command or change a file. "
                + "Paste this into a .mcp.json to connect an agent:</body></html>";
    }

    static String shownToken(AgentPort port) {
        return System.getProperty("nmox.shots.dir") != null ? "TOKEN" : port.token();
    }

    /** The listening port, its attached stream count, and the seconds since the last authorized request (-1: none yet), or null while nothing listens (v2.84.0/v2.85.0, the status-line chip). */
    public static int[] listening() {
        AgentPort p = RUNNING.get();
        if (p == null) {
            return null;
        }
        long since = p.sinceLastRequestMillis();
        return new int[]{p.port(), p.attachedStreams(), since < 0 ? -1 : (int) Math.min(Integer.MAX_VALUE, since / 1000)};
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (RUNNING.get() != null) {
            showRunning();
            return;
        }
        McpTools tools = McpTools.production();
        AgentPort port;
        try {
            port = AgentPort.start(tools, productVersion());
            // the FIRST STEPS record (v2.84.0): the port was started once —
            // a userdir preference, the Getting Started column's own idiom;
            // it never carries the token
            org.openide.util.NbPreferences.forModule(AgentPortAction.class)
                    .putBoolean("agentport.started", true);
        } catch (IOException ex) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    org.nmox.studio.core.util.PlainDialogs.plain("The Agent Port could not start: " + ex.getMessage(), "Message")));
            return;
        }
        RUNNING.set(port);
        StatusDisplayer.getDefault().setStatusText(
                "Agent Port listening on 127.0.0.1:" + port.port());
        showRunning();
    }

    /** The running product version, or "dev" — through the one reader (core.util.ProductVersion). */
    private static String productVersion() {
        String cur = org.nmox.studio.core.util.ProductVersion.current();
        return cur == null ? "dev" : cur;
    }

    private void showRunning() {
        AgentPort port = RUNNING.get();
        if (port == null) {
            return;
        }
        String snippet = """
                {
                  "mcpServers": {
                    "nmox-studio": {
                      "type": "http",
                      "url": "%s",
                      "headers": { "Authorization": "Bearer %s" }
                    }
                  }
                }""".formatted(port.url(), shownToken(port));
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel(disclosureHtml(port.port(), McpProtocol.disclosure(McpTools.production()))),
                BorderLayout.NORTH);
        JTextArea config = new JTextArea(snippet);
        config.setEditable(false);
        config.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,
                java.awt.Font.PLAIN, 12));
        config.getAccessibleContext().setAccessibleName("MCP client configuration");
        panel.add(new JScrollPane(config), BorderLayout.CENTER);
        JButton copy = new JButton("Copy Config");
        copy.addActionListener(ev -> {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(snippet), null);
            StatusDisplayer.getDefault().setStatusText("Agent Port config copied.");
        });
        JPanel south = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        south.add(copy);
        panel.add(south, BorderLayout.SOUTH);

        Object stopOption = "Stop Agent Port";
        Object close = "Close";
        DialogDescriptor descriptor = new DialogDescriptor(panel,
                "Agent Port (MCP)", true, new Object[]{close, stopOption},
                close, DialogDescriptor.DEFAULT_ALIGN, null, null);
        if (DialogDisplayer.getDefault().notify(descriptor) == stopOption) {
            port.stop();
            RUNNING.set(null);
            StatusDisplayer.getDefault().setStatusText(
                    "Agent Port stopped — nothing is listening.");
        }
    }
}
