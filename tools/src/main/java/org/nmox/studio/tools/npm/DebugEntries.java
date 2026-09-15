package org.nmox.studio.tools.npm;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.json.JSONObject;
import org.nmox.studio.rack.devices.ProjectInspector;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;

/**
 * What "Debug Main Project" debugs (v2.158.0): the project's entry, read
 * from the toolchain's own contract and never guessed past it. A Node
 * project's entry is the file its {@code start} script runs under
 * {@code node} (flags tolerated; a pipeline, a dev server or a wrapper such
 * as nodemon names no single program), else its {@code main}, else Node's
 * own default {@code index.js}. A Go project's entry is the package
 * directory's {@code main.go}, else its first root source — delve debugs
 * the directory, so any root source names it. Every other kind names
 * nothing: its Run names no program either, and a disabled row is honest
 * where a guess would run the wrong thing. An entry must exist and lie
 * inside the project; the launcher then decides by MIME whether it takes it.
 */
final class DebugEntries {

    /** package.json is project-controlled: read at most this much of it. */
    static final long MAX_PACKAGE_JSON_BYTES = 1L << 20;

    /** Shell operators that make a start script more than one program. */
    private static final Set<String> OPERATORS = Set.of("&&", "||", ";", "|", "&");

    /** node flags that consume the token after them. */
    private static final Set<String> FLAGS_WITH_ARGUMENT = Set.of(
            "-r", "--require", "--import", "--loader", "--experimental-loader", "--env-file",
            "--inspect-port", "--stack-size", "--max-old-space-size", "--title");

    private DebugEntries() {
    }

    /** The project's main entry for {@code kind}, or null when the contract names none. */
    static File mainEntry(File projectDir, ProjectKind kind) {
        if (projectDir == null || kind == null) {
            return null;
        }
        return switch (kind) {
            case NODE -> nodeEntry(projectDir);
            case GO -> goEntry(projectDir);
            default -> null;
        };
    }

    private static File nodeEntry(File projectDir) {
        File dir = ProjectInspector.kindDir(projectDir, ProjectKind.NODE);
        String start = ProjectInspector.scripts(projectDir).get("start");
        if (isNodeScript(start)) {
            String target = nodeTarget(start);
            if (target == null) {
                return null;   // node, but not one program: the contract is not a file
            }
            if (!".".equals(target) && !"./".equals(target)) {
                return inside(dir, target);
            }
            // `node .` runs main or index.js — fall through to that rule
        }
        String main = readMain(dir);
        if (main != null && !main.isBlank()) {
            return inside(dir, main);
        }
        return inside(dir, "index.js");
    }

    /** True when the script's first word is {@code node}. */
    static boolean isNodeScript(String start) {
        if (start == null) {
            return false;
        }
        String[] tokens = start.strip().split("\\s+");
        return tokens.length > 0 && "node".equals(tokens[0]);
    }

    /**
     * The file a {@code node …} script runs, or null when the script is more
     * than one program or names none. Flags before the file are skipped;
     * the first non-flag token is the target.
     */
    static String nodeTarget(String start) {
        List<String> tokens = Arrays.asList(start.strip().split("\\s+"));
        if (tokens.stream().anyMatch(OPERATORS::contains)) {
            return null;
        }
        for (int i = 1; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.startsWith("-")) {
                if (FLAGS_WITH_ARGUMENT.contains(t)) {
                    i++;
                }
                continue;
            }
            return t;
        }
        return null;
    }

    /** package.json's {@code main}, read bounded; null when absent or unreadable. */
    private static String readMain(File dir) {
        File pkg = new File(dir, "package.json");
        try {
            if (!pkg.isFile() || Files.size(pkg.toPath()) > MAX_PACKAGE_JSON_BYTES) {
                return null;
            }
            JSONObject json = new JSONObject(Files.readString(pkg.toPath(), StandardCharsets.UTF_8));
            return json.optString("main", null);
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }

    private static File goEntry(File projectDir) {
        File dir = ProjectInspector.kindDir(projectDir, ProjectKind.GO);
        File main = new File(dir, "main.go");
        if (main.isFile()) {
            return main;
        }
        String[] names = dir.list();
        if (names == null) {
            return null;
        }
        Arrays.sort(names);
        for (String name : names) {
            if (name.endsWith(".go") && !name.endsWith("_test.go") && new File(dir, name).isFile()) {
                return new File(dir, name);
            }
        }
        return null;
    }

    /**
     * {@code rel} resolved under {@code dir} as a plain file that really
     * lies inside it (canonical containment — a {@code ../} in a script
     * can point anywhere), or null.
     */
    static File inside(File dir, String rel) {
        Path normalized = Path.of(rel).normalize();
        File file = normalized.isAbsolute() ? normalized.toFile() : new File(dir, normalized.toString());
        try {
            Path root = dir.getCanonicalFile().toPath();
            Path real = file.getCanonicalFile().toPath();
            return real.startsWith(root) && file.isFile() ? file : null;
        } catch (IOException | RuntimeException unresolvable) {
            return null;
        }
    }
}
