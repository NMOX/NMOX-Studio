package org.nmox.studio.ui;

import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.openide.util.lookup.ServiceProvider;

/**
 * Main window for NMOX Studio.
 * Provides the primary workspace and docking area for tools and editors.
 */
@ConvertAsProperties(
        dtd = "-//org.nmox.studio.ui//MainWindow//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "MainWindowTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "editor", openAtStartup = true, position = 100)
@ActionID(category = "Window", id = "org.nmox.studio.ui.MainWindow")
@ActionReference(path = "Menu/Window", position = 270)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_MainWindowAction",
        preferredID = "MainWindowTopComponent"
)
@Messages({
    "CTL_MainWindowAction=Welcome",
    "CTL_MainWindowTopComponent=Welcome",
    "HINT_MainWindowTopComponent=Start here: new project, Project Studio, Task Rack"
})
public final class MainWindow extends TopComponent {
    
    private static MainWindow instance;
    private static final String PREFERRED_ID = "MainWindowTopComponent";

    public MainWindow() {
        initComponents();
        setName(NbBundle.getMessage(MainWindow.class, "CTL_MainWindowTopComponent"));
        setToolTipText(NbBundle.getMessage(MainWindow.class, "HINT_MainWindowTopComponent"));
        putClientProperty(TopComponent.PROP_MAXIMIZATION_DISABLED, Boolean.TRUE);
    }
    
    public static synchronized MainWindow getDefault() {
        if (instance == null) {
            instance = new MainWindow();
        }
        return instance;
    }
    
    public static synchronized MainWindow findInstance() {
        TopComponent win = WindowManager.getDefault().findTopComponent(PREFERRED_ID);
        if (win == null) {
            return getDefault();
        }
        if (win instanceof MainWindow) {
            return (MainWindow) win;
        }
        return getDefault();
    }

    private void initComponents() {
        setLayout(new java.awt.BorderLayout());
        add(new WelcomePanel(), java.awt.BorderLayout.CENTER);
    }

    /**
     * The launch surface: rack-styled dark panel with the three doors
     * into the studio. Buttons resolve windows by preferredID so the ui
     * module needs no compile dependency on the rack module.
     */
    private static final class WelcomePanel extends javax.swing.JPanel {

        WelcomePanel() {
            setLayout(new java.awt.GridBagLayout());
            java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
            gc.gridx = 0;
            gc.insets = new java.awt.Insets(6, 0, 6, 0);

            javax.swing.JLabel title = new javax.swing.JLabel("NMOX STUDIO");
            title.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 42));
            title.setForeground(new java.awt.Color(235, 236, 240));

            javax.swing.JLabel tagline = new javax.swing.JLabel(
                    "The web studio with a rack — wire your tools like a synth.");
            tagline.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 15));
            tagline.setForeground(new java.awt.Color(150, 152, 158));

            javax.swing.JPanel buttons = new javax.swing.JPanel(
                    new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 14, 0));
            buttons.setOpaque(false);
            buttons.add(launchButton("Project Studio",
                    "Create, browse and configure projects", "ProjectStudioTopComponent",
                    new java.awt.Color(64, 156, 255)));
            buttons.add(launchButton("Task Rack",
                    "Wire build, test, serve and deploy like a synth rack", "RackTopComponent",
                    new java.awt.Color(236, 106, 168)));
            buttons.add(launchButton("NPM Explorer",
                    "Browse scripts and dependencies", "NpmExplorerTopComponent",
                    new java.awt.Color(203, 56, 55)));

            javax.swing.JLabel hint = new javax.swing.JLabel(
                    "New Project lives in the Project Studio toolbar · Tab flips the rack");
            hint.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12));
            hint.setForeground(new java.awt.Color(110, 112, 118));

            gc.gridy = 0;
            add(title, gc);
            gc.gridy = 1;
            add(tagline, gc);
            gc.gridy = 2;
            gc.insets = new java.awt.Insets(28, 0, 6, 0);
            add(buttons, gc);
            gc.gridy = 3;
            gc.insets = new java.awt.Insets(18, 0, 6, 0);
            add(hint, gc);
        }

        private static javax.swing.JButton launchButton(String label, String tooltip,
                String topComponentId, java.awt.Color accent) {
            javax.swing.JButton button = new javax.swing.JButton(
                    "<html><div style='text-align:center'><b>" + label + "</b></div></html>");
            button.setToolTipText(tooltip);
            button.setPreferredSize(new java.awt.Dimension(190, 64));
            button.setBackground(new java.awt.Color(45, 46, 50));
            button.setForeground(new java.awt.Color(230, 231, 235));
            button.setFocusPainted(false);
            button.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createMatteBorder(0, 0, 3, 0, accent),
                    javax.swing.BorderFactory.createEmptyBorder(10, 16, 9, 16)));
            button.addActionListener(e -> {
                TopComponent tc = WindowManager.getDefault().findTopComponent(topComponentId);
                if (tc != null) {
                    tc.open();
                    tc.requestActive();
                }
            });
            return button;
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(30, 30, 34),
                    0, getHeight(), new java.awt.Color(22, 22, 25)));
            g2.fillRect(0, 0, getWidth(), getHeight());
            // rack-rail nod: accent pinstripes along the top
            java.awt.Color[] stripes = {
                new java.awt.Color(236, 106, 168), new java.awt.Color(64, 156, 255),
                new java.awt.Color(232, 166, 35), new java.awt.Color(99, 197, 70)};
            int w = getWidth() / stripes.length + 1;
            for (int i = 0; i < stripes.length; i++) {
                g2.setColor(stripes[i]);
                g2.fillRect(i * w, 0, w, 4);
            }
            g2.dispose();
        }
    }

    @Override
    public void componentOpened() {
        // welcome steals focus exactly once: on the first launch ever.
        // Every later start restores the user's own window arrangement.
        java.util.prefs.Preferences prefs =
                org.openide.util.NbPreferences.forModule(MainWindow.class);
        if (prefs.getBoolean("welcomeShown", false)) {
            return;
        }
        prefs.putBoolean("welcomeShown", true);
        requestActive();
    }

    @Override
    public void componentClosed() {
        // Clean up resources if needed
    }
    
    @Override
    protected String preferredID() {
        return PREFERRED_ID;
    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
        // version "1.0" carries no settings beyond window geometry, which
        // the window system restores itself; nothing further to read
    }
}