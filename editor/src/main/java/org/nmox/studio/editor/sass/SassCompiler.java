package org.nmox.studio.editor.sass;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmox.studio.core.process.ProcessSupport;
import org.nmox.studio.core.process.ToolLocator;

/**
 * Compiles one SCSS/Sass entry stylesheet to its sibling .css with the
 * Dart Sass CLI (v1.230.0, the Senior Web Designer pass): the argv is
 * always {@code sass --no-source-map in.scss out.css}, the binary is
 * resolved the {@link org.nmox.studio.editor.format.PrettierFormatter}
 * way — the project's own {@code node_modules/.bin/sass} only when the
 * workspace is trusted (a committed .bin script is a cloned repo's
 * code), else the user's global install — and partials
 * ({@code _name.scss}) are refused honestly: they are imports, not
 * entry points, and compiling one would litter {@code _name.css}.
 *
 * <p>Everything that can go wrong degrades to a typed outcome the
 * action turns into a status line, never a dialog.
 */
public final class SassCompiler {

    private static final Logger LOG = Logger.getLogger(SassCompiler.class.getName());

    /** A hung compile is killed after this long (cold Dart VM + imports). */
    static final long TIMEOUT_MS = 20_000;
    /** First-line stderr shown on failure; the rest is noise for a status line. */
    static final int ERROR_SNIPPET_CHARS = 160;

    /** Runs the external process; a seam so tests never need a real sass. */
    interface Runner {
        Exec run(List<String> command, File workDir) throws IOException, InterruptedException;
    }

    record Exec(int exitCode, String stderr) {
    }

    public enum Outcome {
        COMPILED, PARTIAL, NO_SASS, FAILED
    }

    /** {@code output} is set for COMPILED; {@code error} for FAILED. */
    public record Result(Outcome outcome, File output, String error) {
    }

    private final Runner runner;

    public SassCompiler() {
        this(SassCompiler::exec);
    }

    SassCompiler(Runner runner) {
        this.runner = runner;
    }

    /** Compile {@code scss} to its sibling .css. */
    public Result compile(File scss) {
        if (isPartial(scss)) {
            return new Result(Outcome.PARTIAL, null, null);
        }
        File dir = scss.getParentFile();
        String binary = dir == null ? null : resolveBinary(dir);
        if (binary == null) {
            return new Result(Outcome.NO_SASS, null, null);
        }
        File out = outputFor(scss);
        try {
            Exec exec = runner.run(List.of(binary, "--no-source-map",
                    scss.getAbsolutePath(), out.getAbsolutePath()), dir);
            if (exec.exitCode() != 0) {
                return new Result(Outcome.FAILED, null, firstLine(exec.stderr()));
            }
            return new Result(Outcome.COMPILED, out, null);
        } catch (IOException ex) {
            LOG.log(Level.INFO, "sass failed to run: {0}", ex.getMessage());
            return new Result(Outcome.FAILED, null, ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result(Outcome.FAILED, null, "interrupted");
        }
    }

    /** The sibling .css for an entry stylesheet: extension swapped, dir kept. */
    static File outputFor(File scss) {
        String name = scss.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return new File(scss.getParentFile(), base + ".css");
    }

    /** {@code _name.scss} is an import, not an entry point. */
    static boolean isPartial(File scss) {
        return scss.getName().startsWith("_");
    }

    /**
     * Sass's own error text is multi-line; a status line wants the
     * head — but the head of the MESSAGE, not of the dump. A crashing
     * sass (the v2.20.0 walk hit a broken npm install whose stderr
     * opened with a stack-frame path) puts its human line further
     * down, so prefer the first line carrying "Error" and fall back
     * to the literal first line (the v1.303.0 Doctor law: never quote
     * wreckage as if it were the answer).
     */
    static String firstLine(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return "sass failed";
        }
        String line = stderr.strip().lines()
                .filter(l -> l.contains("Error"))
                .findFirst()
                .orElseGet(() -> stderr.strip().lines().findFirst()
                        .orElse("sass failed"))
                .strip();
        return line.length() > ERROR_SNIPPET_CHARS
                ? line.substring(0, ERROR_SNIPPET_CHARS) : line;
    }

    /**
     * Trust-gated binary resolution, the PrettierFormatter idiom: the
     * project-local {@code node_modules/.bin/sass} only when the
     * workspace is trusted (silent check — recompile-on-save must never
     * prompt), else the user's own global sass; null when neither exists.
     */
    static String resolveBinary(File startDir) {
        if (org.nmox.studio.rack.service.WorkspaceTrust.isTrusted(startDir)) {
            String local = findLocalBinary(startDir);
            if (local != null) {
                return local;
            }
        }
        String global = ToolLocator.resolve("sass");
        // ToolLocator returns the bare name unchanged when nothing was found
        return global.contains(File.separator) ? global : null;
    }

    static String findLocalBinary(File startDir) {
        for (File dir = startDir; dir != null; dir = dir.getParentFile()) {
            File bin = new File(dir, "node_modules/.bin/sass");
            if (bin.canExecute()) {
                return bin.getAbsolutePath();
            }
            File cmd = new File(dir, "node_modules/.bin/sass.cmd");
            if (cmd.isFile()) {
                return cmd.getAbsolutePath();
            }
            if (new File(dir, ".git").exists()) {
                return null;
            }
        }
        return null;
    }

    /** The real runner: stderr captured (sass reports there), hard timeout, tree kill. */
    static Exec exec(List<String> command, File workDir)
            throws IOException, InterruptedException {
        ProcessBuilder pb = ProcessSupport.builder(command);
        pb.directory(workDir);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = pb.start();
        AtomicReference<String> err = new AtomicReference<>("");
        Thread drain = new Thread(() -> {
            try (InputStream in = process.getErrorStream()) {
                // 64k of stderr is plenty for an error message; keep
                // draining to EOF so the child can't pipe-deadlock
                byte[] head = in.readNBytes(64 * 1024);
                in.transferTo(java.io.OutputStream.nullOutputStream());
                err.set(new String(head, StandardCharsets.UTF_8));
            } catch (IOException ex) {
                // process died; the exit code tells the story
            }
        }, "sass-stderr");
        drain.setDaemon(true);
        drain.start();
        if (!process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            ProcessSupport.killTreeAndWait(process, java.time.Duration.ofSeconds(2));
            LOG.log(Level.INFO, "sass timed out after {0} ms, killed", TIMEOUT_MS);
            return new Exec(-1, "sass timed out");
        }
        drain.join(1_000);
        return new Exec(process.exitValue(), err.get());
    }
}
