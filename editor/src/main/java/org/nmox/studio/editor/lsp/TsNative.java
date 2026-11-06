package org.nmox.studio.editor.lsp;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * TypeScript 7's own language server (3.10.0).
 *
 * <p>TypeScript 7 is the Go port. It ships no {@code tsserver.js}, so
 * typescript-language-server cannot use it, and from v2.85.0 this product
 * answered a TypeScript 7 with a notice that offered to install
 * TypeScript 5. By November 2026 TypeScript 7 is what {@code npm install
 * typescript} gives, and it carries a language server of its own: the
 * compiler binary run as {@code tsc --lsp --stdio} (measured on 7.0.2: it
 * names itself {@code typescript-go} and answers hover, definition,
 * completion, references, rename and formatting).
 *
 * <p>The binary is not the {@code tsc} script. The package
 * {@code typescript} holds a launcher, and the compiler is in a package
 * beside it named for the machine: {@code @typescript/typescript-darwin-arm64},
 * {@code -linux-x64}, {@code -win32-x64} and so on, at {@code lib/tsc}
 * ({@code lib/tsc.exe} on Windows). The launcher needs Node only to find
 * that file and start it; this class finds the file itself, so the server
 * is one process and starts on a machine whose Node is not on the path
 * the IDE was started with.
 *
 * <p>Two things about the server shape the launch, both measured:
 * <ul>
 * <li>It reports a file's problems only when asked
 * ({@code textDocument/diagnostic}); it never sends
 * {@code publishDiagnostics} for a source file. The platform's client
 * cannot ask. See {@link PullDiagnostics}.</li>
 * <li>It loads no plugin a {@code tsconfig.json} names, where the
 * tsserver of TypeScript 5 does ({@code scripts/probes/tsc-lsp-trust}).
 * So the user's own compiler only reads a project, and the one thing of
 * the project's that can run is the compiler binary itself, when it comes
 * from the project's {@code node_modules}. See {@link #choose}.</li>
 * </ul>
 */
final class TsNative {

    /** The name the server is known by: in the trust table, in the log, in Action Items. */
    static final String BINARY = "tsc";

    private TsNative() {
    }

    /** What to do about a TypeScript that has no tsserver. */
    enum Choice {
        /** Start the compiler of the TypeScript that was found: the user's own, or a trusted workspace's. */
        FOUND,
        /** Start the user's own compiler instead: the workspace's may not run yet, or has none for this machine. */
        OWN,
        /** The workspace's compiler is the only one, and the workspace is not trusted: say so and wait. */
        WAIT_FOR_TRUST,
        /** No compiler binary for this machine anywhere: the old notice, which offers TypeScript 5. */
        NONE
    }

    /**
     * The decision, with nothing read and nothing started.
     *
     * <p>A compiler from the workspace's {@code node_modules} is the
     * workspace's code: a repository can bring any file under that name.
     * It runs only in a trusted workspace. Before that the user's own
     * compiler serves the project when there is one, the way the user's
     * own TypeScript serves a clone before {@code npm install}.
     *
     * @param found whether the TypeScript without a tsserver has a compiler binary for this machine
     * @param fromWorkspace whether that TypeScript is the workspace's own
     * @param trusted whether the workspace is trusted
     * @param own whether the user has a TypeScript 7 compiler of their own
     */
    static Choice choose(boolean found, boolean fromWorkspace, boolean trusted, boolean own) {
        if (found && (!fromWorkspace || trusted)) {
            return Choice.FOUND;
        }
        if (own) {
            return Choice.OWN;
        }
        return found ? Choice.WAIT_FOR_TRUST : Choice.NONE;
    }

    /** The command that starts the server. */
    static List<String> command(File exe) {
        return List.of(exe.getAbsolutePath(), "--lsp", "--stdio");
    }

    /**
     * The package that holds the compiler for this machine, as npm names
     * it: Node's {@code process.platform} and {@code process.arch}.
     */
    static String platformPackage(String osName, String osArch) {
        String os = osName.toLowerCase(Locale.ROOT);
        String platform = os.contains("win") && !os.contains("darwin") ? "win32"
                : os.contains("mac") || os.contains("darwin") ? "darwin"
                : os.contains("freebsd") ? "freebsd"
                : "linux";
        String arch = switch (osArch.toLowerCase(Locale.ROOT)) {
            case "aarch64", "arm64" -> "arm64";
            case "amd64", "x86_64", "x64" -> "x64";
            case "arm", "arm32" -> "arm";
            default -> osArch.toLowerCase(Locale.ROOT);
        };
        return "typescript-" + platform + "-" + arch;
    }

    static String platformPackage() {
        return platformPackage(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    /**
     * The compiler binary that belongs to a {@code typescript} package
     * directory, or null.
     *
     * <p>Looked for where npm, pnpm and yarn put an optional dependency:
     * beside the package ({@code node_modules/@typescript/…}, which is
     * also where pnpm's store puts it, seen from the package's real
     * path), and nested inside it. The directory as given is tried
     * before its real path, so a compiler in an ordinary
     * {@code node_modules} is named by a path inside the workspace.
     */
    static File exe(File typescriptDir, String platformPackage, boolean windows) {
        if (typescriptDir == null) {
            return null;
        }
        String relative = "@typescript/" + platformPackage + "/lib/" + (windows ? "tsc.exe" : "tsc");
        File real = null;
        try {
            real = typescriptDir.getCanonicalFile();
        } catch (IOException ex) {
            // the directory as given is still worth a look
        }
        for (File dir : new File[] {typescriptDir, real}) {
            if (dir == null) {
                continue;
            }
            File parent = dir.getAbsoluteFile().getParentFile();
            for (File candidate : new File[] {
                parent == null ? null : new File(parent, relative),
                new File(dir, "node_modules/" + relative)}) {
                // a directory is executable too: the compiler is a file
                if (candidate != null && candidate.isFile() && candidate.canExecute()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    static File exe(File typescriptDir) {
        return exe(typescriptDir, platformPackage(), org.openide.util.BaseUtilities.isWindows());
    }

    /**
     * The user's own TypeScript 7 compiler: the one {@code tsc} on the
     * PATH belongs to, when that TypeScript has no tsserver. Null when
     * there is no {@code tsc}, when it is TypeScript 5 or 6, or when it
     * resolves into {@code insideOf} (a project's {@code node_modules/.bin}
     * put on the PATH is the project's, not the user's).
     *
     * @param tscOnPath the resolved {@code tsc} launcher, or null
     * @param insideOf the workspace, or null
     */
    static File ownExe(File tscOnPath, File insideOf) {
        if (tscOnPath == null || !tscOnPath.isAbsolute()) {
            return null;
        }
        File typescript = packageOf(tscOnPath);
        if (typescript == null || new File(typescript, "lib/tsserver.js").isFile()) {
            return null;
        }
        File exe = exe(typescript);
        if (exe == null || inside(exe, insideOf) || inside(tscOnPath, insideOf)) {
            return null;
        }
        return exe;
    }

    /**
     * The {@code typescript} package a {@code tsc} launcher belongs to.
     * npm links {@code <prefix>/bin/tsc} to the package's own
     * {@code bin/tsc}, so the link's target names the package; where the
     * launcher is a script of its own (npm on Windows), the package is
     * where the precheck looks for the server's TypeScript.
     */
    static File packageOf(File tscOnPath) {
        try {
            File real = tscOnPath.getCanonicalFile();
            File bin = real.getParentFile();
            File pkg = bin == null ? null : bin.getParentFile();
            if (pkg != null && "bin".equals(bin.getName()) && "typescript".equals(pkg.getName())
                    && new File(pkg, "package.json").isFile()) {
                return pkg;
            }
        } catch (IOException ex) {
            // the layouts below are still worth a look
        }
        return TsServerPrecheck.check(null, tscOnPath).typescriptDir();
    }

    /** Whether a file is under a directory, by the path as written or by the real one. */
    static boolean inside(File file, File dir) {
        if (file == null || dir == null) {
            return false;
        }
        if (under(file.getAbsolutePath(), dir.getAbsolutePath())) {
            return true;
        }
        try {
            return under(file.getCanonicalPath(), dir.getCanonicalPath());
        } catch (IOException ex) {
            return true; // a path that cannot be resolved is not shown to be outside
        }
    }

    private static boolean under(String path, String dir) {
        return path.equals(dir) || path.startsWith(dir.endsWith(File.separator) ? dir : dir + File.separator);
    }
}
