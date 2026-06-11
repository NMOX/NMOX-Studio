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

    private final Rack rack = new Rack();
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
        loadDefaultRack();

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
        followOpenProjects();
    }

    /**
     * Aims the rack at whatever the IDE has open: the first open project
     * with a package.json wins (it is what the devices know how to drive),
     * else the first open project. Manual Project… choice still works and
     * holds until the open-project set changes again.
     */
    private void followOpenProjects() {
        try {
            org.netbeans.api.project.ui.OpenProjects open =
                    org.netbeans.api.project.ui.OpenProjects.getDefault();
            open.addPropertyChangeListener(evt -> {
                if (org.netbeans.api.project.ui.OpenProjects.PROPERTY_OPEN_PROJECTS
                        .equals(evt.getPropertyName())) {
                    javax.swing.SwingUtilities.invokeLater(this::aimAtOpenProject);
                }
            });
            aimAtOpenProject();
        } catch (RuntimeException | LinkageError ex) {
            // project APIs unavailable (tests, stripped platform); manual choice only
        }
    }

    private void aimAtOpenProject() {
        org.netbeans.api.project.Project[] projects =
                org.netbeans.api.project.ui.OpenProjects.getDefault().getOpenProjects();
        File fallback = null;
        for (org.netbeans.api.project.Project p : projects) {
            File dir = org.openide.filesystems.FileUtil.toFile(p.getProjectDirectory());
            if (dir == null) {
                continue;
            }
            if (new File(dir, "package.json").isFile()) {
                rack.setProjectDir(dir);
                return;
            }
            if (fallback == null) {
                fallback = dir;
            }
        }
        if (fallback != null) {
            rack.setProjectDir(fallback);
        }
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
                rack.setProjectDir(chooser.getSelectedFile());
                File patch = new File(chooser.getSelectedFile(), RackIO.DEFAULT_FILENAME);
                if (patch.isFile() && JOptionPane.showConfirmDialog(this,
                        "This project has a saved rack patch. Load it?", "Task Rack",
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    loadPatch(patch);
                }
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

    /**
     * Starter rack: a working web pipeline. MAESTRO fires install; a
     * green install builds; a green build tests; test output scrolls on
     * the console. Independently, starting SURGE hands its URL to SCOPE
     * and pops the browser the moment the dev server answers.
     */
    private void loadDefaultRack() {
        RackDevice master = DeviceType.MASTER.create();
        RackDevice deps = DeviceType.PACKAGE_MANAGER.create();
        RackDevice build = DeviceType.BUILD.create();
        RackDevice test = DeviceType.TEST.create();
        RackDevice server = DeviceType.DEV_SERVER.create();
        RackDevice browser = DeviceType.BROWSER.create();
        RackDevice console = DeviceType.CONSOLE.create();
        rack.addDevice(master);
        rack.addDevice(deps);
        rack.addDevice(build);
        rack.addDevice(test);
        rack.addDevice(server);
        rack.addDevice(browser);
        rack.addDevice(console);

        // CI lane: install -> build -> test -> console
        rack.connect(master.getPort("trig1"), deps.getPort("run"));
        rack.connect(deps.getPort("ok"), build.getPort("run"));
        rack.connect(build.getPort("ok"), test.getPort("run"));
        rack.connect(test.getPort("out"), console.getPort("in"));

        // dev lane: server URL + ready trigger open the browser
        rack.connect(server.getPort("url"), browser.getPort("url"));
        rack.connect(server.getPort("ready"), browser.getPort("open"));
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
                rack.setProjectDir(f);
            }
        }
    }
}
