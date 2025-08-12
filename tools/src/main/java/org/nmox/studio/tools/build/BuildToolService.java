package org.nmox.studio.tools.build;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.openide.util.Lookup;

/**
 * Service interface for build tool integration.
 * Provides a unified API for different build tools.
 */
public interface BuildToolService {
    
    /**
     * Detects which build tool is configured for the project.
     */
    BuildToolType detectBuildTool(File projectDir);
    
    /**
     * Runs a build command.
     */
    CompletableFuture<BuildResult> build(File projectDir, BuildConfiguration config);
    
    /**
     * Runs the development server.
     */
    CompletableFuture<BuildResult> serve(File projectDir, BuildConfiguration config);
    
    /**
     * Runs tests.
     */
    CompletableFuture<BuildResult> test(File projectDir, BuildConfiguration config);
    
    /**
     * Runs linting.
     */
    CompletableFuture<BuildResult> lint(File projectDir, BuildConfiguration config);
    
    /**
     * Gets available scripts from package.json or build config.
     */
    List<String> getAvailableScripts(File projectDir);
    
    /**
     * Runs a custom script.
     */
    CompletableFuture<BuildResult> runScript(File projectDir, String scriptName);
    
    /**
     * Stops any running build processes.
     */
    void stopAll();
    
    /**
     * Gets the default instance of the build tool service.
     */
    static BuildToolService getDefault() {
        return Lookup.getDefault().lookup(BuildToolService.class);
    }
    
    /**
     * Gets the singleton instance of BuildToolService.
     */
    static BuildToolService getInstance() {
        return Lookup.getDefault().lookup(BuildToolService.class);
    }
    
    enum BuildToolType {
        WEBPACK("Webpack", "webpack.config.js"),
        VITE("Vite", "vite.config.js", "vite.config.ts"),
        PARCEL("Parcel", ".parcelrc"),
        ROLLUP("Rollup", "rollup.config.js"),
        SNOWPACK("Snowpack", "snowpack.config.js"),
        ESBUILD("esbuild", "esbuild.config.js"),
        NPM_SCRIPTS("NPM Scripts", "package.json"),
        UNKNOWN("Unknown", null);
        
        private final String displayName;
        private final String[] configFiles;
        
        BuildToolType(String displayName, String... configFiles) {
            this.displayName = displayName;
            this.configFiles = configFiles;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String[] getConfigFiles() {
            return configFiles;
        }
    }
}