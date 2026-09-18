package org.nmox.studio.rack;

import org.nmox.studio.core.util.PlainText;
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
    "HINT_RackTopComponent=Reason-style rack of web development task devices",
    "RackTopComponent_projectButton=Project…",
    "RackTopComponent_projectTooltip=Choose the project directory the rack operates on",
    "RackTopComponent_chooserTitle=Select Project Directory",
    "RackTopComponent_rearToggle=Rear (Tab)",
    "RackTopComponent_frontToggle=Front (Tab)",
    "RackTopComponent_flipTooltip=Flip the rack around to patch cables (Tab)",
    "RackTopComponent_savePatch=Save Patch",
    "RackTopComponent_savedLabel={0}  [saved]",
    "RackTopComponent_saveFailed=Could not save the patch: {0}",
    "RackTopComponent_loadPatch=Load Patch",
    "RackTopComponent_theSavedPatch=the saved patch",
    "RackTopComponent_noPatchInProject=No {0} in project.",
    "RackTopComponent_presetsButton=Presets ▾",
    "RackTopComponent_presetsTooltip=Wire a ready-made pipeline into the rack — drop a saved patch into ~/.nmox/presets.d to add your own",
    "RackTopComponent_exportCi=Export CI…",
    "RackTopComponent_exportCiTooltip=Compile this patch into .github/workflows/nmox-rack.yml — the same commands the rack runs, as a GitHub Actions pipeline",
    "RackTopComponent_exported=Exported {0}",
    "RackTopComponent_exportFailed=Could not export the CI workflow: {0}",
    "RackTopComponent_stopAll=Stop All",
    "RackTopComponent_stopAllTooltip=Kill every process the rack is running",
    "RackTopComponent_stoppedOne=Stopped {0} tool",
    "RackTopComponent_stoppedMany=Stopped {0} tools",
    "RackTopComponent_stoppingOne=Stopping {0} tool…",
    "RackTopComponent_stoppingMany=Stopping {0} tools…",
    "RackTopComponent_replaceConfirm=Replace the rack with {0}? This patch has unsaved changes, and loading cannot be undone — save it first if you want to keep it.",
    "RackTopComponent_replaceTitle=Replace Rack",
    "RackTopComponent_thePreset=the {0} preset",
    "RackTopComponent_presetFailed=Could not wire the preset: {0}",
    "RackTopComponent_yoursItem={0} · yours",
    "RackTopComponent_loadFailed=Could not load the patch: {0}",
    "RackTopComponent_shareRack=Share…",
    "RackTopComponent_shareTooltip=Save this rack as a file another NMOX Studio user can import — commands and settings travel, your home directory does not",
    "RackTopComponent_shareTitle=Share Rack As",
    "RackTopComponent_shareFilter=Rack patch (*.nmoxrack.json)",
    "RackTopComponent_sharedLabel={0}  [shared]",
    "RackTopComponent_shareFailed=Could not share the rack: {0}",
    "RackTopComponent_copiedLabel={0}  [copied]",
    "RackTopComponent_keptLabel=[kept as {0}]",
    "RackTopComponent_alreadyKept=My Racks already holds {0} — give this rack another name, or remove that one in the Rack Gallery first.",
    "RackTopComponent_leavingSummary={0} devices, {1} cables. Settings that travel:",
    "RackTopComponent_leavingNothing={0} devices, {1} cables. No command, path or address travels in its settings — the devices work out their commands from the project they land in.",
    "RackTopComponent_leavingSecrets=LOOKS LIKE A CREDENTIAL — take it out of the rack before sharing (shown masked here):",
    "RackTopComponent_leavingPaths=Still names somebody’s home directory:",
    "RackTopComponent_importRack=Import…",
    "RackTopComponent_importTooltip=Mount a rack someone shared as a file — you see what it holds before anything mounts, and nothing runs until you press GO",
    "RackTopComponent_importTitle=Import Rack",
    "RackTopComponent_importSummary={0} devices, {1} cables. Nothing runs on import; {2} saved armed or running arrive at rest.",
    "RackTopComponent_importUnknown=Not in this install (they mount as placeholders that keep their cables): {0}",
    "RackTopComponent_importSettings=Settings this rack carries — read them before mounting:",
    "RackTopComponent_importMount=Mount",
    "RackTopComponent_importFailed=Could not import the rack: {0}",
    "RackTopComponent_theSharedRack=the shared rack",
    "RackTopComponent_importSharedBy=Shared by {0}",
    "RackTopComponent_importMadeWithNewer=Made with NMOX Studio {0}, newer than this install — update to get everything it uses.",
    "RackTopComponent_importCarriesWhole=Every cable and setting it uses exists in this install.",
    "RackTopComponent_importLostCables=Cables this install cannot connect — it will mount WITHOUT them:",
    "RackTopComponent_importLostSettings=Settings this install’s devices do not have — ignored:",
    "RackTopComponent_importTooNew=This rack file is in format {0}; this install reads format {1}. Update NMOX Studio to import it.",
    "RackTopComponent_importClipboard=Import Rack from Clipboard",
    "RackTopComponent_gallery=Rack Gallery…",
    "RackTopComponent_removeConfirm=Remove {0} from My Racks? The file is deleted; racks already mounted from it are not touched.",
    "RackTopComponent_removeTitle=Remove from My Racks",
    "RackTopComponent_removedLabel=[removed {0}]",
    "RackTopComponent_removeFailed=Could not remove the rack: {0}",
    "RackTopComponent_theRack=the {0} rack",
    "RackTopComponent_clipboardEmpty=The clipboard holds no text.",
    "RackTopComponent_clipboardTooLarge=The clipboard text is too large to be a rack.",
    "RackTopComponent_clipboardNotJson=The clipboard text is not a rack — copy the whole file, from its first brace to its last.",
    "RackTopComponent_clipboardNoDevices=The clipboard holds JSON, but not a rack: it has no devices.",
    "RackTopComponent_importAimMoved=The project changed while the manifest was open — nothing was mounted. Import again."
})
public final class RackTopComponent extends TopComponent {

    private final Rack rack = org.nmox.studio.rack.service.RackService.getDefault().getRack();
    private RackPanel rackPanel;
    private final JLabel projectLabel = new JLabel();
    private JToggleButton flipToggle;

    /** Single-throughput writer lane for the interactive patch save. */
    private static final org.openide.util.RequestProcessor SAVE_RP =
            new org.openide.util.RequestProcessor("nmox-rack-patch-save", 1);

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

        rackScroll = new JScrollPane(rackPanel);
        JScrollPane scroll = rackScroll;
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
            // selection follows the aim only while this tab is showing —
            // componentShowing catches up on whatever moved while hidden
            if (aimNodeShowing) {
                aimPublisher.publish(rack.getProjectDir());
            }
        }
    };
    private boolean projectListenerAttached;
    /** The rack's scroller — kept so a re-show can put it at its logical start. */
    private JScrollPane rackScroll;

    /**
     * Ledger 29 (v1.45.0): the aimed directory's DataFolder node becomes
     * this window's activated nodes, so the GLOBAL selection — and every
     * platform context action reading it (Team menu, git verbs) — sees
     * the aim whenever the rack is the active window. The default
     * TopComponent lookup proxies activated nodes, so getLookup() carries
     * the DataObject/FileObject too.
     */
    private final org.nmox.studio.rack.service.AimNodePublisher aimPublisher =
            new org.nmox.studio.rack.service.AimNodePublisher(node ->
                    setActivatedNodes(new org.openide.nodes.Node[]{node}));

    /**
     * True between componentShowing and componentHidden/Closed. Gates all
     * selection publication: a hidden default-open tab must do zero
     * filesystem resolution at boot (the v1.38.0 law). Volatile — the
     * rack listener fires off-EDT on async project switches.
     */
    private volatile boolean aimNodeShowing;

    private JToolBar buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        JButton chooseProject = new JButton(Bundle.RackTopComponent_projectButton());
        chooseProject.setToolTipText(Bundle.RackTopComponent_projectTooltip());
        chooseProject.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(rack.getProjectDir());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle(Bundle.RackTopComponent_chooserTitle());
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

        flipToggle = new JToggleButton(Bundle.RackTopComponent_rearToggle());
        flipToggle.setToolTipText(Bundle.RackTopComponent_flipTooltip());
        flipToggle.setFocusable(false);
        flipToggle.addActionListener(e -> {
            rackPanel.setFront(!flipToggle.isSelected());
            flipToggle.setText(PlainText.plain(flipToggle.isSelected() ? Bundle.RackTopComponent_frontToggle() : Bundle.RackTopComponent_rearToggle()));
        });
        bar.add(flipToggle);
        bar.addSeparator();

        JButton save = new JButton(Bundle.RackTopComponent_savePatch());
        save.addActionListener(e -> {
            File target = new File(rack.getProjectDir(), RackIO.DEFAULT_FILENAME);
            // the JSON snapshot is taken here (synchronous, model-consistent);
            // only the disk write rides the lane — the one workspace writer
            // the v1.44 SaveLane sweep left on the EDT (v1.56 review, F3)
            org.json.JSONObject snapshot = RackIO.toJson(rack);
            String projectName = rack.getProjectDir().getName();
            SAVE_RP.post(() -> {
                try {
                    org.nmox.studio.core.util.AtomicFiles.writeString(
                            target.toPath(), snapshot.toString(2));
                    java.awt.EventQueue.invokeLater(() -> {
                        markPersisted(); // saved work is no longer at risk
                        // momentary confirmation, then back to the plain name -
                        // never appended onto itself across repeated saves
                        projectLabel.setText(PlainText.plain(Bundle.RackTopComponent_savedLabel(projectName)));
                        javax.swing.Timer revert = new javax.swing.Timer(2000,
                                ev -> updateProjectLabel());
                        revert.setRepeats(false);
                        revert.start();
                    });
                } catch (IOException ex) {
                    java.awt.EventQueue.invokeLater(() ->
                            error(Bundle.RackTopComponent_saveFailed(ex.getMessage())));
                }
            });
        });
        bar.add(save);

        JButton load = new JButton(Bundle.RackTopComponent_loadPatch());
        load.addActionListener(e -> {
            File source = new File(rack.getProjectDir(), RackIO.DEFAULT_FILENAME);
            if (source.isFile()) {
                if (!confirmReplace(Bundle.RackTopComponent_theSavedPatch())) {
                    return;
                }
                loadPatch(source);
            } else {
                info(Bundle.RackTopComponent_noPatchInProject(RackIO.DEFAULT_FILENAME));
            }
        });
        bar.add(load);

        // Share / Import (v2.176.0): the patch format has always been a file; these
        // are the two doors that let it travel between people. Share writes a
        // portable copy wherever the user says; Import shows what a file holds
        // BEFORE anything mounts, and mounts it at rest — nothing runs until GO.
        JButton share = new JButton(Bundle.RackTopComponent_shareRack());
        share.setToolTipText(PlainText.plain(Bundle.RackTopComponent_shareTooltip()));
        share.addActionListener(e -> shareRack());
        bar.add(share);
        JButton importButton = new JButton(Bundle.RackTopComponent_importRack());
        importButton.setToolTipText(PlainText.plain(Bundle.RackTopComponent_importTooltip()));
        importButton.addActionListener(e -> importRack());
        bar.add(importButton);

        JButton presets = new JButton(Bundle.RackTopComponent_presetsButton());
        presets.setToolTipText(Bundle.RackTopComponent_presetsTooltip());
        presets.addActionListener(e -> {
            // the drop-in scan is file IO — off the EDT per the v1.33.1 law,
            // then the whole menu (built-ins + yours) shows on the callback;
            // a local dir listing lands within a frame, so the menu still
            // feels attached to the click. The button disables until the
            // menu is up: two fast clicks used to post two scans and stack
            // two popups (the v1.296.0 arc review's find).
            presets.setEnabled(false);
            org.openide.util.RequestProcessor.getDefault().post(() -> {
                java.util.List<org.nmox.studio.rack.projectstudio.UserPresets.Custom> yours =
                        org.nmox.studio.rack.projectstudio.UserPresets.list();
                java.awt.EventQueue.invokeLater(() -> {
                    presets.setEnabled(true);
                    showPresetsMenu(presets, yours);
                });
            });
        });
        bar.add(presets);

        JButton exportCi = new JButton(Bundle.RackTopComponent_exportCi());
        exportCi.setToolTipText(Bundle.RackTopComponent_exportCiTooltip());
        exportCi.addActionListener(e -> {
            // YAML built here (synchronous, model-consistent on the EDT); the
            // mkdir + write ride the lane — the same no-blocking-I/O-on-the-EDT
            // discipline the Save button uses, applied to its sibling.
            String yaml = org.nmox.studio.rack.projectstudio.CiExporter.toWorkflowYaml(rack);
            File dir = new File(rack.getProjectDir(), ".github/workflows");
            File out = new File(dir, "nmox-rack.yml");
            SAVE_RP.post(() -> {
                try {
                    java.nio.file.Files.createDirectories(dir.toPath());
                    java.nio.file.Files.writeString(out.toPath(), yaml,
                            java.nio.charset.StandardCharsets.UTF_8);
                    java.awt.EventQueue.invokeLater(() -> {
                        org.openide.awt.StatusDisplayer.getDefault()
                                .setStatusText(Bundle.RackTopComponent_exported(out.getAbsolutePath()));
                        org.nmox.studio.rack.engine.FileLink.open(
                                new org.nmox.studio.rack.engine.FileLink.Location(out, 1));
                    });
                } catch (Exception ex) {
                    java.awt.EventQueue.invokeLater(() ->
                            error(Bundle.RackTopComponent_exportFailed(ex.getMessage())));
                }
            });
        });
        bar.add(exportCi);
        bar.addSeparator();

        JButton stopAll = new JButton(Bundle.RackTopComponent_stopAll());
        stopAll.setForeground(new Color(180, 40, 40));
        stopAll.setToolTipText(Bundle.RackTopComponent_stopAllTooltip());
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
                            n == 1 ? Bundle.RackTopComponent_stoppedOne(String.valueOf(n)) : Bundle.RackTopComponent_stoppedMany(String.valueOf(n)));
                }
            });
            if (started) {
                stopAll.setEnabled(false);
                if (n > 0) {
                    org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                            n == 1 ? Bundle.RackTopComponent_stoppingOne(String.valueOf(n)) : Bundle.RackTopComponent_stoppingMany(String.valueOf(n)));
                }
            }
        });
        bar.add(stopAll);
        return bar;
    }

    /**
     * The text of the patch the rack was last loaded from or saved to —
     * the baseline "unsaved work" is measured against. Set at open (the
     * rack has just been autoloaded), on a successful save, and after
     * every load or preset apply.
     */
    private String lastPersistedJson;

    /**
     * Whether the rack holds work that replacing it would destroy
     * (v1.280.0, the Task Rack persona walk). Both patch verbs route
     * through {@code RackIO.fromJson}, which CLEARS UNDO HISTORY by
     * design (v1.50.0 — so undo can never peel a just-loaded patch
     * apart), and neither asked first: clicking Presets ▸ anything on
     * a pipeline you had not saved destroyed it with no confirmation
     * and no way back. Every other irreversible gesture in the product
     * carries the v1.98.0 safe-default confirm; these two never did.
     */
    static boolean unsavedWork(String currentJson, String lastPersistedJson) {
        return lastPersistedJson != null && !lastPersistedJson.equals(currentJson);
    }

    /**
     * Asks before an action that replaces the whole rack. Enter lands
     * on No (the v1.98.0 idiom: {@code Confirmation} hard-codes OK, so
     * the full constructor with {@code NO_OPTION} as initialValue is
     * the only safe shape). Returns true when the caller may proceed.
     */
    private boolean confirmReplace(String what) {
        if (!unsavedWork(RackIO.toJson(rack).toString(2), lastPersistedJson)) {
            return true;
        }
        org.openide.NotifyDescriptor confirm = new org.openide.NotifyDescriptor(
                Bundle.RackTopComponent_replaceConfirm(what),
                Bundle.RackTopComponent_replaceTitle(),
                org.openide.NotifyDescriptor.YES_NO_OPTION,
                org.openide.NotifyDescriptor.QUESTION_MESSAGE,
                null,
                org.openide.NotifyDescriptor.NO_OPTION);
        return org.openide.DialogDisplayer.getDefault().notify(confirm)
                == org.openide.NotifyDescriptor.YES_OPTION;
    }

    /** Re-baselines the dirty check to whatever the rack now holds. */
    private void markPersisted() {
        lastPersistedJson = RackIO.toJson(rack).toString(2);
    }

    /**
     * The Presets menu: built-ins first, then any {@code ~/.nmox/presets.d}
     * drop-ins under a separator (v1.294.0). A custom entry applies through
     * the same {@link #loadPatch} path the Load Patch button uses — read and
     * parse off the EDT, apply on it — and the same replace-confirm guards
     * both kinds, because a preset click destroys the current wiring with
     * undo powerless either way (the v1.280.0 law).
     */
    private void showPresetsMenu(JButton anchor,
            java.util.List<org.nmox.studio.rack.projectstudio.UserPresets.Custom> yours) {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        javax.swing.JMenuItem gallery = new javax.swing.JMenuItem(Bundle.RackTopComponent_gallery());
        gallery.addActionListener(a -> showGallery());
        menu.add(gallery);
        menu.addSeparator();
        for (org.nmox.studio.rack.projectstudio.RackPresets preset
                : org.nmox.studio.rack.projectstudio.RackPresets.values()) {
            javax.swing.JMenuItem item = new javax.swing.JMenuItem(PlainText.plain(preset.getDisplayName()));
            item.setToolTipText(PlainText.plain(preset.getDescription()));
            item.addActionListener(a -> {
                if (!confirmReplace(Bundle.RackTopComponent_thePreset(preset.getDisplayName()))) {
                    return;
                }
                try {
                    RackIO.fromJson(rack, preset.buildPatch());
                    markPersisted();
                } catch (RuntimeException ex) {
                    error(Bundle.RackTopComponent_presetFailed(ex.getMessage()));
                }
            });
            menu.add(item);
        }
        if (!yours.isEmpty()) {
            menu.addSeparator();
            for (org.nmox.studio.rack.projectstudio.UserPresets.Custom custom : yours) {
                javax.swing.JMenuItem item = new javax.swing.JMenuItem(PlainText.plain(Bundle.RackTopComponent_yoursItem(custom.name())));
                item.setToolTipText(PlainText.plain(custom.file().getAbsolutePath()));
                // a file in the drop-in dir is a FILE: it may be one the user kept
                // from Share (its paths spelled ~/…, which only the shared door
                // expands) or one a stranger sent them — it mounts through the
                // manifest, the dry run and arrival at rest like any import (v2.179.0)
                item.addActionListener(a -> importFile(custom.file()));
                menu.add(item);
            }
        }
        menu.addSeparator();
        javax.swing.JMenuItem fromClipboard = new javax.swing.JMenuItem(Bundle.RackTopComponent_importClipboard());
        fromClipboard.addActionListener(a -> importFromClipboard());
        menu.add(fromClipboard);
        menu.show(anchor, 0, anchor.getHeight());
    }

    private void loadPatch(File file) {
        // Read + parse off the EDT (a project-homed patch can live on a slow or
        // network volume), then apply on the EDT where fromJson mutates the
        // device components — the read/apply split the Save button's sibling
        // needs but load() (called from off-EDT autoload) doesn't.
        SAVE_RP.post(() -> {
            org.json.JSONObject doc;
            try {
                doc = RackIO.readDocument(file);
            } catch (IOException | RuntimeException ex) {
                java.awt.EventQueue.invokeLater(() ->
                        error(Bundle.RackTopComponent_loadFailed(ex.getMessage())));
                return;
            }
            java.awt.EventQueue.invokeLater(() -> {
                try {
                    RackIO.fromJson(rack, doc);
                    markPersisted();
                } catch (RuntimeException ex) {
                    error(Bundle.RackTopComponent_loadFailed(ex.getMessage()));
                }
            });
        });
    }

    // ---- share / import (v2.176.0) ----

    /**
     * Writes this rack as a shared file: the patch with every path under this
     * user's home rewritten to {@code ~} (a home path is a username) and a header
     * naming the product version — see {@link org.nmox.studio.rack.model.RackShare}.
     * The snapshot is taken on the EDT; only the write rides the lane.
     */
    /**
     * Share… (v2.179.0): one dialog the rack leaves through. The sender names
     * it, reads what travels inside it, and picks a file, the clipboard or My
     * Racks. The rack is snapshotted at the gesture — what the sender read is
     * what leaves, whatever the rack does while the dialog is up — and the
     * kind detection (a disk walk) runs before the dialog, off the EDT.
     */
    private void shareRack() {
        final File dir = rack.getProjectDir();
        final org.json.JSONObject shared = org.nmox.studio.rack.model.RackShare.export(
                RackIO.toJson(rack), java.nio.file.Path.of(System.getProperty("user.home")),
                stampedVersion());
        SAVE_RP.post(() -> {
            org.nmox.studio.rack.devices.ProjectInspector.ProjectKind kind =
                    org.nmox.studio.rack.devices.ProjectInspector.detectKind(dir);
            String kindName = kind == null || "NONE".equals(kind.name()) || "LEARN".equals(kind.name())
                    ? null : kind.name();
            java.awt.EventQueue.invokeLater(() -> shareSnapshot(shared, dir, kindName));
        });
    }

    /** This build's version when a release stamped one; null for a dev build, whose "1.0" is a sentinel and not a version. */
    private static String stampedVersion() {
        return org.nmox.studio.core.util.ProductVersion.stamped()
                ? org.nmox.studio.core.util.ProductVersion.number() : null;
    }

    /** EDT: the dialog, then the destination the sender picked. */
    private void shareSnapshot(org.json.JSONObject shared, File dir, String kindName) {
        java.util.Optional<org.nmox.studio.rack.sharing.ShareDialog.Result> asked =
                org.nmox.studio.rack.sharing.ShareDialog.ask(dir.getName(), kindName,
                        org.nmox.studio.rack.sharing.ShareCards.suggestRequires(shared), leavingText(shared));
        if (asked.isEmpty()) {
            return;
        }
        asked.get().card().writeTo(shared.getJSONObject(org.nmox.studio.rack.model.RackShare.SHARED));
        String projectName = dir.getName();
        switch (asked.get().destination()) {
            case FILE -> shareToFile(shared, dir, asked.get().card().name(), projectName);
            case CLIPBOARD -> {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new java.awt.datatransfer.StringSelection(
                                org.nmox.studio.rack.sharing.RackText.render(shared)), null);
                flashLabel(Bundle.RackTopComponent_copiedLabel(projectName));
            }
            case MY_RACKS -> SAVE_RP.post(() -> {
                try {
                    File kept = org.nmox.studio.rack.sharing.MyRacks.keep(shared, asked.get().card().name());
                    java.awt.EventQueue.invokeLater(() -> flashLabel(Bundle.RackTopComponent_keptLabel(kept.getName())));
                } catch (org.nmox.studio.rack.sharing.MyRacks.AlreadyKeptException taken) {
                    java.awt.EventQueue.invokeLater(() -> error(Bundle.RackTopComponent_alreadyKept(taken.getMessage())));
                } catch (IOException ex) {
                    java.awt.EventQueue.invokeLater(() -> error(Bundle.RackTopComponent_shareFailed(ex.getMessage())));
                }
            });
            default -> {
            }
        }
    }

    private void shareToFile(org.json.JSONObject shared, File dir, String rackName, String projectName) {
        File picked = new org.openide.filesystems.FileChooserBuilder(RackTopComponent.class)
                .setTitle(Bundle.RackTopComponent_shareTitle())
                .setDefaultWorkingDirectory(dir)
                .setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        Bundle.RackTopComponent_shareFilter(), "json"))
                .setFilesOnly(true)
                .showSaveDialog();
        if (picked == null) {
            return;
        }
        File target = picked.getName().endsWith(".json") ? picked : new File(picked.getPath() + ".nmoxrack.json");
        SAVE_RP.post(() -> {
            try {
                org.nmox.studio.core.util.AtomicFiles.writeString(target.toPath(), shared.toString(2));
                java.awt.EventQueue.invokeLater(() -> flashLabel(Bundle.RackTopComponent_sharedLabel(projectName)));
            } catch (IOException ex) {
                java.awt.EventQueue.invokeLater(() -> error(Bundle.RackTopComponent_shareFailed(ex.getMessage())));
            }
        });
    }

    /** EDT: a two-second word on the project label, then the label again. */
    private void flashLabel(String text) {
        projectLabel.setText(PlainText.plain(text));
        javax.swing.Timer revert = new javax.swing.Timer(2000, ev -> updateProjectLabel());
        revert.setRepeats(false);
        revert.start();
    }

    /**
     * What the sender reads before the rack goes: first anything that should
     * give them pause — a value that looks like a credential (shown MASKED; the
     * audit must never be a second copy of the secret) and any path that still
     * names somebody's home after the rewrite — then every setting the
     * receiver's manifest will list.
     */
    static String leavingText(org.json.JSONObject shared) {
        org.nmox.studio.rack.model.RackShare.Audit audit = org.nmox.studio.rack.model.RackShare.audit(shared);
        int devices = shared.optJSONArray("devices") == null ? 0 : shared.optJSONArray("devices").length();
        int cables = shared.optJSONArray("cables") == null ? 0 : shared.optJSONArray("cables").length();
        StringBuilder sb = new StringBuilder();
        if (!audit.secretLooking().isEmpty()) {
            sb.append(Bundle.RackTopComponent_leavingSecrets()).append("\n");
            appendSettings(sb, audit.secretLooking());
            sb.append("\n");
        }
        if (!audit.personalPaths().isEmpty()) {
            sb.append(Bundle.RackTopComponent_leavingPaths()).append("\n");
            appendSettings(sb, audit.personalPaths());
            sb.append("\n");
        }
        if (audit.settings().isEmpty()) {
            // walked 2026-09-18: a rack of AUTO lanes stores no command at all,
            // and "Settings that travel:" over an empty pane read as a bug
            sb.append(Bundle.RackTopComponent_leavingNothing(devices, cables)).append("\n");
        } else {
            sb.append(Bundle.RackTopComponent_leavingSummary(devices, cables)).append("\n");
            appendSettings(sb, audit.settings());
        }
        return sb.toString();
    }

    private static void appendSettings(StringBuilder sb, java.util.List<org.nmox.studio.rack.model.RackShare.Setting> settings) {
        for (org.nmox.studio.rack.model.RackShare.Setting st : settings) {
            sb.append("  ").append(st.typeId()).append(" · ").append(st.key()).append(": ").append(st.value()).append("\n");
        }
    }

    /**
     * Mounts a rack someone shared: read and parse off the EDT, then on the EDT
     * show the manifest (devices, cables, what this install lacks, the settings
     * worth reading) with Cancel as the default, ask the replace question if the
     * current rack has unsaved work, and only then mount the file made local —
     * {@code ~} expanded, every self-starting flag off. A plain Save Patch file
     * takes the same door. Nothing here spawns; every GO stays behind Workspace Trust.
     */
    private void importRack() {
        File picked = new org.openide.filesystems.FileChooserBuilder(RackTopComponent.class)
                .setTitle(Bundle.RackTopComponent_importTitle())
                .setDefaultWorkingDirectory(rack.getProjectDir())
                .setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        Bundle.RackTopComponent_shareFilter(), "json"))
                .setFilesOnly(true)
                .showOpenDialog();
        if (picked == null) {
            return;
        }
        importFile(picked);
    }

    /** EDT. Reads {@code picked} off the EDT and offers it for mounting into the rack aimed at NOW. */
    private void importFile(File picked) {
        // the project the import was asked FOR: a result belongs to the
        // workspace that produced it (the v1.172.0 law), and the aim can move
        // under the manifest dialog — the OpenProjects bridge, a --open handshake
        final File aimedAt = rack.getProjectDir();
        SAVE_RP.post(() -> {
            org.json.JSONObject doc;
            try {
                doc = RackIO.readDocument(picked);
            } catch (IOException | RuntimeException ex) {
                java.awt.EventQueue.invokeLater(() -> error(Bundle.RackTopComponent_importFailed(ex.getMessage())));
                return;
            }
            java.awt.EventQueue.invokeLater(() -> mountShared(doc, aimedAt));
        });
    }

    /**
     * EDT. The Rack Gallery (v2.179.0): the window chooses, the doors below do
     * the mounting. A preset or a starter is the product's own wiring and asks
     * only the replace question, as the Presets menu always has; a community or
     * kept rack is a FILE and takes the shared door — manifest, dry run, arrival
     * at rest — with its document read off the EDT.
     */
    public void showGallery() {
        final File aimedAt = rack.getProjectDir();
        java.util.Optional<org.nmox.studio.rack.sharing.RackGalleryDialog.Result> asked =
                org.nmox.studio.rack.sharing.RackGalleryDialog.ask(aimedAt);
        if (asked.isEmpty()) {
            return;
        }
        org.nmox.studio.rack.gallery.RackGallery.Entry entry = asked.get().entry();
        switch (asked.get().action()) {
            // deferred a turn: opened straight from here, the file chooser adopts the
            // closing gallery as its owner and keeps its native window alive behind
            // it (walked 2026-09-18: one ghost "Rack Gallery" window per import)
            case IMPORT_FILE -> java.awt.EventQueue.invokeLater(this::importRack);
            case IMPORT_CLIPBOARD -> java.awt.EventQueue.invokeLater(this::importFromClipboard);
            case REMOVE -> removeKept(entry);
            case MOUNT -> {
                if (!aimedAt.equals(rack.getProjectDir())) {
                    // the gallery is modal and pumps events: the re-aim law
                    error(Bundle.RackTopComponent_importAimMoved());
                    return;
                }
                switch (entry.source()) {
                    case PRESET, STARTER -> {
                        if (!confirmReplace(Bundle.RackTopComponent_theRack(entry.card().name()))) {
                            return;
                        }
                        try {
                            RackIO.fromJson(rack, entry.patch());
                            markPersisted();
                        } catch (RuntimeException ex) {
                            error(Bundle.RackTopComponent_presetFailed(ex.getMessage()));
                        }
                    }
                    default -> SAVE_RP.post(() -> {
                        org.json.JSONObject doc;
                        try {
                            doc = entry.patch();
                        } catch (RuntimeException ex) {
                            java.awt.EventQueue.invokeLater(
                                    () -> error(Bundle.RackTopComponent_importFailed(ex.getMessage())));
                            return;
                        }
                        java.awt.EventQueue.invokeLater(() -> mountShared(doc, aimedAt));
                    });
                }
            }
            default -> {
            }
        }
    }

    /** EDT. Removing a kept rack deletes a file: asked first, No by default (v1.98.0), done off the EDT. */
    private void removeKept(org.nmox.studio.rack.gallery.RackGallery.Entry entry) {
        File file = entry.file();
        if (file == null) {
            return;
        }
        org.openide.NotifyDescriptor ask = new org.openide.NotifyDescriptor(
                org.nmox.studio.core.util.PlainDialogs.plain(
                        Bundle.RackTopComponent_removeConfirm(file.getName()), Bundle.RackTopComponent_removeTitle()),
                Bundle.RackTopComponent_removeTitle(),
                org.openide.NotifyDescriptor.YES_NO_OPTION,
                org.openide.NotifyDescriptor.WARNING_MESSAGE,
                null,
                org.openide.NotifyDescriptor.NO_OPTION);
        if (!org.openide.NotifyDescriptor.YES_OPTION.equals(DialogDisplayer.getDefault().notify(ask))) {
            return;
        }
        SAVE_RP.post(() -> {
            try {
                org.nmox.studio.rack.sharing.MyRacks.remove(file);
                java.awt.EventQueue.invokeLater(() -> flashLabel(Bundle.RackTopComponent_removedLabel(file.getName())));
            } catch (IOException ex) {
                java.awt.EventQueue.invokeLater(() -> error(Bundle.RackTopComponent_removeFailed(ex.getMessage())));
            }
        });
    }

    /**
     * EDT. The clipboard door (v2.179.0): a rack pasted into a chat comes back
     * through the same manifest, the same two questions and the same arrival at
     * rest as a file. What is on a clipboard is a stranger's text — capped and
     * refused by reason in {@code RackText}.
     */
    void importFromClipboard() {
        String pasted = null;
        try {
            java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                pasted = (String) clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
            }
        } catch (java.awt.datatransfer.UnsupportedFlavorException | IOException | IllegalStateException ex) {
            // the clipboard is busy or holds something that is not text: the empty refusal below says so
            pasted = null;
        }
        org.json.JSONObject doc;
        try {
            doc = org.nmox.studio.rack.sharing.RackText.parse(pasted);
        } catch (org.nmox.studio.rack.sharing.RackText.NotARackException refused) {
            error(switch (refused.reason()) {
                case EMPTY -> Bundle.RackTopComponent_clipboardEmpty();
                case TOO_LARGE -> Bundle.RackTopComponent_clipboardTooLarge();
                case NOT_JSON -> Bundle.RackTopComponent_clipboardNotJson();
                case NO_DEVICES -> Bundle.RackTopComponent_clipboardNoDevices();
            });
            return;
        }
        mountShared(doc, rack.getProjectDir());
    }

    /** EDT: the manifest, the two confirmations, the mount — into {@code aimedAt}'s rack and no other. */
    private void mountShared(org.json.JSONObject doc, File aimedAt) {
        org.nmox.studio.rack.model.RackShare.Manifest manifest;
        try {
            manifest = org.nmox.studio.rack.model.RackShare.inspect(
                    doc, id -> org.nmox.studio.rack.devices.DeviceCatalog.byId(id).isPresent());
        } catch (RuntimeException ex) {
            // a stranger's file can hold anything: a refusal, never a red
            // exception dialog (the 2026-09-17 arc review, hostile input lens)
            error(Bundle.RackTopComponent_importFailed(ex.getMessage()));
            return;
        }
        // what this install cannot give the file, found by a dry run BEFORE the
        // question is asked (v2.179.0): a rack that arrives one cable short
        // looks complete and does something else
        org.nmox.studio.rack.model.RackCompat.Report compat;
        try {
            // a dev build carries the "1.0" sentinel, which would read as older than
            // every release ever shipped (walked 2026-09-18: a rack made with this
            // very build said "newer than this install") — unstamped is unknown
            compat = org.nmox.studio.rack.model.RackCompat.check(doc, stampedVersion());
        } catch (RuntimeException ex) {
            error(Bundle.RackTopComponent_importFailed(ex.getMessage()));
            return;
        }
        if (compat.formatTooNew()) {
            error(Bundle.RackTopComponent_importTooNew(compat.format(), org.nmox.studio.rack.model.RackCompat.FORMAT));
            return;
        }
        String manifestBody = manifestText(manifest, org.nmox.studio.rack.model.RackCard.of(doc), compat);
        // the manifest dialog: OK is not the default — a reflexive Enter must not
        // mount a stranger's rack (the v1.98.0 safe-default idiom)
        Object mount = Bundle.RackTopComponent_importMount();
        org.openide.NotifyDescriptor ask = new org.openide.NotifyDescriptor(
                org.nmox.studio.core.util.PlainDialogs.plain(manifestBody, Bundle.RackTopComponent_importTitle()),
                Bundle.RackTopComponent_importTitle(),
                org.openide.NotifyDescriptor.OK_CANCEL_OPTION,
                org.openide.NotifyDescriptor.PLAIN_MESSAGE,
                new Object[]{mount, org.openide.NotifyDescriptor.CANCEL_OPTION},
                org.openide.NotifyDescriptor.CANCEL_OPTION);
        if (!mount.equals(DialogDisplayer.getDefault().notify(ask))) {
            return;
        }
        if (!aimedAt.equals(rack.getProjectDir())) {
            // the manifest is modal and pumps events: the rack under it can be
            // another project's by the time Mount is pressed (the 2026-09-17
            // arc review, re-aim lens) — mount nothing, say so, no confirm
            // asked about a project the reader never chose
            error(Bundle.RackTopComponent_importAimMoved());
            return;
        }
        if (!confirmReplace(Bundle.RackTopComponent_theSharedRack())) {
            return;
        }
        try {
            RackIO.fromJson(rack, org.nmox.studio.rack.model.RackShare.imported(
                    doc, java.nio.file.Path.of(System.getProperty("user.home"))));
            // deliberately NOT markPersisted(): an imported rack is unsaved work
            // until Save Patch writes it beside this project
        } catch (RuntimeException ex) {
            error(Bundle.RackTopComponent_importFailed(ex.getMessage()));
        }
    }

    /**
     * The whole page a reader meets before Mount: what the rack says it is, what
     * it holds, and what this install cannot give it. The card is a stranger's
     * text — {@code RackCard} has already folded its control characters, so a
     * name cannot forge one of the lines below it.
     */
    static String manifestText(org.nmox.studio.rack.model.RackShare.Manifest m,
            org.nmox.studio.rack.model.RackCard card, org.nmox.studio.rack.model.RackCompat.Report compat) {
        StringBuilder sb = new StringBuilder();
        if (!card.name().isEmpty()) {
            sb.append(card.name()).append("\n");
        }
        if (!card.description().isEmpty()) {
            sb.append(card.description()).append("\n");
        }
        if (!card.author().isEmpty()) {
            sb.append(Bundle.RackTopComponent_importSharedBy(card.author())).append("\n");
        }
        if (sb.length() > 0) {
            sb.append("\n");
        }
        sb.append(manifestText(m));
        sb.append("\n");
        if (compat.madeWithNewer()) {
            sb.append(Bundle.RackTopComponent_importMadeWithNewer(compat.madeWith())).append("\n");
        }
        if (compat.carriesWhole()) {
            sb.append(Bundle.RackTopComponent_importCarriesWhole()).append("\n");
        }
        if (!compat.lostCables().isEmpty()) {
            sb.append(Bundle.RackTopComponent_importLostCables()).append("\n");
            compat.lostCables().forEach(c -> sb.append("  ").append(c).append("\n"));
        }
        if (!compat.lostSettings().isEmpty()) {
            sb.append(Bundle.RackTopComponent_importLostSettings()).append("\n");
            compat.lostSettings().forEach(c -> sb.append("  ").append(c).append("\n"));
        }
        return sb.toString();
    }

    /** The manifest's middle: one device per line, then what needs reading. */
    static String manifestText(org.nmox.studio.rack.model.RackShare.Manifest m) {
        StringBuilder sb = new StringBuilder();
        sb.append(Bundle.RackTopComponent_importSummary(m.devices().size(), m.cables(), m.atRest())).append("\n\n");
        for (org.nmox.studio.rack.model.RackShare.Device d : m.devices()) {
            String title = org.nmox.studio.rack.devices.DeviceCatalog.byId(d.typeId())
                    .map(org.nmox.studio.rack.devices.DeviceCatalog.Entry::title).orElse(d.typeId());
            sb.append("  ").append(d.known() ? title : d.typeId() + " ?").append("\n");
        }
        if (!m.unknownTypes().isEmpty()) {
            sb.append("\n").append(Bundle.RackTopComponent_importUnknown(String.join(", ", m.unknownTypes()))).append("\n");
        }
        if (!m.settings().isEmpty()) {
            sb.append("\n").append(Bundle.RackTopComponent_importSettings()).append("\n");
            for (org.nmox.studio.rack.model.RackShare.Setting st : m.settings()) {
                sb.append("  ").append(st.typeId()).append(" · ").append(st.key()).append(": ").append(st.value()).append("\n");
            }
        }
        return sb.toString();
    }

    // ---- platform dialogs (parented, keyboard-correct, consistent chrome) ----

    private void info(String message) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                org.nmox.studio.core.util.PlainDialogs.plain(message, "Message"), NotifyDescriptor.INFORMATION_MESSAGE));
    }

    private void error(String message) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                org.nmox.studio.core.util.PlainDialogs.plain(message, "Message"), NotifyDescriptor.ERROR_MESSAGE));
    }

    private void updateProjectLabel() {
        projectLabel.setText(PlainText.plain(rack.getProjectDir().getName()));
        projectLabel.setToolTipText(PlainText.plain(rack.getProjectDir().getAbsolutePath()));
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
        if (lastPersistedJson == null) {
            // baseline the unsaved-work check against whatever the rack
            // holds now — at open that IS the autoloaded patch, so a
            // preset click without edits asks nothing (v1.280.0)
            markPersisted();
        }
    }

    @Override
    protected void componentShowing() {
        aimNodeShowing = true;
        // a rack faceplate is painted geometry and never mirrors, so the
        // rack starts at its left edge in every language — but a mirrored
        // window opens a wide view at the FAR end of it, which is how the
        // Hebrew and Arabic pictures came out with every device face cut
        // off (v2.162.0)
        javax.swing.SwingUtilities.invokeLater(
                () -> org.nmox.studio.rack.ui.Scrolls.toContentStart(rackScroll));
        // the publisher's equality guard makes re-shows of an unchanged aim
        // free; a fresh boot pays one ~/NMOX folder-node resolve on first
        // show (never while hidden), which touches no TCC-protected path
        aimPublisher.publish(rack.getProjectDir());
    }

    @Override
    protected void componentHidden() {
        aimNodeShowing = false;
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
        aimNodeShowing = false;
        aimPublisher.reset(); // reopen re-resolves even for the same aim
        setActivatedNodes(new org.openide.nodes.Node[0]); // don't pin the DataObject
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
