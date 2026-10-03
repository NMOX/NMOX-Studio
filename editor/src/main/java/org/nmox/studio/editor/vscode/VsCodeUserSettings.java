package org.nmox.studio.editor.vscode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nmox.studio.core.util.Jsonc;
import org.nmox.studio.core.util.PlainText;
import org.openide.util.NbBundle;

/**
 * A switcher's own VS Code preferences, read once when they ask: the pure
 * half of Tools ▸ Import VS Code Settings… (no Swing, no platform state).
 *
 * <p><b>Whose file.</b> This is the USER's {@code settings.json}, not a
 * repository's ({@link org.nmox.studio.editor.standards.VsCodeSettings}
 * reads that one, the team's). It lives outside every project and holds
 * whatever the person ever put there: tokens ({@code "github.token"}),
 * proxy passwords, shell paths, environment blocks. So the rule is narrow
 * and total: only the settings this class RECOGNISES ever leave
 * {@link #plan}, each as a row it renders itself; every other key is
 * COUNTED and nothing more. Its name and value are not in the plan, not
 * on the status line, not in the log. The terminal's settings are
 * recognised as a family and collapsed into one row that shows how many
 * there are, never what they say ({@code terminal.integrated.env.*} is an
 * environment).
 *
 * <p><b>What a row is.</b> The VS Code key, its value as this class
 * renders it, what it becomes here, how close that is ({@link Fit}) and
 * the {@link Change}s pressing Apply would make. {@link Fit#EXACT} rows
 * start checked; {@link Fit#NEAR} rows can be applied but start
 * unchecked, because what they do here is not what they did there and
 * the row says how; {@link Fit#NONE} rows explain why nothing happens.
 * A mapping is EXACT only where this product keeps the same preference
 * with the same meaning; nothing is approximated silently.
 *
 * <p><b>Where the changes go.</b> {@link #apply} hands each change to a
 * {@link Homes}, the seam that owns the product's real preference homes
 * (each through the setter the product's own menu or Options panel uses);
 * tests hand it a recorder, so no test writes the developer's
 * preferences.
 */
public final class VsCodeUserSettings {

    /** A settings file larger than this is not a settings file. */
    static final long MAX_BYTES = 1024L * 1024;

    /** Values are shown cut to this many code points. */
    static final int VALUE_CHARS = 60;

    /** The VS Code builds whose user settings are looked for, in the order tried. */
    public enum Build {
        CODE("Code", "VS Code"),
        INSIDERS("Code - Insiders", "VS Code Insiders"),
        CODIUM("VSCodium", "VSCodium");

        /** The folder the build keeps its user data under. */
        final String folder;
        /** How the sheet names it. */
        public final String label;

        Build(String folder, String label) {
            this.folder = folder;
            this.label = label;
        }
    }

    /** One place a user settings file may be. */
    public record Location(Build build, Path file) {
    }

    /** How close a row's meaning here is to its meaning in VS Code. */
    public enum Fit {
        /** The same preference, the same meaning: checked by default. */
        EXACT,
        /** Applicable, but not quite the same: unchecked, and the row says how it differs. */
        NEAR,
        /** Nothing here to set; the row says why. */
        NONE
    }

    /** A preference home this product keeps, which Apply can write. */
    public enum Target {
        /** All languages: {@code tab-size}. */
        TAB_SIZE,
        /** All languages: {@code indent-shift-width} and {@code spaces-per-tab}. */
        INDENT,
        /** All languages: {@code expand-tabs}. */
        EXPAND_TABS,
        /** All languages: {@code text-line-wrap}, then the open editors told. */
        LINE_WRAP,
        /** All languages: {@code text-limit-width}. */
        RULER_WIDTH,
        /** All languages: {@code text-limit-line-visible}. */
        RULER_VISIBLE,
        /** All languages: {@code non-printable-characters-visible}. */
        WHITESPACE_VISIBLE,
        /** All languages: {@code on-save-remove-trailing-whitespace}. */
        TRIM_ON_SAVE,
        /** View ▸ Minimap. */
        MINIMAP,
        /** View ▸ Sticky Scroll. */
        STICKY_SCROLL,
        /** Format on Save. */
        FORMAT_ON_SAVE,
        /** The platform autosave: timer on or off. */
        AUTOSAVE_ACTIVE,
        /** The platform autosave: its interval in whole minutes. */
        AUTOSAVE_MINUTES,
        /** The platform autosave: save when an editor loses focus. */
        AUTOSAVE_ON_FOCUS_LOST
    }

    /** One preference write. {@code value} is a Boolean, an Integer or a String, by target. */
    public record Change(Target target, Object value) {
    }

    /**
     * One row of the sheet.
     *
     * @param key     the VS Code setting (a recognised key, or {@code terminal.integrated.*})
     * @param value   its value as rendered here, one line, cut
     * @param here    what it becomes in NMOX Studio, a sentence
     * @param fit     how close that is
     * @param changes what Apply writes; empty for {@link Fit#NONE}
     */
    public record Row(String key, String value, String here, Fit fit, List<Change> changes) {

        public Row {
            changes = List.copyOf(changes);
        }

        /** Whether Apply can do anything with this row. */
        public boolean applicable() {
            return fit != Fit.NONE && !changes.isEmpty();
        }
    }

    /**
     * What a settings file says, as far as this product can hear it.
     *
     * @param rows    one per recognised setting, in the order below
     * @param others  how many settings (and language blocks) are not recognised
     */
    public record Plan(List<Row> rows, int others) {

        public Plan {
            rows = List.copyOf(rows);
        }
    }

    /** The file is not JSON with comments, or not an object. Carries no content. */
    public static final class Unreadable extends Exception {

        private static final long serialVersionUID = 1L;

        Unreadable() {
            super("not a settings object");
        }
    }

    /**
     * The homes Apply writes to. The product's implementation goes through
     * each preference's own setter; a test records.
     */
    public interface Homes {

        /** Writes one change; throws when the home is not there, naming it. */
        void write(Change change);

        /** Called once after the writes, so open editors and timers follow. */
        void settled(Set<Target> written);
    }

    /** What Apply did: the rows written, and the keys of the rows that could not be. */
    public record Outcome(List<String> applied, List<String> failed) {

        public Outcome {
            applied = List.copyOf(applied);
            failed = List.copyOf(failed);
        }
    }

    private VsCodeUserSettings() {
    }

    // ------------------------------------------------------------------ where

    /**
     * Every place a user settings file may be on this system, in the order
     * tried: VS Code, then Insiders, then VSCodium. macOS keeps them under
     * {@code ~/Library/Application Support}, Windows under
     * {@code %APPDATA%} (else {@code ~/AppData/Roaming}), others under
     * {@code $XDG_CONFIG_HOME} (else {@code ~/.config}). Pure.
     *
     * @param osName the {@code os.name} property
     * @param home   the {@code user.home} property
     * @param env    the environment, by variable name
     */
    public static List<Location> candidates(String osName, String home, Function<String, String> env) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        Path base;
        if (os.startsWith("mac") || os.startsWith("darwin")) {
            base = Path.of(home, "Library", "Application Support");
        } else if (os.startsWith("windows")) {
            String appData = env.apply("APPDATA");
            base = appData == null || appData.isBlank() ? Path.of(home, "AppData", "Roaming") : Path.of(appData);
        } else {
            String xdg = env.apply("XDG_CONFIG_HOME");
            // the spec: a relative XDG_CONFIG_HOME is invalid and is ignored
            base = xdg == null || xdg.isBlank() || !Path.of(xdg).isAbsolute() ? Path.of(home, ".config") : Path.of(xdg);
        }
        List<Location> out = new ArrayList<>();
        for (Build build : Build.values()) {
            out.add(new Location(build, base.resolve(build.folder).resolve("User").resolve("settings.json")));
        }
        return out;
    }

    /** The candidates that exist, in order; the first is the one read. */
    public static List<Location> found(List<Location> candidates, Predicate<Path> exists) {
        List<Location> out = new ArrayList<>();
        for (Location l : candidates) {
            if (exists.test(l.file())) {
                out.add(l);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ what

    /** The settings this recognises, in the order the sheet lists them. */
    static final List<String> RECOGNISED = List.of(
            "editor.fontFamily", "editor.fontSize", "editor.fontLigatures",
            "editor.tabSize", "editor.indentSize", "editor.insertSpaces", "editor.detectIndentation",
            "editor.wordWrap", "editor.rulers", "editor.renderWhitespace",
            "editor.formatOnSave", "editor.minimap.enabled", "editor.stickyScroll.enabled",
            "files.autoSave", "files.autoSaveDelay",
            "files.trimTrailingWhitespace", "files.insertFinalNewline", "files.eol",
            "workbench.colorTheme", "workbench.iconTheme");

    /** The terminal's settings: recognised as one family, never shown one by one. */
    static final String TERMINAL_PREFIX = "terminal.integrated.";

    /** The widest indentation or tab a setting may name, as {@code VsCodeSettings} honours it. */
    static final int MAX_WIDTH = 32;

    /**
     * Reads a settings file's text into a plan. Pure apart from the fonts
     * it is told are installed.
     *
     * @param jsonc     the file, JSON with comments
     * @param installed the font families this machine has
     * @throws Unreadable when it is not a JSON object; the exception carries
     *         nothing of the file
     */
    public static Plan plan(String jsonc, Collection<String> installed) throws Unreadable {
        JSONObject json;
        try {
            json = new JSONObject(Jsonc.strip(jsonc));
        } catch (JSONException | StackOverflowError ex) {
            throw new Unreadable();
        }
        Map<String, Object> seen = new LinkedHashMap<>();
        int others = 0;
        int terminal = 0;
        for (String key : json.keySet()) {
            if (RECOGNISED.contains(key)) {
                seen.put(key, json.opt(key));
            } else if (key.startsWith(TERMINAL_PREFIX)) {
                terminal++;
            } else {
                others++; // a stranger's key: counted, nothing more
            }
        }
        List<Row> rows = new ArrayList<>();
        for (String key : RECOGNISED) {
            if (seen.containsKey(key)) {
                Row row = row(key, seen.get(key), seen, installed);
                if (row != null) {
                    rows.add(row);
                }
            }
        }
        if (terminal > 0) {
            rows.add(new Row(TERMINAL_PREFIX + "*", msg("Value_count", terminal), msg("Here_terminal"), Fit.NONE,
                    List.of()));
        }
        return new Plan(rows, others);
    }

    private static Row row(String key, Object v, Map<String, Object> all, Collection<String> installed) {
        String shown = render(v);
        return switch (key) {
            case "editor.fontFamily" -> {
                if (!(v instanceof String s)) {
                    yield none(key, shown, msg("Here_invalid"));
                }
                String family = firstInstalled(s, installed);
                yield none(key, shown, family == null ? msg("Here_fontNone") : msg("Here_font", family));
            }
            case "editor.fontSize", "editor.fontLigatures" -> none(key, shown, msg("Here_fonts"));
            case "editor.tabSize" -> {
                Integer n = width(v);
                if (n == null) {
                    yield none(key, shown, msg("Here_invalid"));
                }
                boolean indentApart = width(all.get("editor.indentSize")) != null;
                List<Change> c = new ArrayList<>();
                c.add(new Change(Target.TAB_SIZE, n));
                if (!indentApart) {
                    c.add(new Change(Target.INDENT, n));
                }
                yield new Row(key, shown, msg(indentApart ? "Here_tabOnly" : "Here_tab", n), Fit.EXACT, c);
            }
            case "editor.indentSize" -> {
                Integer n = width(v);
                if (n != null) {
                    yield new Row(key, shown, msg("Here_indent", n), Fit.EXACT, List.of(new Change(Target.INDENT, n)));
                }
                // "tabSize" is VS Code's default: the tab size row already says it
                yield "tabSize".equals(v) ? null : none(key, shown, msg("Here_invalid"));
            }
            case "editor.insertSpaces" -> v instanceof Boolean b
                    ? new Row(key, shown, msg(b ? "Here_spaces" : "Here_tabs"), Fit.EXACT,
                            List.of(new Change(Target.EXPAND_TABS, b)))
                    : none(key, shown, msg("Here_invalid"));
            case "editor.detectIndentation" -> none(key, shown, msg("Here_detect"));
            case "editor.wordWrap" -> {
                if ("on".equals(v) || "off".equals(v)) {
                    boolean on = "on".equals(v);
                    yield new Row(key, shown, msg(on ? "Here_wrapOn" : "Here_wrapOff"), Fit.EXACT,
                            List.of(new Change(Target.LINE_WRAP, on ? "words" : "none")));
                }
                yield none(key, shown, "wordWrapColumn".equals(v) || "bounded".equals(v)
                        ? msg("Here_wrapColumn") : msg("Here_invalid"));
            }
            case "editor.rulers" -> rulers(key, v, shown);
            case "editor.renderWhitespace" -> {
                if ("none".equals(v)) {
                    yield new Row(key, shown, msg("Here_whitespaceOff"), Fit.EXACT,
                            List.of(new Change(Target.WHITESPACE_VISIBLE, false)));
                }
                if ("all".equals(v)) {
                    yield new Row(key, shown, msg("Here_whitespaceAll"), Fit.NEAR,
                            List.of(new Change(Target.WHITESPACE_VISIBLE, true)));
                }
                yield none(key, shown, v instanceof String ? msg("Here_whitespaceSome") : msg("Here_invalid"));
            }
            case "editor.formatOnSave" -> v instanceof Boolean b
                    ? new Row(key, shown, msg(b ? "Here_formatOn" : "Here_formatOff"), Fit.EXACT,
                            List.of(new Change(Target.FORMAT_ON_SAVE, b)))
                    : none(key, shown, msg("Here_invalid"));
            case "editor.minimap.enabled" -> v instanceof Boolean b
                    ? new Row(key, shown, msg(b ? "Here_minimapOn" : "Here_minimapOff"), Fit.EXACT,
                            List.of(new Change(Target.MINIMAP, b)))
                    : none(key, shown, msg("Here_invalid"));
            case "editor.stickyScroll.enabled" -> v instanceof Boolean b
                    ? new Row(key, shown, msg(b ? "Here_stickyOn" : "Here_stickyOff"), Fit.EXACT,
                            List.of(new Change(Target.STICKY_SCROLL, b)))
                    : none(key, shown, msg("Here_invalid"));
            case "files.autoSave" -> autoSave(key, v, shown, all.get("files.autoSaveDelay"));
            case "files.autoSaveDelay" -> all.containsKey("files.autoSave") ? null
                    : none(key, shown, msg("Here_delayAlone"));
            case "files.trimTrailingWhitespace" -> v instanceof Boolean b
                    ? new Row(key, shown, msg(b ? "Here_trimOn" : "Here_trimOff"), Fit.EXACT,
                            List.of(new Change(Target.TRIM_ON_SAVE, b ? "always" : "never")))
                    : none(key, shown, msg("Here_invalid"));
            case "files.insertFinalNewline", "files.eol" -> none(key, shown, msg("Here_repositoryOnly"));
            case "workbench.colorTheme", "workbench.iconTheme" -> none(key, shown, msg("Here_oneLook"));
            default -> null;
        };
    }

    private static Row rulers(String key, Object v, String shown) {
        if (!(v instanceof JSONArray a)) {
            return none(key, shown, msg("Here_invalid"));
        }
        if (a.isEmpty()) {
            return new Row(key, shown, msg("Here_rulerOff"), Fit.EXACT,
                    List.of(new Change(Target.RULER_VISIBLE, false)));
        }
        Object first = a.opt(0);
        if (first instanceof JSONObject o) {
            first = o.opt("column");
        }
        Integer column = column(first);
        if (column == null) {
            return none(key, shown, msg("Here_invalid"));
        }
        List<Change> c = List.of(new Change(Target.RULER_WIDTH, column), new Change(Target.RULER_VISIBLE, true));
        return a.length() == 1
                ? new Row(key, shown, msg("Here_ruler", column), Fit.EXACT, c)
                : new Row(key, shown, msg("Here_rulerFirst", column, a.length()), Fit.NEAR, c);
    }

    private static Row autoSave(String key, Object v, String shown, Object delayValue) {
        if ("off".equals(v)) {
            return new Row(key, shown, msg("Here_autoSaveOff"), Fit.EXACT, List.of(
                    new Change(Target.AUTOSAVE_ACTIVE, false), new Change(Target.AUTOSAVE_ON_FOCUS_LOST, false)));
        }
        if ("onFocusChange".equals(v)) {
            return new Row(key, shown, msg("Here_autoSaveFocus"), Fit.EXACT, List.of(
                    new Change(Target.AUTOSAVE_ACTIVE, false), new Change(Target.AUTOSAVE_ON_FOCUS_LOST, true)));
        }
        if ("onWindowChange".equals(v)) {
            return new Row(key, shown, msg("Here_autoSaveWindow"), Fit.NEAR, List.of(
                    new Change(Target.AUTOSAVE_ACTIVE, false), new Change(Target.AUTOSAVE_ON_FOCUS_LOST, true)));
        }
        if ("afterDelay".equals(v)) {
            // VS Code's default delay is one second; the platform's interval is whole minutes, 1 to 999
            long delay = delayValue instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())
                    ? n.longValue() : 1000L;
            String value = shown + " · " + render(delayValue == null ? 1000 : delayValue) + " ms";
            if (delay > 0 && delay % 60_000L == 0 && delay / 60_000L <= 999) {
                int minutes = (int) (delay / 60_000L);
                return new Row(key, value, msg("Here_autoSaveMinutes", minutes), Fit.EXACT, List.of(
                        new Change(Target.AUTOSAVE_ACTIVE, true), new Change(Target.AUTOSAVE_MINUTES, minutes),
                        new Change(Target.AUTOSAVE_ON_FOCUS_LOST, false)));
            }
            return new Row(key, value, msg("Here_autoSaveDelay", delay), Fit.NEAR, List.of(
                    new Change(Target.AUTOSAVE_ACTIVE, true), new Change(Target.AUTOSAVE_ON_FOCUS_LOST, false)));
        }
        return none(key, shown, msg("Here_invalid"));
    }

    private static Row none(String key, String shown, String here) {
        return new Row(key, shown, here, Fit.NONE, List.of());
    }

    /** A width VS Code would honour, a whole number from 1 to {@link #MAX_WIDTH}; else null. */
    static Integer width(Object v) {
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && d >= 1 && d <= MAX_WIDTH) {
                return (int) d;
            }
        }
        return null;
    }

    /** A ruler column, a whole number from 1 to 1000; else null. */
    static Integer column(Object v) {
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && d >= 1 && d <= 1000) {
                return (int) d;
            }
        }
        return null;
    }

    /**
     * The first family of a CSS-style list ({@code "'Fira Code', Menlo,
     * monospace"}) this machine has, in the machine's own spelling; the
     * generic {@code monospace} is Java's {@code Monospaced}, which every
     * runtime has. Null when none of them is installed. Pure.
     */
    public static String firstInstalled(String cssList, Collection<String> installed) {
        if (cssList == null) {
            return null;
        }
        for (String raw : cssList.split(",")) {
            String name = raw.strip();
            if (name.length() >= 2 && (name.charAt(0) == '\'' || name.charAt(0) == '"')
                    && name.charAt(name.length() - 1) == name.charAt(0)) {
                name = name.substring(1, name.length() - 1).strip();
            }
            if (name.isEmpty()) {
                continue;
            }
            if (name.equalsIgnoreCase("monospace")) {
                name = "Monospaced";
            }
            for (String family : installed) {
                if (family.equalsIgnoreCase(name)) {
                    return family;
                }
            }
        }
        return null;
    }

    /** A recognised value as one short line: the file's own words for strings, JSON for the rest. */
    static String render(Object v) {
        String s;
        if (v == null || v == JSONObject.NULL) {
            s = "null";
        } else if (v instanceof String str) {
            s = str;
        } else {
            s = v.toString();
        }
        return PlainText.oneLine(s, VALUE_CHARS);
    }

    // ------------------------------------------------------------------ apply

    /**
     * Writes the changes of the {@code chosen} rows, row by row: a row
     * whose home is not there (it throws) is reported by its key and the
     * others still apply. Rows Apply cannot act on are skipped. Then the
     * homes are told once what was written, so the open editors follow.
     */
    public static Outcome apply(List<Row> chosen, Homes homes) {
        List<String> applied = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Set<Target> written = java.util.EnumSet.noneOf(Target.class);
        for (Row row : chosen) {
            if (!row.applicable()) {
                continue;
            }
            try {
                for (Change c : row.changes()) {
                    homes.write(c);
                    written.add(c.target());
                }
                applied.add(row.key());
            } catch (RuntimeException ex) {
                failed.add(row.key());
            }
        }
        if (!written.isEmpty()) {
            try {
                homes.settled(written);
            } catch (RuntimeException ex) {
                // the values are written; only the live nudge failed. Named by class, never by content.
                java.util.logging.Logger.getLogger(VsCodeUserSettings.class.getName()).log(
                        java.util.logging.Level.INFO, "imported settings written; the open editors were not told ({0})",
                        ex.getClass().getSimpleName());
            }
        }
        return new Outcome(applied, failed);
    }

    static String msg(String key, Object... args) {
        return args.length == 0 ? NbBundle.getMessage(VsCodeUserSettings.class, key)
                : NbBundle.getMessage(VsCodeUserSettings.class, key, args);
    }
}
