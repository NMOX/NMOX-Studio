package org.nmox.studio.rack.devices;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The ship-readiness checklist, planned from what the project actually
 * is. The conviction behind it: "done" should be a machine-checkable
 * state, not a feeling - every gap between looks-done and is-done is a
 * bug with a delay on it.
 */
public final class PreflightPlan {

    /** How a check decides it passed. */
    public enum Pass {
        EXIT_ZERO,
        /** Passes only when the command prints nothing (git status --porcelain). */
        EMPTY_OUTPUT
    }

    /**
     * One checklist row. Soft checks warn instead of blocking the
     * verdict - advisory, like a CHANGELOG nudge.
     */
    public record Check(String name, List<String> command, Pass pass, boolean soft) {
    }

    private PreflightPlan() {
    }

    /** The checklist for this project, in run order. */
    public static List<Check> forProject(File dir) {
        List<Check> checks = new ArrayList<>();

        if (new File(dir, ".git").isDirectory()) {
            checks.add(new Check("GIT CLEAN",
                    List.of("git", "status", "--porcelain"), Pass.EMPTY_OUTPUT, false));
        }

        ProjectInspector.ProjectKind kind = ProjectInspector.detectKind(dir);
        switch (kind) {
            case NODE -> {
                if (ProjectInspector.hasScript(dir, "test")) {
                    checks.add(new Check("TESTS", List.of("npm", "test"), Pass.EXIT_ZERO, false));
                }
                if (ProjectInspector.hasScript(dir, "build")) {
                    checks.add(new Check("BUILD", List.of("npm", "run", "build"), Pass.EXIT_ZERO, false));
                }
                if (hasLintConfig(dir)) {
                    checks.add(new Check("LINT", List.of("npx", "eslint", "."), Pass.EXIT_ZERO, false));
                }
                checks.add(new Check("AUDIT",
                        List.of("npm", "audit", "--omit=dev", "--audit-level=high"),
                        Pass.EXIT_ZERO, true));
            }
            case RUST -> {
                checks.add(new Check("TESTS", List.of("cargo", "test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("cargo", "build", "--release"), Pass.EXIT_ZERO, false));
                checks.add(new Check("LINT", List.of("cargo", "clippy", "--", "-D", "warnings"), Pass.EXIT_ZERO, true));
            }
            case GO -> {
                checks.add(new Check("TESTS", List.of("go", "test", "./..."), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("go", "build", "./..."), Pass.EXIT_ZERO, false));
                checks.add(new Check("VET", List.of("go", "vet", "./..."), Pass.EXIT_ZERO, true));
            }
            case PYTHON -> {
                checks.add(new Check("TESTS", List.of("python3", "-m", "pytest"), Pass.EXIT_ZERO, false));
            }
            case MAVEN -> {
                checks.add(new Check("TESTS", List.of("mvn", "-q", "test"), Pass.EXIT_ZERO, false));
                checks.add(new Check("BUILD", List.of("mvn", "-q", "package", "-DskipTests"), Pass.EXIT_ZERO, false));
            }
            default -> {
                // no toolchain: git-clean (if present) is the whole list
            }
        }

        return checks;
    }

    private static boolean hasLintConfig(File dir) {
        for (String f : new String[]{"eslint.config.js", "eslint.config.mjs", ".eslintrc",
            ".eslintrc.json", ".eslintrc.js", ".eslintrc.cjs"}) {
            if (new File(dir, f).isFile()) {
                return true;
            }
        }
        return false;
    }

    /** The verdict for one finished check. */
    public static boolean passed(Check check, int exit, String output) {
        return switch (check.pass()) {
            case EXIT_ZERO -> exit == 0;
            case EMPTY_OUTPUT -> exit == 0 && output.isBlank();
        };
    }
}
