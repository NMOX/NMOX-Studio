package org.nmox.studio.core;

import org.openide.modules.ModuleInstall;
import org.openide.util.NbBundle;

/**
 * Core module installer for NMOX Studio.
 * Manages the lifecycle of the core module and provides central services.
 */
@NbBundle.Messages({
    "NMOXStudioCore.name=NMOX Studio Core",
    "NMOXStudioCore.description=Core functionality and services for NMOX Studio"
})
public class NMOXStudioCore extends ModuleInstall {

    private static NMOXStudioCore instance;

    public NMOXStudioCore() {
        instance = this;
    }

    public static NMOXStudioCore getInstance() {
        return instance;
    }

    @Override
    public void restored() {
        super.restored();
        initializeCore();
    }

    @Override
    public void validate() throws IllegalStateException {
        super.validate();
    }

    @Override
    public boolean closing() {
        shutdownCore();
        return super.closing();
    }

    private void initializeCore() {
        System.setProperty("nmox.studio.version", "1.0.0");
        System.setProperty("nmox.studio.name", Bundle.NMOXStudioCore_name());
    }

    private void shutdownCore() {
        // Cleanup resources
    }

    public String getVersion() {
        return System.getProperty("nmox.studio.version", "unknown");
    }

    public String getName() {
        return Bundle.NMOXStudioCore_name();
    }
}