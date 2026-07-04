package org.nmox.studio.web3.ui;

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
    private final JButton detectButton = new JButton("Detect");
    private final JCheckBox secretCheck =
            new JCheckBox("URL contains a secret (store in Keyring)");
    private final JLabel noteLabel = new JLabel(" ");

    private NetworkDialog() {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));

        JPanel grid = new JPanel(new GridBagLayout());
        addRow(grid, 0, "Name:", nameField);
        addRow(grid, 1, "RPC URL:", urlField);
        JPanel chainRow = new JPanel(new BorderLayout(6, 0));
        chainRow.add(chainIdField, BorderLayout.CENTER);
        detectButton.setToolTipText("Ask the node (eth_chainId) and fill this in");
        detectButton.addActionListener(e -> detect());
        chainRow.add(detectButton, BorderLayout.EAST);
        addRow(grid, 2, "Chain id:", chainRow);
        secretCheck.setToolTipText("The URL goes to the OS keychain only — "
                + ".nmoxweb3.json will carry no url field for this network");
        addRow(grid, 3, "", secretCheck);
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
        DialogDescriptor descriptor = new DialogDescriptor(panel, "Add Network");
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
                    problem, NotifyDescriptor.WARNING_MESSAGE));
        }
    }

    // ---- detect (eth_chainId, off-EDT) ----------------------------------

    private void detect() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            note("Enter the RPC URL first.", FAIL_RED);
            return;
        }
        detectButton.setEnabled(false);
        note("Asking the node…", Color.GRAY);
        Web3StudioTopComponent.RP.post(() -> {
            try {
                long chainId = new JsonRpcClient(url).chainId();
                SwingUtilities.invokeLater(() -> {
                    chainIdField.setText(String.valueOf(chainId));
                    note("The node reports chain " + chainId + ".", OK_GREEN);
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
        noteLabel.setText(text);
    }

    // ---- validate + commit ------------------------------------------------

    private String validateFields(Set<String> takenNames) {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            return "Every network needs a name.";
        }
        if (takenNames != null
                && takenNames.contains(name.toLowerCase(Locale.ROOT))) {
            return "A network named \"" + name + "\" already exists.";
        }
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            return "Enter the node's RPC URL, like http://127.0.0.1:8545.";
        }
        if (!isHttpUrl(url)) {
            return "The RPC URL must be an http(s) endpoint with a host.";
        }
        String chainText = chainIdField.getText().trim();
        try {
            if (Integer.parseInt(chainText) <= 0) {
                return "The chain id must be a positive number — Detect asks the node.";
            }
        } catch (NumberFormatException notANumber) {
            return "The chain id must be a whole number — Detect asks the node.";
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
        panel.add(new JLabel(label), l);
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
