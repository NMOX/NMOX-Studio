package org.nmox.studio.tools.build;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openide.util.lookup.ServiceProvider;

/**
 * Service for TypeScript compilation and configuration.
 */
@ServiceProvider(service = TypeScriptService.class)
public class TypeScriptService {
    
    /**
     * TypeScript compiler options.
     */
    public static class TsConfig {
        private String target = "ES2020";
        private String module = "ESNext";
        private boolean strict = true;
        private boolean esModuleInterop = true;
        private boolean skipLibCheck = true;
        private boolean forceConsistentCasingInFileNames = true;
        private String moduleResolution = "node";
        private boolean resolveJsonModule = true;
        private boolean isolatedModules = true;
        private boolean noEmit = false;
        private String jsx = "react-jsx";
        private String outDir = "./dist";
        private String rootDir = "./src";
        private List<String> lib = Arrays.asList("ES2020", "DOM", "DOM.Iterable");
        private Map<String, List<String>> paths = new HashMap<>();
        private List<String> types = new ArrayList<>();
        private boolean sourceMap = true;
        private boolean declaration = false;
        private boolean declarationMap = false;
        private boolean allowJs = false;
        private boolean checkJs = false;
        
        // Getters and setters
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
        
        public String getModule() { return module; }
        public void setModule(String module) { this.module = module; }
        
        public boolean isStrict() { return strict; }
        public void setStrict(boolean strict) { this.strict = strict; }
        
        public String getJsx() { return jsx; }
        public void setJsx(String jsx) { this.jsx = jsx; }
        
        public String getOutDir() { return outDir; }
        public void setOutDir(String outDir) { this.outDir = outDir; }
        
        public String getRootDir() { return rootDir; }
        public void setRootDir(String rootDir) { this.rootDir = rootDir; }
        
        public boolean isSourceMap() { return sourceMap; }
        public void setSourceMap(boolean sourceMap) { this.sourceMap = sourceMap; }
        
        public String toJson() {
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"compilerOptions\": {\n");
            json.append("    \"target\": \"").append(target).append("\",\n");
            json.append("    \"module\": \"").append(module).append("\",\n");
            json.append("    \"strict\": ").append(strict).append(",\n");
            json.append("    \"esModuleInterop\": ").append(esModuleInterop).append(",\n");
            json.append("    \"skipLibCheck\": ").append(skipLibCheck).append(",\n");
            json.append("    \"forceConsistentCasingInFileNames\": ").append(forceConsistentCasingInFileNames).append(",\n");
            json.append("    \"moduleResolution\": \"").append(moduleResolution).append("\",\n");
            json.append("    \"resolveJsonModule\": ").append(resolveJsonModule).append(",\n");
            json.append("    \"isolatedModules\": ").append(isolatedModules).append(",\n");
            json.append("    \"noEmit\": ").append(noEmit).append(",\n");
            json.append("    \"jsx\": \"").append(jsx).append("\",\n");
            json.append("    \"outDir\": \"").append(outDir).append("\",\n");
            json.append("    \"rootDir\": \"").append(rootDir).append("\",\n");
            json.append("    \"sourceMap\": ").append(sourceMap).append(",\n");
            json.append("    \"declaration\": ").append(declaration).append(",\n");
            json.append("    \"allowJs\": ").append(allowJs).append(",\n");
            json.append("    \"checkJs\": ").append(checkJs).append(",\n");
            json.append("    \"lib\": [");
            for (int i = 0; i < lib.size(); i++) {
                json.append("\"").append(lib.get(i)).append("\"");
                if (i < lib.size() - 1) json.append(", ");
            }
            json.append("]\n");
            json.append("  },\n");
            json.append("  \"include\": [\"src/**/*\"],\n");
            json.append("  \"exclude\": [\"node_modules\", \"dist\"]\n");
            json.append("}\n");
            return json.toString();
        }
    }
    
    /**
     * Checks if TypeScript is installed in the project.
     */
    public boolean isTypeScriptInstalled(File projectDir) {
        File nodeModules = new File(projectDir, "node_modules/typescript");
        return nodeModules.exists();
    }
    
    /**
     * Checks if a tsconfig.json exists.
     */
    public boolean hasTsConfig(File projectDir) {
        return new File(projectDir, "tsconfig.json").exists();
    }
    
    /**
     * Creates a default tsconfig.json file.
     */
    public void createTsConfig(File projectDir, TsConfig config) throws IOException {
        File tsConfigFile = new File(projectDir, "tsconfig.json");
        Files.writeString(tsConfigFile.toPath(), config.toJson());
    }
    
    /**
     * Reads and parses existing tsconfig.json.
     */
    public TsConfig readTsConfig(File projectDir) throws IOException {
        File tsConfigFile = new File(projectDir, "tsconfig.json");
        if (!tsConfigFile.exists()) {
            return new TsConfig();
        }
        
        String content = Files.readString(tsConfigFile.toPath());
        return parseTsConfig(content);
    }
    
    /**
     * Compiles TypeScript files.
     */
    public CompletableFuture<CompileResult> compile(File projectDir, boolean watch) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<String> command = new ArrayList<>();
                
                // Use npx to run TypeScript compiler
                command.add("npx");
                command.add("tsc");
                
                if (watch) {
                    command.add("--watch");
                }
                
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(projectDir);
                pb.redirectErrorStream(true);
                
                Process process = pb.start();
                
                List<CompileError> errors = new ArrayList<>();
                List<CompileError> warnings = new ArrayList<>();
                
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        CompileError error = parseCompilerOutput(line);
                        if (error != null) {
                            if (error.severity == CompileError.Severity.ERROR) {
                                errors.add(error);
                            } else {
                                warnings.add(error);
                            }
                        }
                    }
                }
                
                int exitCode = process.waitFor();
                
                return new CompileResult(exitCode == 0, errors, warnings);
                
            } catch (Exception e) {
                CompileError error = new CompileError(
                    CompileError.Severity.ERROR,
                    e.getMessage(),
                    null, 0, 0
                );
                return new CompileResult(false, Arrays.asList(error), new ArrayList<>());
            }
        });
    }
    
    /**
     * Type checks TypeScript files without emitting.
     */
    public CompletableFuture<CompileResult> typeCheck(File projectDir) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<String> command = Arrays.asList("npx", "tsc", "--noEmit");
                
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(projectDir);
                pb.redirectErrorStream(true);
                
                Process process = pb.start();
                
                List<CompileError> errors = new ArrayList<>();
                List<CompileError> warnings = new ArrayList<>();
                
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        CompileError error = parseCompilerOutput(line);
                        if (error != null) {
                            if (error.severity == CompileError.Severity.ERROR) {
                                errors.add(error);
                            } else {
                                warnings.add(error);
                            }
                        }
                    }
                }
                
                int exitCode = process.waitFor();
                
                return new CompileResult(exitCode == 0, errors, warnings);
                
            } catch (Exception e) {
                CompileError error = new CompileError(
                    CompileError.Severity.ERROR,
                    e.getMessage(),
                    null, 0, 0
                );
                return new CompileResult(false, Arrays.asList(error), new ArrayList<>());
            }
        });
    }
    
    /**
     * Gets TypeScript version.
     */
    public String getTypeScriptVersion(File projectDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder("npx", "tsc", "--version");
            pb.directory(projectDir);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && line.startsWith("Version")) {
                    return line.substring(8); // Remove "Version " prefix
                }
            }
            
            process.waitFor();
        } catch (Exception e) {
            // Return unknown
        }
        return "unknown";
    }
    
    private TsConfig parseTsConfig(String content) {
        TsConfig config = new TsConfig();
        
        // Simple JSON parsing for common options
        Pattern targetPattern = Pattern.compile("\"target\"\\s*:\\s*\"([^\"]+)\"");
        Matcher targetMatcher = targetPattern.matcher(content);
        if (targetMatcher.find()) {
            config.setTarget(targetMatcher.group(1));
        }
        
        Pattern modulePattern = Pattern.compile("\"module\"\\s*:\\s*\"([^\"]+)\"");
        Matcher moduleMatcher = modulePattern.matcher(content);
        if (moduleMatcher.find()) {
            config.setModule(moduleMatcher.group(1));
        }
        
        Pattern strictPattern = Pattern.compile("\"strict\"\\s*:\\s*(true|false)");
        Matcher strictMatcher = strictPattern.matcher(content);
        if (strictMatcher.find()) {
            config.setStrict(Boolean.parseBoolean(strictMatcher.group(1)));
        }
        
        Pattern jsxPattern = Pattern.compile("\"jsx\"\\s*:\\s*\"([^\"]+)\"");
        Matcher jsxMatcher = jsxPattern.matcher(content);
        if (jsxMatcher.find()) {
            config.setJsx(jsxMatcher.group(1));
        }
        
        return config;
    }
    
    private CompileError parseCompilerOutput(String line) {
        // Parse TypeScript compiler output
        // Format: file.ts(line,col): error TS2345: Message
        Pattern pattern = Pattern.compile("(.+?)\\((\\d+),(\\d+)\\):\\s+(error|warning)\\s+TS\\d+:\\s+(.+)");
        Matcher matcher = pattern.matcher(line);
        
        if (matcher.matches()) {
            String file = matcher.group(1);
            int line_num = Integer.parseInt(matcher.group(2));
            int column = Integer.parseInt(matcher.group(3));
            String severity = matcher.group(4);
            String message = matcher.group(5);
            
            return new CompileError(
                severity.equals("error") ? CompileError.Severity.ERROR : CompileError.Severity.WARNING,
                message,
                file,
                line_num,
                column
            );
        }
        
        return null;
    }
    
    /**
     * Result of TypeScript compilation.
     */
    public static class CompileResult {
        private final boolean success;
        private final List<CompileError> errors;
        private final List<CompileError> warnings;
        
        public CompileResult(boolean success, List<CompileError> errors, List<CompileError> warnings) {
            this.success = success;
            this.errors = errors;
            this.warnings = warnings;
        }
        
        public boolean isSuccess() { return success; }
        public List<CompileError> getErrors() { return errors; }
        public List<CompileError> getWarnings() { return warnings; }
    }
    
    /**
     * Compilation error or warning.
     */
    public static class CompileError {
        public enum Severity {
            ERROR, WARNING, INFO
        }
        
        private final Severity severity;
        private final String message;
        private final String file;
        private final int line;
        private final int column;
        
        public CompileError(Severity severity, String message, String file, int line, int column) {
            this.severity = severity;
            this.message = message;
            this.file = file;
            this.line = line;
            this.column = column;
        }
        
        public Severity getSeverity() { return severity; }
        public String getMessage() { return message; }
        public String getFile() { return file; }
        public int getLine() { return line; }
        public int getColumn() { return column; }
    }
}