package org.nmox.studio.infra.ops.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import org.nmox.studio.core.util.Numbers;
import org.nmox.studio.core.util.PlainDialogs;
import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.core.util.TextDirection;
import org.nmox.studio.infra.api.CloudProvider;
import org.nmox.studio.infra.ops.model.OpsNode;
import org.nmox.studio.infra.ops.provider.CloudInit;
import org.nmox.studio.infra.ops.provider.HostSpec;
import org.nmox.studio.infra.ops.provider.OpsProvider;
import org.nmox.studio.infra.ops.provider.ProviderOptions;
import org.nmox.studio.infra.ops.remote.DeployPlan;
import org.nmox.studio.infra.ops.remote.Dockerfiles;
import org.nmox.studio.infra.ops.remote.SshTarget;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle.Messages;

/**
 * The Hub's dialogs. Every one that leads to a bill, a destruction or a
 * deploy shows exactly what will happen and defaults to the safe button
 * (the v1.98.0 idiom); every input carries a name a screen reader says.
 */
@Messages({
    // new server
    "NewServer_title=New Server",
    "NewServer_name=Name",
    "NewServer_region=Region",
    "NewServer_size=Size",
    "NewServer_image=Image",
    "NewServer_keys=SSH keys on the account",
    "NewServer_noKeys=The account has no SSH key, so a new server could not be reached. Add this machine's key first.",
    "# {0} - the key file",
    "NewServer_addKey=Add this machine''s public key ({0}) to the account",
    "NewServer_cloudInit=First boot installs docker, mosh-server and curl (cloud-init)",
    "# {0} - monthly price",
    "# {1} - hourly price",
    "NewServer_cost=≈ ${0}/month (${1}/hour), billed by the provider from the moment it exists",
    "# {0} - monthly price",
    "NewServer_costSimulated=≈ ${0}/month if it were real — simulated, nothing is billed",
    "NewServer_create=Create",
    "NewServer_cancel=Cancel",
    "NewServer_badName=The name must be a hostname: lowercase letters, digits and dashes.",
    "NewServer_noOptions=No sizes or regions could be read from the provider.",
    // confirm
    "# {0} - the host name",
    "# {1} - provider",
    "# {2} - size",
    "# {3} - region",
    "# {4} - monthly price",
    "NewServer_confirm=Create {0} at {1}?\\n\\n{2} in {3}, ≈ ${4}/month until you destroy it.",
    "NewServer_confirmTitle=Billable: create a server?",
    // add host
    "AddHost_title=Add Host",
    "AddHost_spec=user@host[:port]",
    "AddHost_label=Label",
    "AddHost_identity=Identity file (optional)",
    "AddHost_add=Add",
    "AddHost_bad=That is not an ssh target. Expected user@host or user@host:port.",
    // deploy
    "DeployDialog_title=Deploy",
    "# {0} - the host's label",
    "DeployDialog_to=Deploy the aimed project to {0}",
    "DeployDialog_app=App name",
    "DeployDialog_hostPort=Port on the host",
    "DeployDialog_containerPort=Port the image listens on",
    "DeployDialog_plan=What will run on the host",
    "DeployDialog_deploy=Deploy",
    "DeployDialog_badPort=Ports are numbers from 1 to 65535.",
    "DeployDialog_badApp=The app name must be lowercase letters, digits and dashes.",
    "# {0} - the host's label",
    "DeployDialog_confirmTitle=Deploy to {0}?",
    // destroy
    "# {0} - the host's label",
    "# {1} - the provider",
    "Destroy_question=Destroy {0} at {1}?\\n\\nEverything on it is gone for good. Billing stops.",
    "Destroy_title=Destroy a server",
    // tokens
    "Tokens_title=DigitalOcean API token",
    "Tokens_field=API token (stored in the OS keychain; blank keeps the current one)",
    "Tokens_save=Save",
    "OpsDialogs_messageName=Ops Hub"
})
public final class OpsDialogs {

    private OpsDialogs() {
    }

    // ---- New Server ----

    /** What the dialog decided. Null when cancelled. */
    public record NewServer(HostSpec spec, ProviderOptions.Size size, ProviderOptions.Region region) {
    }

    /**
     * Asks for a new host. {@code options} came from the provider off the
     * EDT; {@code localPublicKey} is this machine's key when one was found,
     * offered for upload when the account has none.
     */
    public static NewServer newServer(OpsProvider provider, ProviderOptions options, String localPublicKeyName,
            java.util.function.Supplier<String> addKey) {
        if (options.sizes().isEmpty() || options.regions().isEmpty()) {
            message(Bundle.NewServer_noOptions());
            return null;
        }
        JPanel form = new JPanel(new GridBagLayout());
        JTextField name = TextDirection.keepLeftToRight(new JTextField("nmox-" + Long.toString(System.currentTimeMillis() % 100000, 36), 24));
        name.getAccessibleContext().setAccessibleName(Bundle.NewServer_name());
        JComboBox<ProviderOptions.Region> region = new JComboBox<>(options.regions().stream()
                .filter(ProviderOptions.Region::available).toArray(ProviderOptions.Region[]::new));
        region.getAccessibleContext().setAccessibleName(Bundle.NewServer_region());
        region.setRenderer(new Labeled<ProviderOptions.Region>(r -> r.name() + " (" + r.slug() + ")"));
        JComboBox<ProviderOptions.Size> size = new JComboBox<>(options.sizes().toArray(ProviderOptions.Size[]::new));
        size.getAccessibleContext().setAccessibleName(Bundle.NewServer_size());
        size.setRenderer(new Labeled<ProviderOptions.Size>(s ->
                s.describe() + "  ·  $" + Numbers.display(s.priceMonthly(), 2) + "/mo"));
        JComboBox<ProviderOptions.Image> image = new JComboBox<>(options.images().toArray(ProviderOptions.Image[]::new));
        image.getAccessibleContext().setAccessibleName(Bundle.NewServer_image());
        image.setRenderer(new Labeled<ProviderOptions.Image>(i -> i.distribution() + " " + i.name()));
        int row = 0;
        row = field(form, row, Bundle.NewServer_name(), name);
        row = field(form, row, Bundle.NewServer_region(), region);
        row = field(form, row, Bundle.NewServer_size(), size);
        row = field(form, row, Bundle.NewServer_image(), image);
        List<JCheckBox> keyBoxes = new ArrayList<>();
        JPanel keys = new JPanel();
        keys.setLayout(new javax.swing.BoxLayout(keys, javax.swing.BoxLayout.PAGE_AXIS));
        for (ProviderOptions.SshKey k : options.sshKeys()) {
            JCheckBox box = new JCheckBox(PlainText.plain(k.name() + "  " + k.fingerprint()), true);
            box.getAccessibleContext().setAccessibleName(k.name());
            keyBoxes.add(box);
            keys.add(box);
        }
        JCheckBox addLocal = null;
        if (localPublicKeyName != null && addKey != null) {
            addLocal = new JCheckBox(PlainText.plain(Bundle.NewServer_addKey(localPublicKeyName)), options.sshKeys().isEmpty());
            addLocal.getAccessibleContext().setAccessibleName(Bundle.NewServer_addKey(localPublicKeyName));
            keys.add(addLocal);
        }
        if (options.sshKeys().isEmpty()) {
            keys.add(new JLabel(PlainText.plain(Bundle.NewServer_noKeys())));
        }
        row = field(form, row, Bundle.NewServer_keys(), keys);
        JLabel cloudInit = new JLabel(PlainText.plain(Bundle.NewServer_cloudInit()));
        row = field(form, row, "", cloudInit);
        JLabel cost = new JLabel();
        Runnable recost = () -> {
            ProviderOptions.Size s = (ProviderOptions.Size) size.getSelectedItem();
            cost.setText(PlainText.plain(s == null ? "" : provider.simulated()
                    ? Bundle.NewServer_costSimulated(Numbers.display(s.priceMonthly(), 2))
                    : Bundle.NewServer_cost(Numbers.display(s.priceMonthly(), 2), Numbers.display(s.priceHourly(), 5))));
        };
        size.addActionListener(e -> recost.run());
        recost.run();
        field(form, row, "", cost);
        // sizes narrow to the region
        region.addActionListener(e -> {
            ProviderOptions.Region r = (ProviderOptions.Region) region.getSelectedItem();
            Object keep = size.getSelectedItem();
            size.removeAllItems();
            for (ProviderOptions.Size s : options.sizes()) {
                if (r == null || s.regions().isEmpty() || s.regions().contains(r.slug())) {
                    size.addItem(s);
                }
            }
            if (keep != null) {
                size.setSelectedItem(keep);
            }
            recost.run();
        });
        if (region.getItemCount() > 0) {
            region.setSelectedIndex(0);
        }

        Object create = Bundle.NewServer_create();
        Object cancel = Bundle.NewServer_cancel();
        DialogDescriptor d = new DialogDescriptor(form, Bundle.NewServer_title(), true,
                new Object[]{create, cancel}, cancel, DialogDescriptor.DEFAULT_ALIGN, null, null);
        while (true) {
            if (!create.equals(DialogDisplayer.getDefault().notify(d))) {
                return null;
            }
            String n = name.getText().trim();
            if (!HostSpec.validName(n)) {
                message(Bundle.NewServer_badName());
                continue;
            }
            ProviderOptions.Size s = (ProviderOptions.Size) size.getSelectedItem();
            ProviderOptions.Region r = (ProviderOptions.Region) region.getSelectedItem();
            ProviderOptions.Image im = (ProviderOptions.Image) image.getSelectedItem();
            if (s == null || r == null || im == null) {
                message(Bundle.NewServer_noOptions());
                return null;
            }
            List<String> keyIds = new ArrayList<>();
            for (int i = 0; i < keyBoxes.size(); i++) {
                if (keyBoxes.get(i).isSelected()) {
                    keyIds.add(options.sshKeys().get(i).id());
                }
            }
            if (addLocal != null && addLocal.isSelected()) {
                String id = addKey.get();
                if (id != null) {
                    keyIds.add(id);
                }
            }
            if (keyIds.isEmpty() && !provider.simulated()) {
                message(Bundle.NewServer_noKeys());
                continue;
            }
            if (!confirm(Bundle.NewServer_confirm(n, provider.displayName(), s.describe(), r.name(),
                    Numbers.display(s.priceMonthly(), 2)), Bundle.NewServer_confirmTitle())) {
                return null;
            }
            return new NewServer(new HostSpec(n, r.slug(), s.slug(), im.slug(), keyIds, CloudInit.USER_DATA), s, r);
        }
    }

    /** This machine's public key, if a conventional one exists: its text and its file name. */
    public static String[] localPublicKey() {
        Path ssh = Path.of(System.getProperty("user.home"), ".ssh");
        for (String name : List.of("id_ed25519.pub", "id_ecdsa.pub", "id_rsa.pub")) {
            Path p = ssh.resolve(name);
            if (Files.isRegularFile(p)) {
                try {
                    String text = org.nmox.studio.core.util.BoundedReads.read(p, 16 * 1024).trim();
                    if (text.startsWith("ssh-") || text.startsWith("ecdsa-")) {
                        return new String[]{text, name};
                    }
                } catch (java.io.IOException unreadable) {
                    // the next candidate
                }
            }
        }
        return null;
    }

    // ---- Add Host ----

    public record AddHost(SshTarget target, String label) {
    }

    public static AddHost addHost() {
        JPanel form = new JPanel(new GridBagLayout());
        JTextField spec = TextDirection.keepLeftToRight(new JTextField("root@", 28));
        spec.getAccessibleContext().setAccessibleName(Bundle.AddHost_spec());
        JTextField label = TextDirection.followsReader(new JTextField(28));
        label.getAccessibleContext().setAccessibleName(Bundle.AddHost_label());
        JTextField identity = TextDirection.keepLeftToRight(new JTextField(28));
        identity.getAccessibleContext().setAccessibleName(Bundle.AddHost_identity());
        int row = 0;
        row = field(form, row, Bundle.AddHost_spec(), spec);
        row = field(form, row, Bundle.AddHost_label(), label);
        field(form, row, Bundle.AddHost_identity(), identity);
        Object add = Bundle.AddHost_add();
        Object cancel = Bundle.NewServer_cancel();
        DialogDescriptor d = new DialogDescriptor(form, Bundle.AddHost_title(), true,
                new Object[]{add, cancel}, add, DialogDescriptor.DEFAULT_ALIGN, null, null);
        while (true) {
            if (!add.equals(DialogDisplayer.getDefault().notify(d))) {
                return null;
            }
            try {
                String id = identity.getText().trim();
                SshTarget t = SshTarget.parse(spec.getText(), id.isEmpty() ? null : id);
                return new AddHost(t, label.getText().trim());
            } catch (IllegalArgumentException bad) {
                message(Bundle.AddHost_bad());
            }
        }
    }

    // ---- Deploy ----

    public static DeployPlan deploy(OpsNode host, File project) {
        String dockerfile = "";
        try {
            dockerfile = org.nmox.studio.core.util.BoundedReads.read(new File(project, "Dockerfile"), 512 * 1024);
        } catch (java.io.IOException unreadable) {
            // the port stays for the user to type
        }
        List<Integer> exposed = Dockerfiles.exposedPorts(dockerfile);
        JPanel form = new JPanel(new GridBagLayout());
        JTextField app = TextDirection.keepLeftToRight(new JTextField(DeployPlan.slugOf(project.getName()), 20));
        app.getAccessibleContext().setAccessibleName(Bundle.DeployDialog_app());
        JTextField hostPort = TextDirection.keepLeftToRight(new JTextField("80", 8));
        hostPort.getAccessibleContext().setAccessibleName(Bundle.DeployDialog_hostPort());
        JTextField containerPort = TextDirection.keepLeftToRight(new JTextField(
                exposed.isEmpty() ? "" : Integer.toString(exposed.get(0)), 8));
        containerPort.getAccessibleContext().setAccessibleName(Bundle.DeployDialog_containerPort());
        JTextArea plan = PlainDialogs.plain("", Bundle.DeployDialog_plan());
        plan.setRows(8);
        plan.setColumns(72);
        plan.setLineWrap(true);
        plan.setWrapStyleWord(true);
        plan.setFont(HudStyle.mono(10.5f));
        int row = 0;
        row = field(form, row, "", new JLabel(PlainText.plain(Bundle.DeployDialog_to(host.label))));
        row = field(form, row, Bundle.DeployDialog_app(), app);
        row = field(form, row, Bundle.DeployDialog_hostPort(), hostPort);
        row = field(form, row, Bundle.DeployDialog_containerPort(), containerPort);
        field(form, row, Bundle.DeployDialog_plan(), new javax.swing.JScrollPane(plan));
        Runnable preview = () -> {
            try {
                DeployPlan p = new DeployPlan(app.getText().trim(), Integer.parseInt(hostPort.getText().trim()),
                        Integer.parseInt(containerPort.getText().trim()), "0");
                plan.setText(String.join("\n", p.describe()));
            } catch (RuntimeException notYet) {
                plan.setText("");
            }
        };
        javax.swing.event.DocumentListener dl = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { preview.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { preview.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { preview.run(); }
        };
        app.getDocument().addDocumentListener(dl);
        hostPort.getDocument().addDocumentListener(dl);
        containerPort.getDocument().addDocumentListener(dl);
        preview.run();
        Object go = Bundle.DeployDialog_deploy();
        Object cancel = Bundle.NewServer_cancel();
        DialogDescriptor d = new DialogDescriptor(form, Bundle.DeployDialog_confirmTitle(host.label), true,
                new Object[]{go, cancel}, cancel, DialogDescriptor.DEFAULT_ALIGN, null, null);
        while (true) {
            if (!go.equals(DialogDisplayer.getDefault().notify(d))) {
                return null;
            }
            int hp;
            int cp;
            try {
                hp = Integer.parseInt(hostPort.getText().trim());
                cp = Integer.parseInt(containerPort.getText().trim());
                if (hp < 1 || hp > 65535 || cp < 1 || cp > 65535) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException bad) {
                message(Bundle.DeployDialog_badPort());
                continue;
            }
            String slug = app.getText().trim();
            if (!DeployPlan.SLUG.matcher(slug).matches()) {
                message(Bundle.DeployDialog_badApp());
                continue;
            }
            return new DeployPlan(slug, hp, cp, Long.toString(System.currentTimeMillis()));
        }
    }

    // ---- Destroy ----

    public static boolean confirmDestroy(OpsNode host, String providerName) {
        return confirm(Bundle.Destroy_question(host.label, providerName), Bundle.Destroy_title());
    }

    // ---- Tokens ----

    /** Asks for a DigitalOcean token; stores it off the EDT; returns true when one was entered. */
    public static boolean tokens(Runnable then) {
        JPanel form = new JPanel(new GridBagLayout());
        JPasswordField field = TextDirection.keepLeftToRight(new JPasswordField(36));
        field.getAccessibleContext().setAccessibleName(Bundle.Tokens_field());
        field(form, 0, Bundle.Tokens_field(), field);
        Object save = Bundle.Tokens_save();
        Object cancel = Bundle.NewServer_cancel();
        DialogDescriptor d = new DialogDescriptor(form, Bundle.Tokens_title(), true,
                new Object[]{save, cancel}, save, DialogDescriptor.DEFAULT_ALIGN, null, null);
        if (!save.equals(DialogDisplayer.getDefault().notify(d))) {
            return false;
        }
        char[] chars = field.getPassword();
        String token = new String(chars).trim();
        java.util.Arrays.fill(chars, '\0');
        if (token.isEmpty()) {
            return false;
        }
        org.openide.util.RequestProcessor.getDefault().post(() -> {
            CloudProvider.DIGITALOCEAN.storeToken(token);
            javax.swing.SwingUtilities.invokeLater(then);
        });
        return true;
    }

    // ---- helpers ----

    /** A combo renderer that prints a record as one plain line (html-disabled at construction, the v1.311.0 rule). */
    private static final class Labeled<T> extends javax.swing.DefaultListCellRenderer {
        private final java.util.function.Function<T, String> text;

        Labeled(java.util.function.Function<T, String> text) {
            this.text = text;
            org.nmox.studio.core.util.PlainTables.plain(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value != null) {
                setText(text.apply((T) value));
            }
            return this;
        }
    }

    private static int field(JPanel form, int row, String label, JComponent input) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.gridx = 0;
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.insets = new Insets(4, 6, 4, 8);
        JLabel l = new JLabel(PlainText.plain(label));
        l.setLabelFor(input);
        form.add(l, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        if (!label.isEmpty() && input.getAccessibleContext().getAccessibleName() == null) {
            input.getAccessibleContext().setAccessibleName(label);
        }
        form.add(input, c);
        return row + 1;
    }

    /** A yes/no whose default is No: a reflexive Enter never bills or destroys (v1.98.0). */
    static boolean confirm(String message, String title) {
        NotifyDescriptor d = new NotifyDescriptor(PlainDialogs.plain(message, title), title,
                NotifyDescriptor.YES_NO_OPTION, NotifyDescriptor.WARNING_MESSAGE,
                new Object[]{NotifyDescriptor.YES_OPTION, NotifyDescriptor.NO_OPTION},
                NotifyDescriptor.NO_OPTION);
        return DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.YES_OPTION;
    }

    static void message(String text) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                PlainDialogs.plain(text, Bundle.OpsDialogs_messageName()), NotifyDescriptor.INFORMATION_MESSAGE));
    }
}
