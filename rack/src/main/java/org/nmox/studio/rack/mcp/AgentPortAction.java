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
    static String shownToken(AgentPort port) {
        return System.getProperty("nmox.shots.dir") != null ? "TOKEN" : port.token();
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
                    "The Agent Port could not start: " + ex.getMessage()));
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
        panel.add(new JLabel("<html><b>The Agent Port is listening on "
                + "127.0.0.1:" + port.port() + "</b> — loopback only.<br><br>"
                + "Any program holding the token below can READ, and only read: "
                + "<i>" + McpProtocol.disclosure(McpTools.production()) + "</i>.<br>"
                + "Nothing it says can run a command or change a file. "
                + "Paste this into a .mcp.json to connect an agent:</html>"),
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
