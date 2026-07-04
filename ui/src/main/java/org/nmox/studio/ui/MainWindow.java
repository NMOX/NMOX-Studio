package org.nmox.studio.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Main window for NMOX Studio: the Welcome launchpad. Start actions,
 * the projects you were just in, every tool window with its shortcut,
 * and an honest version line — the first screen reflects the whole
 * product, not the product as of v1.0.
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
    "HINT_MainWindowTopComponent=Start here: projects, learning spaces, and every tool window"
})
public final class MainWindow extends TopComponent {

    private static MainWindow instance;
    private static final String PREFERRED_ID = "MainWindowTopComponent";

    private WelcomePanel welcomePanel;

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
        setLayout(new BorderLayout());
        welcomePanel = new WelcomePanel();
        add(welcomePanel, BorderLayout.CENTER);
    }

    /** The launchpad: dark rack-styled panel, three columns, honest footer. */
    private static final class WelcomePanel extends JPanel {

        private static final Color HEADING = new Color(150, 152, 158);
        private static final Color LINK = new Color(210, 212, 218);
        private static final Color DIM = new Color(110, 112, 118);

        private final JPanel recentColumn = column("RECENT");

        WelcomePanel() {
            setLayout(new GridBagLayout());
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = 0;
            gc.insets = new Insets(6, 0, 6, 0);

            JLabel title = new JLabel("NMOX STUDIO");
            title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
            title.setForeground(new Color(235, 236, 240));

            JLabel tagline = new JLabel(
                    "The web studio with a rack — wire your tools like a synth.");
            tagline.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            tagline.setForeground(HEADING);

            JPanel start = column("START");
            start.add(actionLink("New Project…  ⇧⌘N", "File",
                    "org.nmox.studio.ui.actions.NewProjectAction"));
            start.add(actionLink("New Experiment…  ⇧⌘E", "File",
                    "org.nmox.studio.ui.actions.NewExperimentAction"));
            start.add(actionLink("New Learning Space…  ⇧⌘L", "File",
                    "org.nmox.studio.ui.actions.NewLearningSpaceAction"));
            start.add(actionLink("Open Folder…  ⇧⌘O", "File",
                    "org.nmox.studio.ui.actions.OpenFolderAction"));

            JPanel windows = column("TOOLING");
            windows.add(windowLink("Task Rack  ⌘9", "RackTopComponent"));
            windows.add(windowLink("Workbench  ⌘0", "ProjectExplorerTopComponent"));
            windows.add(windowLink("Project Studio", "ProjectStudioTopComponent"));
            windows.add(windowLink("DB Studio  ⇧⌘7", "DbStudioTopComponent"));
            windows.add(windowLink("Contract Studio  ⇧⌘6", "Web3StudioTopComponent"));
            windows.add(windowLink("API Studio  ⇧⌘8", "ApiClientTopComponent"));
            windows.add(windowLink("Infra Designer  ⇧⌘9", "InfraDesignerTopComponent"));
            windows.add(windowLink("Docker Panel  ⌘8", "DockerPanelTopComponent"));

            JPanel columns = new JPanel(new java.awt.GridLayout(1, 3, 44, 0));
            columns.setOpaque(false);
            columns.add(start);
            columns.add(recentColumn);
            columns.add(windows);
            refreshRecents();

            JLabel version = new JLabel(footerText());
            version.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            version.setForeground(DIM);
            JButton whatsNew = textButton("What's new ↗", DIM);
            whatsNew.setToolTipText("Open the release notes on GitHub");
            whatsNew.addActionListener(e -> browse(UpdateCheck.RELEASES_PAGE));
            JPanel footer = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 10, 0));
            footer.setOpaque(false);
            footer.add(version);
            footer.add(whatsNew);

            gc.gridy = 0;
            add(title, gc);
            gc.gridy = 1;
            add(tagline, gc);
            gc.gridy = 2;
            gc.insets = new Insets(30, 0, 6, 0);
            add(columns, gc);
            gc.gridy = 3;
            gc.insets = new Insets(26, 0, 6, 0);
            add(footer, gc);
        }

        /** Rebuilds the recent-projects column from the live service. */
        void refreshRecents() {
            recentColumn.removeAll();
            recentColumn.add(columnHeading("RECENT"));
            List<File> recents = recentProjects();
            if (recents.isEmpty()) {
                JLabel none = new JLabel("projects you open gather here");
                none.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 12));
                none.setForeground(DIM);
                none.setAlignmentX(Component.LEFT_ALIGNMENT);
                recentColumn.add(none);
            } else {
                for (File dir : recents.subList(0, Math.min(6, recents.size()))) {
                    JButton link = textButton(dir.getName(), LINK);
                    link.setToolTipText(dir.getAbsolutePath());
                    link.addActionListener(e ->
                            org.nmox.studio.rack.service.RackService.getDefault().openProject(dir));
                    recentColumn.add(link);
                }
            }
            recentColumn.revalidate();
            recentColumn.repaint();
        }

        private static List<File> recentProjects() {
            try {
                return org.nmox.studio.rack.service.RackService.getDefault().getRecentProjects();
            } catch (RuntimeException rackUnavailable) {
                return List.of();
            }
        }

        private static JPanel column(String heading) {
            JPanel col = new JPanel();
            col.setOpaque(false);
            col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
            col.add(columnHeading(heading));
            return col;
        }

        private static JLabel columnHeading(String text) {
            JLabel label = new JLabel(text);
            label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            label.setForeground(HEADING);
            label.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 2, 6, 0));
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            return label;
        }

        private static JButton textButton(String text, Color color) {
            JButton button = new JButton(text);
            button.setForeground(color);
            button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            button.setContentAreaFilled(false);
            button.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 2, 3, 2));
            button.setFocusPainted(false);
            button.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
            button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            return button;
        }

        private static JButton actionLink(String label, String category, String id) {
            JButton link = textButton(label, LINK);
            link.addActionListener(e -> {
                javax.swing.Action action = org.openide.awt.Actions.forID(category, id);
                if (action != null) {
                    action.actionPerformed(e);
                }
            });
            return link;
        }

        private static JButton windowLink(String label, String topComponentId) {
            JButton link = textButton(label, LINK);
            link.addActionListener(e -> {
                TopComponent tc = WindowManager.getDefault().findTopComponent(topComponentId);
                if (tc != null) {
                    tc.open();
                    tc.requestActive();
                }
            });
            return link;
        }

        private static String footerText() {
            String version = UpdateCheck.currentVersion();
            return version == null || version.isBlank() ? "NMOX Studio" : version;
        }

        private static void browse(String url) {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            } catch (Exception ignored) {
                // no browser wired up; the footer text still names the version
            }
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setPaint(new java.awt.GradientPaint(0, 0, new Color(30, 30, 34),
                    0, getHeight(), new Color(22, 22, 25)));
            g2.fillRect(0, 0, getWidth(), getHeight());
            // rack-rail nod: accent pinstripes along the top
            Color[] stripes = {
                new Color(236, 106, 168), new Color(64, 156, 255),
                new Color(232, 166, 35), new Color(99, 197, 70)};
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
    protected void componentShowing() {
        // the recents column is live, not a launch-time snapshot
        if (welcomePanel != null) {
            welcomePanel.refreshRecents();
        }
    }

    @Override
    public void componentClosed() {
        // nothing to release
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
