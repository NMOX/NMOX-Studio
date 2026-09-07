package org.nmox.studio.ui.actions;

import org.nmox.studio.core.util.PlainText;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import org.nmox.studio.rack.projectstudio.ClassicKit;
import org.nmox.studio.rack.service.RackService;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * The Classic Kit wizard: one dialog, and the aimed project gains the
 * classic web stack - jQuery, MooTools, Prototype, Backbone
 * (+Underscore), Knockout - vendored (pinned bundled builds, script-tag
 * wired) or via npm, plus never-clobber webpack/Grunt/gulp/bower
 * scaffolds. Existing files are never overwritten; an existing config
 * gets a .suggested sibling instead.
 */
@ActionID(category = "File", id = "org.nmox.studio.ui.actions.ClassicKitAction")
@ActionRegistration(displayName = "#CTL_ClassicKitAction")
@ActionReference(path = "Menu/File", position = 119)
@Messages({
    "CTL_ClassicKitAction=Classic Kit…",
    "ClassicKitAction_libraryWithUnderscore={0} (+ {1} — hard dependency, wired first)",
    "ClassicKitAction_webpackBox=webpack.config.js — entry auto-detected, dist/bundle.js, dev server",
    "ClassicKitAction_gruntBox=Gruntfile.js — uglify js/ into dist/, watch, build/default tasks",
    "ClassicKitAction_gulpBox=gulpfile.js — gulp 4 exports: build, watch",
    "ClassicKitAction_bowerBox=bower.json — name from folder, records vendored libraries",
    "ClassicKitAction_lineChanged=  ✓ {0}",
    "ClassicKitAction_lineKept=  – {0}",
    "ClassicKitAction_lineStatus=  ({0})",
    "ClassicKitAction_aimFirst=Aim the studio at a project first (open a folder or project).",
    "ClassicKitAction_vendoredRadio=Vendored — pinned builds copied into vendor/, script tags wired into index.html",
    "ClassicKitAction_npmRadio=npm — added to package.json dependencies (no network run here)",
    "ClassicKitAction_libraryHeading=Add a classic library:",
    "ClassicKitAction_deliveryHeading=Delivery:",
    "ClassicKitAction_buildToolHeading=Add a build tool:",
    "ClassicKitAction_note=<html><small>Existing files are never overwritten — an existing config gets a .suggested sibling instead.</small></html>",
    "ClassicKitAction_title=Classic Kit — {0}",
    "ClassicKitAction_messageName=Message",
    "ClassicKitAction_report=Classic Kit:\n\n{0}",
    "ClassicKitAction_couldNotWrite=Could not write: {0}"
})
public final class ClassicKitAction implements ActionListener {

    /** Checkbox text; Backbone announces the Underscore it brings along. */
    static String libraryLabel(ClassicKit.Lib lib) {
        if ("backbone".equals(lib.id())) {
            return Bundle.ClassicKitAction_libraryWithUnderscore(lib.label(),
                    ClassicKit.underscore().label());
        }
        return lib.label();
    }

    /** npm mode greys out what npm cannot deliver (Prototype). */
    static boolean enabledFor(ClassicKit.Lib lib, boolean npmMode) {
        return !npmMode || lib.npmCapable();
    }

    /** The generators, id → honest checkbox text. */
    static Map<String, String> generatorLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("webpack", Bundle.ClassicKitAction_webpackBox());
        labels.put("grunt", Bundle.ClassicKitAction_gruntBox());
        labels.put("gulp", Bundle.ClassicKitAction_gulpBox());
        labels.put("bower", Bundle.ClassicKitAction_bowerBox());
        return labels;
    }

    /** The report surface, Standards Kit style: ✓ changed, – left alone. */
    static String renderReport(List<ClassicKit.Outcome> outcomes) {
        StringBuilder report = new StringBuilder();
        for (ClassicKit.Outcome o : outcomes) {
            report.append(o.changed() ? Bundle.ClassicKitAction_lineChanged(o.path())
                    : Bundle.ClassicKitAction_lineKept(o.path()));
            if (!"written".equals(o.status())) {
                report.append(Bundle.ClassicKitAction_lineStatus(o.status()));
            }
            report.append('\n');
        }
        return report.toString();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        File project = RackService.getDefault().getRack().getProjectDir();
        if (project == null || !project.isDirectory()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    Bundle.ClassicKitAction_aimFirst()));
            return;
        }

        Map<String, JCheckBox> libraryBoxes = new LinkedHashMap<>();
        for (ClassicKit.Lib lib : ClassicKit.libraries()) {
            libraryBoxes.put(lib.id(), new JCheckBox(PlainText.plain(libraryLabel(lib)),
                    "jquery".equals(lib.id())));
        }
        JRadioButton vendored = new JRadioButton(
                Bundle.ClassicKitAction_vendoredRadio(), true);
        JRadioButton npm = new JRadioButton(
                Bundle.ClassicKitAction_npmRadio());
        ButtonGroup delivery = new ButtonGroup();
        delivery.add(vendored);
        delivery.add(npm);
        Runnable syncMode = () -> {
            boolean npmMode = npm.isSelected();
            for (ClassicKit.Lib lib : ClassicKit.libraries()) {
                JCheckBox box = libraryBoxes.get(lib.id());
                boolean enabled = enabledFor(lib, npmMode);
                box.setEnabled(enabled);
                box.setToolTipText(PlainText.plain(enabled ? null : ClassicKit.PROTOTYPE_NPM_NOTE));
            }
        };
        vendored.addActionListener(ev -> syncMode.run());
        npm.addActionListener(ev -> syncMode.run());

        Map<String, JCheckBox> generatorBoxes = new LinkedHashMap<>();
        generatorLabels().forEach((id, label)
                -> generatorBoxes.put(id, new JCheckBox(PlainText.plain(label))));

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel(Bundle.ClassicKitAction_libraryHeading()));
        libraryBoxes.values().forEach(panel::add);
        panel.add(new JLabel(Bundle.ClassicKitAction_deliveryHeading()));
        panel.add(vendored);
        panel.add(npm);
        panel.add(new JLabel(Bundle.ClassicKitAction_buildToolHeading()));
        generatorBoxes.values().forEach(panel::add);
        panel.add(new JLabel(Bundle.ClassicKitAction_note()));

        DialogDescriptor descriptor = new DialogDescriptor(panel,
                Bundle.ClassicKitAction_title(project.getName()));
        if (DialogDisplayer.getDefault().notify(descriptor) != DialogDescriptor.OK_OPTION) {
            return;
        }

        Set<String> libraries = new LinkedHashSet<>();
        libraryBoxes.forEach((id, box) -> {
            if (box.isSelected() && box.isEnabled()) {
                libraries.add(id);
            }
        });
        Set<String> generators = new LinkedHashSet<>();
        generatorBoxes.forEach((id, box) -> {
            if (box.isSelected()) {
                generators.add(id);
            }
        });
        ClassicKit.Options opts = new ClassicKit.Options(libraries,
                npm.isSelected() ? ClassicKit.Mode.NPM : ClassicKit.Mode.VENDORED,
                generators);
        List<String> problems = ClassicKit.validate(opts, project);
        if (!problems.isEmpty()) {
            SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                    new NotifyDescriptor.Message(org.nmox.studio.core.util.PlainDialogs.plain(String.join("\n", problems), Bundle.ClassicKitAction_messageName()),
                            NotifyDescriptor.WARNING_MESSAGE)));
            return;
        }
        // disk I/O has no place in an event dispatch; the report then hops
        // back to a fresh EDT dispatch so it can't stack behind the main window
        org.openide.util.RequestProcessor.getDefault().post(() -> {
            try {
                List<ClassicKit.Outcome> outcomes = ClassicKit.write(project, opts);
                String report = renderReport(outcomes);
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(org.nmox.studio.core.util.PlainDialogs.plain(Bundle.ClassicKitAction_report(report), Bundle.ClassicKitAction_messageName()),
                                NotifyDescriptor.INFORMATION_MESSAGE)));
            } catch (Exception ex) {
                String message = Bundle.ClassicKitAction_couldNotWrite(ex.getMessage());
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(org.nmox.studio.core.util.PlainDialogs.plain(message, Bundle.ClassicKitAction_messageName()), NotifyDescriptor.ERROR_MESSAGE)));
            }
        });
    }
}
