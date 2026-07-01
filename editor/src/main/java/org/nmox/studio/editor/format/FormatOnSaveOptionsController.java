package org.nmox.studio.editor.format;

import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * Options → Editor → Format on Save: the one switch for the Prettier
 * save hook. The heavy lifting (project opt-in, binary resolution)
 * happens per save in {@link FormatOnSave}; this only flips the
 * IDE-wide preference.
 */
@NbBundle.Messages({
    "FormatOnSave_DisplayName=Format on Save",
    "FormatOnSave_Keywords=format save prettier formatter"
})
@OptionsPanelController.SubRegistration(
        location = "Editor",
        displayName = "#FormatOnSave_DisplayName",
        keywords = "#FormatOnSave_Keywords",
        keywordsCategory = "Editor/FormatOnSave"
)
public class FormatOnSaveOptionsController extends OptionsPanelController {

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private JPanel panel;
    private JCheckBox enabled;

    @Override
    public void update() {
        getPanel();
        enabled.setSelected(FormatOnSave.isEnabled());
    }

    @Override
    public void applyChanges() {
        FormatOnSave.setEnabled(enabled.isSelected());
    }

    @Override
    public void cancel() {
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean isChanged() {
        return enabled != null && enabled.isSelected() != FormatOnSave.isEnabled();
    }

    @Override
    public JComponent getComponent(Lookup masterLookup) {
        return getPanel();
    }

    private JPanel getPanel() {
        if (panel != null) {
            return panel;
        }
        enabled = new JCheckBox("Format files with Prettier on save");
        JLabel detail = new JLabel("<html><i>Runs only in projects that opted into Prettier"
                + " — a .prettierrc / prettier.config file or a package.json that mentions"
                + " prettier. The project's own node_modules/.bin/prettier is preferred"
                + " over a global install. Files that fail to format save unchanged.</i></html>");
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        enabled.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        detail.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        column.add(enabled);
        column.add(Box.createVerticalStrut(8));
        column.add(detail);
        panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(column, BorderLayout.NORTH);
        return panel;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }
}
