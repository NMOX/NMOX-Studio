package org.nmox.studio.tools.npm;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;
import org.openide.windows.OutputWriter;

@ServiceProvider(service = NpmService.class)
public class NpmService {

    private static final String NPM_COMMAND = "npm";
    private static final String YARN_COMMAND = "yarn";
    private static final String PNPM_COMMAND = "pnpm";

    public enum PackageManager {
        NPM, YARN, PNPM
    }

    public CompletableFuture<String> install(File projectDir, PackageManager manager) {
        return runCommand(projectDir, getCommand(manager), "install");
    }

    public CompletableFuture<String> runScript(File projectDir, String scriptName, PackageManager manager) {
        return runCommand(projectDir, getCommand(manager), "run", scriptName);
    }

    public boolean isAvailable(PackageManager manager) {
        try {
            Process process = org.nmox.studio.core.process.ProcessSupport
                    .builder(java.util.List.of(getCommand(manager), "--version"))
                    .redirectErrorStream(true)
                    .start();
            // bound the wait: a wedged tool must not hang the calling thread
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    public PackageManager detectPackageManager(File projectDir) {
        if (new File(projectDir, "yarn.lock").exists()) {
            return PackageManager.YARN;
        } else if (new File(projectDir, "pnpm-lock.yaml").exists()) {
            return PackageManager.PNPM;
        } else if (new File(projectDir, "package-lock.json").exists()) {
            return PackageManager.NPM;
        }
        return PackageManager.NPM;
    }

    public String getCommand(PackageManager manager) {
        switch (manager) {
            case YARN:
                return YARN_COMMAND;
            case PNPM:
                return PNPM_COMMAND;
            default:
                return NPM_COMMAND;
        }
    }

    private CompletableFuture<String> runCommand(File workingDir, String... command) {
        return CompletableFuture.supplyAsync(() -> {
            InputOutput io = IOProvider.getDefault().getIO("NPM Output", false);
            io.select();
            OutputWriter out = io.getOut();
            OutputWriter err = io.getErr();
            
            try {
                out.println("Running: " + String.join(" ", command));
                out.println("Directory: " + workingDir.getAbsolutePath());
                out.println("----------------------------------------");
                
                ProcessBuilder pb = org.nmox.studio.core.process.ProcessSupport.builder(java.util.List.of(command));
                pb.directory(workingDir);
                pb.redirectErrorStream(true);
                
                Process process = pb.start();
                StringBuilder output = new StringBuilder();
                
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        out.println(line);
                        output.append(line).append("\n");
                    }
                }
                
                // the read loop above already drained all output (it blocks
                // until stdout closes), so the process should exit promptly;
                // a generous grace then reap guards against a child that
                // closes its pipe but never exits, without ever truncating a
                // legitimately slow build (whose output had to finish first)
                if (!process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new RuntimeException("Command did not exit after its output ended");
                }
                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    err.println("Command failed with exit code: " + exitCode);
                    throw new RuntimeException("Command failed with exit code: " + exitCode + "\nOutput: " + output);
                } else {
                    out.println("----------------------------------------");
                    out.println("Command completed successfully");
                }
                
                return output.toString();
            } catch (IOException | InterruptedException e) {
                err.println("Failed to execute command: " + e.getMessage());
                throw new RuntimeException("Failed to execute command: " + String.join(" ", command), e);
            } finally {
                out.close();
                err.close();
            }
        });
    }
    
    /**
     * Run a simple command string (for compatibility with NpmExplorerTopComponent)
     */
    public void runCommand(File projectDir, String command) {
        List<String> parts = parseArguments(command);
        List<String> cmdList = new ArrayList<>();
        cmdList.add(getCommand(detectPackageManager(projectDir)));
        cmdList.addAll(parts);
        runCommand(projectDir, cmdList.toArray(new String[0]));
    }

    static List<String> parseArguments(String commandLine) {
        List<String> list = new ArrayList<>();
        if (commandLine == null || commandLine.trim().isEmpty()) {
            return list;
        }
        
        StringBuilder current = new StringBuilder();
        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;
        
        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);
            if (c == '\"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            } else if (Character.isWhitespace(c) && !inDoubleQuotes && !inSingleQuotes) {
                if (current.length() > 0) {
                    list.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        
        if (current.length() > 0) {
            list.add(current.toString());
        }
        
        return list;
    }

    public static NpmService getInstance() {
        return Lookup.getDefault().lookup(NpmService.class);
    }
}