package org.nmox.studio.editor.lsp;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether the TypeScript the language server would use can SERVE (v2.85.0).
 * typescript-language-server needs {@code typescript/lib/tsserver.js};
 * TypeScript 7 — the Go port, {@code tsgo} — ships none (the v1.237.0
 * ceiling, the F5 watch item), so a workspace or a global install on 7
 * makes the server fail its initialize with "Could not find a valid
 * TypeScript installation" — logged as a WARNING stack trace on every
 * file open, the editor's TypeScript intelligence silently dead (the
 * v2.85.0 Docker walk's log). The precheck reads the SAME candidates the
 * server does — the workspace's {@code node_modules/typescript}, else the
 * global sibling beside the server binary — and names the wall so the
 * launch site can put a door on it instead of a stack trace. Pure: two
 * package.json reads, one file existence check, no spawn.
 */
final class TsServerPrecheck {

    enum Kind {
        /** A typescript with a tsserver: launch. */
        SERVEABLE,
        /** A typescript without lib/tsserver.js (7+): refuse with the door. */
        NO_TSSERVER,
        /** No typescript found where the server looks: launch and let the server say so. */
        NOT_FOUND
    }

    record Verdict(Kind kind, String version, File typescriptDir) {
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
            return verdict(local);
        }
        // the npm prefix layout: <prefix>/bin/typescript-language-server beside
        // <prefix>/lib/node_modules/typescript (nvm, Homebrew node, npm -g)
        if (serverBin != null) {
            File bin = serverBin.getAbsoluteFile().getParentFile();
            if (bin != null) {
                File global = new File(bin.getParentFile(), "lib/node_modules/typescript");
                if (new File(global, "package.json").isFile()) {
                    return verdict(global);
                }
                // a project-local server: its own node_modules/typescript sibling
                File sibling = new File(bin.getParentFile(), "typescript");
                if (new File(sibling, "package.json").isFile()) {
                    return verdict(sibling);
                }
                // npm's WINDOWS prefix: <prefix>/typescript-language-server.cmd
                // beside <prefix>/node_modules/typescript (no lib/ level)
                File windows = new File(bin, "node_modules/typescript");
                if (new File(windows, "package.json").isFile()) {
                    return verdict(windows);
                }
            }
        }
        return new Verdict(Kind.NOT_FOUND, null, null);
    }

    private static Verdict verdict(File typescriptDir) {
        String version = version(new File(typescriptDir, "package.json"));
        boolean tsserver = new File(typescriptDir, "lib/tsserver.js").isFile();
        return new Verdict(tsserver ? Kind.SERVEABLE : Kind.NO_TSSERVER, version, typescriptDir);
    }

    static String version(File packageJson) {
        try {
            String text = Files.readString(packageJson.toPath(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
            return m.find() ? m.group(1) : "?";
        } catch (java.io.IOException e) {
            return "?";
        }
    }
}
