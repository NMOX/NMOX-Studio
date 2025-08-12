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

    public CompletableFuture<String> addPackage(File projectDir, String packageName, boolean isDev, PackageManager manager) {
        List<String> args = new ArrayList<>();
        args.add(getCommand(manager));
        
        switch (manager) {
            case NPM:
                args.add("install");
                if (isDev) args.add("--save-dev");
                break;
            case YARN:
                args.add("add");
                if (isDev) args.add("--dev");
                break;
            case PNPM:
                args.add("add");
                if (isDev) args.add("--save-dev");
                break;
        }
        args.add(packageName);
        
        return runCommand(projectDir, args.toArray(new String[0]));
    }

    public CompletableFuture<String> removePackage(File projectDir, String packageName, PackageManager manager) {
        String command = getCommand(manager);
        String removeCmd = manager == PackageManager.YARN ? "remove" : "uninstall";
        return runCommand(projectDir, command, removeCmd, packageName);
    }

    public CompletableFuture<String> runScript(File projectDir, String scriptName, PackageManager manager) {
        return runCommand(projectDir, getCommand(manager), "run", scriptName);
    }

    public CompletableFuture<String> listPackages(File projectDir, PackageManager manager) {
        String command = getCommand(manager);
        if (manager == PackageManager.NPM) {
            return runCommand(projectDir, command, "list", "--depth=0");
        } else {
            return runCommand(projectDir, command, "list");
        }
    }

    public CompletableFuture<String> init(File projectDir, PackageManager manager) {
        return runCommand(projectDir, getCommand(manager), "init", "-y");
    }

    public CompletableFuture<String> update(File projectDir, PackageManager manager) {
        String command = getCommand(manager);
        String updateCmd = manager == PackageManager.YARN ? "upgrade" : "update";
        return runCommand(projectDir, command, updateCmd);
    }

    public CompletableFuture<String> audit(File projectDir, PackageManager manager) {
        return runCommand(projectDir, getCommand(manager), "audit");
    }

    public boolean isAvailable(PackageManager manager) {
        try {
            Process process = new ProcessBuilder(getCommand(manager), "--version")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
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
                
                ProcessBuilder pb = new ProcessBuilder(command);
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
                
                int exitCode = process.waitFor();
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
        String[] parts = command.split("\\s+");
        List<String> cmdList = new ArrayList<>();
        cmdList.add(getCommand(detectPackageManager(projectDir)));
        for (String part : parts) {
            cmdList.add(part);
        }
        runCommand(projectDir, cmdList.toArray(new String[0]));
    }

    public static NpmService getInstance() {
        return Lookup.getDefault().lookup(NpmService.class);
    }
}