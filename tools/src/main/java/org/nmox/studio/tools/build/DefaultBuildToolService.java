package org.nmox.studio.tools.build;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmox.studio.tools.npm.NpmService;
import org.openide.util.lookup.ServiceProvider;

/**
 * Default implementation of BuildToolService.
 */
@ServiceProvider(service = BuildToolService.class)
public class DefaultBuildToolService implements BuildToolService {
    
    private final NpmService npmService;
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();
    private final Map<BuildToolType, BuildToolHandler> handlers = new HashMap<>();
    
    public DefaultBuildToolService() {
        this.npmService = NpmService.getInstance();
        initializeHandlers();
    }
    
    private void initializeHandlers() {
        handlers.put(BuildToolType.WEBPACK, new WebpackHandler());
        handlers.put(BuildToolType.VITE, new ViteHandler());
        handlers.put(BuildToolType.PARCEL, new ParcelHandler());
        handlers.put(BuildToolType.NPM_SCRIPTS, new NpmScriptsHandler());
    }
    
    @Override
    public BuildToolType detectBuildTool(File projectDir) {
        // Check for specific build tool configs
        for (BuildToolType type : BuildToolType.values()) {
            if (type.getConfigFiles() != null) {
                for (String configFile : type.getConfigFiles()) {
                    if (new File(projectDir, configFile).exists()) {
                        return type;
                    }
                }
            }
        }
        
        // Check package.json for build tool dependencies
        File packageJson = new File(projectDir, "package.json");
        if (packageJson.exists()) {
            try {
                String content = Files.readString(packageJson.toPath());
                if (content.contains("\"webpack\"")) return BuildToolType.WEBPACK;
                if (content.contains("\"vite\"")) return BuildToolType.VITE;
                if (content.contains("\"parcel\"")) return BuildToolType.PARCEL;
                return BuildToolType.NPM_SCRIPTS;
            } catch (IOException e) {
                // Fall through
            }
        }
        
        return BuildToolType.UNKNOWN;
    }
    
    @Override
    public CompletableFuture<BuildResult> build(File projectDir, BuildConfiguration config) {
        BuildToolType toolType = detectBuildTool(projectDir);
        BuildToolHandler handler = handlers.get(toolType);
        
        if (handler != null) {
            return handler.build(projectDir, config);
        }
        
        // Fallback to npm build
        return runNpmScript(projectDir, "build", config);
    }
    
    @Override
    public CompletableFuture<BuildResult> serve(File projectDir, BuildConfiguration config) {
        BuildToolType toolType = detectBuildTool(projectDir);
        BuildToolHandler handler = handlers.get(toolType);
        
        if (handler != null) {
            return handler.serve(projectDir, config);
        }
        
        // Fallback to npm start/dev
        return runNpmScript(projectDir, "dev", config);
    }
    
    @Override
    public CompletableFuture<BuildResult> test(File projectDir, BuildConfiguration config) {
        return runNpmScript(projectDir, "test", config);
    }
    
    @Override
    public CompletableFuture<BuildResult> lint(File projectDir, BuildConfiguration config) {
        return runNpmScript(projectDir, "lint", config);
    }
    
    @Override
    public List<String> getAvailableScripts(File projectDir) {
        List<String> scripts = new ArrayList<>();
        File packageJson = new File(projectDir, "package.json");
        
        if (packageJson.exists()) {
            try {
                String content = Files.readString(packageJson.toPath());
                Pattern pattern = Pattern.compile("\"scripts\"\\s*:\\s*\\{([^}]+)\\}");
                Matcher matcher = pattern.matcher(content);
                
                if (matcher.find()) {
                    String scriptsContent = matcher.group(1);
                    Pattern scriptPattern = Pattern.compile("\"([^\"]+)\"\\s*:");
                    Matcher scriptMatcher = scriptPattern.matcher(scriptsContent);
                    
                    while (scriptMatcher.find()) {
                        scripts.add(scriptMatcher.group(1));
                    }
                }
            } catch (IOException e) {
                // Return empty list
            }
        }
        
        return scripts;
    }
    
    @Override
    public CompletableFuture<BuildResult> runScript(File projectDir, String scriptName) {
        return runNpmScript(projectDir, scriptName, BuildConfiguration.builder().build());
    }
    
    @Override
    public void stopAll() {
        runningProcesses.values().forEach(process -> {
            if (process.isAlive()) {
                process.destroy();
            }
        });
        runningProcesses.clear();
    }
    
    private CompletableFuture<BuildResult> runNpmScript(File projectDir, String script, BuildConfiguration config) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                NpmService.PackageManager pm = npmService.detectPackageManager(projectDir);
                String command = npmService.getCommand(pm);
                
                List<String> args = new ArrayList<>();
                args.add(command);
                args.add("run");
                args.add(script);
                
                // Add environment variables
                ProcessBuilder pb = new ProcessBuilder(args);
                pb.directory(projectDir);
                Map<String, String> env = pb.environment();
                env.putAll(config.getEnvironment());
                env.put("NODE_ENV", config.getMode().getValue());
                
                Process process = pb.start();
                String processKey = projectDir.getAbsolutePath() + ":" + script;
                runningProcesses.put(processKey, process);
                
                // Capture output
                StringBuilder output = new StringBuilder();
                StringBuilder errorOutput = new StringBuilder();
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                     BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                    
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                }
                
                int exitCode = process.waitFor();
                runningProcesses.remove(processKey);
                
                long duration = System.currentTimeMillis() - startTime;
                BuildResult result = new BuildResult(
                    exitCode == 0,
                    output.toString(),
                    errorOutput.toString(),
                    duration
                );
                
                // Parse output for messages
                parseOutput(result, output.toString(), errorOutput.toString());
                
                return result;
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                return new BuildResult(false, "", e.getMessage(), duration);
            }
        });
    }
    
    private void parseOutput(BuildResult result, String output, String errorOutput) {
        // Parse webpack/vite/parcel output for errors and warnings
        Pattern errorPattern = Pattern.compile("ERROR in (.+)");
        Pattern warningPattern = Pattern.compile("WARNING in (.+)");
        
        Matcher errorMatcher = errorPattern.matcher(errorOutput);
        while (errorMatcher.find()) {
            result.addMessage(new BuildResult.BuildMessage(
                BuildResult.BuildMessage.Type.ERROR,
                errorMatcher.group(1)
            ));
            result.getStatistics().setErrors(result.getStatistics().getErrors() + 1);
        }
        
        Matcher warningMatcher = warningPattern.matcher(output);
        while (warningMatcher.find()) {
            result.addMessage(new BuildResult.BuildMessage(
                BuildResult.BuildMessage.Type.WARNING,
                warningMatcher.group(1)
            ));
            result.getStatistics().setWarnings(result.getStatistics().getWarnings() + 1);
        }
    }
    
    /**
     * Handler interface for specific build tools.
     */
    private interface BuildToolHandler {
        CompletableFuture<BuildResult> build(File projectDir, BuildConfiguration config);
        CompletableFuture<BuildResult> serve(File projectDir, BuildConfiguration config);
    }
    
    /**
     * Webpack-specific handler.
     */
    private class WebpackHandler implements BuildToolHandler {
        @Override
        public CompletableFuture<BuildResult> build(File projectDir, BuildConfiguration config) {
            // Use webpack CLI with appropriate flags
            return runNpmScript(projectDir, "build", config);
        }
        
        @Override
        public CompletableFuture<BuildResult> serve(File projectDir, BuildConfiguration config) {
            // Use webpack-dev-server
            return runNpmScript(projectDir, "serve", config);
        }
    }
    
    /**
     * Vite-specific handler.
     */
    private class ViteHandler implements BuildToolHandler {
        @Override
        public CompletableFuture<BuildResult> build(File projectDir, BuildConfiguration config) {
            return runNpmScript(projectDir, "build", config);
        }
        
        @Override
        public CompletableFuture<BuildResult> serve(File projectDir, BuildConfiguration config) {
            return runNpmScript(projectDir, "dev", config);
        }
    }
    
    /**
     * Parcel-specific handler.
     */
    private class ParcelHandler implements BuildToolHandler {
        @Override
        public CompletableFuture<BuildResult> build(File projectDir, BuildConfiguration config) {
            return runNpmScript(projectDir, "build", config);
        }
        
        @Override
        public CompletableFuture<BuildResult> serve(File projectDir, BuildConfiguration config) {
            return runNpmScript(projectDir, "start", config);
        }
    }
    
    /**
     * NPM Scripts handler (fallback).
     */
    private class NpmScriptsHandler implements BuildToolHandler {
        @Override
        public CompletableFuture<BuildResult> build(File projectDir, BuildConfiguration config) {
            return runNpmScript(projectDir, "build", config);
        }
        
        @Override
        public CompletableFuture<BuildResult> serve(File projectDir, BuildConfiguration config) {
            // Try common script names
            List<String> scripts = getAvailableScripts(projectDir);
            if (scripts.contains("dev")) return runNpmScript(projectDir, "dev", config);
            if (scripts.contains("serve")) return runNpmScript(projectDir, "serve", config);
            if (scripts.contains("start")) return runNpmScript(projectDir, "start", config);
            return runNpmScript(projectDir, "start", config);
        }
    }
}