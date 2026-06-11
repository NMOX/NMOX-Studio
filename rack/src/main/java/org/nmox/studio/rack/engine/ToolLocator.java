package org.nmox.studio.rack.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds developer tools when the IDE was launched from Finder or the
 * Dock, where macOS hands GUI apps a bare PATH (/usr/bin:/bin:...)
 * that contains neither node, npm nor git. Terminal launches never hit
 * this, which is exactly why it ships broken so often.
 *
 * Two jobs:
 *  - resolve a command name to an absolute path by scanning the real
 *    PATH plus every common toolchain install location (Homebrew on
 *    both architectures, nvm, volta, fnm, asdf, MacPorts)
 *  - provide the augmented PATH so child processes (npm scripts that
 *    spawn node, npx that spawns installed tools) resolve too
 */
public final class ToolLocator {

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static volatile String augmentedPath;

    private ToolLocator() {
    }

    /**
     * Resolves a command to an absolute path if it can be found in the
     * augmented search path; otherwise returns the name unchanged and
     * lets the OS try (and report) it.
     */
    public static String resolve(String command) {
        if (command.contains(File.separator)) {
            return command; // already a path
        }
        return CACHE.computeIfAbsent(command, name -> {
            for (String dir : searchDirs()) {
                File candidate = new File(dir, name);
                if (candidate.isFile() && candidate.canExecute()) {
                    return candidate.getAbsolutePath();
                }
                // Windows: PATHEXT resolution, the common two suffice here
                File exe = new File(dir, name + ".exe");
                if (exe.isFile()) {
                    return exe.getAbsolutePath();
                }
                File cmd = new File(dir, name + ".cmd");
                if (cmd.isFile()) {
                    return cmd.getAbsolutePath();
                }
            }
            return name;
        });
    }

    /** A command list with its executable resolved. */
    public static List<String> resolveCommand(List<String> command) {
        if (command.isEmpty()) {
            return command;
        }
        List<String> resolved = new ArrayList<>(command);
        resolved.set(0, resolve(command.get(0)));
        return resolved;
    }

    /** PATH including every toolchain dir that exists on this machine. */
    public static String augmentedPath() {
        String cached = augmentedPath;
        if (cached == null) {
            cached = String.join(File.pathSeparator, searchDirs());
            augmentedPath = cached;
        }
        return cached;
    }

    private static List<String> searchDirs() {
        Set<String> dirs = new LinkedHashSet<>();
        String envPath = System.getenv("PATH");
        if (envPath != null) {
            dirs.addAll(Arrays.asList(envPath.split(File.pathSeparator)));
        }
        String home = System.getProperty("user.home");
        // Homebrew (Apple Silicon, Intel), MacPorts, common Linux
        dirs.add("/opt/homebrew/bin");
        dirs.add("/usr/local/bin");
        dirs.add("/opt/local/bin");
        dirs.add("/usr/bin");
        dirs.add("/bin");
        // version managers: newest node first where versioned
        addNewestVersionDir(dirs, new File(home, ".nvm/versions/node"), "bin");
        dirs.add(home + "/.volta/bin");
        addNewestVersionDir(dirs, new File(home, ".fnm/node-versions"), "installation/bin");
        addNewestVersionDir(dirs, new File(home, ".asdf/installs/nodejs"), "bin");
        dirs.add(home + "/.asdf/shims");
        dirs.add(home + "/.local/bin");

        List<String> existing = new ArrayList<>();
        for (String d : dirs) {
            if (d != null && !d.isBlank() && new File(d).isDirectory()) {
                existing.add(d);
            }
        }
        return existing;
    }

    /** Adds versionsRoot/&lt;newest&gt;/suffix if such a directory exists. */
    private static void addNewestVersionDir(Set<String> dirs, File versionsRoot, String suffix) {
        File[] versions = versionsRoot.listFiles(File::isDirectory);
        if (versions == null || versions.length == 0) {
            return;
        }
        Arrays.sort(versions, Comparator.comparing(File::getName).reversed());
        for (File version : versions) {
            File bin = new File(version, suffix);
            if (bin.isDirectory()) {
                dirs.add(bin.getAbsolutePath());
                return;
            }
        }
    }

    /** Test hook: forget cached lookups (the filesystem changed). */
    static void reset() {
        CACHE.clear();
        augmentedPath = null;
    }
}
