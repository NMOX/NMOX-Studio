package org.nmox.studio.ui.report;

import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.nmox.studio.core.util.Versions;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.modules.Places;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Help ▸ Report a Problem… — a bug report with the right diagnostics and
 * nothing secret. The log tail is read OFF the EDT and bounded (the last
 * 64 KB of messages.log, then the last {@link ProblemReport#MAX_LOG_LINES}
 * lines), redacted (home path, login, credential shapes), and shown in an
 * EDITABLE dialog that says exactly what leaves: nothing, until the user
 * opens the pre-filled GitHub issue page and presses Submit there, or
 * copies the text. NMOX Studio sends no bytes anywhere.
 */
@ActionID(category = "Help", id = "org.nmox.studio.ui.report.ReportProblemAction")
@ActionRegistration(displayName = "#CTL_ReportProblemAction", lazy = true)
@ActionReference(path = "Menu/Help", position = 227)
@Messages("CTL_ReportProblemAction=Report a Problem…")
public final class ReportProblemAction implements ActionListener {

    private static final RequestProcessor RP = new RequestProcessor("Report a Problem", 1, true);
    private static final int LOG_READ_BYTES = 64 * 1024;

    @Override
    public void actionPerformed(ActionEvent e) {
        StatusDisplayer.getDefault().setStatusText("Gathering diagnostics…");
        RP.post(() -> {
            String tail = ProblemReport.redact(ProblemReport.tail(readLogTail(), ProblemReport.MAX_LOG_LINES),
                    System.getProperty("user.home"), System.getProperty("user.name"));
            String version = runningVersion();
            String os = System.getProperty("os.name") + " " + System.getProperty("os.version")
                    + " (" + System.getProperty("os.arch") + ")";
            String java = System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")";
            String body = ProblemReport.compose(version, os, java, tail, lastFailure());
            String title = "NMOX Studio " + (version == null ? "dev" : version) + ": ";
            SwingUtilities.invokeLater(() -> dialog(title, body));
        });
    }

    /**
     * The rack's last failed run this session, through the same bounded
     * FailureContext ORACLE explains (command, exit code, at most five
     * error lines, duration) — redacted like the log tail, since a
     * command line can carry a path or a token. Null when nothing failed.
     */
    static ProblemReport.LastFailure lastFailure() {
        try {
            org.nmox.studio.rack.service.RackService rs = org.nmox.studio.rack.service.RackService.getDefault();
            File dir = rs == null ? null : rs.getRack().getProjectDir();
            String project = dir == null ? "" : dir.getName();
            return org.nmox.studio.rack.engine.OracleClient.FailureContext
                    .fromRecorder(org.nmox.studio.rack.engine.FlightRecorder.getDefault(), project)
                    .map(f -> new ProblemReport.LastFailure(f.device(),
                            ProblemReport.redact(f.command(), System.getProperty("user.home"),
                                    System.getProperty("user.name")),
                            f.exitCode(),
                            f.errorLines().stream().map(l -> ProblemReport.redact(l,
                                    System.getProperty("user.home"), System.getProperty("user.name"))).toList(),
                            f.durationMs()))
                    .orElse(null);
        } catch (RuntimeException | LinkageError unavailable) {
            return null; // no rack in this session: the report simply has no failed run
        }
    }

    static String runningVersion() {
        try {
            String v = Versions.extract(java.util.ResourceBundle
                    .getBundle("org.netbeans.core.startup.Bundle").getString("currentVersion"));
            return Versions.isStamped(v) ? v : null;
        } catch (RuntimeException missing) {
            return null;
        }
    }

    /** The last 64 KB of the userdir's messages.log; empty when absent. */
    static String readLogTail() {
        File userdir = Places.getUserDirectory();
        if (userdir == null) {
            return "";
        }
        File log = new File(new File(new File(userdir, "var"), "log"), "messages.log");
        if (!log.isFile()) {
            return "";
        }
        try (RandomAccessFile raf = new RandomAccessFile(log, "r")) {
            long len = raf.length();
            int n = (int) Math.min(len, LOG_READ_BYTES);
            raf.seek(len - n);
            byte[] buf = new byte[n];
            raf.readFully(buf);
            String s = new String(buf, StandardCharsets.UTF_8);
            int nl = s.indexOf('\n');
            return n < len && nl >= 0 ? s.substring(nl + 1) : s; // drop a torn first line
        } catch (IOException ex) {
            return "";
        }
    }

    private static void dialog(String title, String body) {
        StatusDisplayer.getDefault().setStatusText("");
        JTextArea area = new JTextArea(body, 26, 88);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        area.getAccessibleContext().setAccessibleName("Problem report, editable");
        JPanel panel = new JPanel(new java.awt.BorderLayout(0, 6));
        JLabel note = new JLabel("This is the whole report. NMOX Studio sends nothing — Open on GitHub "
                + "pre-fills a new issue that you submit yourself; Copy puts the text on the clipboard.");
        panel.add(note, java.awt.BorderLayout.NORTH);
        panel.add(new JScrollPane(area), java.awt.BorderLayout.CENTER);
        Object open = "Open on GitHub";
        Object copy = "Copy";
        Object cancel = "Cancel";
        NotifyDescriptor nd = new NotifyDescriptor(panel, "Report a Problem", NotifyDescriptor.DEFAULT_OPTION,
                NotifyDescriptor.PLAIN_MESSAGE, new Object[]{open, copy, cancel}, cancel);
        Object answer = DialogDisplayer.getDefault().notify(nd);
        String text = area.getText();
        if (answer == copy) {
            copy(text);
            StatusDisplayer.getDefault().setStatusText("Report copied to the clipboard.");
        } else if (answer == open) {
            String url = ProblemReport.issueUrl(title, text);
            if (ProblemReport.clipped(url)) {
                copy(text);
                StatusDisplayer.getDefault().setStatusText(
                        "The log tail was clipped to fit the issue URL — the full report is on your clipboard.");
            }
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            } catch (Exception ex) {
                copy(text);
                StatusDisplayer.getDefault().setStatusText(
                        "No browser could be opened — the report is on your clipboard; paste it at "
                        + ProblemReport.NEW_ISSUE);
            }
        }
    }

    private static void copy(String text) {
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
    }
}
