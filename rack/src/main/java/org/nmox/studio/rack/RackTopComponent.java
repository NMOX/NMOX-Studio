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
import javax.swing.JOptionPane;
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
@TopComponent.Registration(mode = "editor", openAtStartup = true)
@ActionID(category = "Window", id = "org.nmox.studio.rack.RackTopComponent")
@ActionReference(path = "Menu/Window", position = 250)
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
    private final RackPanel rackPanel;
    private final JLabel projectLabel = new JLabel();
    private JToggleButton flipToggle;

    /**
     * Flips the rack on Tab whenever this window is active. Swing's focus
     * traversal normally consumes Tab before key bindings ever see it, so
     * an input-map binding is not enough - we intercept at the keyboard
     * focus manager, exactly while the rack is the activated TopComponent.
     */
    private final java.awt.KeyEventDispatcher tabFlipDispatcher = e -> {
        if (e.getID() == KeyEvent.KEY_PRESSED
                && e.getKeyCode() == KeyEvent.VK_TAB
                && e.getModifiersEx() == 0
                && TopComponent.getRegistry().getActivated() == RackTopComponent.this) {
            flipToggle.doClick();
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
        rack.addListener(new Rack.Listener() {
            @Override
            public void projectChanged() {
                javax.swing.SwingUtilities.invokeLater(RackTopComponent.this::updateProjectLabel);
            }
        });
    }

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
                projectLabel.setText(projectLabel.getText() + "  [saved]");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage(),
                        "Task Rack", JOptionPane.ERROR_MESSAGE);
            }
        });
        bar.add(save);

        JButton load = new JButton("Load Patch");
        load.addActionListener(e -> {
            File source = new File(rack.getProjectDir(), RackIO.DEFAULT_FILENAME);
            if (source.isFile()) {
                loadPatch(source);
            } else {
                JOptionPane.showMessageDialog(this, "No " + RackIO.DEFAULT_FILENAME + " in project.",
                        "Task Rack", JOptionPane.INFORMATION_MESSAGE);
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
                        JOptionPane.showMessageDialog(this, "Preset failed: " + ex.getMessage(),
                                "Task Rack", JOptionPane.ERROR_MESSAGE);
                    }
                });
                menu.add(item);
            }
            menu.show(presets, 0, presets.getHeight());
        });
        bar.add(presets);
        bar.addSeparator();

        JButton stopAll = new JButton("Stop All");
        stopAll.setForeground(new Color(180, 40, 40));
        stopAll.setToolTipText("Kill every process the rack is running");
        stopAll.addActionListener(e -> {
            for (RackDevice d : rack.getDevices()) {
                d.panic();
            }
        });
        bar.add(stopAll);
        return bar;
    }

    private void loadPatch(File file) {
        try {
            RackIO.load(rack, file);
        } catch (IOException | RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Load failed: " + ex.getMessage(),
                    "Task Rack", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateProjectLabel() {
        projectLabel.setText(rack.getProjectDir().getName());
        projectLabel.setToolTipText(rack.getProjectDir().getAbsolutePath());
    }

    @Override
    public void componentOpened() {
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(tabFlipDispatcher);
    }

    @Override
    public void componentClosed() {
        // keep processes alive when the window merely closes; the rack
        // survives until the module is unloaded
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removeKeyEventDispatcher(tabFlipDispatcher);
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
        p.setProperty("projectDir", rack.getProjectDir().getAbsolutePath());
    }

    void readProperties(java.util.Properties p) {
        String dir = p.getProperty("projectDir");
        if (dir != null) {
            File f = new File(dir);
            if (f.isDirectory()) {
                org.nmox.studio.rack.service.RackService.getDefault().openProject(f);
            }
        }
    }
}
