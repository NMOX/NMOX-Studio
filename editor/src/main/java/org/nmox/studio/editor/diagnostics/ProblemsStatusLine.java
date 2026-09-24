package org.nmox.studio.editor.diagnostics;

import java.awt.Component;
import java.util.Collection;
import java.util.List;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.nmox.studio.rack.engine.DiagnosticsBus;
import org.openide.awt.Actions;
import org.openide.awt.StatusLineElementProvider;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;

/**
 * The count a VS Code switcher looks for at the bottom of the window
 * (3.1.0): {@code ✕ 2  ⚠ 1} on the status line while anything the IDE
 * checks has an error or a warning, and a click opens Action Items, where
 * each one is a row that goes to its line. Nothing shows when there is
 * nothing to fix.
 *
 * <p>It counts what the {@link DiagnosticsBus} holds: every language
 * server (through {@code DiagnosticsTap}), the rack's quality tools, Check
 * Translations and the Browser's page errors, across every project.
 * Action Items' own footer counts the scope chosen in that window, so the
 * two can differ; the tooltip says what this one counts.
 *
 * <p>It listens only while it is in the status bar, and repaints on the
 * EDT, coalesced: a chatty server publishes many batches a second.
 */
@ServiceProvider(service = StatusLineElementProvider.class, position = 595)
@Messages({
    "# {0} - errors",
    "# {1} - warnings",
    "ProblemsStatusLine_text=✕ {0}  ⚠ {1}",
    "# {0} - errors",
    "# {1} - warnings",
    "ProblemsStatusLine_tooltip=Errors: {0} · Warnings: {1}, from the language servers and the IDE’s tools across every open project. Click to open Action Items.",
    "ProblemsStatusLine_name=Problems"
})
public final class ProblemsStatusLine implements StatusLineElementProvider {

    /** The platform's own Action Items action, the Window ▸ Action Items row. */
    static final String ACTION_CATEGORY = "Window";
    static final String ACTION_ID = "org.netbeans.modules.tasklist.ui.TaskListAction";

    @Override
    public Component getStatusLineElement() {
        return new Chip();
    }

    /** {errors, warnings} across every tool's current batch. Pure. */
    static int[] count(Collection<List<DiagnosticsBus.Problem>> batches) {
        int errors = 0;
        int warnings = 0;
        for (List<DiagnosticsBus.Problem> batch : batches) {
            for (DiagnosticsBus.Problem p : batch) {
                if (p.error()) {
                    errors++;
                } else {
                    warnings++;
                }
            }
        }
        return new int[]{errors, warnings};
    }

    /** The chip's text, or null when there is nothing to fix. Pure. */
    static String text(int[] counts) {
        if (counts[0] == 0 && counts[1] == 0) {
            return null;
        }
        return Bundle.ProblemsStatusLine_text(String.valueOf(counts[0]), String.valueOf(counts[1]));
    }

    static final class Chip extends JLabel {

        private final DiagnosticsBus.Listener listener = (tool, problems) -> schedule();
        private boolean scheduled;

        Chip() {
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            getAccessibleContext().setAccessibleName(Bundle.ProblemsStatusLine_name());
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    open();
                }
            });
            setVisible(false);
        }

        @Override
        public void addNotify() {
            super.addNotify();
            DiagnosticsBus.addListener(listener);
            refresh();
        }

        @Override
        public void removeNotify() {
            DiagnosticsBus.removeListener(listener);
            super.removeNotify();
        }

        private void schedule() {
            synchronized (this) {
                if (scheduled) {
                    return;
                }
                scheduled = true;
            }
            SwingUtilities.invokeLater(() -> {
                synchronized (this) {
                    scheduled = false;
                }
                refresh();
            });
        }

        void refresh() {
            int[] counts = count(DiagnosticsBus.all().values());
            String t = text(counts);
            setVisible(t != null);
            setText(t == null ? "" : t);
            // the tooltip is our own sentence around two numbers; plain() keeps
            // the gate's law that no tooltip can start as markup
            setToolTipText(org.nmox.studio.core.util.PlainText.plain(t == null ? null
                    : Bundle.ProblemsStatusLine_tooltip(String.valueOf(counts[0]), String.valueOf(counts[1]))));
            getAccessibleContext().setAccessibleDescription(getToolTipText());
        }

        private static void open() {
            Action a = Actions.forID(ACTION_CATEGORY, ACTION_ID);
            if (a != null) {
                a.actionPerformed(new java.awt.event.ActionEvent(new Object(),
                        java.awt.event.ActionEvent.ACTION_PERFORMED, ""));
            }
        }
    }
}
