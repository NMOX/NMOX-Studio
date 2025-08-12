package org.nmox.studio.tools.build;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for build operations.
 */
public class BuildConfiguration {
    
    private BuildMode mode = BuildMode.DEVELOPMENT;
    private boolean watch = false;
    private boolean sourceMaps = true;
    private boolean minify = false;
    private String outputDir = "dist";
    private String publicPath = "/";
    private int port = 3000;
    private boolean open = true;
    private Map<String, String> environment = new HashMap<>();
    private Map<String, Object> customOptions = new HashMap<>();
    
    public enum BuildMode {
        DEVELOPMENT("development"),
        PRODUCTION("production"),
        TEST("test");
        
        private final String value;
        
        BuildMode(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    // Builder pattern for easy configuration
    public static class Builder {
        private BuildConfiguration config = new BuildConfiguration();
        
        public Builder mode(BuildMode mode) {
            config.mode = mode;
            return this;
        }
        
        public Builder watch(boolean watch) {
            config.watch = watch;
            return this;
        }
        
        public Builder sourceMaps(boolean sourceMaps) {
            config.sourceMaps = sourceMaps;
            return this;
        }
        
        public Builder minify(boolean minify) {
            config.minify = minify;
            return this;
        }
        
        public Builder outputDir(String outputDir) {
            config.outputDir = outputDir;
            return this;
        }
        
        public Builder publicPath(String publicPath) {
            config.publicPath = publicPath;
            return this;
        }
        
        public Builder port(int port) {
            config.port = port;
            return this;
        }
        
        public Builder open(boolean open) {
            config.open = open;
            return this;
        }
        
        public Builder environment(Map<String, String> environment) {
            config.environment = environment;
            return this;
        }
        
        public Builder addEnvironment(String key, String value) {
            config.environment.put(key, value);
            return this;
        }
        
        public Builder customOption(String key, Object value) {
            config.customOptions.put(key, value);
            return this;
        }
        
        public BuildConfiguration build() {
            // Set production defaults
            if (config.mode == BuildMode.PRODUCTION) {
                config.minify = true;
                config.sourceMaps = false;
            }
            return config;
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public BuildMode getMode() { return mode; }
    public boolean isWatch() { return watch; }
    public boolean isSourceMaps() { return sourceMaps; }
    public boolean isMinify() { return minify; }
    public String getOutputDir() { return outputDir; }
    public String getPublicPath() { return publicPath; }
    public int getPort() { return port; }
    public boolean isOpen() { return open; }
    public Map<String, String> getEnvironment() { return environment; }
    public Map<String, Object> getCustomOptions() { return customOptions; }
}