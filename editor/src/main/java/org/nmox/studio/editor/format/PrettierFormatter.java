package org.nmox.studio.editor.format;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmox.studio.rack.engine.ToolLocator;

/**
 * Pipes a document through Prettier the way the project itself would run
 * it: only when the project has opted in (a Prettier config file anywhere
 * up the directory tree, or a package.json that mentions prettier), and
 * preferring the project's own node_modules/.bin/prettier over a global
 * install so the output matches what the project pinned.
 *
 * Everything that can go wrong — no opt-in, no binary, a syntax error
 * mid-edit, a hung process — degrades to "no change" and a log line,
 * never a dialog. A formatter that interrupts typing is worse than none.
 */
public final class PrettierFormatter {

    private static final Logger LOG = Logger.getLogger(PrettierFormatter.class.getName());

    /** Documents beyond this size save unformatted rather than stall the save. */
    static final int MAX_CHARS = 512_000;
    /** A hung prettier is killed after this many milliseconds. */
    static final long TIMEOUT_MS = 5_000;

    /** Every filename Prettier recognizes as its own configuration. */
    static final List<String> CONFIG_FILES = List.of(
            ".prettierrc", ".prettierrc.json", ".prettierrc.yml", ".prettierrc.yaml",
            ".prettierrc.json5", ".prettierrc.js", ".prettierrc.cjs", ".prettierrc.mjs",
            ".prettierrc.toml", "prettier.config.js", "prettier.config.cjs",
            "prettier.config.mjs");

    /** Runs the external process; a seam so tests never need a real prettier. */
    interface Runner {
        Result run(List<String> command, File workDir, String stdin)
                throws IOException, InterruptedException;
    }

    record Result(int exitCode, String stdout) {
    }

    private final Runner runner;

    public PrettierFormatter() {
        this(PrettierFormatter::exec);
    }

    PrettierFormatter(Runner runner) {
        this.runner = runner;
    }

    /**
     * The formatted text, or null whenever the save should proceed
     * untouched: project not opted in, prettier absent, text already
     * formatted, or any failure.
     */
    public String format(String text, File file) {
        if (text.length() > MAX_CHARS) {
            LOG.log(Level.FINE, "Skipping format-on-save, file too large: {0}", file);
            return null;
        }
        File dir = file.getParentFile();
        if (dir == null || !projectOptedIn(dir)) {
            return null;
        }
        String binary = resolveBinary(dir);
        if (binary == null) {
            return null;
        }
        try {
            Result result = runner.run(
                    List.of(binary, "--stdin-filepath", file.getAbsolutePath()), dir, text);
            if (result.exitCode() != 0) {
                // usually a syntax error mid-edit: a normal condition, save as-is
                LOG.log(Level.FINE, "prettier exited {0} for {1}",
                        new Object[]{result.exitCode(), file});
                return null;
            }
            String formatted = result.stdout();
            return formatted.isEmpty() || formatted.equals(text) ? null : formatted;
        } catch (IOException ex) {
            LOG.log(Level.INFO, "prettier failed to run: {0}", ex.getMessage());
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * True when some directory from {@code startDir} upward carries a
     * Prettier config file or a package.json that mentions prettier (a
     * "prettier" options key or dependency). Walks past non-mentioning
     * package.json files so a monorepo's root config still counts.
     */
    static boolean projectOptedIn(File startDir) {
        for (File dir = startDir; dir != null; dir = dir.getParentFile()) {
            for (String name : CONFIG_FILES) {
                if (new File(dir, name).isFile()) {
                    return true;
                }
            }
            File packageJson = new File(dir, "package.json");
            if (packageJson.isFile() && mentionsPrettier(packageJson)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A cheap textual test — a {@code "prettier"} key anywhere in
     * package.json covers both the embedded-options form and a
     * (dev)dependency, without parsing JSON on every save.
     */
    private static boolean mentionsPrettier(File packageJson) {
        try {
            return Files.readString(packageJson.toPath(), StandardCharsets.UTF_8)
                    .contains("\"prettier\"");
        } catch (IOException ex) {
            return false;
        }
    }

    /**
     * The nearest node_modules/.bin/prettier walking up from
     * {@code startDir}, falling back to a global install on the
     * augmented PATH; null when neither exists.
     */
    static String resolveBinary(File startDir) {
        String local = findLocalBinary(startDir);
        if (local != null) {
            return local;
        }
        String global = ToolLocator.resolve("prettier");
        // ToolLocator returns the bare name unchanged when nothing was found
        return global.contains(File.separator) ? global : null;
    }

    /** The project-pinned prettier, or null when no ancestor has one. */
    static String findLocalBinary(File startDir) {
        for (File dir = startDir; dir != null; dir = dir.getParentFile()) {
            File bin = new File(dir, "node_modules/.bin/prettier");
            if (bin.canExecute()) {
                return bin.getAbsolutePath();
            }
            File cmd = new File(dir, "node_modules/.bin/prettier.cmd");
            if (cmd.isFile()) {
                return cmd.getAbsolutePath();
            }
        }
        return null;
    }

    /** The real runner: stdout drained on its own thread, stdin fed, hard timeout. */
    private static Result exec(List<String> command, File workDir, String stdin)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir);
        pb.environment().put("PATH", ToolLocator.augmentedPath());
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = pb.start();
        AtomicReference<byte[]> output = new AtomicReference<>(new byte[0]);
        Thread drain = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                output.set(in.readAllBytes());
            } catch (IOException ex) {
                // process died; the exit code tells the story
            }
        }, "prettier-stdout");
        drain.start();
        try (OutputStream out = process.getOutputStream()) {
            out.write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        if (!process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            LOG.log(Level.INFO, "prettier timed out after {0} ms, killed", TIMEOUT_MS);
            return new Result(-1, "");
        }
        drain.join(1_000);
        return new Result(process.exitValue(), new String(output.get(), StandardCharsets.UTF_8));
    }
}
