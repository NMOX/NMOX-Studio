package org.nmox.studio.core.process;

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

    /** Windows PATHEXT, the two entries a developer toolchain actually ships. */
    private static final List<String> WINDOWS_SUFFIXES = List.of(".exe", ".cmd");

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
                File found = foundIn(new File(dir), name);
                if (found != null) {
                    return found.getAbsolutePath();
                }
            }
            return name;
        });
    }

    /**
     * {@code name} as it exists inside ONE search directory, or null.
     *
     * <p>The suffixes are the whole point and the reason this is a named
     * method rather than three lines inlined per caller. On Windows
     * nothing is executable under the bare name: native tools ship
     * {@code .exe} and npm ships {@code .cmd} shims, and the v1.42.0
     * Windows lane found that without the {@code .cmd} arm NO language
     * server was ever detected there. That fact had three homes — this
     * one, {@code LanguageServerCatalog.foundIn} and the rack's
     * {@code CommandDevice.toolOnPath} — and the rack's copy was missing
     * {@code .cmd}, so a console probing for an npm-shipped tool would
     * have greyed out on Windows while the editor found it. Nothing in
     * the fleet probes an npm shim today, so it was a latent trap rather
     * than a live bug; one home means it cannot become one.
     *
     * <p>Deliberately NOT cached: {@link #resolve} caches, and two
     * callers must see a tool the user just installed — the LSP health
     * panel re-checks right after its install flow, and a rack console
     * re-probes when its GO button is pressed.
     */
    public static File foundIn(File dir, String name) {
        File bare = new File(dir, name);
        if (bare.isFile() && bare.canExecute()) {
            return bare;
        }
        for (String suffix : WINDOWS_SUFFIXES) {
            File shim = new File(dir, name + suffix);
            if (shim.isFile()) {
                return shim;
            }
        }
        return null;
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
        // web-capable toolchains beyond the usual suspects
        dirs.add(home + "/.dotnet");
        dirs.add("/usr/local/share/dotnet");
        dirs.add(home + "/.pub-cache/bin");
        dirs.add("/opt/homebrew/opt/dart/libexec/bin");
        dirs.add(home + "/.ghcup/bin");
        dirs.add(home + "/.opam/default/bin");
        // language-server install homes
        dirs.add(home + "/.dotnet/tools");                           // csharp-ls, fsautocomplete
        dirs.add(home + "/Library/Application Support/Coursier/bin"); // metals (macOS)
        dirs.add(home + "/.local/share/coursier/bin");               // metals (Linux)
        dirs.add(home + "/.cabal/bin");                              // haskell-language-server
        dirs.add(home + "/.mix/escripts");                           // elixir escripts
        dirs.add(home + "/.juliaup/bin");                            // julia
        // polyglot toolchains
        dirs.add(home + "/.cargo/bin");                              // rust
        dirs.add("/usr/local/go/bin");                               // go
        dirs.add(home + "/go/bin");                                  // go installs
        dirs.add(home + "/.rbenv/shims");                            // ruby
        dirs.add(home + "/.pyenv/shims");                            // python
        dirs.add(home + "/.composer/vendor/bin");                    // php
        dirs.add(home + "/.config/composer/vendor/bin");
        dirs.add(home + "/.sdkman/candidates/java/current/bin");     // java
        dirs.add(home + "/.sdkman/candidates/maven/current/bin");
        dirs.add(home + "/.sdkman/candidates/gradle/current/bin");

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
