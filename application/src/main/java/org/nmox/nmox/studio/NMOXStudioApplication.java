package org.nmox.nmox.studio;

import org.openide.modules.OnStart;
import org.openide.util.NbBundle;
import java.util.logging.Logger;

/**
 * Application startup hook for NMOX Studio.
 * Initializes the application and performs startup tasks.
 */
@OnStart
@NbBundle.Messages({
    "NMOXStudio.starting=Starting NMOX Studio...",
    "NMOXStudio.welcome=Welcome to NMOX Studio - Professional Media Development Environment"
})
public class NMOXStudioApplication implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(NMOXStudioApplication.class.getName());

    @Override
    public void run() {
        LOGGER.info(Bundle.NMOXStudio_starting());
        
        // Initialize application
        initializeApplication();
        
        LOGGER.info(Bundle.NMOXStudio_welcome());
    }

    private void initializeApplication() {
        // Set application properties
        System.setProperty("nmox.studio.app.name", "NMOX Studio");
        System.setProperty("nmox.studio.app.version", "1.0.0");
        
        // Configure logging
        configureLogging();
        
        // Initialize services
        initializeServices();
    }

    private void configureLogging() {
        // Configure application-wide logging settings
        System.setProperty("java.util.logging.config.file", 
            System.getProperty("user.dir") + "/config/logging.properties");
    }

    private void initializeServices() {
        // Services will be initialized automatically through the ServiceManager
        LOGGER.info("Core services initialized");
    }
}
