package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The VS Code keymap profile's chord table ({@code scripts/vscode-keymap/chords.txt}),
 * read once and shared by the two things that must agree about it: the
 * generator that lays the chords over the default profile
 * ({@link VsCodeKeymapProfile}) and the census that resolves each one through
 * the assembled cluster ({@link VsCodeKeymapResolutionTest}). The file's own
 * header is the format's documentation.
 *
 * <p>A stroke is written here the way the census writes it, {@code
 * "ctrl+shift|P"}: the modifiers in alphabetical order, a bar, the key's
 * {@code KeyEvent} name. A chord is a list of strokes.
 */
final class VsCodeChordTable {

    static final Path FILE = Path.of("..", "scripts", "vscode-keymap", "chords.txt");

    enum Os { MAC, WINDOWS, LINUX }

    enum Scope { EDITOR, GLOBAL }

    /**
     * One VS Code default keybinding. {@code chords} holds the strokes each OS
     * presses and has no entry where VS Code binds nothing on that OS;
     * {@code target} is null where the product has no honest equivalent.
     */
    record Row(int line, Scope scope, Map<Os, List<String>> chords, String target, String command,
            Map<String, String> variants) {

        boolean bound() {
            return target != null;
        }

        /** The action an editor of {@code mime} answers this chord with. */
        String targetFor(String mime) {
            return variants.getOrDefault(mime, target);
        }

        @Override
        public String toString() {
            return "chords.txt:" + line + " (" + command + ")";
        }
    }

    private VsCodeChordTable() {
    }

    static List<Row> read() throws IOException {
        return parse(Files.readAllLines(FILE, StandardCharsets.UTF_8));
    }

    static List<Row> parse(List<String> lines) {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\\|", -1);
            for (int j = 0; j < f.length; j++) {
                f[j] = f[j].strip();
            }
            if (f.length < 6) {
                throw new IllegalArgumentException("chords.txt:" + (i + 1) + ": six columns are needed, found " + f.length);
            }
            Scope scope = switch (f[0]) {
                case "editor" -> Scope.EDITOR;
                case "global" -> Scope.GLOBAL;
                default -> throw new IllegalArgumentException("chords.txt:" + (i + 1) + ": scope is editor or global, not " + f[0]);
            };
            Map<Os, List<String>> chords = new EnumMap<>(Os.class);
            put(chords, Os.MAC, f[1], i);
            put(chords, Os.WINDOWS, f[2], i);
            put(chords, Os.LINUX, "=".equals(f[3]) ? f[2] : f[3], i);
            if (chords.isEmpty()) {
                throw new IllegalArgumentException("chords.txt:" + (i + 1) + ": a row binds a chord on at least one OS");
            }
            String target = "none".equals(f[4]) ? null : f[4];
            if (target != null && target.isEmpty()) {
                throw new IllegalArgumentException("chords.txt:" + (i + 1) + ": the target is an action or \"none\"");
            }
            if (scope == Scope.GLOBAL && target != null && !target.startsWith("Actions/")) {
                throw new IllegalArgumentException("chords.txt:" + (i + 1) + ": a global target is a path under Actions/");
            }
            Map<String, String> variants = new LinkedHashMap<>();
            for (int j = 6; j < f.length; j++) {
                int eq = f[j].indexOf('=');
                if (eq <= 0 || scope != Scope.EDITOR) {
                    throw new IllegalArgumentException("chords.txt:" + (i + 1) + ": mime=action, on an editor row: " + f[j]);
                }
                variants.put(f[j].substring(0, eq).strip(), f[j].substring(eq + 1).strip());
            }
            rows.add(new Row(i + 1, scope, chords, target, f[5], variants));
        }
        return rows;
    }

    private static void put(Map<Os, List<String>> chords, Os os, String written, int line) {
        if ("-".equals(written)) {
            return;
        }
        if (written.isEmpty()) {
            throw new IllegalArgumentException("chords.txt:" + (line + 1) + ": a chord, \"-\", or (Linux) \"=\"");
        }
        List<String> strokes = new ArrayList<>();
        for (String stroke : written.split("\\s+")) {
            strokes.add(explicit(stroke, line + 1));
        }
        chords.put(os, List.copyOf(strokes));
    }

    /** {@code SC-P} to {@code ctrl+shift|P}; the wildcards D and O are refused, a row says what each OS presses. */
    static String explicit(String stroke, int line) {
        int dash = stroke.lastIndexOf('-');
        String mods = dash > 0 ? stroke.substring(0, dash) : "";
        String key = dash > 0 ? stroke.substring(dash + 1) : stroke;
        Set<String> m = new TreeSet<>();
        for (char c : mods.toCharArray()) {
            switch (c) {
                case 'S' -> m.add("shift");
                case 'C' -> m.add("ctrl");
                case 'A' -> m.add("alt");
                case 'M' -> m.add("meta");
                default -> throw new IllegalArgumentException("chords.txt:" + line + ": modifier " + c + " in " + stroke
                        + " - write S, C, A or M, what the OS in that column presses");
            }
        }
        if (key.isEmpty() || !key.equals(key.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("chords.txt:" + line + ": a key is its KeyEvent name in capitals: " + stroke);
        }
        return String.join("+", m) + "|" + key;
    }

    /** {@code ctrl+shift|P} back to the platform's notation with explicit modifiers, Shift first: {@code SC-P}. */
    static String spell(String stroke) {
        int bar = stroke.indexOf('|');
        String mods = stroke.substring(0, bar);
        StringBuilder sb = new StringBuilder();
        if (mods.contains("shift")) {
            sb.append('S');
        }
        if (mods.contains("ctrl")) {
            sb.append('C');
        }
        if (mods.contains("alt")) {
            sb.append('A');
        }
        if (mods.contains("meta")) {
            sb.append('M');
        }
        return (sb.length() == 0 ? "" : sb + "-") + stroke.substring(bar + 1);
    }

    /** Whether one chord is the other, or its first strokes: the two cannot both be bound. */
    static boolean collide(List<String> a, List<String> b) {
        int n = Math.min(a.size(), b.size());
        return a.subList(0, n).equals(b.subList(0, n));
    }
}
