package org.nmox.studio.ui.actions;

import org.nmox.studio.core.util.PlainText;
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
    @ActionReference(path = "Menu/File", position = 112),
    @ActionReference(path = "Shortcuts", name = "DS-L")
})
@Messages({
    "CTL_NewLearningSpaceAction=New Learning Space…",
    "NewLearningSpaceAction_catalogEmpty=The learning catalog is empty or unreadable.",
    "NewLearningSpaceAction_listName=Learning spaces",
    "NewLearningSpaceAction_searchName=Search the learning spaces",
    "NewLearningSpaceAction_searchTip=Filter by name, family, or description",
    "NewLearningSpaceAction_heading=Learn by doing — pick a language, framework, or library:",
    "NewLearningSpaceAction_title=New Learning Space",
    "NewLearningSpaceAction_couldNotCreate=Could not create the learning space: {0}",
    "NewLearningSpaceAction_messageName=Message",
    "NewLearningSpaceAction_requiresChecking=requires {0} — checking…",
    "NewLearningSpaceAction_requiresFound=requires {0} — ✓ found",
    "NewLearningSpaceAction_requiresMissing=requires {0} — ✗ not found",
    "NewLearningSpaceAction_requiresMissingHint=requires {0} — ✗ not found · {1}"
})
public final class NewLearningSpaceAction implements ActionListener {

    /**
     * One picker row, bound to the width it is given (v2.120.0).
     *
     * <p>An unbounded HTML label is as wide as its longest line, so the
     * list's preferred width ran past its own viewport: the walk of the
     * picker photographed blurbs clipped mid-word with a horizontal
     * scrollbar under them — the ledger-75 class (v1.273.0: squeeze to the
     * viewport, never grow a sideways scrollbar). Given a width the blurb
     * WRAPS, so unlike an ellipsis nothing is cut at all.
     *
     * <p>Unitless on purpose: Swing's CSS honours {@code width: 520} and
     * ignores {@code width: 520px} (measured v2.84.0).
     */
    static String cellHtml(LearningCatalog.Space s, int room) {
        return "<html><body style='width: " + room + "'><b>"
                + escape(s.shown().name()) + "</b>  <font color='#888'>"
                + CatalogText.category(s.category()).toLowerCase(Locale.ROOT)
                + " · " + escape(CatalogText.family(s.family()))
                + "</font><br><font color='#aaa'><small>" + escape(s.shown().blurb())
                + "</small></font></body></html>";
    }

    /** Scrollbar, selection border and cell padding the text does not get. */
    private static final int LIST_TEXT_INSET = 40;


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
                    Bundle.NewLearningSpaceAction_catalogEmpty()));
            return;
        }

        DefaultListModel<LearningCatalog.Space> model = new DefaultListModel<>();
        all.forEach(model::addElement);
        JList<LearningCatalog.Space> list = new JList<>(model);
        list.getAccessibleContext().setAccessibleName(Bundle.NewLearningSpaceAction_listName());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v,
                    int i, boolean sel, boolean focus) {
                LearningCatalog.Space s = (LearningCatalog.Space) v;
                String label = cellHtml(s, Math.max(320, l.getWidth() - LIST_TEXT_INSET));
                return super.getListCellRendererComponent(l, label, i, sel, focus);
            }
        });

        JTextField search = new JTextField();

        search.getAccessibleContext().setAccessibleName(Bundle.NewLearningSpaceAction_searchName());
        search.setToolTipText(Bundle.NewLearningSpaceAction_searchTip());
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
        panel.add(new JLabel(Bundle.NewLearningSpaceAction_heading()),
                BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.add(search, BorderLayout.NORTH);
        body.add(new JScrollPane(list), BorderLayout.CENTER);
        body.add(availability, BorderLayout.SOUTH);
        panel.add(body, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(560, 460));

        DialogDescriptor descriptor = new DialogDescriptor(panel, Bundle.NewLearningSpaceAction_title());
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
                String message = Bundle.NewLearningSpaceAction_couldNotCreate(ex.getMessage());
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
                        new NotifyDescriptor.Message(org.nmox.studio.core.util.PlainDialogs.plain(message, Bundle.NewLearningSpaceAction_messageName()), NotifyDescriptor.ERROR_MESSAGE)));
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
        label.setText(Bundle.NewLearningSpaceAction_requiresChecking(tool));
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
        label.setText(PlainText.plain(availabilityText(tool, found, installHint)));
    }

    /** The availability line, pure: the ✓/✗ verdict plus the OS-appropriate install command. */
    static String availabilityText(String tool, boolean found, String installHint) {
        if (found) {
            return Bundle.NewLearningSpaceAction_requiresFound(tool);
        }
        return installHint == null || installHint.isBlank()
                ? Bundle.NewLearningSpaceAction_requiresMissing(tool)
                : Bundle.NewLearningSpaceAction_requiresMissingHint(tool, installHint);
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
        return matches(s.shown().name(), CatalogText.family(s.family()), s.slug(),
                s.shown().blurb(), q);
    }

    /**
     * The pure filter discipline, testable without building a whole
     * catalog Space: a lower-cased query hits any of name, family, slug,
     * or blurb (slug is already lower-cased, so it matches literally).
     */
    static boolean matches(String name, String family, String slug, String blurb, String q) {
        // through the product's one matcher since v2.106.0 — this picker
        // predates the v1.215.0 findability sprint and kept the raw
        // contains it replaced, so a two-word query in the wrong order
        // missed and an accented space name could not be typed plainly.
        // The slug keeps its literal check beside it: it is a machine id,
        // and pasting one is a real habit.
        return org.nmox.studio.core.search.SearchTerms.matches(q, name, family, blurb)
                || slug.contains(q);
    }

    static String escape(String s) {
        return PlainText.escape(s);
    }
}
