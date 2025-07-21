package org.nmox.studio.ui;

import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

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
@TopComponent.Registration(mode = "editor", openAtStartup = true)
@ActionID(category = "Window", id = "org.nmox.studio.ui.MainWindow")
@ActionReference(path = "Menu/Window" /*, position = 333 */)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_MainWindowAction",
        preferredID = "MainWindowTopComponent"
)
@Messages({
    "CTL_MainWindowAction=Main Window",
    "CTL_MainWindowTopComponent=Main Window",
    "HINT_MainWindowTopComponent=This is the main window"
})
public final class MainWindow extends TopComponent {

    public MainWindow() {
        initComponents();
        setName(Bundle.CTL_MainWindowTopComponent());
        setToolTipText(Bundle.HINT_MainWindowTopComponent());
    }

    private void initComponents() {
        setLayout(new java.awt.BorderLayout());
        
        // Welcome panel
        javax.swing.JPanel welcomePanel = new javax.swing.JPanel();
        welcomePanel.setLayout(new java.awt.BorderLayout());
        
        javax.swing.JLabel welcomeLabel = new javax.swing.JLabel();
        welcomeLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        welcomeLabel.setText("Welcome to NMOX Studio");
        welcomeLabel.setFont(welcomeLabel.getFont().deriveFont(24.0f));
        
        javax.swing.JLabel subtitleLabel = new javax.swing.JLabel();
        subtitleLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        subtitleLabel.setText("Professional Media Development Environment");
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(14.0f));
        
        javax.swing.JPanel centerPanel = new javax.swing.JPanel();
        centerPanel.setLayout(new javax.swing.BoxLayout(centerPanel, javax.swing.BoxLayout.Y_AXIS));
        centerPanel.add(javax.swing.Box.createVerticalGlue());
        centerPanel.add(welcomeLabel);
        centerPanel.add(javax.swing.Box.createVerticalStrut(10));
        centerPanel.add(subtitleLabel);
        centerPanel.add(javax.swing.Box.createVerticalGlue());
        
        welcomePanel.add(centerPanel, java.awt.BorderLayout.CENTER);
        add(welcomePanel, java.awt.BorderLayout.CENTER);
    }

    @Override
    public void componentOpened() {
        // TODO add custom code on component opening
    }

    @Override
    public void componentClosed() {
        // TODO add custom code on component closing
    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
        // TODO read your settings according to their version
    }
}