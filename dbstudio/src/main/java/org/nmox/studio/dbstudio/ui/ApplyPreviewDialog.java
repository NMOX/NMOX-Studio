package org.nmox.studio.dbstudio.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;

/**
 * The modal "here is exactly what Apply will run" preview: the UPDATE
 * statements verbatim in a read-only monospaced area, a one-line
 * summary, and Apply/Cancel. Nothing executes unless the user chooses
 * Apply — the SQL shown is the SQL run, no rewriting in between.
 *
 * <p>Thin by design (JaCoCo-excluded as a *Dialog): the statements
 * were built and validated by the tested {@code EditSession}/
 * {@code UpdateBuilder} pair before this dialog ever opens.
 */
final class ApplyPreviewDialog {

    private ApplyPreviewDialog() {
    }

    /**
     * Shows the preview. Returns true only when the user chose Apply.
     */
    static boolean confirm(List<String> statements, int rowCount, String tableName) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));

        JLabel summary = new JLabel(statements.size()
                + (statements.size() == 1 ? " UPDATE statement" : " UPDATE statements")
                + " · " + rowCount + (rowCount == 1 ? " row" : " rows")
                + " of " + tableName);
        summary.setFont(summary.getFont().deriveFont(Font.BOLD));
        panel.add(summary, BorderLayout.NORTH);

        JTextArea sql = new JTextArea(String.join("\n", statements));
        sql.setEditable(false);
        sql.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        sql.setLineWrap(false);
        JScrollPane scroll = new JScrollPane(sql);
        scroll.setPreferredSize(new java.awt.Dimension(560, Math.min(300, 60 + statements.size() * 18)));
        panel.add(scroll, BorderLayout.CENTER);

        panel.add(new JLabel("<html><small>Statements run in order; on the first failure the "
                + "rest stay unrun and your edits are kept.</small></html>"),
                BorderLayout.SOUTH);

        Object applyOption = "Apply";
        DialogDescriptor descriptor = new DialogDescriptor(panel, "Apply Edits", true,
                new Object[]{applyOption, NotifyDescriptor.CANCEL_OPTION},
                applyOption, DialogDescriptor.DEFAULT_ALIGN, null, null);
        return DialogDisplayer.getDefault().notify(descriptor) == applyOption;
    }
}
