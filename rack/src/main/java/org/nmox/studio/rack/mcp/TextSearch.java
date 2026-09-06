package org.nmox.studio.rack.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A bounded, read-only text search over the aimed project (v2.79.0) —
 * the Agent Port's {@code search_text}: a case-insensitive LITERAL (an
 * agent that wants regex has its own grep; a literal never surprises),
 * over at most {@link #MAX_FILES} text files under {@link #MAX_FILE_BYTES}
 * each, skipping the same heavy directories the symbol index skips and
 * anything with a NUL in its first bytes, at most {@link #MAX_HITS}
 * hits with each line clipped to {@link #MAX_LINE} characters. Every
 * cap is REPORTED ({@code truncated}, {@code filesScanned}) so a partial
 * answer never reads as a complete one. Pure: the walk is the only I/O.
 */
final class TextSearch {

    static final int MAX_FILES = 2_000;
    static final int MAX_FILE_BYTES = 256 * 1024;
    static final int MAX_HITS = 50;
    static final int MAX_LINE = 200;
    static final int MAX_DEPTH = 32;
    static final Set<String> SKIP_DIRS = Set.of(
            "node_modules", ".git", "dist", "build", "coverage", "target",
            "out", "vendor", ".next", ".nuxt", ".svelte-kit", "__pycache__");

    /** Files that exist to hold secrets: never searched, never listed (v2.84.0). */
    static final Set<String> SECRET_NAMES = Set.of(
            ".npmrc", ".yarnrc", ".yarnrc.yml", ".netrc", ".git-credentials", ".pypirc",
            ".htpasswd", ".dockercfg", "secrets.json", "secrets.yaml", "secrets.yml", "credentials.json",
            "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519");
    // "env" covers app.env / production.env (docker --env-file's shape) beside the dotenv family
    static final Set<String> SECRET_EXTENSIONS = Set.of(
            "env", "pem", "key", "p12", "pfx", "jks", "keystore", "ppk");

    private TextSearch() {
    }

    /**
     * Whether a file is one the product never reads on an agent's behalf:
     * the .env family (the editor's own env law — an agent may learn a
     * KEY's name through the IDE, never its value), package-manager rc
     * files that carry auth tokens, and private keys / certificates. A
     * search that could return {@code API_KEY=…} is a disclosure, so the
     * answer is total: not searched, not counted, not completable.
     */
    static boolean isSecretBearing(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        if (n.equals(".env") || n.startsWith(".env.")) {
            return true;
        }
        if (SECRET_NAMES.contains(n)) {
            return true;
        }
        int dot = n.lastIndexOf('.');
        return dot > 0 && SECRET_EXTENSIONS.contains(n.substring(dot + 1));
    }

    /** One hit: file relative to the root, 1-based line, the clipped line text. */
    record Hit(String file, int line, String text) {
    }

    /** The answer: hits, how many files were read, whether any cap bit. */
    record Answer(List<Hit> hits, int filesScanned, boolean truncated) {
    }

    static Answer search(Path root, String query, int limit) {
        if (root == null || query == null || query.isBlank() || limit <= 0) {
            return new Answer(List.of(), 0, false);
        }
        String needle = query.toLowerCase(Locale.ROOT);
        int cap = Math.min(limit, MAX_HITS);
        List<Path> files = new ArrayList<>();
        boolean truncated = collect(root, files);
        List<Hit> hits = new ArrayList<>();
        int scanned = 0;
        for (Path file : files) {
            String text = readText(file);
            if (text == null) {
                continue;
            }
            scanned++;
            String[] lines = text.split("\\r?\\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].toLowerCase(Locale.ROOT).contains(needle)) {
                    // truncated is EXACT: it is set only when a (cap+1)th match
                    // exists — a file with exactly cap matches is complete
                    if (hits.size() >= cap) {
                        truncated = true;
                        break;
                    }
                    hits.add(new Hit(root.relativize(file).toString().replace(java.io.File.separatorChar, '/'),
                            i + 1, clip(lines[i].strip())));
                }
            }
            if (truncated) {
                break;
            }
        }
        return new Answer(List.copyOf(hits), scanned, truncated);
    }

    /** The project's files relative to root, forward-slashed, the same walk and caps search uses (v2.84.0). */
    static List<String> relativeFiles(Path root) {
        List<Path> files = new java.util.ArrayList<>();
        collect(root, files);
        List<String> rel = new java.util.ArrayList<>(files.size());
        for (Path f : files) {
            rel.add(root.relativize(f).toString().replace(java.io.File.separatorChar, '/'));
        }
        return rel;
    }

    /** Files under root in walk order; true when the file cap stopped the walk. */
    private static boolean collect(Path root, List<Path> into) {
        try (Stream<Path> walk = Files.walk(root, MAX_DEPTH)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (Files.isDirectory(p)) {
                    continue;
                }
                boolean skipped = false;
                for (Path part : root.relativize(p)) {
                    if (SKIP_DIRS.contains(part.toString())) {
                        skipped = true;
                        break;
                    }
                }
                if (skipped || isSecretBearing(p.getFileName().toString())) {
                    continue;
                }
                if (into.size() >= MAX_FILES) {
                    return true;
                }
                into.add(p);
            }
        } catch (IOException | java.io.UncheckedIOException e) {
            // an unreadable subtree ends the walk where it stands
            return true;
        }
        return false;
    }

    /** The file's text, or null when it is over the cap or binary. */
    private static String readText(Path file) {
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(file);
            int probe = Math.min(bytes.length, 8_192);
            for (int i = 0; i < probe; i++) {
                if (bytes[i] == 0) {
                    return null;
                }
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static String clip(String line) {
        if (line.length() <= MAX_LINE) {
            return line;
        }
        int end = line.offsetByCodePoints(0, Math.min(line.codePointCount(0, line.length()), MAX_LINE));
        return line.substring(0, end) + "\u2026";
    }

    /** The structured object — the single source of truth Texts renders. */
    static JSONObject toJson(String query, Answer a) {
        JSONArray hits = new JSONArray();
        for (Hit h : a.hits()) {
            hits.put(new JSONObject().put("file", h.file()).put("line", h.line()).put("text", h.text()));
        }
        return new JSONObject()
                .put("query", query == null ? "" : query.strip())
                .put("matches", hits)
                .put("filesScanned", a.filesScanned())
                .put("truncated", a.truncated());
    }
}
