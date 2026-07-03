package org.nmox.studio.ui.actions;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.nmox.studio.rack.projectstudio.PwaKit;
import org.nmox.studio.rack.service.RackService;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * The PWA Kit wizard: one dialog and the aimed project becomes
 * installable - generated icon set (monogram or your own artwork),
 * maskable-complete manifest, a readable service worker with a chosen
 * caching strategy, an offline page, and index.html wired up. Existing
 * files are never overwritten; the wiring is idempotent.
 */
@ActionID(category = "File", id = "org.nmox.studio.ui.actions.PwaKitAction")
@ActionRegistration(displayName = "#CTL_PwaKitAction")
@ActionReference(path = "Menu/File", position = 118)
@Messages("CTL_PwaKitAction=PWA Kit...")
public final class PwaKitAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        File project = RackService.getDefault().getRack().getProjectDir();
        if (project == null || !project.isDirectory()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Aim the studio at a project first (open a folder or project)."));
            return;
        }

        String projectName = project.getName();
        JTextField name = new JTextField(projectName);
        JTextField shortName = new JTextField(projectName.length() > 12
                ? projectName.substring(0, 12) : projectName);
        JTextField theme = new JTextField("#1a1a1e");
        JTextField background = new JTextField("#1a1a1e");
        JTextField monogram = new JTextField(projectName.isEmpty() ? "A"
                : projectName.substring(0, 1).toUpperCase(Locale.ROOT));
        JTextField artwork = new JTextField();
        JButton browse = new JButton("Browse…");
        browse.addActionListener(ev -> {
            JFileChooser chooser = new JFileChooser(project);
            chooser.setDialogTitle("Icon artwork (square PNG works best)");
            if (chooser.showOpenDialog(browse) == JFileChooser.APPROVE_OPTION) {
                artwork.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        JComboBox<String> strategy = new JComboBox<>(new String[]{
            "App shell — cache first, instant loads, offline-ready",
            "Network first — always-fresh content, cache fallback"
        });
        JCheckBox icons = new JCheckBox(
                "Icons — icon-192/512, maskable pair, apple-touch-icon", true);
        JCheckBox manifest = new JCheckBox(
                "site.webmanifest — W3C manifest, installability-complete", true);
        JCheckBox serviceWorker = new JCheckBox(
                "sw.js + offline.html — service worker precaching this project's files", true);
        JCheckBox wire = new JCheckBox(
                "Wire index.html — manifest link, theme-color, registration (idempotent)", true);

        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 4));
        rows.add(new JLabel("App name:"));
        rows.add(name);
        rows.add(new JLabel("Short name (12 chars max, shown under the icon):"));
        rows.add(shortName);
        rows.add(new JLabel("Theme color / background color:"));
        JPanel colors = new JPanel(new GridLayout(1, 2, 6, 0));
        colors.add(theme);
        colors.add(background);
        rows.add(colors);
        rows.add(new JLabel("Icon monogram (1–2 letters) — or pick artwork below:"));
        rows.add(monogram);
        JPanel art = new JPanel(new BorderLayout(6, 0));
        art.add(artwork, BorderLayout.CENTER);
        art.add(browse, BorderLayout.EAST);
        rows.add(art);
        rows.add(new JLabel("Caching strategy:"));
        rows.add(strategy);
        rows.add(new JLabel(" "));
        rows.add(icons);
        rows.add(manifest);
        rows.add(serviceWorker);
        rows.add(wire);
        rows.add(new JLabel("<html><small>Existing files are never overwritten; "
                + "wiring only adds what's missing.</small></html>"));
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(rows, BorderLayout.CENTER);

        DialogDescriptor descriptor = new DialogDescriptor(panel,
                "PWA Kit — " + projectName);
        if (DialogDisplayer.getDefault().notify(descriptor) != DialogDescriptor.OK_OPTION) {
            return;
        }
        String artPath = artwork.getText().trim();
        if (!artPath.isEmpty()) {
            File artFile = new File(artPath);
            if (!artFile.isFile() || !artFile.canRead()) {
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message("Icon artwork not readable: "
                                + artFile.getAbsolutePath(), NotifyDescriptor.WARNING_MESSAGE)));
                return;
            }
        }
        if (name.getText().isBlank()) {
            SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                    new NotifyDescriptor.Message("Give the app a name — it goes into the manifest and offline page.",
                            NotifyDescriptor.WARNING_MESSAGE)));
            return;
        }
        PwaKit.Options opts = new PwaKit.Options(
                name.getText().trim(), shortName.getText().trim(),
                theme.getText().trim(), background.getText().trim(),
                monogram.getText().trim(),
                artPath.isEmpty() ? null : new File(artPath),
                strategy.getSelectedIndex() == 0
                        ? PwaKit.Strategy.APP_SHELL : PwaKit.Strategy.NETWORK_FIRST,
                icons.isSelected(), manifest.isSelected(),
                serviceWorker.isSelected(), wire.isSelected());
        // PNG encoding and disk writes run off the EDT; the report then hops
        // back to a fresh EDT dispatch so it can't stack behind the main window
        org.openide.util.RequestProcessor.getDefault().post(() -> {
            try {
                List<PwaKit.Outcome> outcomes = PwaKit.write(project, opts);
                StringBuilder report = new StringBuilder();
                for (PwaKit.Outcome o : outcomes) {
                    report.append(o.written() ? "  ✓ " : "  – ").append(o.path());
                    if (!"written".equals(o.status())) {
                        report.append("  (").append(o.status()).append(')');
                    }
                    report.append('\n');
                }
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message("PWA Kit:\n\n" + report,
                                NotifyDescriptor.INFORMATION_MESSAGE)));
            } catch (Exception ex) {
                String message = "Could not write: " + ex.getMessage();
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(message, NotifyDescriptor.ERROR_MESSAGE)));
            }
        });
    }
}
