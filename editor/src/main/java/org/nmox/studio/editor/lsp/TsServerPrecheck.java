package org.nmox.studio.editor.lsp;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether the TypeScript the language server would use can SERVE (v2.85.0).
 * typescript-language-server needs {@code typescript/lib/tsserver.js};
 * TypeScript 7 — the Go port — ships none (the v1.237.0 ceiling, the F5
 * watch item), so a workspace or a global install on 7 makes that server
 * fail its initialize with "Could not find a valid TypeScript
 * installation" — logged as a WARNING stack trace on every file open,
 * the editor's TypeScript intelligence silently dead (the v2.85.0 Docker
 * walk's log). The precheck reads the SAME candidates the server does —
 * the workspace's {@code node_modules/typescript}, else the global
 * sibling beside the server binary — and says which kind of TypeScript
 * is there, so the launch site starts the server that kind has: since
 * 3.10.0 a TypeScript without a tsserver is served by its own native one
 * ({@link TsNative}). Pure: two package.json reads, one file existence
 * check, no spawn.
 */
final class TsServerPrecheck {

    enum Kind {
        /** A typescript with a tsserver: launch. */
        SERVEABLE,
        /** A typescript without lib/tsserver.js (7+): its own native server serves instead (3.10.0, {@link TsNative}). */
        NO_TSSERVER,
        /** No typescript found where the server looks: launch and let the server say so. */
        NOT_FOUND
    }

    /**
     * @param workspace whether the TypeScript judged is the workspace's own
     *        ({@code node_modules/typescript} under the project): what it
     *        carries is then the project's, and runs only once the project
     *        is trusted
     */
    record Verdict(Kind kind, String version, File typescriptDir, boolean workspace) {
    }

    private TsServerPrecheck() {
    }

    /**
     * @param projectDir the workspace (may be null)
     * @param serverBin the resolved server binary (a global install's bin/ path, or the project-local .bin one), may be null
     */
    static Verdict check(File projectDir, File serverBin) {
        File local = projectDir == null ? null : new File(projectDir, "node_modules/typescript");
        if (local != null && new File(local, "package.json").isFile()) {
            return verdict(local, true);
        }
        // the npm prefix layout: <prefix>/bin/typescript-language-server beside
        // <prefix>/lib/node_modules/typescript (nvm, Homebrew node, npm -g)
        if (serverBin != null) {
            File bin = serverBin.getAbsoluteFile().getParentFile();
            if (bin != null) {
                File global = new File(bin.getParentFile(), "lib/node_modules/typescript");
                if (new File(global, "package.json").isFile()) {
                    return verdict(global, false);
                }
                // a project-local server: its own node_modules/typescript sibling
                File sibling = new File(bin.getParentFile(), "typescript");
                if (new File(sibling, "package.json").isFile()) {
                    return verdict(sibling, false);
                }
                // npm's WINDOWS prefix: <prefix>/typescript-language-server.cmd
                // beside <prefix>/node_modules/typescript (no lib/ level)
                File windows = new File(bin, "node_modules/typescript");
                if (new File(windows, "package.json").isFile()) {
                    return verdict(windows, false);
                }
            }
        }
        return new Verdict(Kind.NOT_FOUND, null, null, false);
    }

    private static Verdict verdict(File typescriptDir, boolean workspace) {
        String version = version(new File(typescriptDir, "package.json"));
        boolean tsserver = new File(typescriptDir, "lib/tsserver.js").isFile();
        return new Verdict(tsserver ? Kind.SERVEABLE : Kind.NO_TSSERVER, version, typescriptDir, workspace);
    }

    /** How much of a package.json the version read looks at: the field sits at the top of every real one. */
    static final int VERSION_READ_CAP = 64 * 1024;

    /**
     * Reads the version from a capped PREFIX (v2.85.0 review): this file
     * lives under a cloned project's node_modules, so it is other people's
     * bytes, and the bounded-read law holds for a file the way it does for
     * a socket — a multi-gigabyte package.json must cost 64 KB on file
     * open, not the heap.
     */
    static String version(File packageJson) {
        try (java.io.InputStream in = Files.newInputStream(packageJson.toPath())) {
            String text = new String(in.readNBytes(VERSION_READ_CAP), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
            return m.find() ? m.group(1) : "?";
        } catch (java.io.IOException e) {
            return "?";
        }
    }
}
