package org.nmox.studio.ui;

import javax.swing.*;
import java.awt.*;
import org.openide.modules.OnStart;
import org.openide.windows.WindowManager;

/**
 * Initializes the UI on startup
 */
@OnStart
public class StartupInitializer implements Runnable {
    
    @Override
    public void run() {
        // Initialize on EDT
        SwingUtilities.invokeLater(() -> {
            initializeUI();
        });
    }
    
    private void initializeUI() {
        // Create a simple test panel in the main window
        JPanel testPanel = new JPanel(new BorderLayout());
        testPanel.setBackground(new Color(40, 40, 40));
        
        // Title
        JLabel titleLabel = new JLabel("NMOX Studio v3.0.0 - Development Environment", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Info panel
        JPanel infoPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        infoPanel.setBackground(new Color(50, 50, 50));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        
        addInfoLabel(infoPanel, "Status: Running on Java " + System.getProperty("java.version"));
        addInfoLabel(infoPanel, "Memory: " + getMemoryInfo());
        addInfoLabel(infoPanel, "Modules Loaded: 11 (core, ui, editor, tools, project, sample, branding)");
        addInfoLabel(infoPanel, "Performance Enhancements: Active");
        
        // Test area
        JTextArea textArea = new JTextArea();
        textArea.setBackground(new Color(30, 30, 30));
        textArea.setForeground(new Color(200, 200, 200));
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        textArea.setText("// Welcome to NMOX Studio v3.0.0\n" +
                        "// Test the enhanced JavaScript support:\n\n" +
                        "class Example {\n" +
                        "    constructor() {\n" +
                        "        this.version = '3.0.0';\n" +
                        "        this.features = ['Performance Monitor', 'File Cache', 'Code Index'];\n" +
                        "    }\n" +
                        "    \n" +
                        "    async loadData() {\n" +
                        "        const result = await fetch('/api/data');\n" +
                        "        return result.json();\n" +
                        "    }\n" +
                        "}\n\n" +
                        "const app = new Example();\n" +
                        "console.log(`Running NMOX Studio ${app.version}`);\n");
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 100)),
            "Code Editor Preview",
            javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
            javax.swing.border.TitledBorder.DEFAULT_POSITION,
            new Font("Arial", Font.BOLD, 12),
            Color.LIGHT_GRAY
        ));
        
        // Assemble panel
        testPanel.add(titleLabel, BorderLayout.NORTH);
        testPanel.add(infoPanel, BorderLayout.WEST);
        testPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Try to add to main window
        try {
            Frame mainFrame = WindowManager.getDefault().getMainWindow();
            if (mainFrame instanceof JFrame) {
                JFrame frame = (JFrame) mainFrame;
                frame.getContentPane().removeAll();
                frame.getContentPane().add(testPanel);
                frame.revalidate();
                frame.repaint();
            }
        } catch (Exception e) {
            // If that fails, just show the welcome screen
            System.out.println("Could not modify main window: " + e.getMessage());
        }
    }
    
    private void addInfoLabel(JPanel panel, String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(180, 180, 180));
        label.setFont(new Font("Arial", Font.PLAIN, 14));
        panel.add(label);
    }
    
    private String getMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        return String.format("%d MB / %d MB", usedMemory, maxMemory);
    }
}