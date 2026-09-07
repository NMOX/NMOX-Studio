package org.nmox.studio.dbstudio.ui;

import org.nmox.studio.core.util.PlainText;
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
import org.openide.util.NbBundle.Messages;

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
@Messages({
    // chrome (shift-2970): every user-visible English string of this dialog.
    // Counts are formatted {n,number,0} so MessageFormat never groups the
    // digits where the old concatenation printed them bare.
    "ApplyPreviewDialog_summary={0,choice,0#{0,number,0} UPDATE statements|1#{0,number,0} UPDATE statement|1<{0,number,0} UPDATE statements} \u00b7 {1,choice,0#{1,number,0} rows|1#{1,number,0} row|1<{1,number,0} rows} of {2}",
    "ApplyPreviewDialog_sqlA11y=SQL to apply",
    "ApplyPreviewDialog_note=<html><small>Statements run in order; on the first failure the rest stay unrun and your edits are kept.</small></html>",
    "ApplyPreviewDialog_apply=Apply",
    "ApplyPreviewDialog_title=Apply Edits"
})
final class ApplyPreviewDialog {

    private ApplyPreviewDialog() {
    }

    /**
     * Shows the preview. Returns true only when the user chose Apply.
     */
    static boolean confirm(List<String> statements, int rowCount, String tableName) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));

        JLabel summary = new JLabel(PlainText.plain(
                Bundle.ApplyPreviewDialog_summary(statements.size(), rowCount, tableName)));
        summary.setFont(summary.getFont().deriveFont(Font.BOLD));
        panel.add(summary, BorderLayout.NORTH);

        JTextArea sql = new JTextArea(String.join("\n", statements));
        sql.getAccessibleContext().setAccessibleName(Bundle.ApplyPreviewDialog_sqlA11y());
        sql.setEditable(false);
        sql.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        sql.setLineWrap(false);
        JScrollPane scroll = new JScrollPane(sql);
        scroll.setPreferredSize(new java.awt.Dimension(560, Math.min(300, 60 + statements.size() * 18)));
        panel.add(scroll, BorderLayout.CENTER);

        panel.add(new JLabel(Bundle.ApplyPreviewDialog_note()), BorderLayout.SOUTH);

        Object applyOption = Bundle.ApplyPreviewDialog_apply();
        // Cancel is the initialValue (the focused/default button), so a
        // reflexive Enter does NOT run the UPDATEs against the live DB —
        // the v1.98.0 dialog-safety law applied to the one destructive
        // dialog in the module.
        DialogDescriptor descriptor = new DialogDescriptor(panel, Bundle.ApplyPreviewDialog_title(), true,
                new Object[]{applyOption, NotifyDescriptor.CANCEL_OPTION},
                NotifyDescriptor.CANCEL_OPTION, DialogDescriptor.DEFAULT_ALIGN, null, null);
        return applyOption.equals(DialogDisplayer.getDefault().notify(descriptor));
    }
}
