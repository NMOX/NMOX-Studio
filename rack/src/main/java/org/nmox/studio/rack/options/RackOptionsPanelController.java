package org.nmox.studio.rack.options;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;

/**
 * Tools → Options → NMOX Rack: the settings that used to hide in
 * scattered dialogs - cloud API tokens for the infra designer and the
 * REFLEX watcher's poll interval - in one honest panel.
 */
@OptionsPanelController.TopLevelRegistration(
        categoryName = "NMOX Rack",
        iconBase = "org/nmox/studio/rack/options/rack32.png",
        keywords = "nmox rack reflex token digitalocean hetzner cloudflare",
        keywordsCategory = "NmoxRack"
)
public class RackOptionsPanelController extends OptionsPanelController {

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private JPanel panel;
    private JSpinner reflexInterval;
    private JPasswordField doToken;
    private JPasswordField hetznerToken;
    private JPasswordField cloudflareToken;

    @Override
    public void update() {
        getPanel();
        reflexInterval.setValue(NbPreferences
                .forModule(org.nmox.studio.rack.devices.ReflexDevice.class)
                .getInt("reflexIntervalMs", 1200));
        doToken.setText("");
        hetznerToken.setText("");
        cloudflareToken.setText("");
    }

    @Override
    public void applyChanges() {
        NbPreferences.forModule(org.nmox.studio.rack.devices.ReflexDevice.class)
                .putInt("reflexIntervalMs", (Integer) reflexInterval.getValue());
        storeIfSet(doToken, "doToken");
        storeIfSet(hetznerToken, "hetznerToken");
        storeIfSet(cloudflareToken, "cloudflareToken");
    }

    private void storeIfSet(JPasswordField field, String key) {
        String value = new String(field.getPassword()).trim();
        if (!value.isEmpty()) {
            // the explicit node the infra designer's CloudProvider reads
            NbPreferences.root().node("nmox/cloud").put(key, value);
        }
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
        return true;
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

        reflexInterval = new JSpinner(new SpinnerNumberModel(1200, 200, 10_000, 100));
        doToken = new JPasswordField(28);
        hetznerToken = new JPasswordField(28);
        cloudflareToken = new JPasswordField(28);

        panel.add(new JLabel("REFLEX poll interval (ms):"), c);
        c.gridx = 1;
        panel.add(reflexInterval, c);

        c.gridx = 0;
        c.gridy++;
        panel.add(new JLabel("DigitalOcean API token:"), c);
        c.gridx = 1;
        panel.add(doToken, c);

        c.gridx = 0;
        c.gridy++;
        panel.add(new JLabel("Hetzner Cloud token:"), c);
        c.gridx = 1;
        panel.add(hetznerToken, c);

        c.gridx = 0;
        c.gridy++;
        panel.add(new JLabel("Cloudflare API token:"), c);
        c.gridx = 1;
        panel.add(cloudflareToken, c);

        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        panel.add(new JLabel("<html><i>Blank token fields keep the stored value. Env vars"
                + " (DIGITALOCEAN_TOKEN, HCLOUD_TOKEN, CLOUDFLARE_API_TOKEN) act as fallbacks."
                + "</i></html>"), c);
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
