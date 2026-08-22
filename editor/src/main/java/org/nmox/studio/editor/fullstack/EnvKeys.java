package org.nmox.studio.editor.fullstack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The project's environment keys as data (v2.31.0, the full-stack
 * wishlist): {@code process.env.DATABASE_URL} is a magic string the
 * editor knew nothing about while the same editor completed the
 * stylesheet's every class. This core reads the project's {@code .env}
 * family and makes the keys first-class — completion after
 * {@code process.env.} / {@code import.meta.env.}, and ⌘-click from a
 * usage to the declaring line.
 *
 * <p>Deliberately its own reader beside the rack's {@code EnvFiles}:
 * the rack wants VALUES to inject into a spawn; the editor wants
 * KEYS WITH OFFSETS to navigate to, and must never couple an editor
 * gesture to the rack's engine. Values are read only to show alongside
 * the key — and {@code .env} files hold secrets, so values are shown
 * TRUNCATED and never logged.
 */
public final class EnvKeys {

    private EnvKeys() {
    }

    /** The env-file family, in the order Vite/Node tooling loads them. */
    static final List<String> ENV_FILES = List.of(
            ".env", ".env.local", ".env.development", ".env.production",
            ".env.test");

    private static final long MAX_BYTES = 256 * 1024;

    /** One declaration: key, its file, the OFFSET of the key in it. */
    public record EnvKey(String name, File file, int offset, String value) {
    }

    /**
     * Every key declared across the project root's env family — first
     * file in load order wins a duplicate name (navigation goes to the
     * first declaration). Reads only the fixed family names in the ROOT
     * (env files live beside the manifest, not in subtrees); a missing
     * or oversized file is skipped whole.
     */
    public static List<EnvKey> scan(File root) {
        List<EnvKey> out = new ArrayList<>();
        if (root == null || !root.isDirectory()) {
            return out;
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String name : ENV_FILES) {
            File f = new File(root, name);
            if (!f.isFile() || f.length() > MAX_BYTES) {
                continue;
            }
            try {
                String text = Files.readString(f.toPath());
                for (EnvKey k : declarations(text, f)) {
                    if (seen.add(k.name())) {
                        out.add(k);
                    }
                }
            } catch (IOException unreadable) {
                // skip the file, keep the scan
            }
        }
        return out;
    }

    /**
     * KEY=value lines of one env file, offsets preserved — comment
     * lines and {@code export KEY=} prefixes handled, values unquoted
     * and truncated to 24 chars for display (an env file holds
     * secrets; the popup shows a hint, never the whole credential).
     */
    static List<EnvKey> declarations(String text, File file) {
        List<EnvKey> out = new ArrayList<>();
        int lineStart = 0;
        for (String line : text.split("\n", -1)) {
            int i = 0;
            while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
                i++;
            }
            String rest = line.substring(i);
            int keyAt = lineStart + i;
            if (rest.startsWith("export ")) {
                rest = rest.substring(7);
                keyAt += 7;
            }
            int eq = rest.indexOf('=');
            if (eq > 0 && !rest.startsWith("#")) {
                String key = rest.substring(0, eq).strip();
                if (isKey(key)) {
                    String value = rest.substring(eq + 1).strip();
                    if (value.length() >= 2 && (value.startsWith("\"") || value.startsWith("'"))
                            && value.endsWith(value.substring(0, 1))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    if (value.length() > 24) {
                        value = value.substring(0, 21) + "…";
                    }
                    out.add(new EnvKey(key, file, keyAt, value));
                }
            }
            lineStart += line.length() + 1;
        }
        return out;
    }

    private static boolean isKey(String s) {
        if (s.isEmpty() || Character.isDigit(s.charAt(0))) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    // ---- the usage context ------------------------------------------------

    private static final List<String> ACCESSORS =
            List.of("process.env.", "import.meta.env.");

    /**
     * When the caret sits right after {@code process.env.} or
     * {@code import.meta.env.} typing a key, the partial key typed so
     * far; null anywhere else. The completion trigger, pure.
     */
    public static String keyPrefix(String beforeCaret) {
        int i = beforeCaret.length();
        int nameStart = i;
        while (nameStart > 0 && isKeyChar(beforeCaret.charAt(nameStart - 1))) {
            nameStart--;
        }
        String head = beforeCaret.substring(0, nameStart);
        for (String accessor : ACCESSORS) {
            if (head.endsWith(accessor)) {
                // word boundary before the accessor: myprocess.env.X is
                // somebody's own object, not the platform's (the test's
                // refusal caught this before any mutant had to)
                int at = head.length() - accessor.length();
                if (at == 0 || !isKeyChar(head.charAt(at - 1))) {
                    return beforeCaret.substring(nameStart);
                }
            }
        }
        return null;
    }

    /**
     * The key span under {@code offset} when it is an env access —
     * the ⌘-click subject. Returns {start, end} or null.
     */
    public static int[] keySpanAt(String text, int offset) {
        if (offset < 0 || offset > text.length()) {
            return null;
        }
        int start = offset;
        while (start > 0 && isKeyChar(text.charAt(start - 1))) {
            start--;
        }
        int end = offset;
        while (end < text.length() && isKeyChar(text.charAt(end))) {
            end++;
        }
        if (end == start) {
            return null;
        }
        return keyPrefix(text.substring(0, end)) == null
                ? null : new int[] {start, end};
    }

    private static boolean isKeyChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
