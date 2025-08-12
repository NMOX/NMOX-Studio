package org.nmox.studio.core;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Window;
import org.openide.modules.OnStart;

/**
 * Installs FlatLaf dark theme on startup.
 * This gives NMOX Studio a modern dark appearance.
 */
@OnStart
public class ThemeInstaller implements Runnable {
    
    @Override
    public void run() {
        // Run on EDT
        SwingUtilities.invokeLater(() -> {
            try {
                // Set FlatLaf Dark theme
                UIManager.setLookAndFeel(new FlatDarkLaf());
                
                // Update all existing windows
                for (Window window : Window.getWindows()) {
                    SwingUtilities.updateComponentTreeUI(window);
                    window.repaint();
                }
                
                System.out.println("NMOX Studio: FlatLaf Dark theme installed successfully");
                
            } catch (Exception e) {
                System.err.println("NMOX Studio: Failed to install FlatLaf theme: " + e.getMessage());
                e.printStackTrace();
                // Fall back to default theme
            }
        });
    }
}