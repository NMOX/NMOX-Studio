package org.nmox.studio.ui.options;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;

/**
 * Tools → Options → NMOX Studio → General: the handful of app-wide toggles
 * that don't belong to any one subsystem. Today that's a single switch for
 * the once-daily update check, reading and writing the same shared
 * {@code nmox/ui} preference node the {@link org.nmox.studio.ui.UpdateCheck}
 * startup task gates on.
 */
@OptionsPanelController.SubRegistration(
        location = "NmoxStudio",
        displayName = "#GeneralOptions_DisplayName",
        keywords = "#GeneralOptions_Keywords",
        keywordsCategory = "NmoxStudio/General",
        position = 10
)
@org.openide.util.NbBundle.Messages({
    "GeneralOptions_DisplayName=General",
    "GeneralOptions_Keywords=update check startup"
})
public class GeneralOptionsPanelController extends OptionsPanelController {

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private JPanel panel;
    private JCheckBox updateCheck;

    /** The shared node the update-check startup task gates on. */
    private static java.util.prefs.Preferences prefs() {
        return NbPreferences.root().node("nmox/ui");
    }

    @Override
    public void update() {
        getPanel();
        updateCheck.setSelected(prefs().getBoolean("updateCheck", true));
    }

    @Override
    public void applyChanges() {
        prefs().putBoolean("updateCheck", updateCheck.isSelected());
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
        return updateCheck.isSelected() != prefs().getBoolean("updateCheck", true);
    }

    @Override
    public JComponent getComponent(Lookup masterLookup) {
        return getPanel();
    }

    private JPanel getPanel() {
        if (panel != null) {
            return panel;
        }
        panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 8, 4, 8);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;

        updateCheck = new JCheckBox("Check for updates on startup (once daily)");
        panel.add(updateCheck, c);
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
