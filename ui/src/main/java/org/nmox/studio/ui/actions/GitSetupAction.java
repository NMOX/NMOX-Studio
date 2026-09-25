package org.nmox.studio.ui.actions;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.nmox.studio.core.process.ProcessSupport;
import org.nmox.studio.core.process.ToolLocator;
import org.nmox.studio.core.util.PlainStatus;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Team ▸ Use NMOX Studio with Git… (3.2.0): the door to {@code nmox -w} and
 * {@code nmox -d} for someone who will never read {@code nmox --help}.
 *
 * <p>The dialog shows the three global settings ({@link GitSetup}) with the
 * value each has NOW, so nothing is replaced unseen, and offers them two
 * ways: Copy Commands puts the {@code git config} lines on the clipboard, and
 * Apply runs them. Close is the default button: this writes the user's global
 * git configuration, which a stray Enter must not do. Everything runs the
 * user's own {@code git} with fixed words and a value from this class, off
 * the EDT; nothing a project controls is executed. When {@code nmox} is not
 * on the PATH, Apply is not offered and the dialog says where the PATH
 * setup lives, because a setting naming a command git cannot find would
 * break every commit.
 */
@ActionID(category = "Team", id = "org.nmox.studio.ui.actions.GitSetupAction")
@ActionRegistration(displayName = "#CTL_GitSetupAction")
@ActionReference(path = "Menu/Versioning", position = 1990, separatorBefore = 1985)
@Messages({
    "CTL_GitSetupAction=Use NMOX Studio with Git…",
    "GitSetupAction_title=Use NMOX Studio with Git",
    "GitSetupAction_intro=These global git settings make git open commit messages, rebase plans and merge messages in NMOX Studio, and show git difftool's comparisons in its diff view. Close the tab to hand the file back to git.",
    "GitSetupAction_notOnPath=The nmox command is not on your PATH, so git could not start it. The user guide shows how to put it there; then open this again.",
    "GitSetupAction_noGit=Git was not found on this computer.",
    "GitSetupAction_alreadySet=Git already uses NMOX Studio: every setting below has the value shown.",
    "GitSetupAction_settingsName=The settings and their current values",
    "# {0} - the current value",
    "GitSetupAction_now=now: {0}",
    "GitSetupAction_unset=now: not set",
    "GitSetupAction_apply=Apply",
    "GitSetupAction_copy=Copy Commands",
    "GitSetupAction_close=Close",
    "GitSetupAction_copied=The git config commands are on the clipboard",
    "GitSetupAction_applied=Git now opens its messages and comparisons in NMOX Studio",
    "# {0} - the setting, {1} - git's own words",
    "GitSetupAction_failed=Git refused to set {0}: {1}"
})
public final class GitSetupAction implements ActionListener {

    private static final RequestProcessor RP = new RequestProcessor("nmox-git-setup", 1);
    private static final Duration LEASH = Duration.ofSeconds(10);

    @Override
    public void actionPerformed(ActionEvent e) {
        RP.post(() -> {
            boolean git = new File(ToolLocator.resolve("git")).isAbsolute();
            boolean nmox = new File(ToolLocator.resolve("nmox")).isAbsolute();
            Map<String, String> current = new LinkedHashMap<>();
            if (git) {
                for (String key : GitSetup.settings().keySet()) {
                    ProcessSupport.BoundedResult r = run(GitSetup.getCommand(key));
                    current.put(key, r != null && r.exitCode() == 0 ? r.stdout().strip() : null);
                }
            }
            SwingUtilities.invokeLater(() -> show(git, nmox, current));
        });
    }

    /** True when every setting already has the value this dialog would give it. */
    static boolean alreadySet(Map<String, String> current) {
        for (Map.Entry<String, String> s : GitSetup.settings().entrySet()) {
            if (!s.getValue().equals(current.get(s.getKey()))) {
                return false;
            }
        }
        return true;
    }

    /** The settings, one per line, with what each is now. */
    static String describe(Map<String, String> current) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> s : GitSetup.settings().entrySet()) {
            String now = current.get(s.getKey());
            b.append(s.getKey()).append(" = ").append(s.getValue()).append('\n')
                    .append("    ").append(now == null || now.isEmpty()
                            ? Bundle.GitSetupAction_unset() : Bundle.GitSetupAction_now(now)).append('\n');
        }
        return b.toString();
    }

    private void show(boolean git, boolean nmox, Map<String, String> current) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        boolean done = git && alreadySet(current);
        JTextArea intro = new JTextArea(!git ? Bundle.GitSetupAction_noGit()
                : Bundle.GitSetupAction_intro() + (done ? "\n\n" + Bundle.GitSetupAction_alreadySet()
                        : nmox ? "" : "\n\n" + Bundle.GitSetupAction_notOnPath()));
        intro.setEditable(false);
        intro.setLineWrap(true);
        intro.setWrapStyleWord(true);
        intro.setOpaque(false);
        intro.setColumns(56);
        intro.getAccessibleContext().setAccessibleName(Bundle.GitSetupAction_title());
        panel.add(intro, BorderLayout.NORTH);
        JTextArea settings = org.nmox.studio.core.util.TextDirection.keepLeftToRight(new JTextArea(describe(current)));
        settings.setEditable(false);
        settings.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        settings.getAccessibleContext().setAccessibleName(Bundle.GitSetupAction_settingsName());
        panel.add(new JScrollPane(settings), BorderLayout.CENTER);

        Object apply = Bundle.GitSetupAction_apply();
        Object copy = Bundle.GitSetupAction_copy();
        Object close = Bundle.GitSetupAction_close();
        Object[] options = git && nmox && !done ? new Object[] {apply, copy, close} : new Object[] {copy, close};
        DialogDescriptor d = new DialogDescriptor(panel, Bundle.GitSetupAction_title(), true, options,
                close, DialogDescriptor.DEFAULT_ALIGN, null, null);
        Object chosen = DialogDisplayer.getDefault().notify(d);
        if (copy.equals(chosen)) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(String.join("\n", GitSetup.shellLines()) + "\n"), null);
            StatusDisplayer.getDefault().setStatusText(Bundle.GitSetupAction_copied());
        } else if (apply.equals(chosen)) {
            RP.post(GitSetupAction::apply);
        }
    }

    /** Sets each setting in order; the first refusal stops and speaks. Off the EDT. */
    static void apply() {
        for (Map.Entry<String, String> s : GitSetup.settings().entrySet()) {
            List<String> argv = GitSetup.setCommand(s.getKey(), s.getValue());
            ProcessSupport.BoundedResult r = run(argv);
            if (r == null || r.exitCode() != 0) {
                String why = r == null ? Bundle.GitSetupAction_noGit()
                        : (r.stderr().isBlank() ? r.stdout() : r.stderr()).strip();
                status(Bundle.GitSetupAction_failed(s.getKey(), why));
                return;
            }
        }
        status(Bundle.GitSetupAction_applied());
    }

    /** The user's own git with fixed words, bounded; null when it could not start. */
    private static ProcessSupport.BoundedResult run(List<String> argv) {
        try {
            return ProcessSupport.runBounded(argv, null, LEASH);
        } catch (java.io.IOException ex) {
            return null;
        }
    }

    private static void status(String text) {
        SwingUtilities.invokeLater(() -> StatusDisplayer.getDefault().setStatusText(PlainStatus.text(text)));
    }
}
