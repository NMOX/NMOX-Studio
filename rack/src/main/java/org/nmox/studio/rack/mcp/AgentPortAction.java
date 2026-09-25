package org.nmox.studio.rack.mcp;

import org.nmox.studio.core.util.PlainText;
import java.awt.EventQueue;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import org.openide.util.RequestProcessor;

/**
 * Tools ▸ Agent Port… — the explicit gesture that is the Agent Port's
 * whole consent model: nothing listens until the user starts it here,
 * the dialog names EXACTLY what a token-holder can read (the tool
 * roster, by name, read-only), and the token exists only in this
 * dialog and the caller's config — never a log, never a file of ours.
 * Zero boot cost: a menu item. Stop is one click and the port dies
 * with the JVM regardless.
 *
 * <p>3.2, "connects once": two opt-in boxes in the running dialog.
 * <b>Keep this address and token</b> puts the token in the OS keychain
 * and the port in the module's preferences ({@link AgentPortKeep}), so
 * the next start reuses both and an agent configured once stays
 * connected; clearing it deletes the keychain entry. <b>Start when NMOX
 * Studio starts</b> (only while Keep is on) lets {@link AgentPortAutostart}
 * start the port once the window shows. Both default off: nothing
 * listens unless the user asked, and the dialog says what each box does
 * while it is ticked.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.rack.mcp.AgentPortAction")
@ActionRegistration(displayName = "#CTL_AgentPortAction", lazy = true)
@ActionReference(path = "Menu/Tools", position = 95)
@Messages({
    "CTL_AgentPortAction=Agent Port (MCP)…",
    "AgentPortAction_disclosure=<html><body style=''width: {0}''><b>The Agent Port is listening on 127.0.0.1:{1}</b> — loopback only.<br><br>Any program holding the token below can READ, and only read: <i>{2}</i> — and be told when a run starts, a server goes live, or a file you edit changes, and hear a run''s own output at the level it asks for.<br>Nothing it says can run a command or change a file. Paste this into a .mcp.json to connect an agent:</body></html>",
    "AgentPortAction_startFailed=The Agent Port could not start: {0}",
    "AgentPortAction_listening=Agent Port listening on 127.0.0.1:{0}",
    "AgentPortAction_configName=MCP client configuration",
    "AgentPortAction_copyConfig=Copy Config",
    "AgentPortAction_configCopied=Agent Port config copied.",
    "AgentPortAction_stop=Stop Agent Port",
    "AgentPortAction_close=Close",
    "AgentPortAction_title=Agent Port (MCP)",
    "AgentPortAction_stopped=Agent Port stopped — nothing is listening.",
    "AgentPortAction_copyClaude=Copy for Claude Code",
    "AgentPortAction_claudeCopied=Claude Code command copied — run it once in a terminal.",
    "AgentPortAction_claudeRefused=Not copied: the token holds a character a shell would change.",
    "AgentPortAction_keep=Keep this address and token",
    "AgentPortAction_autostart=Start when NMOX Studio starts",
    "AgentPortAction_keepNote=The token is kept in the system keychain until you clear the first box.",
    "AgentPortAction_autostartNote=The port also starts with NMOX Studio, so an agent can connect whenever NMOX Studio is open.",
    "AgentPortAction_kept=Agent Port address and token kept.",
    "AgentPortAction_keptSession=Keychain unavailable — the Agent Port token is kept for this session only.",
    "AgentPortAction_forgotten=Agent Port address and token forgotten — the keychain entry is deleted.",
    "AgentPortAction_moved=The kept Agent Port address was taken, so the port now listens on 127.0.0.1:{0} with a new token — give your agent the new address and token.",
    "AgentPortAction_newToken=The kept Agent Port token was not found, so the port has a new one — give your agent the new token.",
    "AgentPortAction_changedTitle=Your agent needs the Agent Port's new details"
})
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
        // wrapped); a units-bearing value would silently restore the bug.
        // The tool list is escaped rather than trusted, so the exemption at
        // the label below is true by construction and not by inspection.
        return Bundle.AgentPortAction_disclosure(String.valueOf(LABEL_WIDTH),
                String.valueOf(port), PlainText.escape(tools));
    }

    /**
     * The disclosure as the dialog builds it — extracted so the law can be
     * tested on the real label rather than on a string.
     *
     * <p>NOT {@code PlainText.plain}-guarded, and that is the whole point:
     * {@code plain} prepends a space, Swing then declines to parse the text
     * as HTML, and the v2.84.0 width-bounded body — the wrap this value
     * exists for — renders as a screenful of literal tags. The v2.86.0 sweep
     * wrote the exemption comment here AND applied the guard anyway, so the
     * comment and the code disagreed and the comment was right (the v1.189.0
     * law: a comment claiming a property the code does not have is a test
     * not yet written). This is that test's subject.
     */
    static JLabel disclosureLabel(int port, String tools) {
        // PLAIN-LABEL-EXEMPT: the disclosure MEANS its markup (the v2.84.0
        // width-bounded body); the one spliced string is escaped in
        // disclosureHtml and every other argument is an int
        return new JLabel(disclosureHtml(port, tools));
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


    /**
     * The one lane every start and every keep/forget rides (3.2): the
     * keychain may block on OS calls and a bind is socket work, so neither
     * happens on the EDT. Throughput 1 also serializes a double-click into
     * one start and a second look at the running port.
     */
    private static final RequestProcessor LANE = new RequestProcessor("nmox-agent-port", 1);

    /**
     * The command that points Claude Code at this port, once:
     * {@code claude mcp add --transport http nmox-studio <url> --header "Authorization: Bearer <token>"}.
     * Returns null — and the caller says so rather than copy — when the URL
     * is not this port's loopback shape or the token holds a character
     * outside {@code [A-Za-z0-9_-]}: the header value rides inside double
     * quotes, and only that alphabet passes through them unchanged by every
     * shell a user might paste into.
     */
    static String claudeCodeCommand(String url, String token) {
        if (url == null || !url.matches("http://127\\.0\\.0\\.1:[0-9]{1,5}/mcp")
                || !shellInert(token)) {
            return null;
        }
        return "claude mcp add --transport http nmox-studio " + url
                + " --header \"Authorization: Bearer " + token + "\"";
    }

    private static boolean shellInert(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /**
     * The honest sentence under the two boxes: what ticking them has done,
     * or nothing when neither is ticked. The keychain half is said whenever
     * Keep is on; the start-with-the-IDE half only when both are.
     */
    static String keepNote(boolean keep, boolean autostart) {
        if (!keep) {
            return "";
        }
        return autostart
                ? Bundle.AgentPortAction_keepNote() + " " + Bundle.AgentPortAction_autostartNote()
                : Bundle.AgentPortAction_keepNote();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (RUNNING.get() != null) {
            showRunning();
            return;
        }
        start(AgentPortKeep.production(), true);
    }

    /**
     * Starts the port on the lane and applies the outcome on the EDT: the
     * listening sentence, any moved-address or new-token sentence, and —
     * for the menu gesture, not the autostart — the dialog.
     */
    static void start(AgentPortKeep keep, boolean showDialog) {
        LANE.post(() -> {
            AgentPort running = RUNNING.get();
            if (running != null) {
                if (showDialog) {
                    EventQueue.invokeLater(() -> new AgentPortAction().showRunning());
                }
                return;
            }
            AgentPortKeep.Started started;
            try {
                started = keep.start(McpTools.production(), productVersion());
            } catch (IOException ex) {
                String why = ex.getMessage();
                EventQueue.invokeLater(() -> DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                        org.nmox.studio.core.util.PlainDialogs.plain(Bundle.AgentPortAction_startFailed(why), "Message"))));
                return;
            }
            // the FIRST STEPS record (v2.84.0) is written by keep.start, in
            // the same preferences node the Getting Started column reads
            RUNNING.set(started.port());
            EventQueue.invokeLater(() -> {
                // the address change is the sentence the user must act on, so it wins the line
                // a sentence the user must ACT on is also a notification: a
                // plain status line deletes itself after five seconds, and the
                // autostart says it during boot, when project-open traffic
                // replaces it (the v2.182.0 finding) — the bell keeps it
                if (started.moved()) {
                    String said = Bundle.AgentPortAction_moved(String.valueOf(started.port().port()));
                    StatusDisplayer.getDefault().setStatusText(said);
                    notifyChanged(said);
                } else if (started.newToken()) {
                    StatusDisplayer.getDefault().setStatusText(Bundle.AgentPortAction_newToken());
                    notifyChanged(Bundle.AgentPortAction_newToken());
                } else {
                    StatusDisplayer.getDefault().setStatusText(
                            Bundle.AgentPortAction_listening(String.valueOf(started.port().port())));
                }
                if (showDialog) {
                    new AgentPortAction().showRunning();
                }
            });
        });
    }

    private static final javax.swing.Icon CHANGED_ICON = changedIcon();

    /** The notification a changed address or token raises; a seam for tests. */
    static java.util.function.Consumer<String> changed = details ->
            org.openide.awt.NotificationDisplayer.getDefault().notify(Bundle.AgentPortAction_changedTitle(),
                    CHANGED_ICON, org.nmox.studio.core.util.PlainText.plain(details), null);

    private static javax.swing.Icon changedIcon() {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(240, 196, 25));
        g.fillOval(3, 3, 10, 10);
        g.dispose();
        return new javax.swing.ImageIcon(img);
    }

    private static void notifyChanged(String details) {
        changed.accept(details);
    }

    /** Waits for the lane to drain — tests only. */
    static void awaitLaneIdle() {
        LANE.post(() -> { }).waitFinished();
    }

    /** The running port, or null — tests only. */
    static AgentPort running() {
        return RUNNING.get();
    }

    /** Stops whatever runs — tests only; the dialog's Stop is the user's door. */
    static void stopForTest() {
        AgentPort p = RUNNING.getAndSet(null);
        if (p != null) {
            p.stop();
        }
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
        panel.add(disclosureLabel(port.port(), McpProtocol.disclosure(McpTools.production())),
                BorderLayout.NORTH);
        JTextArea config = org.nmox.studio.core.util.TextDirection.keepLeftToRight(new JTextArea(snippet));
        config.setEditable(false);
        config.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,
                java.awt.Font.PLAIN, 12));
        config.getAccessibleContext().setAccessibleName(Bundle.AgentPortAction_configName());
        panel.add(new JScrollPane(config), BorderLayout.CENTER);
        JButton copy = new JButton(Bundle.AgentPortAction_copyConfig());
        copy.addActionListener(ev -> {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(snippet), null);
            StatusDisplayer.getDefault().setStatusText(Bundle.AgentPortAction_configCopied());
        });
        JButton copyClaude = new JButton(Bundle.AgentPortAction_copyClaude());
        copyClaude.addActionListener(ev -> {
            String command = claudeCodeCommand(port.url(), shownToken(port));
            if (command == null) {
                StatusDisplayer.getDefault().setStatusText(Bundle.AgentPortAction_claudeRefused());
                return;
            }
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(command), null);
            StatusDisplayer.getDefault().setStatusText(Bundle.AgentPortAction_claudeCopied());
        });
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        buttons.add(copy);
        buttons.add(copyClaude);

        // the two opt-in boxes read their state from the preferences alone —
        // a preference read, no keychain, so the EDT never waits here
        AgentPortKeep keep = AgentPortKeep.production();
        JCheckBox keepBox = new JCheckBox(Bundle.AgentPortAction_keep(), keep.keeping());
        JCheckBox autostartBox = new JCheckBox(Bundle.AgentPortAction_autostart(), keep.autostart());
        autostartBox.setEnabled(keepBox.isSelected());
        JLabel note = new JLabel(PlainText.plain(keepNote(keepBox.isSelected(), autostartBox.isSelected())));
        Runnable renote = () -> note.setText(PlainText.plain(
                keepNote(keepBox.isSelected(), autostartBox.isSelected())));
        keepBox.addActionListener(ev -> {
            boolean on = keepBox.isSelected();
            autostartBox.setEnabled(on);
            if (!on) {
                autostartBox.setSelected(false);
            }
            renote.run();
            LANE.post(() -> {
                if (on) {
                    boolean durable = keep.keep(port);
                    EventQueue.invokeLater(() -> StatusDisplayer.getDefault().setStatusText(durable
                            ? Bundle.AgentPortAction_kept() : Bundle.AgentPortAction_keptSession()));
                } else {
                    keep.forget();
                    EventQueue.invokeLater(() -> StatusDisplayer.getDefault().setStatusText(
                            Bundle.AgentPortAction_forgotten()));
                }
            });
        });
        autostartBox.addActionListener(ev -> {
            boolean on = autostartBox.isSelected();
            renote.run();
            LANE.post(() -> keep.setAutostart(on));
        });
        JPanel south = new JPanel();
        south.setLayout(new javax.swing.BoxLayout(south, javax.swing.BoxLayout.Y_AXIS));
        for (javax.swing.JComponent c : new javax.swing.JComponent[]{buttons, keepBox, autostartBox, note}) {
            c.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            south.add(c);
        }
        panel.add(south, BorderLayout.SOUTH);

        Object stopOption = Bundle.AgentPortAction_stop();
        Object close = Bundle.AgentPortAction_close();
        DialogDescriptor descriptor = new DialogDescriptor(panel,
                Bundle.AgentPortAction_title(), true, new Object[]{close, stopOption},
                close, DialogDescriptor.DEFAULT_ALIGN, null, null);
        if (stopOption.equals(DialogDisplayer.getDefault().notify(descriptor))) {
            port.stop();
            RUNNING.set(null);
            StatusDisplayer.getDefault().setStatusText(
                    Bundle.AgentPortAction_stopped());
        }
    }
}
