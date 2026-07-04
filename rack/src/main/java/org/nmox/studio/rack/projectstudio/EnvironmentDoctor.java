package org.nmox.studio.rack.projectstudio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.nmox.studio.core.process.ProcessSupport;

/**
 * The environment doctor: one honest answer to "what does this machine
 * actually have?" The IDE leans on dozens of external tools — the core
 * four every project touches, a toolchain per language lane, and an
 * interpreter per learning space — and each device already speaks up
 * when its own tool is missing. This sweeps them all at once: each
 * tool is probed with {@code --version} through the same hardened
 * launcher the devices use, so the verdict here is the verdict there.
 */
public final class EnvironmentDoctor {

    /** One probed tool: found (with its version line) or missing (with the fix). */
    public record Finding(String tool, String purpose, boolean found,
            String detail, String installHint) {
    }

    private EnvironmentDoctor() {
    }

    /** The full checkup list: core tools, toolchains, then REPL interpreters. */
    public static List<String[]> checklist() {
        // {binary, purpose, install hint} — hints favor Homebrew on the
        // assumption the doctor's OS hint column stays honest per-entry
        List<String[]> checks = new ArrayList<>(List.of(
                new String[]{"git", "version control — TIMELINE, project init", "brew install git"},
                new String[]{"node", "JavaScript runtime — most web tooling", "brew install node"},
                new String[]{"npm", "package manager — CRATE, NPM-9000", "ships with node"},
                new String[]{"docker", "containers — HARBOR, Docker panel", "Docker Desktop"},
                new String[]{"java", "JVM — Maven lanes, jshell space", "brew install openjdk"},
                new String[]{"mvn", "Maven builds", "brew install maven"},
                new String[]{"cargo", "Rust toolchain", "brew install rustup && rustup default stable"},
                new String[]{"go", "Go toolchain", "brew install go"},
                new String[]{"python3", "Python — tooling and spaces", "brew install python"},
                new String[]{"ruby", "Ruby toolchain", "brew install ruby"},
                new String[]{"php", "PHP toolchain", "brew install php"},
                new String[]{"composer", "PHP package manager", "brew install composer"},
                new String[]{"mysql", "MySQL/MariaDB client", "brew install mysql-client"},
                new String[]{"nginx", "web server", "brew install nginx"},
                new String[]{"apachectl", "Apache HTTP server", "preinstalled on macOS / apt install apache2"},
                new String[]{"bun", "Bun runtime", "brew install oven-sh/bun/bun"},
                new String[]{"deno", "Deno runtime", "brew install deno"},
                new String[]{"mix", "Elixir/BEAM toolchain", "brew install elixir"}));
        // every distinct REPL interpreter the learning catalog can launch
        Map<String, String[]> repls = new LinkedHashMap<>();
        for (LearningCatalog.Space space : LearningCatalog.all()) {
            if (space.driver().kind() != LearningCatalog.DriverKind.REPL
                    || space.driver().command().isEmpty()) {
                continue;
            }
            String binary = space.driver().command().get(0);
            String hint = space.install().getOrDefault(LearningSpace.osKey(), "");
            repls.putIfAbsent(binary, new String[]{binary,
                "learning space: " + space.name(), hint});
        }
        for (String[] check : checks) {
            repls.remove(check[0]); // already covered above
        }
        checks.addAll(repls.values());
        return checks;
    }

    /**
     * Probes one tool by running {@code tool --version} through the
     * hardened launcher with a short leash. Missing binary → not found;
     * a tool that launches but dislikes --version still counts as found.
     */
    public static Finding probe(String tool, String purpose, String installHint) {
        try {
            // most tools speak --version; the holdouts get their own dialect
            List<String> versionCmd = switch (tool) {
                case "go" -> List.of("go", "version"); // rejects --version
                // nginx has no --version and prints `nginx -v` to STDERR —
                // redirectErrorStream below folds it into the read stream
                case "nginx" -> List.of("nginx", "-v");
                case "apachectl" -> List.of("apachectl", "-v"); // --version unsupported
                default -> List.of(tool, "--version");
            };
            Process p = ProcessSupport.builder(versionCmd)
                    .redirectErrorStream(true)
                    .start();
            String firstLine = "";
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line = r.readLine();
                if (line != null) {
                    firstLine = line.strip();
                }
                r.transferTo(java.io.Writer.nullWriter()); // drain so the tool can exit
            }
            if (!p.waitFor(4, TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
            String detail = firstLine.isBlank() ? "found (version unknown)"
                    : (firstLine.length() > 72 ? firstLine.substring(0, 72) + "…" : firstLine);
            return new Finding(tool, purpose, true, detail, installHint);
        } catch (IOException notFound) {
            return new Finding(tool, purpose, false, "not found", installHint);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Finding(tool, purpose, false, "interrupted", installHint);
        }
    }
}
