package org.nmox.studio.tools.debug;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.openide.util.Lookup;

/**
 * Service interface for JavaScript debugging capabilities.
 */
public interface DebuggerService {
    
    /**
     * Starts a debug session.
     */
    CompletableFuture<DebugSession> startDebugSession(DebugConfiguration config);
    
    /**
     * Attaches to an existing Chrome/Edge instance.
     */
    CompletableFuture<DebugSession> attach(String host, int port);
    
    /**
     * Launches Chrome/Edge with debugging enabled.
     */
    CompletableFuture<DebugSession> launch(URI url, LaunchConfiguration config);
    
    /**
     * Gets all active debug sessions.
     */
    List<DebugSession> getActiveSessions();
    
    /**
     * Stops a debug session.
     */
    void stopSession(String sessionId);
    
    /**
     * Stops all debug sessions.
     */
    void stopAllSessions();
    
    /**
     * Gets the default instance.
     */
    static DebuggerService getInstance() {
        return Lookup.getDefault().lookup(DebuggerService.class);
    }
    
    /**
     * Debug configuration.
     */
    class DebugConfiguration {
        private String type; // "chrome", "node", "edge"
        private String name;
        private String request; // "launch" or "attach"
        private String url;
        private String webRoot;
        private int port = 9222;
        private boolean sourceMaps = true;
        private Map<String, String> sourceMapPathOverrides;
        private String[] skipFiles;
        private boolean trace = false;
        
        // Builder pattern
        public static class Builder {
            private DebugConfiguration config = new DebugConfiguration();
            
            public Builder type(String type) {
                config.type = type;
                return this;
            }
            
            public Builder name(String name) {
                config.name = name;
                return this;
            }
            
            public Builder request(String request) {
                config.request = request;
                return this;
            }
            
            public Builder url(String url) {
                config.url = url;
                return this;
            }
            
            public Builder webRoot(String webRoot) {
                config.webRoot = webRoot;
                return this;
            }
            
            public Builder port(int port) {
                config.port = port;
                return this;
            }
            
            public Builder sourceMaps(boolean sourceMaps) {
                config.sourceMaps = sourceMaps;
                return this;
            }
            
            public DebugConfiguration build() {
                return config;
            }
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public String getType() { return type; }
        public String getName() { return name; }
        public String getRequest() { return request; }
        public String getUrl() { return url; }
        public String getWebRoot() { return webRoot; }
        public int getPort() { return port; }
        public boolean isSourceMaps() { return sourceMaps; }
        public Map<String, String> getSourceMapPathOverrides() { return sourceMapPathOverrides; }
        public String[] getSkipFiles() { return skipFiles; }
        public boolean isTrace() { return trace; }
    }
    
    /**
     * Launch configuration for browser.
     */
    class LaunchConfiguration {
        private String browser = "chrome"; // "chrome", "edge", "firefox"
        private String userDataDir;
        private boolean headless = false;
        private String[] args;
        private Map<String, String> env;
        private int width = 1280;
        private int height = 720;
        
        // Builder pattern
        public static class Builder {
            private LaunchConfiguration config = new LaunchConfiguration();
            
            public Builder browser(String browser) {
                config.browser = browser;
                return this;
            }
            
            public Builder userDataDir(String dir) {
                config.userDataDir = dir;
                return this;
            }
            
            public Builder headless(boolean headless) {
                config.headless = headless;
                return this;
            }
            
            public Builder args(String... args) {
                config.args = args;
                return this;
            }
            
            public Builder windowSize(int width, int height) {
                config.width = width;
                config.height = height;
                return this;
            }
            
            public LaunchConfiguration build() {
                return config;
            }
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public String getBrowser() { return browser; }
        public String getUserDataDir() { return userDataDir; }
        public boolean isHeadless() { return headless; }
        public String[] getArgs() { return args; }
        public Map<String, String> getEnv() { return env; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
    }
}