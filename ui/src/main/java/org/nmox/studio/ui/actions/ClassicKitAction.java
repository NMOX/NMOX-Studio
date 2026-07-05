package org.nmox.studio.ui.actions;

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
@Messages("CTL_ClassicKitAction=Classic Kit...")
public final class ClassicKitAction implements ActionListener {

    /** Checkbox text; Backbone announces the Underscore it brings along. */
    static String libraryLabel(ClassicKit.Lib lib) {
        if ("backbone".equals(lib.id())) {
            return lib.label() + " (+ " + ClassicKit.underscore().label()
                    + " — hard dependency, wired first)";
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
        labels.put("webpack", "webpack.config.js — entry auto-detected, dist/bundle.js, dev server");
        labels.put("grunt", "Gruntfile.js — uglify js/ into dist/, watch, build/default tasks");
        labels.put("gulp", "gulpfile.js — gulp 4 exports: build, watch");
        labels.put("bower", "bower.json — name from folder, records vendored libraries");
        return labels;
    }

    /** The report surface, Standards Kit style: ✓ changed, – left alone. */
    static String renderReport(List<ClassicKit.Outcome> outcomes) {
        StringBuilder report = new StringBuilder();
        for (ClassicKit.Outcome o : outcomes) {
            report.append(o.changed() ? "  ✓ " : "  – ").append(o.path());
            if (!"written".equals(o.status())) {
                report.append("  (").append(o.status()).append(')');
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
                    "Aim the studio at a project first (open a folder or project)."));
            return;
        }

        Map<String, JCheckBox> libraryBoxes = new LinkedHashMap<>();
        for (ClassicKit.Lib lib : ClassicKit.libraries()) {
            libraryBoxes.put(lib.id(), new JCheckBox(libraryLabel(lib),
                    "jquery".equals(lib.id())));
        }
        JRadioButton vendored = new JRadioButton(
                "Vendored — pinned builds copied into vendor/, script tags wired into index.html", true);
        JRadioButton npm = new JRadioButton(
                "npm — added to package.json dependencies (no network run here)");
        ButtonGroup delivery = new ButtonGroup();
        delivery.add(vendored);
        delivery.add(npm);
        Runnable syncMode = () -> {
            boolean npmMode = npm.isSelected();
            for (ClassicKit.Lib lib : ClassicKit.libraries()) {
                JCheckBox box = libraryBoxes.get(lib.id());
                boolean enabled = enabledFor(lib, npmMode);
                box.setEnabled(enabled);
                box.setToolTipText(enabled ? null : ClassicKit.PROTOTYPE_NPM_NOTE);
            }
        };
        vendored.addActionListener(ev -> syncMode.run());
        npm.addActionListener(ev -> syncMode.run());

        Map<String, JCheckBox> generatorBoxes = new LinkedHashMap<>();
        generatorLabels().forEach((id, label)
                -> generatorBoxes.put(id, new JCheckBox(label)));

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 4));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel("Add a classic library:"));
        libraryBoxes.values().forEach(panel::add);
        panel.add(new JLabel("Delivery:"));
        panel.add(vendored);
        panel.add(npm);
        panel.add(new JLabel("Add a build tool:"));
        generatorBoxes.values().forEach(panel::add);
        panel.add(new JLabel("<html><small>Existing files are never overwritten — "
                + "an existing config gets a .suggested sibling instead.</small></html>"));

        DialogDescriptor descriptor = new DialogDescriptor(panel,
                "Classic Kit — " + project.getName());
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
                    new NotifyDescriptor.Message(String.join("\n", problems),
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
                        new NotifyDescriptor.Message("Classic Kit:\n\n" + report,
                                NotifyDescriptor.INFORMATION_MESSAGE)));
            } catch (Exception ex) {
                String message = "Could not write: " + ex.getMessage();
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(message, NotifyDescriptor.ERROR_MESSAGE)));
            }
        });
    }
}
