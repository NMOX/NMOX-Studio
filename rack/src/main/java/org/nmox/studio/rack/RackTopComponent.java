package org.nmox.studio.rack;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import org.netbeans.api.settings.ConvertAsProperties;
import org.nmox.studio.rack.devices.DeviceType;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.RackIO;
import org.nmox.studio.rack.ui.PalettePanel;
import org.nmox.studio.rack.ui.RackPanel;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 * The Rack window: a Reason-style virtual rack where every web
 * development task is a hardware device. Drag devices in from the
 * shelf, twist their knobs, then hit Flip (Tab) to turn the rack
 * around and patch task pipelines together with cables.
 */
@ConvertAsProperties(
        dtd = "-//org.nmox.studio.rack//Rack//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "RackTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "editor", openAtStartup = true, position = 200)
@ActionID(category = "Window", id = "org.nmox.studio.rack.RackTopComponent")
@org.openide.awt.ActionReferences({
    @ActionReference(path = "Menu/Window", position = 250),
    @ActionReference(path = "Shortcuts", name = "D-9")
})
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_RackAction",
        preferredID = "RackTopComponent"
)
@Messages({
    "CTL_RackAction=Task Rack",
    "CTL_RackTopComponent=Task Rack",
    "HINT_RackTopComponent=Reason-style rack of web development task devices"
})
public final class RackTopComponent extends TopComponent {

    private final Rack rack = org.nmox.studio.rack.service.RackService.getDefault().getRack();
    private RackPanel rackPanel;
    private final JLabel projectLabel = new JLabel();
    private JToggleButton flipToggle;

    /**
     * Flips the rack on Tab whenever this window is active. Swing's focus
     * traversal normally consumes Tab before key bindings ever see it, so
     * an input-map binding is not enough - we intercept at the keyboard
     * focus manager, exactly while the rack is the activated TopComponent.
     */
    private static final int MENU_MASK = menuMask();

    private static int menuMask() {
        try {
            return java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (java.awt.HeadlessException ex) {
            // headless test JVMs have no toolkit; no key events arrive there
            // either, so any mask works — pick the cross-platform default
            return java.awt.event.InputEvent.CTRL_DOWN_MASK;
        }
    }

    private final java.awt.KeyEventDispatcher tabFlipDispatcher = e -> {
        if (e.getID() != KeyEvent.KEY_PRESSED
                || TopComponent.getRegistry().getActivated() != RackTopComponent.this) {
            return false;
        }
        boolean inText = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .getFocusOwner() instanceof javax.swing.text.JTextComponent;
        // ⌘Z undo / ⇧⌘Z redo — the biggest missing safety net on the rack
        if (e.getKeyCode() == KeyEvent.VK_Z && (e.getModifiersEx() & MENU_MASK) != 0 && !inText) {
            boolean shift = (e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) != 0;
            if (shift) {
                rack.redo();
            } else {
                rack.undo();
            }
            return true;
        }
        if (e.getModifiersEx() != 0) {
            return false;
        }
        if (e.getKeyCode() == KeyEvent.VK_TAB) {
            // faceplate controls are keyboard-operable: while one of them
            // (or the REPL's text field) holds focus, Tab must traverse to
            // the next control, not flip the rack — the toolbar toggle
            // still flips at any time
            java.awt.Component focus = java.awt.KeyboardFocusManager
                    .getCurrentKeyboardFocusManager().getFocusOwner();
            if (focus != null && focus != rackPanel && focus.isFocusable()
                    && javax.swing.SwingUtilities.isDescendingFrom(focus, rackPanel)) {
                return false;
            }
            flipToggle.doClick();
            return true;
        }
        if ((e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE)
                && rackPanel.getSelected() != null
                // never swallow Delete while something editable has focus
                && !inText) {
            rackPanel.removeSelected();
            return true;
        }
        return false;
    };

    public RackTopComponent() {
        setName(org.openide.util.NbBundle.getMessage(RackTopComponent.class, "CTL_RackTopComponent"));
        setToolTipText(org.openide.util.NbBundle.getMessage(RackTopComponent.class, "HINT_RackTopComponent"));
        setLayout(new BorderLayout());

        rackPanel = new RackPanel(rack);

        JScrollPane scroll = new JScrollPane(rackPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getViewport().setBackground(RackStyle.RACK_BG);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new PalettePanel(rack), scroll);
        split.setDividerLocation(230);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);

        add(buildToolbar(), BorderLayout.NORTH);

        setFocusTraversalKeysEnabled(false);

        updateProjectLabel();
        // the rack listener attaches in componentOpened, not here (ledger
        // item 17): constructor wiring on this singleton kept re-labelling
        // a closed window on every project switch, forever
    }

    /**
     * Attached in componentOpened, detached in componentClosed — the v1.35
     * listener-symmetry idiom (see ProjectExplorerTopComponent). The
     * componentOpened re-sync repaint covers whatever moved while closed.
     */
    private final Rack.Listener projectListener = new Rack.Listener() {
        @Override
        public void projectChanged() {
            javax.swing.SwingUtilities.invokeLater(RackTopComponent.this::updateProjectLabel);
        }
    };
    private boolean projectListenerAttached;

    private JToolBar buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        JButton chooseProject = new JButton("Project…");
        chooseProject.setToolTipText("Choose the project directory the rack operates on");
        chooseProject.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(rack.getProjectDir());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Select Project Directory");
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                // a saved patch in the chosen project loads automatically
                org.nmox.studio.rack.service.RackService.getDefault()
                        .openProject(chooser.getSelectedFile());
            }
        });
        bar.add(chooseProject);

        projectLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        bar.add(projectLabel);
        bar.addSeparator();

        flipToggle = new JToggleButton("Rear (Tab)");
        flipToggle.setToolTipText("Flip the rack around to patch cables (Tab)");
        flipToggle.setFocusable(false);
        flipToggle.addActionListener(e -> {
            rackPanel.setFront(!flipToggle.isSelected());
            flipToggle.setText(flipToggle.isSelected() ? "Front (Tab)" : "Rear (Tab)");
        });
        bar.add(flipToggle);
        bar.addSeparator();

        JButton save = new JButton("Save Patch");
        save.addActionListener(e -> {
            File target = new File(rack.getProjectDir(), RackIO.DEFAULT_FILENAME);
            try {
                RackIO.save(rack, target);
                // momentary confirmation, then back to the plain name -
                // never appended onto itself across repeated saves
                projectLabel.setText(rack.getProjectDir().getName() + "  [saved]");
                javax.swing.Timer revert = new javax.swing.Timer(2000, ev -> updateProjectLabel());
                revert.setRepeats(false);
                revert.start();
            } catch (IOException ex) {
                error("Could not save the patch: " + ex.getMessage());
            }
        });
        bar.add(save);

        JButton load = new JButton("Load Patch");
        load.addActionListener(e -> {
            File source = new File(rack.getProjectDir(), RackIO.DEFAULT_FILENAME);
            if (source.isFile()) {
                loadPatch(source);
            } else {
                info("No " + RackIO.DEFAULT_FILENAME + " in project.");
            }
        });
        bar.add(load);

        JButton presets = new JButton("Presets ▾");
        presets.setToolTipText("Wire a ready-made pipeline into the rack");
        presets.addActionListener(e -> {
            javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
            for (org.nmox.studio.rack.projectstudio.RackPresets preset
                    : org.nmox.studio.rack.projectstudio.RackPresets.values()) {
                javax.swing.JMenuItem item = new javax.swing.JMenuItem(preset.getDisplayName());
                item.setToolTipText(preset.getDescription());
                item.addActionListener(a -> {
                    try {
                        RackIO.fromJson(rack, preset.buildPatch());
                    } catch (RuntimeException ex) {
                        error("Could not wire the preset: " + ex.getMessage());
                    }
                });
                menu.add(item);
            }
            menu.show(presets, 0, presets.getHeight());
        });
        bar.add(presets);

        JButton exportCi = new JButton("Export CI…");
        exportCi.setToolTipText("Compile this patch into .github/workflows/nmox-rack.yml —"
                + " the same commands the rack runs, as a GitHub Actions pipeline");
        exportCi.addActionListener(e -> {
            try {
                String yaml = org.nmox.studio.rack.projectstudio.CiExporter.toWorkflowYaml(rack);
                File dir = new File(rack.getProjectDir(), ".github/workflows");
                java.nio.file.Files.createDirectories(dir.toPath());
                File out = new File(dir, "nmox-rack.yml");
                java.nio.file.Files.writeString(out.toPath(), yaml, java.nio.charset.StandardCharsets.UTF_8);
                org.openide.awt.StatusDisplayer.getDefault()
                        .setStatusText("Exported " + out.getAbsolutePath());
                org.nmox.studio.rack.engine.FileLink.open(
                        new org.nmox.studio.rack.engine.FileLink.Location(out, 1));
            } catch (Exception ex) {
                error("Could not export the CI workflow: " + ex.getMessage());
            }
        });
        bar.add(exportCi);
        bar.addSeparator();

        JButton stopAll = new JButton("Stop All");
        stopAll.setForeground(new Color(180, 40, 40));
        stopAll.setToolTipText("Kill every process the rack is running");
        stopAll.addActionListener(e -> {
            // async: panic() escalates TERM → grace → KILL and can block
            // ~2.5s per stubborn device — that must not freeze the paint
            // thread (ledger item 15). The devices' own STOP LEDs/status
            // show the honest per-device state; the button disables until
            // the pass completes so it cannot double-fire.
            int live = 0;
            for (RackDevice d : rack.getDevices()) {
                if (d.isLive()) {
                    live++;
                }
            }
            final int n = live;
            boolean started = rack.stopAllAsync(() -> {
                stopAll.setEnabled(true);
                if (n > 0) {
                    org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                            "Stopped " + n + (n == 1 ? " tool" : " tools"));
                }
            });
            if (started) {
                stopAll.setEnabled(false);
                if (n > 0) {
                    org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                            "Stopping " + n + (n == 1 ? " tool…" : " tools…"));
                }
            }
        });
        bar.add(stopAll);
        return bar;
    }

    private void loadPatch(File file) {
        try {
            RackIO.load(rack, file);
        } catch (IOException | RuntimeException ex) {
            error("Could not load the patch: " + ex.getMessage());
        }
    }

    // ---- platform dialogs (parented, keyboard-correct, consistent chrome) ----

    private void info(String message) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                message, NotifyDescriptor.INFORMATION_MESSAGE));
    }

    private void error(String message) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                message, NotifyDescriptor.ERROR_MESSAGE));
    }

    private void updateProjectLabel() {
        projectLabel.setText(rack.getProjectDir().getName());
        projectLabel.setToolTipText(rack.getProjectDir().getAbsolutePath());
    }

    @Override
    public void componentOpened() {
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(tabFlipDispatcher);
        if (!projectListenerAttached) {
            rack.addListener(projectListener);
            projectListenerAttached = true;
        }
        updateProjectLabel(); // re-sync: the aim may have moved while closed
    }

    @Override
    public void componentClosed() {
        // keep processes alive when the window merely closes; the rack
        // survives until the module is unloaded
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removeKeyEventDispatcher(tabFlipDispatcher);
        if (projectListenerAttached) {
            rack.removeListener(projectListener);
            projectListenerAttached = false;
        }
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
        p.setProperty("projectDir", rack.getProjectDir().getAbsolutePath());
    }

    void readProperties(java.util.Properties p) {
        // restore last session's aim - but never clobber a choice the
        // user already made this session (e.g. the New Project wizard
        // aimed the rack before this window deserialized)
        String dir = p.getProperty("projectDir");
        if (dir != null) {
            org.nmox.studio.rack.service.RackService.getDefault()
                    .openProjectPassively(new File(dir));
        }
    }
}
