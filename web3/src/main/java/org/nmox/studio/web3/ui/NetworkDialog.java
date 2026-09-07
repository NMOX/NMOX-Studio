package org.nmox.studio.web3.ui;

import org.nmox.studio.core.util.PlainText;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.nmox.studio.web3.engine.JsonRpcClient;
import org.nmox.studio.web3.model.Network;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;

/**
 * The Add Network dialog: name, RPC URL, chain id (with a Detect button
 * that asks the node via {@code eth_chainId}, off the EDT), and the
 * secret checkbox.
 *
 * <p><b>The security boundary, UI edition:</b> when "URL contains a
 * secret" is checked, the URL leaves this dialog as a {@code char[]}
 * headed for the OS keyring ({@code RpcSecrets}) and the persisted
 * {@link Network} carries {@code plainUrl == null} — the workspace file
 * never sees it. There is no private-key field here and never will be.
 */
@org.openide.util.NbBundle.Messages({
    "NetworkDialog_title=Add Network",
    "NetworkDialog_detect=Detect",
    "NetworkDialog_secretCheck=URL contains a secret (store in Keyring)",
    "NetworkDialog_nameA11y=Network name",
    "NetworkDialog_urlA11y=RPC URL",
    "NetworkDialog_chainIdA11y=Chain id",
    "NetworkDialog_presetA11y=Network preset",
    "NetworkDialog_presetTip=Fill the fields from a known public gateway "
        + "\u2014 read-only engagement, no keys, still editable",
    "NetworkDialog_presetNote=Public gateway \u2014 reads work with no keys; "
        + "sends need a devnet or your own wallet",
    "NetworkDialog_presetCustom=(custom)",
    "NetworkDialog_presetMainnet=Ethereum Mainnet \u2014 public gateway",
    "NetworkDialog_presetSepolia=Sepolia testnet \u2014 public gateway",
    "NetworkDialog_presetAnvil8546=Local anvil on 8546",
    "NetworkDialog_rowPreset=Preset:",
    "NetworkDialog_rowName=Name:",
    "NetworkDialog_rowUrl=RPC URL:",
    "NetworkDialog_rowChainId=Chain id:",
    "NetworkDialog_detectTip=Ask the node (eth_chainId) and fill this in",
    "NetworkDialog_secretTip=The URL goes to the OS keychain only \u2014 "
        + ".nmoxweb3.json will carry no url field for this network",
    "NetworkDialog_enterUrlFirst=Enter the RPC URL first.",
    "NetworkDialog_asking=Asking the node\u2026",
    "NetworkDialog_detected=The node reports chain {0}.",
    "NetworkDialog_needName=Every network needs a name.",
    "NetworkDialog_nameTaken=A network named \"{0}\" already exists.",
    "NetworkDialog_needUrl=Enter the node's RPC URL, like http://127.0.0.1:8545.",
    "NetworkDialog_needHttpUrl=The RPC URL must be an http(s) endpoint with a host.",
    "NetworkDialog_chainIdPositive=The chain id must be a positive number \u2014 Detect asks the node.",
    "NetworkDialog_chainIdWhole=The chain id must be a whole number \u2014 Detect asks the node."
})
final class NetworkDialog extends JPanel {

    private static final Color OK_GREEN = new Color(0x4E, 0xC9, 0x8B);
    private static final Color FAIL_RED = new Color(0xE2, 0x4B, 0x4A);

    /**
     * What the dialog hands back: the network to add, plus — only when
     * {@code network.secretUrl()} — the URL destined for the keyring.
     * The caller stores it via {@code RpcSecrets.save} off the EDT and
     * wipes the array.
     */
    record Result(Network network, char[] secretUrl) {
    }

    private final JTextField nameField = new JTextField(22);
    private final JTextField urlField = new JTextField("http://127.0.0.1:8545", 22);
    private final JTextField chainIdField = new JTextField(8);
    private final JButton detectButton = new JButton(Bundle.NetworkDialog_detect());
    private final JCheckBox secretCheck =
            new JCheckBox(Bundle.NetworkDialog_secretCheck());
    private final JLabel noteLabel = new JLabel(" ");

    /**
     * Public-gateway presets (v2.45.0, the definitive-engagement arc):
     * one click from localhost to the real chains, read-only by the
     * product's own law — no keys means presets can never spend. The
     * URLs are keyless public gateways; the walk proves each answers
     * its chainId before a release ships them. Fields stay editable —
     * a preset is a starting point, not a lock.
     */
    private record Preset(String label, String name, String url, int chainId) {

        @Override
        public String toString() {
            return label;
        }
    }

    private static final Preset[] PRESETS = {
        new Preset(Bundle.NetworkDialog_presetCustom(), "", "", 0),
        new Preset(Bundle.NetworkDialog_presetMainnet(),
                "Ethereum Mainnet", "https://ethereum-rpc.publicnode.com", 1),
        new Preset(Bundle.NetworkDialog_presetSepolia(),
                "Sepolia", "https://ethereum-sepolia-rpc.publicnode.com", 11155111),
        new Preset(Bundle.NetworkDialog_presetAnvil8546(),
                "Anvil 8546", "http://127.0.0.1:8546", 31337),
    };

    private final javax.swing.JComboBox<Preset> presetCombo =
            new javax.swing.JComboBox<>(PRESETS);

    private NetworkDialog() {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        nameField.getAccessibleContext().setAccessibleName(Bundle.NetworkDialog_nameA11y());
        urlField.getAccessibleContext().setAccessibleName(Bundle.NetworkDialog_urlA11y());
        chainIdField.getAccessibleContext().setAccessibleName(Bundle.NetworkDialog_chainIdA11y());

        presetCombo.getAccessibleContext().setAccessibleName(Bundle.NetworkDialog_presetA11y());
        presetCombo.setToolTipText(Bundle.NetworkDialog_presetTip());
        presetCombo.addActionListener(e -> {
            Preset preset = (Preset) presetCombo.getSelectedItem();
            if (preset != null && preset.chainId() != 0) {
                nameField.setText(preset.name());
                urlField.setText(preset.url());
                chainIdField.setText(String.valueOf(preset.chainId()));
                noteLabel.setForeground(OK_GREEN);
                noteLabel.setText(Bundle.NetworkDialog_presetNote());
            }
        });

        JPanel grid = new JPanel(new GridBagLayout());
        addRow(grid, 0, Bundle.NetworkDialog_rowPreset(), presetCombo);
        addRow(grid, 1, Bundle.NetworkDialog_rowName(), nameField);
        addRow(grid, 2, Bundle.NetworkDialog_rowUrl(), urlField);
        JPanel chainRow = new JPanel(new BorderLayout(6, 0));
        chainRow.add(chainIdField, BorderLayout.CENTER);
        detectButton.setToolTipText(Bundle.NetworkDialog_detectTip());
        detectButton.addActionListener(e -> detect());
        chainRow.add(detectButton, BorderLayout.EAST);
        addRow(grid, 3, Bundle.NetworkDialog_rowChainId(), chainRow);
        secretCheck.setToolTipText(Bundle.NetworkDialog_secretTip());
        addRow(grid, 4, "", secretCheck);
        add(grid, BorderLayout.CENTER);

        noteLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        add(noteLabel, BorderLayout.SOUTH);
    }

    /**
     * Shows the dialog; returns null on cancel. Validation loops until
     * the fields commit or the user gives up (the ConnectionDialog
     * idiom).
     *
     * @param takenNames existing network names, matched case-insensitively
     */
    static Result show(Set<String> takenNames) {
        NetworkDialog panel = new NetworkDialog();
        DialogDescriptor descriptor = new DialogDescriptor(panel, Bundle.NetworkDialog_title());
        while (true) {
            if (DialogDisplayer.getDefault().notify(descriptor)
                    != NotifyDescriptor.OK_OPTION) {
                return null;
            }
            String problem = panel.validateFields(takenNames);
            if (problem == null) {
                return panel.commit();
            }
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    org.nmox.studio.core.util.PlainDialogs.plain(problem, "Message"), NotifyDescriptor.WARNING_MESSAGE));
        }
    }

    // ---- detect (eth_chainId, off-EDT) ----------------------------------

    private void detect() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            note(Bundle.NetworkDialog_enterUrlFirst(), FAIL_RED);
            return;
        }
        detectButton.setEnabled(false);
        note(Bundle.NetworkDialog_asking(), Color.GRAY);
        Web3StudioTopComponent.RP.post(() -> {
            try {
                long chainId = new JsonRpcClient(url).chainId();
                SwingUtilities.invokeLater(() -> {
                    chainIdField.setText(String.valueOf(chainId));
                    note(Bundle.NetworkDialog_detected(String.valueOf(chainId)), OK_GREEN);
                    detectButton.setEnabled(true);
                });
            } catch (IOException | RuntimeException unreachable) {
                String message = unreachable.getMessage() == null
                        ? unreachable.getClass().getSimpleName()
                        : unreachable.getMessage();
                SwingUtilities.invokeLater(() -> {
                    note(message, FAIL_RED);
                    detectButton.setEnabled(true);
                });
            }
        });
    }

    private void note(String text, Color color) {
        noteLabel.setForeground(color);
        noteLabel.setText(PlainText.plain(text));
    }

    // ---- validate + commit ------------------------------------------------

    private String validateFields(Set<String> takenNames) {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            return Bundle.NetworkDialog_needName();
        }
        if (takenNames != null
                && takenNames.contains(name.toLowerCase(Locale.ROOT))) {
            return Bundle.NetworkDialog_nameTaken(name);
        }
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            return Bundle.NetworkDialog_needUrl();
        }
        if (!isHttpUrl(url)) {
            return Bundle.NetworkDialog_needHttpUrl();
        }
        String chainText = chainIdField.getText().trim();
        try {
            if (Integer.parseInt(chainText) <= 0) {
                return Bundle.NetworkDialog_chainIdPositive();
            }
        } catch (NumberFormatException notANumber) {
            return Bundle.NetworkDialog_chainIdWhole();
        }
        return null;
    }

    private static boolean isHttpUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? ""
                    : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https"))
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (RuntimeException unparseable) {
            return false;
        }
    }

    private Result commit() {
        String name = nameField.getText().trim();
        String url = urlField.getText().trim();
        int chainId = Integer.parseInt(chainIdField.getText().trim());
        if (secretCheck.isSelected()) {
            return new Result(new Network(name, chainId, true, null),
                    url.toCharArray());
        }
        return new Result(new Network(name, chainId, false, url), null);
    }

    // ---- layout helper -------------------------------------------------------

    private static void addRow(JPanel panel, int row, String label, Component field) {
        GridBagConstraints l = new GridBagConstraints();
        l.gridx = 0;
        l.gridy = row;
        l.anchor = GridBagConstraints.EAST;
        l.insets = new Insets(3, 0, 3, 8);
        panel.add(new JLabel(PlainText.plain(label)), l);
        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 1;
        f.gridy = row;
        f.anchor = GridBagConstraints.WEST;
        f.fill = GridBagConstraints.HORIZONTAL;
        f.weightx = 1;
        f.insets = new Insets(3, 0, 3, 0);
        panel.add(field, f);
    }
}
