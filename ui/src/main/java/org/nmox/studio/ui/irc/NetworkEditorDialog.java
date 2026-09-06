package org.nmox.studio.ui.irc;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import org.nmox.studio.ui.irc.engine.IrcConfig;
import org.nmox.studio.ui.irc.engine.IrcSecrets;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.RequestProcessor;

/**
 * The Add/Edit-network form: name, host, port, TLS, nick, SASL account,
 * password, and autojoin channels in one {@code DialogDescriptor}. The
 * password field is a {@link JPasswordField} whose value goes ONLY to
 * the OS keychain via {@link IrcSecrets} — never into preferences (the
 * Keyring-only law). An existing secret shows as an unchanged
 * placeholder and is re-saved only when the user actually types a new
 * one, so opening and OK-ing the dialog can't churn the keychain.
 * Keychain writes ride a RequestProcessor because the keyring may block
 * on OS calls (the EDT-never-blocks law).
 */
final class NetworkEditorDialog {

    /** Shown for an existing secret; saving this exact value is a no-op. */
    private static final String UNCHANGED = "•••unchanged•••";

    private static final RequestProcessor SECRETS_RP
            = new RequestProcessor("IRC Secrets", 1);

    private NetworkEditorDialog() {
    }

    /**
     * Opens the form; returns the saved network name, or {@code null}
     * on cancel/invalid. Pass {@code existing == null} for Add.
     */
    static String show(IrcConfig config, IrcConfig.Network existing) {
        JTextField name = new JTextField(existing == null ? "" : existing.name(), 18);
        name.getAccessibleContext().setAccessibleName("Network name");
        name.setEditable(existing == null); // the name is the store key
        JTextField host = new JTextField(existing == null ? "" : existing.host(), 18);
        host.getAccessibleContext().setAccessibleName("Server host");
        JTextField port = new JTextField(
                existing == null ? "6697" : Integer.toString(existing.port()), 6);
        port.getAccessibleContext().setAccessibleName("Port");
        JCheckBox tls = new JCheckBox("TLS", existing == null || existing.tls());
        JTextField nick = new JTextField(existing == null ? "nmox-user" : existing.nick(), 18);
        nick.getAccessibleContext().setAccessibleName("Nickname");
        JTextField sasl = new JTextField(existing == null ? "" : existing.saslAccount(), 18);
        sasl.getAccessibleContext().setAccessibleName("SASL account");
        JPasswordField password = new JPasswordField(18);
        password.getAccessibleContext().setAccessibleName("Password");
        if (existing != null) {
            // the keyring may block on OS calls — probe it OFF the EDT and
            // mask the field once known (the modal dialog's nested event
            // pump still runs invokeLater work)
            String net = existing.name();
            SECRETS_RP.post(() -> {
                if (!IrcSecrets.read(net).isEmpty()) {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        if (password.getPassword().length == 0) {
                            password.setText(UNCHANGED);
                        }
                    });
                }
            });
        }
        JTextField autojoin = new JTextField(existing == null
                ? "" : String.join(", ", existing.autojoin()), 18);
        autojoin.getAccessibleContext().setAccessibleName("Auto-join channels");

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(3, 4, 3, 4);
        gc.anchor = GridBagConstraints.WEST;
        int row = 0;
        row = addRow(form, gc, row, "Name:", name);
        row = addRow(form, gc, row, "Host:", host);
        row = addRow(form, gc, row, "Port:", port);
        row = addRow(form, gc, row, "", tls);
        row = addRow(form, gc, row, "Nick:", nick);
        row = addRow(form, gc, row, "SASL account:", sasl);
        row = addRow(form, gc, row, "Password:", password);
        row = addRow(form, gc, row, "Autojoin:", autojoin);
        gc.gridx = 1;
        gc.gridy = row;
        form.add(new JLabel("<html><i>Password goes to the OS keychain, never to disk.<br>"
                + "With a SASL account it authenticates in-registration;<br>"
                + "without one it identifies to NickServ after connect.</i></html>"), gc);

        DialogDescriptor dd = new DialogDescriptor(form,
                existing == null ? "Add IRC Network" : "Edit IRC Network");
        if (DialogDisplayer.getDefault().notify(dd) != NotifyDescriptor.OK_OPTION) {
            return null;
        }
        String netName = name.getText().trim();
        String hostText = host.getText().trim();
        if (netName.isEmpty() || hostText.isEmpty()) {
            return null;
        }
        int portNum;
        try {
            portNum = Integer.parseInt(port.getText().trim());
        } catch (NumberFormatException ex) {
            portNum = 6697;
        }
        List<String> channels = new ArrayList<>();
        for (String c : autojoin.getText().split(",")) {
            String t = c.trim();
            if (!t.isEmpty()) {
                channels.add(t);
            }
        }
        config.save(new IrcConfig.Network(netName, hostText, portNum, tls.isSelected(),
                nick.getText().trim(), sasl.getText().trim(), channels));

        char[] pw = password.getPassword();
        String pwText = new String(pw);
        Arrays.fill(pw, '\0');
        if (!UNCHANGED.equals(pwText)) {
            // save only what actually changed; blank deletes (IrcSecrets law)
            SECRETS_RP.post(() -> IrcSecrets.save(netName, pwText));
        }
        return netName;
    }

    /**
     * The delete confirmation + delete: v1.98.0 safe-default idiom (the
     * full {@link NotifyDescriptor} constructor with NO as the initial
     * value, so a reflexive Enter can't destroy a saved network), and
     * the v1.200.0 delete-hygiene law — the network's keychain entry
     * dies with it. Returns true when deleted.
     */
    static boolean confirmAndDelete(IrcConfig config, String network) {
        NotifyDescriptor d = new NotifyDescriptor(
                org.nmox.studio.core.util.PlainDialogs.plain("Delete network \"" + network + "\"? Its saved password is removed "
                + "from the OS keychain too.", "Message"),
                "Delete IRC Network",
                NotifyDescriptor.YES_NO_OPTION,
                NotifyDescriptor.WARNING_MESSAGE,
                null,
                NotifyDescriptor.NO_OPTION);
        if (DialogDisplayer.getDefault().notify(d) != NotifyDescriptor.YES_OPTION) {
            return false;
        }
        config.remove(network);
        SECRETS_RP.post(() -> IrcSecrets.delete(network));
        return true;
    }

    private static int addRow(JPanel form, GridBagConstraints gc, int row,
            String label, javax.swing.JComponent field) {
        gc.gridx = 0;
        gc.gridy = row;
        form.add(new JLabel(label), gc);
        gc.gridx = 1;
        form.add(field, gc);
        return row + 1;
    }
}
