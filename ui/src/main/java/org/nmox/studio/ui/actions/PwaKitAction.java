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
@Messages({
    "CTL_PwaKitAction=PWA Kit…",
    "PwaKitAction_aimFirst=Aim the studio at a project first (open a folder or project).",
    "PwaKitAction_appNameField=App name",
    "PwaKitAction_shortNameField=Short name",
    "PwaKitAction_themeField=Theme color",
    "PwaKitAction_backgroundField=Background color",
    "PwaKitAction_monogramField=Icon monogram",
    "PwaKitAction_artworkField=Icon artwork path",
    "PwaKitAction_browse=Browse…",
    "PwaKitAction_artworkChooserTitle=Icon artwork (square PNG works best)",
    "PwaKitAction_strategyAppShell=App shell — cache first, instant loads, offline-ready",
    "PwaKitAction_strategyNetworkFirst=Network first — always-fresh content, cache fallback",
    "PwaKitAction_strategyField=Caching strategy",
    "PwaKitAction_iconsBox=Icons — icon-192/512, maskable pair, apple-touch-icon",
    "PwaKitAction_manifestBox=site.webmanifest — W3C manifest, installability-complete",
    "PwaKitAction_serviceWorkerBox=sw.js + offline.html — service worker precaching this project's files",
    "PwaKitAction_wireBox=Wire index.html — manifest link, theme-color, registration (idempotent)",
    "PwaKitAction_appNameLabel=App name:",
    "PwaKitAction_shortNameLabel=Short name (12 chars max, shown under the icon):",
    "PwaKitAction_colorsLabel=Theme color / background color:",
    "PwaKitAction_monogramLabel=Icon monogram (1–2 letters) — or pick artwork below:",
    "PwaKitAction_strategyLabel=Caching strategy:",
    "PwaKitAction_note=<html><small>Existing files are never overwritten; wiring only adds what's missing.</small></html>",
    "PwaKitAction_title=PWA Kit — {0}",
    "PwaKitAction_artworkUnreadable=Icon artwork not readable: {0}",
    "PwaKitAction_messageName=Message",
    "PwaKitAction_needsName=Give the app a name — it goes into the manifest and offline page.",
    "PwaKitAction_lineWritten=  ✓ {0}",
    "PwaKitAction_lineKept=  – {0}",
    "PwaKitAction_lineStatus=  ({0})",
    "PwaKitAction_report=PWA Kit:\n\n{0}",
    "PwaKitAction_couldNotWrite=Could not write: {0}"
})
public final class PwaKitAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        // DECIDED (v1.192.0, closing the ledger-29 remainder): the kit
        // family stays always-enabled ON PURPOSE. Every kit acts on the
        // AIMED project, not the window selection — so selection-keyed
        // enablement would be semantically wrong, greying a valid action
        // whenever focus sits in an editor. This runtime guard is the
        // honest gate, applied at the only moment the answer is knowable.
        // The same decision covers StandardsKit/ClassicKit/ContractKit/
        // ImageKit, which share this exact shape.
        File project = RackService.getDefault().getRack().getProjectDir();
        if (project == null || !project.isDirectory()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    Bundle.PwaKitAction_aimFirst()));
            return;
        }

        String projectName = project.getName();
        JTextField name = new JTextField(projectName);
        name.getAccessibleContext().setAccessibleName(Bundle.PwaKitAction_appNameField());
        JTextField shortName = new JTextField(projectName.length() > 12
                ? projectName.substring(0, 12) : projectName);
        shortName.getAccessibleContext().setAccessibleName(Bundle.PwaKitAction_shortNameField());
        JTextField theme = new JTextField("#1a1a1e");
        theme.getAccessibleContext().setAccessibleName(Bundle.PwaKitAction_themeField());
        JTextField background = new JTextField("#1a1a1e");
        background.getAccessibleContext().setAccessibleName(Bundle.PwaKitAction_backgroundField());
        JTextField monogram = new JTextField(projectName.isEmpty() ? "A"
                : projectName.substring(0, 1).toUpperCase(Locale.ROOT));
        monogram.getAccessibleContext().setAccessibleName(Bundle.PwaKitAction_monogramField());
        JTextField artwork = new JTextField();
        artwork.getAccessibleContext().setAccessibleName(Bundle.PwaKitAction_artworkField());
        JButton browse = new JButton(Bundle.PwaKitAction_browse());
        browse.addActionListener(ev -> {
            JFileChooser chooser = new JFileChooser(project);
            chooser.setDialogTitle(Bundle.PwaKitAction_artworkChooserTitle());
            if (chooser.showOpenDialog(browse) == JFileChooser.APPROVE_OPTION) {
                artwork.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        JComboBox<String> strategy = new JComboBox<>(new String[]{
            Bundle.PwaKitAction_strategyAppShell(),
            Bundle.PwaKitAction_strategyNetworkFirst()
        });
        strategy.getAccessibleContext().setAccessibleName(Bundle.PwaKitAction_strategyField());
        JCheckBox icons = new JCheckBox(Bundle.PwaKitAction_iconsBox(), true);
        JCheckBox manifest = new JCheckBox(Bundle.PwaKitAction_manifestBox(), true);
        JCheckBox serviceWorker = new JCheckBox(Bundle.PwaKitAction_serviceWorkerBox(), true);
        JCheckBox wire = new JCheckBox(Bundle.PwaKitAction_wireBox(), true);

        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 4));
        rows.add(new JLabel(Bundle.PwaKitAction_appNameLabel()));
        rows.add(name);
        rows.add(new JLabel(Bundle.PwaKitAction_shortNameLabel()));
        rows.add(shortName);
        rows.add(new JLabel(Bundle.PwaKitAction_colorsLabel()));
        JPanel colors = new JPanel(new GridLayout(1, 2, 6, 0));
        colors.add(theme);
        colors.add(background);
        rows.add(colors);
        rows.add(new JLabel(Bundle.PwaKitAction_monogramLabel()));
        rows.add(monogram);
        JPanel art = new JPanel(new BorderLayout(6, 0));
        art.add(artwork, BorderLayout.CENTER);
        art.add(browse, BorderLayout.EAST);
        rows.add(art);
        rows.add(new JLabel(Bundle.PwaKitAction_strategyLabel()));
        rows.add(strategy);
        rows.add(new JLabel(" "));
        rows.add(icons);
        rows.add(manifest);
        rows.add(serviceWorker);
        rows.add(wire);
        rows.add(new JLabel(Bundle.PwaKitAction_note()));
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(rows, BorderLayout.CENTER);

        DialogDescriptor descriptor = new DialogDescriptor(panel,
                Bundle.PwaKitAction_title(projectName));
        if (DialogDisplayer.getDefault().notify(descriptor) != DialogDescriptor.OK_OPTION) {
            return;
        }
        String artPath = artwork.getText().trim();
        if (!artPath.isEmpty()) {
            File artFile = new File(artPath);
            if (!artFile.isFile() || !artFile.canRead()) {
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(org.nmox.studio.core.util.PlainDialogs.plain(Bundle.PwaKitAction_artworkUnreadable(
                                artFile.getAbsolutePath()), Bundle.PwaKitAction_messageName()), NotifyDescriptor.WARNING_MESSAGE)));
                return;
            }
        }
        if (name.getText().isBlank()) {
            SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                    new NotifyDescriptor.Message(Bundle.PwaKitAction_needsName(),
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
                    report.append(o.written() ? Bundle.PwaKitAction_lineWritten(o.path())
                            : Bundle.PwaKitAction_lineKept(o.path()));
                    if (!"written".equals(o.status())) {
                        report.append(Bundle.PwaKitAction_lineStatus(o.status()));
                    }
                    report.append('\n');
                }
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(org.nmox.studio.core.util.PlainDialogs.plain(Bundle.PwaKitAction_report(report), Bundle.PwaKitAction_messageName()),
                                NotifyDescriptor.INFORMATION_MESSAGE)));
            } catch (Exception ex) {
                String message = Bundle.PwaKitAction_couldNotWrite(ex.getMessage());
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(org.nmox.studio.core.util.PlainDialogs.plain(message, Bundle.PwaKitAction_messageName()), NotifyDescriptor.ERROR_MESSAGE)));
            }
        });
    }
}
