package org.nmox.studio.tools.build.ui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.*;
import javax.swing.text.*;
import org.netbeans.api.settings.ConvertAsProperties;
import org.nmox.studio.tools.build.BuildResult;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 * Build output window component.
 */
@ConvertAsProperties(
        dtd = "-//org.nmox.studio.tools.build.ui//BuildOutput//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "BuildOutputTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "output", openAtStartup = false)
@ActionID(category = "Window", id = "org.nmox.studio.tools.build.ui.BuildOutputTopComponent")
@ActionReference(path = "Menu/Window", position = 400)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_BuildOutputAction",
        preferredID = "BuildOutputTopComponent"
)
@Messages({
    "CTL_BuildOutputAction=Build Output",
    "CTL_BuildOutputTopComponent=Build Output",
    "HINT_BuildOutputTopComponent=Shows build tool output and compilation results"
})
public final class BuildOutputTopComponent extends TopComponent {
    
    private JTextPane outputPane;
    private StyledDocument doc;
    private JToolBar toolbar;
    private JButton clearButton;
    private JButton stopButton;
    private JComboBox<String> filterCombo;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    
    private Style defaultStyle;
    private Style errorStyle;
    private Style warningStyle;
    private Style successStyle;
    private Style infoStyle;
    private Style timestampStyle;
    
    private static BuildOutputTopComponent instance;
    
    public BuildOutputTopComponent() {
        initComponents();
        setName(NbBundle.getMessage(BuildOutputTopComponent.class, "CTL_BuildOutputTopComponent"));
        setToolTipText(NbBundle.getMessage(BuildOutputTopComponent.class, "HINT_BuildOutputTopComponent"));
        initStyles();
    }
    
    public static BuildOutputTopComponent getInstance() {
        if (instance == null) {
            instance = new BuildOutputTopComponent();
        }
        return instance;
    }
    
    private void initComponents() {
        setLayout(new BorderLayout());
        
        // Create toolbar
        toolbar = new JToolBar();
        toolbar.setFloatable(false);
        
        clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clear());
        toolbar.add(clearButton);
        
        stopButton = new JButton("Stop");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopBuild());
        toolbar.add(stopButton);
        
        toolbar.addSeparator();
        
        filterCombo = new JComboBox<>(new String[]{"All", "Errors", "Warnings", "Info"});
        filterCombo.addActionListener(e -> applyFilter());
        toolbar.add(new JLabel("Filter: "));
        toolbar.add(filterCombo);
        
        toolbar.add(Box.createHorizontalGlue());
        
        statusLabel = new JLabel("Ready");
        toolbar.add(statusLabel);
        
        add(toolbar, BorderLayout.NORTH);
        
        // Create output pane
        outputPane = new JTextPane();
        outputPane.setEditable(false);
        outputPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        outputPane.setBackground(new Color(39, 40, 34)); // Dark background
        outputPane.setForeground(new Color(248, 248, 242)); // Light text
        
        doc = outputPane.getStyledDocument();
        
        JScrollPane scrollPane = new JScrollPane(outputPane);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        add(scrollPane, BorderLayout.CENTER);
        
        // Progress bar at bottom
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        add(progressBar, BorderLayout.SOUTH);
    }
    
    private void initStyles() {
        defaultStyle = outputPane.addStyle("default", null);
        StyleConstants.setForeground(defaultStyle, new Color(248, 248, 242));
        
        errorStyle = outputPane.addStyle("error", null);
        StyleConstants.setForeground(errorStyle, new Color(249, 38, 114)); // Red
        StyleConstants.setBold(errorStyle, true);
        
        warningStyle = outputPane.addStyle("warning", null);
        StyleConstants.setForeground(warningStyle, new Color(230, 219, 116)); // Yellow
        
        successStyle = outputPane.addStyle("success", null);
        StyleConstants.setForeground(successStyle, new Color(166, 226, 46)); // Green
        
        infoStyle = outputPane.addStyle("info", null);
        StyleConstants.setForeground(infoStyle, new Color(102, 217, 239)); // Cyan
        
        timestampStyle = outputPane.addStyle("timestamp", null);
        StyleConstants.setForeground(timestampStyle, new Color(117, 113, 94)); // Gray
        StyleConstants.setItalic(timestampStyle, true);
    }
    
    public void startBuild(String taskName) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Building: " + taskName);
            stopButton.setEnabled(true);
            progressBar.setIndeterminate(true);
            progressBar.setVisible(true);
            
            appendTimestamp();
            append("Starting build: " + taskName + "\n", infoStyle);
            append("─".repeat(60) + "\n", defaultStyle);
        });
    }
    
    public void endBuild(BuildResult result) {
        SwingUtilities.invokeLater(() -> {
            stopButton.setEnabled(false);
            progressBar.setVisible(false);
            
            append("─".repeat(60) + "\n", defaultStyle);
            
            if (result.isSuccess()) {
                statusLabel.setText("Build successful");
                append("✓ Build completed successfully", successStyle);
            } else {
                statusLabel.setText("Build failed");
                append("✗ Build failed", errorStyle);
            }
            
            append(" (", defaultStyle);
            append(formatDuration(result.getDuration()), timestampStyle);
            append(")\n", defaultStyle);
            
            // Show statistics
            BuildResult.BuildStatistics stats = result.getStatistics();
            if (stats.getFilesProcessed() > 0 || stats.getErrors() > 0 || stats.getWarnings() > 0) {
                append("\nStatistics:\n", infoStyle);
                if (stats.getFilesProcessed() > 0) {
                    append("  Files processed: " + stats.getFilesProcessed() + "\n", defaultStyle);
                }
                if (stats.getErrors() > 0) {
                    append("  Errors: " + stats.getErrors() + "\n", errorStyle);
                }
                if (stats.getWarnings() > 0) {
                    append("  Warnings: " + stats.getWarnings() + "\n", warningStyle);
                }
                if (stats.getOutputSize() > 0) {
                    append("  Output size: " + formatSize(stats.getOutputSize()) + "\n", defaultStyle);
                    if (stats.getOriginalSize() > 0) {
                        append("  Compression: " + 
                            String.format("%.1f%%", stats.getCompressionRatio() * 100) + "\n", successStyle);
                    }
                }
            }
            
            append("\n", defaultStyle);
        });
    }
    
    public void appendOutput(String text) {
        SwingUtilities.invokeLater(() -> {
            append(text, defaultStyle);
        });
    }
    
    public void appendError(String text) {
        SwingUtilities.invokeLater(() -> {
            append(text, errorStyle);
        });
    }
    
    public void appendWarning(String text) {
        SwingUtilities.invokeLater(() -> {
            append(text, warningStyle);
        });
    }
    
    public void appendInfo(String text) {
        SwingUtilities.invokeLater(() -> {
            append(text, infoStyle);
        });
    }
    
    public void appendBuildMessage(BuildResult.BuildMessage message) {
        SwingUtilities.invokeLater(() -> {
            Style style = defaultStyle;
            String prefix = "";
            
            switch (message.getType()) {
                case ERROR:
                    style = errorStyle;
                    prefix = "[ERROR] ";
                    break;
                case WARNING:
                    style = warningStyle;
                    prefix = "[WARN] ";
                    break;
                case INFO:
                    style = infoStyle;
                    prefix = "[INFO] ";
                    break;
                case SUCCESS:
                    style = successStyle;
                    prefix = "[OK] ";
                    break;
            }
            
            append(prefix, style);
            
            if (message.getFile() != null) {
                append(message.getFile(), defaultStyle);
                if (message.getLine() > 0) {
                    append(":" + message.getLine(), timestampStyle);
                    if (message.getColumn() > 0) {
                        append(":" + message.getColumn(), timestampStyle);
                    }
                }
                append(" ", defaultStyle);
            }
            
            append(message.getMessage() + "\n", style);
        });
    }
    
    private void append(String text, Style style) {
        try {
            doc.insertString(doc.getLength(), text, style);
            outputPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            // Ignore
        }
    }
    
    private void appendTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("[HH:mm:ss] ");
        append(sdf.format(new Date()), timestampStyle);
    }
    
    private void clear() {
        try {
            doc.remove(0, doc.getLength());
            statusLabel.setText("Ready");
        } catch (BadLocationException e) {
            // Ignore
        }
    }
    
    private void stopBuild() {
        // TODO: Implement build cancellation
        stopButton.setEnabled(false);
        progressBar.setVisible(false);
        statusLabel.setText("Build stopped");
    }
    
    private void applyFilter() {
        // TODO: Implement output filtering
    }
    
    private String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        } else if (millis < 60000) {
            return String.format("%.1fs", millis / 1000.0);
        } else {
            long minutes = millis / 60000;
            long seconds = (millis % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
    }
    
    @Override
    public void componentOpened() {
        // Component opened
    }
    
    @Override
    public void componentClosed() {
        // Component closed
    }
    
    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }
    
    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
    }
}