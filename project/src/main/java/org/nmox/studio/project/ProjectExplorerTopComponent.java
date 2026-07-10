package org.nmox.studio.project;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.netbeans.api.settings.ConvertAsProperties;
import org.nmox.studio.rack.devices.ProjectInspector;
import org.nmox.studio.rack.service.RackService;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * The Workbench: a developer's home base in the left dock. Answers the
 * three questions every arrival asks - what am I working on (current
 * project + its toolchains), where was I (open and recent files,
 * recent projects), and what can this thing do (the tooling shelf).
 * One click aims the whole IDE: rack, studio, and trail follow.
 */
@ConvertAsProperties(
        dtd = "-//org.nmox.studio.project//ProjectExplorer//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "ProjectExplorerTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "explorer", openAtStartup = true, position = 60)
@ActionID(category = "Window", id = "org.nmox.studio.project.ProjectExplorerTopComponent")
@org.openide.awt.ActionReferences({
    @ActionReference(path = "Menu/Window", position = 205),
    // Cmd+Alt (DA-) — the studio row lives in the one digit family no
    // shipped module claims. The old chord opened a platform window
    // instead of this one: ⌘0 was the platform's Editor window. Keymaps-profile
    // registrations beat Shortcuts-folder ones, so a layer-only audit
    // misses these; WindowShortcutsTest pins the reserved list.
    @ActionReference(path = "Shortcuts", name = "DA-0")
})
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_ProjectExplorerAction",
        preferredID = "ProjectExplorerTopComponent"
)
@Messages({
    "CTL_ProjectExplorerAction=Workbench",
    "CTL_ProjectExplorerTopComponent=Workbench",
    "HINT_ProjectExplorerTopComponent=Your home base: current project, open and recent files, projects and tooling"
})
public final class ProjectExplorerTopComponent extends TopComponent {

    // the workbench palette: the rack's dark-hardware language, lighter weight
    private static final Color BG = new Color(25, 26, 29);
    private static final Color SECTION = new Color(140, 142, 148);
    private static final Color TEXT = new Color(206, 208, 212);
    private static final Color TEXT_DIM = new Color(140, 142, 148);
    private static final Color HOVER = new Color(42, 43, 48);
    private static final Color ACCENT = new Color(80, 235, 100);
    private static final Color MODIFIED = new Color(255, 190, 60);
    private static final Color CHIP_BG = new Color(46, 47, 52);
    private static final Font NAME_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    private static final Font ROW_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font ROW_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private static final Font TINY = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
    private static final Font CHIP_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 10);

    private final JPanel content = new JPanel();
    private final JPanel header = new JPanel();
    /** Toolchain detection (File.list-heavy) runs here, never on the EDT. */
    private final org.openide.util.RequestProcessor detector =
            new org.openide.util.RequestProcessor("nmox-workbench-detect", 1, true);

    /**
     * Collapses a burst of registry events into a single deferred refresh.
     * At startup the window system opens and activates ~10 TopComponents in a
     * tight run, each firing PROP_OPENED and PROP_ACTIVATED; reacting to every
     * one with a full {@link #refresh()} (which rebuilds the whole panel and
     * spawns a background detection task per row) is what let the burst
     * compound into an EDT-starving post storm. Coalescing drops any event that
     * arrives while a refresh is already queued, so N events cost one refresh.
     */
    private final RefreshCoalescer refreshCoalescer = new RefreshCoalescer(this::refresh);

    private final PropertyChangeListener registryListener = (PropertyChangeEvent evt) -> {
        String p = evt.getPropertyName();
        if (TopComponent.Registry.PROP_OPENED.equals(p)
                || TopComponent.Registry.PROP_ACTIVATED.equals(p)) {
            refreshCoalescer.request();
        }
    };

    /**
     * Rack aim changes fold into the same coalesced refresh. Added and
     * removed in componentOpened/componentClosed beside registryListener —
     * a listener that lives forever keeps rebuilding the CLOSED workbench
     * offscreen (per-project detectAsync walks included) on every switch.
     * Created lazily inside the rack-availability guard, kept for reuse so
     * add and remove always see the same instance.
     */
    private org.nmox.studio.rack.model.Rack.Listener rackListener;

    /**
     * Guards double-attach: the rack's listener list is a
     * CopyOnWriteArrayList, so adding the same listener twice would
     * double-fire every event.
     */
    private boolean rackListenerAttached;

    public ProjectExplorerTopComponent() {
        setName(NbBundle.getMessage(ProjectExplorerTopComponent.class, "CTL_ProjectExplorerTopComponent"));
        setToolTipText(NbBundle.getMessage(ProjectExplorerTopComponent.class, "HINT_ProjectExplorerTopComponent"));
        setLayout(new BorderLayout());
        setBackground(BG);

        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(BG);
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        add(header, BorderLayout.NORTH);

        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG);
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        // No refresh here: this tab is open-at-startup, so componentOpened
        // always follows construction and runs its own re-sync refresh — a
        // constructor refresh just runs the toolchain-detect walk twice per
        // boot. An instance that is deserialized but never opened stays
        // blank, which is also right: nothing is looking at it.
    }

    @Override
    public void componentOpened() {
        TopComponent.getRegistry().addPropertyChangeListener(registryListener);
        if (!rackListenerAttached) {
            try {
                if (rackListener == null) {
                    rackListener = new org.nmox.studio.rack.model.Rack.Listener() {
                        @Override
                        public void projectChanged() {
                            // through the coalescer: an aim that lands amid the
                            // startup registry burst folds into one queued refresh
                            refreshCoalescer.request();
                        }
                    };
                }
                RackService.getDefault().getRack().addListener(rackListener);
                rackListenerAttached = true;
            } catch (RuntimeException | LinkageError ex) {
                // rack unavailable (tests, stripped platform): static workbench
            }
        }
        // one explicit re-sync: aims that happened while closed (listener
        // detached) must show the moment the workbench reopens
        refresh();
    }

    @Override
    public void componentClosed() {
        TopComponent.getRegistry().removePropertyChangeListener(registryListener);
        if (rackListenerAttached) {
            try {
                RackService.getDefault().getRack().removeListener(rackListener);
            } catch (RuntimeException | LinkageError ex) {
                // rack unavailable; nothing was attached to remove
            }
            rackListenerAttached = false;
        }
    }

    // ---- assembly ----

    private void refresh() {
        rebuildHeader();
        content.removeAll();
        addOpenFiles();
        addRecentFiles();
        addProjects();
        addTooling();
        content.add(Box.createVerticalGlue());
        content.revalidate();
        content.repaint();
    }

    private File projectDir() {
        try {
            return RackService.getDefault().getRack().getProjectDir();
        } catch (RuntimeException | LinkageError ex) {
            return new File(System.getProperty("user.home"));
        }
    }

    /** Current project: name, toolchain chips, path, New/Open actions. */
    private void rebuildHeader() {
        header.removeAll();
        File dir = projectDir();

        JLabel name = new JLabel(dir.getName());
        name.setFont(NAME_FONT);
        name.setForeground(TEXT);
        name.setAlignmentX(LEFT_ALIGNMENT);
        header.add(name);

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        chips.setBackground(BG);
        chips.setAlignmentX(LEFT_ALIGNMENT);
        chips.add(chip("detecting…", TEXT_DIM));
        header.add(chips);
        // Toolchain detection walks the project directory (File.list on every
        // manifest lane), which on a fresh $HOME aim would touch the
        // TCC-protected folders on the EDT. Run it on a background thread and
        // fill the chips when it returns.
        fillChipsAsync(chips, dir);

        JLabel path = new JLabel(dir.getAbsolutePath());
        path.setFont(TINY);
        path.setForeground(TEXT_DIM);
        path.setAlignmentX(LEFT_ALIGNMENT);
        header.add(path);
        header.add(Box.createVerticalStrut(8));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setBackground(BG);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        JButton fresh = new JButton("New Project…");
        fresh.addActionListener(e -> newProject());
        JButton open = new JButton("Open…");
        open.setToolTipText("Aim the whole IDE — rack, studio, workbench — at a project directory");
        open.addActionListener(e -> openProjectDialog());
        actions.add(fresh);
        actions.add(open);
        header.add(actions);
        header.revalidate();
        header.repaint();
    }

    private List<String> detectKindNames(File dir) {
        List<String> names = new ArrayList<>();
        try {
            var kinds = ProjectInspector.detectKinds(dir).keySet();
            int shown = 0;
            for (var kind : kinds) {
                if (++shown > 4) {
                    names.add("+" + (kinds.size() - 4));
                    break;
                }
                names.add(kind.name().toLowerCase());
            }
            // classic web libraries ride the same background walk: one
            // chip per detected library, version and honest EOL note
            // included ("jquery 1.12.4 — EOL"). Subtle by design — no
            // dialogs, no alarm colors, just the truth on a chip.
            for (var library : org.nmox.studio.rack.devices.LegacyWeb.scan(dir)) {
                names.add(library.label());
            }
        } catch (RuntimeException | LinkageError ex) {
            // detection is decoration; the workbench works without it
        }
        return names;
    }

    /**
     * Detects {@code dir}'s toolchains on the background detector and replaces
     * the placeholder chip with the real chips on the EDT. The chips panel is
     * only touched if it is still showing (the header wasn't rebuilt under us).
     */
    private void fillChipsAsync(JPanel chips, File dir) {
        WorkbenchDetect.detectAsync(detector, dir, this::detectKindNames, kinds -> {
            if (chips.getParent() == null) {
                return; // header rebuilt since; a newer request owns the chips
            }
            chips.removeAll();
            if (kinds.isEmpty()) {
                chips.add(chip("no toolchain yet", TEXT_DIM));
            } else {
                for (String kind : kinds) {
                    chips.add(chip(kind, ACCENT));
                }
            }
            chips.revalidate();
            chips.repaint();
        });
    }

    // ---- sections ----

    /** Editor tabs open right now; the active one leads in bold. */
    private void addOpenFiles() {
        section("OPEN FILES");
        TopComponent active = TopComponent.getRegistry().getActivated();
        int count = 0;
        for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
            if (!WindowManager.getDefault().isOpenedEditorTopComponent(tc)) {
                continue;
            }
            DataObject dob = tc.getLookup().lookup(DataObject.class);
            if (dob == null) {
                continue;
            }
            File file = FileUtil.toFile(dob.getPrimaryFile());
            String title = dob.getPrimaryFile().getNameExt();
            boolean isActive = tc == active;
            boolean modified = dob.isModified();
            row(title, file != null ? file.getParent() : null,
                    isActive, modified ? MODIFIED : null,
                    file != null ? file.getAbsolutePath() : title,
                    tc::requestActive);
            count++;
        }
        if (count == 0) {
            emptyRow("nothing open — pick up where you left off below");
        }
    }

    /** The trail: recently touched files not currently open. */
    private void addRecentFiles() {
        section("RECENT FILES");
        java.util.Set<String> openPaths = new java.util.HashSet<>();
        for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
            DataObject dob = tc.getLookup().lookup(DataObject.class);
            if (dob != null) {
                File f = FileUtil.toFile(dob.getPrimaryFile());
                if (f != null) {
                    openPaths.add(f.getAbsolutePath());
                }
            }
        }
        int count = 0;
        for (File file : RecentFiles.list()) {
            if (openPaths.contains(file.getAbsolutePath()) || ++count > 10) {
                continue;
            }
            row(file.getName(), file.getParent(), false, null,
                    file.getAbsolutePath(), () -> openFile(file));
        }
        if (count == 0) {
            emptyRow("files you open will gather here");
        }
    }

    /** Recent projects; the aimed one carries the green dot. */
    private void addProjects() {
        section("PROJECTS");
        File current = projectDir();
        List<File> recents;
        try {
            recents = RackService.getDefault().getRecentProjects();
        } catch (RuntimeException | LinkageError ex) {
            recents = List.of();
        }
        if (!recents.contains(current)) {
            recents = new ArrayList<>(recents);
            recents.add(0, current);
        }
        for (File dir : recents) {
            boolean aimed = dir.equals(current);
            // subtitle starts as the parent path; toolchain detection (which
            // walks the directory) resolves off the EDT and refines it
            JLabel sub = row(dir.getName(), dir.getParent(),
                    aimed, aimed ? ACCENT : null,
                    dir.getAbsolutePath() + (aimed ? "  (aimed)" : "  — click to aim the IDE here"),
                    () -> aimAt(dir));
            if (sub != null) {
                WorkbenchDetect.detectAsync(detector, dir, this::detectKindNames, names -> {
                    String kinds = shorten(String.join(" · ", names), 38);
                    // idempotent: skip the setText (and the layout it triggers)
                    // when the subtitle already shows this value
                    if (!kinds.isEmpty() && sub.getParent() != null
                            && !kinds.equals(sub.getText())) {
                        sub.setText(kinds);
                    }
                });
            }
        }
    }

    /** The shelf: every workshop in the building, one click each. */
    private void addTooling() {
        section("TOOLING");
        row("Task Rack", "devices, cables, pipelines — Tab flips it",
                false, null, "The Reason-style rack of task devices",
                () -> openWindow("RackTopComponent"));
        row("Project Studio", "templates, file CRUD, package.json",
                false, null, "Create and configure projects",
                () -> openWindow("ProjectStudioTopComponent"));
        row("Infra Designer", "DigitalOcean · Hetzner · Cloudflare flows",
                false, null, "Design and deploy infrastructure Node-RED style",
                () -> openWindow("InfraDesignerTopComponent"));
        row("Docker Manager", "containers, images, disk reclaim, dockerize",
                false, null, "The Docker Panel — HARBOR's control room",
                () -> {
                    try {
                        org.nmox.studio.rack.docker.DockerPanelTopComponent.openPanel();
                    } catch (RuntimeException | LinkageError ex) {
                        // rack module unavailable; nothing to open
                    }
                });
        row("Terminal", "phosphor shell in the project directory",
                false, null, "Black glass, lime text", this::openTerminal);
    }

    // ---- actions ----

    private void newProject() {
        try {
            org.nmox.studio.rack.projectstudio.NewProjectDialog dialog =
                    new org.nmox.studio.rack.projectstudio.NewProjectDialog(this);
            dialog.setVisible(true);
            if (dialog.getCreatedProject() != null) {
                // the dialog aimed the rack; surface the studio for step two
                openWindow("ProjectStudioTopComponent");
            }
        } catch (RuntimeException | LinkageError ex) {
            // dialog unavailable; the studio's own New button still works
            openWindow("ProjectStudioTopComponent");
        }
    }

    private void openProjectDialog() {
        JFileChooser chooser = new JFileChooser(projectDir());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Open Project Directory");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            aimAt(chooser.getSelectedFile());
        }
    }

    private void aimAt(File dir) {
        try {
            RackService.getDefault().openProject(dir);
        } catch (RuntimeException | LinkageError ex) {
            // nothing to aim without the rack
        }
        refresh();
    }

    private void openFile(File file) {
        try {
            org.nmox.studio.rack.engine.FileLink.open(
                    new org.nmox.studio.rack.engine.FileLink.Location(file, 1));
        } catch (RuntimeException | LinkageError ex) {
            // file may have vanished; next refresh prunes it
        }
    }

    private void openWindow(String preferredId) {
        TopComponent tc = WindowManager.getDefault().findTopComponent(preferredId);
        if (tc != null) {
            tc.open();
            tc.requestActive();
        }
    }

    /** Finds the platform's terminal action wherever the module registered it. */
    private void openTerminal() {
        for (String id : new String[]{
                "org.netbeans.modules.dlight.terminal.action.LocalTerminalAction",
                "LocalTerminalAction"}) {
            javax.swing.Action action = org.openide.awt.Actions.forID("Window", id);
            if (action != null) {
                action.actionPerformed(new java.awt.event.ActionEvent(this, 0, "open"));
                return;
            }
        }
    }

    // ---- widgets ----

    private void section(String title) {
        JLabel label = new JLabel(title);
        label.setFont(TINY);
        label.setForeground(SECTION);
        label.setBorder(BorderFactory.createEmptyBorder(10, 12, 3, 12));
        label.setAlignmentX(LEFT_ALIGNMENT);
        content.add(label);
    }

    private JLabel chip(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(CHIP_FONT);
        label.setForeground(color);
        label.setBackground(CHIP_BG);
        label.setOpaque(true);
        label.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
        return label;
    }

    private void emptyRow(String hint) {
        JLabel label = new JLabel(hint);
        label.setFont(ROW_FONT);
        label.setForeground(TEXT_DIM);
        label.setBorder(BorderFactory.createEmptyBorder(2, 18, 2, 12));
        label.setAlignmentX(LEFT_ALIGNMENT);
        content.add(label);
    }

    /**
     * One clickable row: title, dim subtitle, optional status dot. Returns the
     * subtitle label (or null when there is none) so a caller can refine it
     * later — e.g. after off-EDT toolchain detection.
     */
    private JLabel row(String title, String subtitle, boolean bold, Color dot,
            String tooltip, Runnable onClick) {
        JPanel rowPanel = new JPanel();
        rowPanel.setLayout(new BoxLayout(rowPanel, BoxLayout.X_AXIS));
        rowPanel.setBackground(BG);
        rowPanel.setBorder(BorderFactory.createEmptyBorder(3, 14, 3, 12));
        rowPanel.setAlignmentX(LEFT_ALIGNMENT);
        rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        rowPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        rowPanel.setToolTipText(tooltip);

        if (dot != null) {
            JLabel dotLabel = new JLabel("●");
            dotLabel.setFont(TINY);
            dotLabel.setForeground(dot);
            dotLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
            rowPanel.add(dotLabel);
        }
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(bold ? ROW_BOLD : ROW_FONT);
        titleLabel.setForeground(TEXT);
        rowPanel.add(titleLabel);
        JLabel sub = null;
        if (subtitle != null && !subtitle.isBlank()) {
            sub = new JLabel(shorten(subtitle, 38));
            sub.setFont(TINY);
            sub.setForeground(TEXT_DIM);
            sub.setBorder(BorderFactory.createEmptyBorder(0, 7, 0, 0));
            rowPanel.add(sub);
        }
        rowPanel.add(Box.createHorizontalGlue());

        rowPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                rowPanel.setBackground(HOVER);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                rowPanel.setBackground(BG);
            }

            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    onClick.run();
                }
            }
        });
        content.add(rowPanel);
        return sub;
    }

    /** Middle-ellipsis so deep paths keep their telling ends. */
    static String shorten(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        int keep = (max - 1) / 2;
        return s.substring(0, keep) + "…" + s.substring(s.length() - keep);
    }

    // ---- persistence ----

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "2.0");
    }

    void readProperties(java.util.Properties p) {
        // nothing to restore: the workbench reflects live state
    }
}
