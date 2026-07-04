package org.nmox.studio.dbstudio.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.nmox.studio.dbstudio.engine.DbBackend;
import org.nmox.studio.dbstudio.engine.Passwords;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;

/**
 * The add/edit dialog for one database connection. Server engines
 * (MySQL/MariaDB, PostgreSQL, MongoDB, CouchDB) show host / port /
 * database / user / password; SQLite shows a file picker instead. The
 * port pre-fills with the selected engine's conventional default and
 * follows engine switches until the user types their own.
 *
 * <p>Passwords never touch {@code .nmoxdb.json}: on OK a non-empty
 * password is handed to {@link Passwords} (the OS keychain) off the EDT
 * and the char array is wiped; on edit, a blank field means "keep the
 * stored one". The in-dialog Test button probes with exactly the
 * credentials the connection would use, asynchronously.
 */
final class ConnectionDialog extends JPanel {

    private static final Color OK_GREEN = new Color(0x4E, 0xC9, 0x8B);
    private static final Color FAIL_RED = new Color(0xE2, 0x4B, 0x4A);

    private final JTextField nameField = new JTextField(24);
    private final JComboBox<DbEngine> engineCombo = new JComboBox<>(DbEngine.values());
    private final JTextField hostField = new JTextField("localhost", 18);
    private final JTextField portField = new JTextField(6);
    private final JTextField databaseField = new JTextField(18);
    private final JTextField userField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JTextField fileField = new JTextField(24);
    private final JLabel testLabel = new JLabel(" ");
    private final JPanel cards = new JPanel(new CardLayout());

    private final ConnectionSpec existing;
    private int lastDefaultPort;

    private ConnectionDialog(ConnectionSpec existing) {
        super(new BorderLayout(0, 6));
        this.existing = existing;
        setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));

        engineCombo.setRenderer(new EngineRenderer());
        engineCombo.addActionListener(e -> engineChanged());

        JPanel top = grid();
        addRow(top, 0, "Name:", nameField);
        addRow(top, 1, "Engine:", engineCombo);
        add(top, BorderLayout.NORTH);

        cards.add(buildServerCard(), "server");
        cards.add(buildFileCard(), "file");
        add(cards, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        JButton testButton = new JButton("Test");
        testButton.setToolTipText("Probe the server with these settings (async)");
        testButton.addActionListener(e -> testConnection());
        south.add(testButton, BorderLayout.WEST);
        testLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        south.add(testLabel, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        if (existing != null) {
            nameField.setText(existing.name());
            engineCombo.setSelectedItem(existing.engine());
            hostField.setText(existing.host());
            portField.setText(existing.port() > 0 ? String.valueOf(existing.port()) : "");
            databaseField.setText(existing.database());
            userField.setText(existing.user());
            fileField.setText(existing.filePath());
            passwordField.setToolTipText("Leave blank to keep the stored password");
        }
        lastDefaultPort = selectedEngine().defaultPort();
        if (portField.getText().isBlank() && lastDefaultPort > 0) {
            portField.setText(String.valueOf(lastDefaultPort));
        }
        engineChanged();
    }

    // ---- layout helpers ----

    private static JPanel grid() {
        return new JPanel(new GridBagLayout());
    }

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

    private JPanel buildServerCard() {
        JPanel panel = grid();
        addRow(panel, 0, "Host:", hostField);
        addRow(panel, 1, "Port:", portField);
        addRow(panel, 2, "Database:", databaseField);
        addRow(panel, 3, "User:", userField);
        addRow(panel, 4, "Password:", passwordField);
        JLabel hint = new JLabel("<html><small>Stored in the OS keychain — never in "
                + ".nmoxdb.json.</small></html>");
        addRow(panel, 5, "", hint);
        return panel;
    }

    private JPanel buildFileCard() {
        JPanel panel = grid();
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(fileField, BorderLayout.CENTER);
        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(fileField.getText().isBlank()
                    ? System.getProperty("user.home") : fileField.getText());
            chooser.setDialogTitle("SQLite database file");
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                fileField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        row.add(browse, BorderLayout.EAST);
        addRow(panel, 0, "File:", row);
        addRow(panel, 1, "", new JLabel("<html><small>Path to the database file; it is "
                + "created on first use if missing.</small></html>"));
        return panel;
    }

    // ---- behavior ----

    private DbEngine selectedEngine() {
        DbEngine engine = (DbEngine) engineCombo.getSelectedItem();
        return engine != null ? engine : DbEngine.SQLITE;
    }

    private void engineChanged() {
        DbEngine engine = selectedEngine();
        ((CardLayout) cards.getLayout()).show(cards,
                engine == DbEngine.SQLITE ? "file" : "server");
        // the port follows the engine default until the user types their own
        String current = portField.getText().trim();
        if (current.isBlank() || current.equals(String.valueOf(lastDefaultPort))) {
            portField.setText(engine.defaultPort() > 0
                    ? String.valueOf(engine.defaultPort()) : "");
        }
        lastDefaultPort = engine.defaultPort();
        revalidate();
        repaint();
    }

    private ConnectionSpec specFromFields(String id) {
        return new ConnectionSpec(
                id,
                nameField.getText().trim(),
                selectedEngine(),
                hostField.getText().trim(),
                parsePort(portField.getText()),
                databaseField.getText().trim(),
                userField.getText().trim(),
                fileField.getText().trim());
    }

    private static int parsePort(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException blankOrJunk) {
            return -1; // "use the engine's default", per ConnectionSpec's contract
        }
    }

    /** A human-readable problem with the current fields, or null when OK. */
    private String validateFields() {
        if (nameField.getText().isBlank()) {
            return "Give the connection a name.";
        }
        if (selectedEngine() == DbEngine.SQLITE) {
            if (fileField.getText().isBlank()) {
                return "Pick the SQLite database file.";
            }
        } else if (hostField.getText().isBlank()) {
            return "Enter the server host.";
        }
        return null;
    }

    private void testConnection() {
        ConnectionSpec probe = specFromFields(existing != null ? existing.id() : "test");
        char[] typed = passwordField.getPassword();
        String storedId = existing != null ? existing.id() : null;
        testLabel.setForeground(Color.GRAY);
        testLabel.setText("Testing…");
        DbStudioTopComponent.RP.post(() -> {
            char[] password = typed.length > 0 ? typed
                    : (storedId != null ? Passwords.read(storedId) : null);
            DbBackend backend = DbBackend.create(probe, password);
            String error = backend.test();
            backend.close();
            if (password != null) {
                Arrays.fill(password, '\0'); // == typed when one was entered
            }
            SwingUtilities.invokeLater(() -> {
                testLabel.setForeground(error == null ? OK_GREEN : FAIL_RED);
                testLabel.setText(error == null ? "OK — server reachable" : error);
            });
        });
    }

    /** Builds the final spec and stores a typed password; call only after OK. */
    private ConnectionSpec commit() {
        String id = existing != null ? existing.id() : UUID.randomUUID().toString();
        ConnectionSpec spec = specFromFields(id);
        char[] typed = passwordField.getPassword();
        if (typed.length > 0) {
            char[] copy = typed.clone();
            // the keyring may block on OS calls — never on the EDT
            DbStudioTopComponent.RP.post(() -> {
                Passwords.save(id, copy);
                Arrays.fill(copy, '\0');
            });
        }
        Arrays.fill(typed, '\0');
        return spec;
    }

    /**
     * Shows the dialog. Returns the new/updated spec (same id when
     * editing), or null on cancel. Field problems re-open the dialog
     * after a warning rather than silently saving junk.
     */
    static ConnectionSpec show(ConnectionSpec existing) {
        ConnectionDialog panel = new ConnectionDialog(existing);
        DialogDescriptor descriptor = new DialogDescriptor(panel,
                existing == null ? "Add Database Connection" : "Edit Database Connection");
        while (true) {
            if (DialogDisplayer.getDefault().notify(descriptor) != NotifyDescriptor.OK_OPTION) {
                return null;
            }
            String problem = panel.validateFields();
            if (problem == null) {
                return panel.commit();
            }
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    problem, NotifyDescriptor.WARNING_MESSAGE));
        }
    }

    /** Engine combo entries by display name, not enum constant. */
    private static final class EngineRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof DbEngine engine) {
                setText(engine.displayName());
            }
            return this;
        }
    }
}
