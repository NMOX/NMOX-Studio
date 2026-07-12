package org.nmox.studio.ui.actions;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.nmox.studio.core.process.ToolLocator;
import org.nmox.studio.rack.projectstudio.LearningCatalog;
import org.nmox.studio.rack.projectstudio.LearningSpace;
import org.nmox.studio.rack.service.RackService;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;

/**
 * New Learning Space: pick a language, stack, framework, or library
 * from the catalog and the studio generates a real project you learn
 * by doing — sample code, a tutorial that walks it, and a rack already
 * wired with a REPL (or run command) pointed at the right tool. Type in
 * the search box to filter the fifty-plus spaces.
 */
@ActionID(category = "File", id = "org.nmox.studio.ui.actions.NewLearningSpaceAction")
@ActionRegistration(displayName = "#CTL_NewLearningSpaceAction")
@ActionReferences({
    @ActionReference(path = "Menu/File", position = 119),
    @ActionReference(path = "Shortcuts", name = "DS-L")
})
@Messages("CTL_NewLearningSpaceAction=New Learning Space...")
public final class NewLearningSpaceAction implements ActionListener {

    private static final Color TOOL_OK = new Color(96, 176, 96);
    private static final Color TOOL_MISSING = new Color(214, 143, 60);
    private static final Color TOOL_PROBING = new Color(128, 128, 128);

    /** Catalog reads touch ~/.nmox/learn-catalog.d — disk IO off the EDT. */
    private static final org.openide.util.RequestProcessor CATALOG_RP =
            new org.openide.util.RequestProcessor("nmox-learn-catalog", 1);

    @Override
    public void actionPerformed(ActionEvent e) {
        // the drop-in scan lists and parses ~/.nmox/learn-catalog.d — local
        // and shallow, but still file IO the v1.33.1 lesson says to keep off
        // the EDT (a network-mounted home must not freeze the menu click)
        CATALOG_RP.post(() -> {
            List<LearningCatalog.Space> all = LearningCatalog.all();
            java.awt.EventQueue.invokeLater(() -> showPicker(all));
        });
    }

    private void showPicker(List<LearningCatalog.Space> all) {
        if (all.isEmpty()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "The learning catalog is empty or unreadable."));
            return;
        }

        DefaultListModel<LearningCatalog.Space> model = new DefaultListModel<>();
        all.forEach(model::addElement);
        JList<LearningCatalog.Space> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v,
                    int i, boolean sel, boolean focus) {
                LearningCatalog.Space s = (LearningCatalog.Space) v;
                String label = "<html><b>" + escape(s.name()) + "</b>  <font color='#888'>"
                        + s.category().label.toLowerCase(Locale.ROOT) + " · " + escape(s.family())
                        + "</font><br><font color='#aaa'><small>" + escape(s.blurb())
                        + "</small></font></html>";
                return super.getListCellRendererComponent(l, label, i, sel, focus);
            }
        });

        JTextField search = new JTextField();
        search.setToolTipText("Filter by name, family, or description");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refilter(); }
            @Override public void removeUpdate(DocumentEvent e) { refilter(); }
            @Override public void changedUpdate(DocumentEvent e) { refilter(); }

            private void refilter() {
                String q = search.getText().trim().toLowerCase(Locale.ROOT);
                model.clear();
                for (LearningCatalog.Space s : all) {
                    if (q.isEmpty() || matches(s, q)) {
                        model.addElement(s);
                    }
                }
                if (!model.isEmpty()) {
                    list.setSelectedIndex(0);
                }
            }
        });

        // availability up front: does this machine have the space's tool?
        JLabel availability = new JLabel(" ");
        availability.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 2, 0, 2));
        Map<String, Boolean> probeCache = new HashMap<>(); // EDT-confined, dialog-lifetime
        list.addListSelectionListener(ev -> {
            if (!ev.getValueIsAdjusting()) {
                updateAvailability(list, availability, probeCache);
            }
        });
        updateAvailability(list, availability, probeCache);

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel("Learn by doing — pick a language, framework, or library:"),
                BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.add(search, BorderLayout.NORTH);
        body.add(new JScrollPane(list), BorderLayout.CENTER);
        body.add(availability, BorderLayout.SOUTH);
        panel.add(body, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(560, 460));

        DialogDescriptor descriptor = new DialogDescriptor(panel, "New Learning Space");
        if (DialogDisplayer.getDefault().notify(descriptor) != DialogDescriptor.OK_OPTION) {
            return;
        }
        LearningCatalog.Space chosen = list.getSelectedValue();
        if (chosen == null) {
            return;
        }
        // generation writes sample files, a tutorial, and a pre-wired rack —
        // off the EDT; the open-and-notify hops back on
        org.openide.util.RequestProcessor.getDefault().post(() -> {
            try {
                File dir = LearningSpace.create(chosen);
                SwingUtilities.invokeLater(() -> openSpace(dir, chosen));
            } catch (Exception ex) {
                String message = "Could not create the learning space: " + ex.getMessage();
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(message, NotifyDescriptor.ERROR_MESSAGE)));
            }
        });
    }

    private static void openSpace(File dir, LearningCatalog.Space space) {
        RackService.getDefault().openProject(dir);
        openInEditor(new File(dir, space.openFile()));
        org.openide.windows.TopComponent rack = org.openide.windows.WindowManager
                .getDefault().findTopComponent("RackTopComponent");
        if (rack != null) {
            rack.open();
        }
    }

    private static void openInEditor(File file) {
        try {
            org.openide.filesystems.FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
            if (fo != null) {
                OpenCookie open = DataObject.find(fo).getLookup().lookup(OpenCookie.class);
                if (open != null) {
                    open.open();
                }
            }
        } catch (Exception ignored) {
            // the tutorial is on disk regardless; the file tree can open it
        }
    }

    /**
     * Availability up front: probes the selected space's required tool
     * with ToolLocator OFF the EDT, caches verdicts per tool name for
     * the dialog's lifetime, and drops stale results — a probe only
     * lands if the selection still requires the tool it probed (the NPM
     * explorer's currentProjectDir guard, in miniature).
     */
    private static void updateAvailability(JList<LearningCatalog.Space> list,
            JLabel label, Map<String, Boolean> cache) {
        LearningCatalog.Space space = list.getSelectedValue();
        String tool = space == null ? null : requiredTool(space);
        if (tool == null) {
            label.setText(" ");
            return;
        }
        Boolean found = cache.get(tool);
        if (found != null) {
            renderAvailability(label, tool, found, LearningSpace.installHint(space));
            return;
        }
        label.setForeground(TOOL_PROBING);
        label.setText("requires " + tool + " — checking…");
        org.openide.util.RequestProcessor.getDefault().post(() -> {
            // present = ToolLocator resolves the bare name to a real path
            boolean resolved = !ToolLocator.resolve(tool).equals(tool);
            SwingUtilities.invokeLater(() -> {
                cache.put(tool, resolved);
                LearningCatalog.Space now = list.getSelectedValue();
                if (now != null && tool.equals(requiredTool(now))) {
                    renderAvailability(label, tool, resolved, LearningSpace.installHint(now));
                }
            });
        });
    }

    private static void renderAvailability(JLabel label, String tool, boolean found,
            String installHint) {
        label.setForeground(found ? TOOL_OK : TOOL_MISSING);
        label.setText(availabilityText(tool, found, installHint));
    }

    /** The availability line, pure: the ✓/✗ verdict plus the OS-appropriate install command. */
    static String availabilityText(String tool, boolean found, String installHint) {
        if (found) {
            return "requires " + tool + " — ✓ found";
        }
        return "requires " + tool + " — ✗ not found"
                + (installHint == null || installHint.isBlank() ? "" : " · " + installHint);
    }

    /**
     * The external tool a space needs on PATH: the driver's first
     * command token (the interpreter for REPL spaces, the runner for
     * run spaces). Null when there is nothing meaningful to probe — no
     * command at all, or a project-relative script (bin/rails) that
     * cannot exist before the space is generated.
     */
    static String requiredTool(LearningCatalog.Space space) {
        List<String> command = space.driver() == null ? List.of() : space.driver().command();
        if (command.isEmpty()) {
            return null;
        }
        String tool = command.get(0).trim();
        return tool.isEmpty() || tool.contains("/") || tool.contains("\\") ? null : tool;
    }

    private static boolean matches(LearningCatalog.Space s, String q) {
        return matches(s.name(), s.family(), s.slug(), s.blurb(), q);
    }

    /**
     * The pure filter discipline, testable without building a whole
     * catalog Space: a lower-cased query hits any of name, family, slug,
     * or blurb (slug is already lower-cased, so it matches literally).
     */
    static boolean matches(String name, String family, String slug, String blurb, String q) {
        return name.toLowerCase(Locale.ROOT).contains(q)
                || family.toLowerCase(Locale.ROOT).contains(q)
                || slug.contains(q)
                || blurb.toLowerCase(Locale.ROOT).contains(q);
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
