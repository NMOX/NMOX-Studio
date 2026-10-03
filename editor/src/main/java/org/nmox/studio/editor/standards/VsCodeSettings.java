package org.nmox.studio.editor.standards;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.nmox.studio.core.util.BoundedReads;
import org.nmox.studio.core.util.Jsonc;
import org.nmox.studio.core.util.VsCodeSettingsFile;
import org.nmox.studio.editor.lsp.LspLanguageIds;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * A repository's {@code .vscode/settings.json}, as far as it says how its
 * files are written (3.1.0). Many projects commit their indentation there
 * rather than in an {@code .editorconfig}; a switcher opening one here
 * got the editor's defaults instead.
 *
 * <p>These settings are read, and translated into the EditorConfig
 * properties the editor already honours ({@link EditorConfigIndentation},
 * {@link EditorConfig#applyOnSave}, {@link EditorConfig#lineSeparator}):
 * <ul>
 * <li>{@code editor.tabSize} (a number) is the tab width and the
 *     indentation width, as VS Code uses it unless
 *     {@code editor.indentSize} names another number;</li>
 * <li>{@code editor.insertSpaces} chooses spaces or tabs;</li>
 * <li>{@code files.trimTrailingWhitespace} and
 *     {@code files.insertFinalNewline}, when {@code true}. VS Code's
 *     {@code false} means "leave it alone", while EditorConfig's
 *     {@code false} for a final newline means "strip it", so a false is
 *     never translated;</li>
 * <li>{@code files.eol}, when it is {@code "\n"} or {@code "\r\n"}, is
 *     the line ending files are written with ({@code "auto"} says
 *     nothing);</li>
 * <li>{@code editor.rulers}: the FIRST ruler is the column of the editor's
 *     one right-margin line ({@link EditorConfigMargin}), as
 *     EditorConfig's {@code max_line_length}. A ruler is a whole number,
 *     or an object whose {@code column} is one (its {@code color} is not
 *     read); an empty list is the project saying "no ruler", and the line
 *     is not drawn. Later rulers are not drawn: the platform has one line.
 *     A first ruler that is anything else says nothing, rather than
 *     promoting the second;</li>
 * <li>{@code editor.wordWrap}, when it is {@code "on"} or {@code "off"}:
 *     whether the file's lines wrap at the edge of the editor
 *     ({@link EditorConfigMargin}). VS Code's two other values wrap at a
 *     COLUMN ({@code "wordWrapColumn"}, {@code "bounded"}), which the
 *     platform editor cannot do, so they say nothing. EditorConfig has no
 *     word for this, and the key is handed on under VS Code's own name
 *     ({@link #WORD_WRAP}), which an {@code .editorconfig} - whose keys
 *     are read in lower case - can never spell.</li>
 * </ul>
 * One more is answered as a question rather than translated:
 * {@code editor.formatOnSave} ({@link #formatOnSave(File)}). A project
 * that says {@code false} is not reformatted when its files are saved,
 * whatever formatter configuration it carries.
 * A language block ({@code "[javascript]": {...}}, or several ids at
 * once, {@code "[javascript][typescript]"}) overrides the top level for
 * files of that VS Code language id. VS Code's
 * {@code editor.detectIndentation}, which lets a file's own content win,
 * has no counterpart here: the settings are the project's stated
 * preference.
 *
 * <p>The file is the nearest {@code .vscode/settings.json} above the
 * edited file, looking no higher than the repository's root (the folder
 * holding {@code .git}) and never into the home folder, read bounded and
 * cached by path, time and size. A file that does not parse says
 * nothing, and says so once in the log.
 */
public final class VsCodeSettings {

    private static final Logger LOG = Logger.getLogger(VsCodeSettings.class.getName());

    /** A settings file larger than this is not a settings file. */
    static final long MAX_BYTES = VsCodeSettingsFile.MAX_BYTES;

    /** Parsed files kept; past it the map starts over rather than grow. */
    static final int CACHE_CAP = 256;

    private record Parsed(long modified, long length, JSONObject json) {
    }

    private static final Map<String, Parsed> CACHE = new ConcurrentHashMap<>();

    private VsCodeSettings() {
    }

    /**
     * The EditorConfig properties {@code file}'s project states in
     * {@code .vscode/settings.json}; empty when there is none, or it
     * says nothing this reads.
     */
    public static Map<String, String> propertiesFor(File file) {
        File settings = settingsFor(file);
        if (settings == null) {
            return Map.of();
        }
        JSONObject json = parse(settings);
        return json == null ? Map.of() : translate(json, languageId(file));
    }

    /**
     * The nearest {@code .vscode/settings.json} above {@code file} inside
     * its repository, or null. The rule - never above the repository's
     * root, never the home folder's - has one home, shared with the
     * readers of {@code files.exclude} and {@code search.exclude}:
     * {@link VsCodeSettingsFile#nearest(File)}.
     */
    static File settingsFor(File file) {
        return VsCodeSettingsFile.nearest(file);
    }

    /**
     * The folders whose {@code .vscode} speaks for {@code file}: its own
     * folder and each one above it, nearest first, ending with its
     * repository's root (the folder holding {@code .git}). Empty when no
     * repository is around the file within {@link VsCodeSettingsFile#MAX_DEPTH} levels, or
     * the home folder comes first: a {@code .vscode} that is not inside
     * a repository is nobody's project configuration.
     *
     * <p>One walk for every reader of a repository's {@code .vscode}:
     * the settings above and the project's snippets
     * ({@code editor.snippets.ProjectSnippets}), which would otherwise
     * have grown its own answer to "how far up".
     */
    public static List<File> directoriesToRepositoryRoot(File file) {
        File parent = file.getParentFile();
        return parent == null ? List.of() : VsCodeSettingsFile.foldersToRepositoryRoot(parent);
    }

    private static JSONObject parse(File settings) {
        String key = settings.getAbsolutePath();
        long modified = settings.lastModified();
        long length = settings.length();
        Parsed hit = CACHE.get(key);
        if (hit != null && hit.modified() == modified && hit.length() == length) {
            return hit.json();
        }
        JSONObject json;
        try {
            json = new JSONObject(Jsonc.strip(BoundedReads.read(settings, MAX_BYTES)));
        } catch (IOException | JSONException ex) {
            LOG.log(Level.INFO, "{0} says nothing to the editor: {1}", new Object[] {settings, ex.getMessage()});
            json = null;
        }
        if (CACHE.size() >= CACHE_CAP) {
            CACHE.clear();
        }
        CACHE.put(key, new Parsed(modified, length, json));
        return json;
    }

    /** VS Code's language id for {@code file}: its own names for JSX and TSX, else the LSP rule. */
    public static String languageId(File file) {
        String byName = languageId(file.getName(), null);
        if (byName != null) {
            return byName;
        }
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
        return fo == null ? null : LspLanguageIds.forMime(fo.getMIMEType());
    }

    /**
     * VS Code's language id for a file of this name whose editor is of
     * this mime type: the two names VS Code keeps apart and this product's
     * mime types do not ({@code .jsx} and {@code .tsx} open as JavaScript
     * and TypeScript here), else {@link LspLanguageIds}' answer for the
     * mime; null when neither says. The one place a VS Code language id
     * is decided: a {@code "[typescript]"} settings block and a snippet's
     * {@code "scope": "typescript"} must name the same files.
     */
    public static String languageId(String fileName, String mime) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".jsx")) {
            return "javascriptreact";
        }
        if (name.endsWith(".tsx")) {
            return "typescriptreact";
        }
        return LspLanguageIds.forMime(mime);
    }

    /**
     * The EditorConfig properties {@code settings} states for a file of
     * VS Code language {@code languageId} (null: the top level only).
     * Pure.
     */
    static Map<String, String> translate(JSONObject settings, String languageId) {
        Map<String, Object> values = effective(settings, languageId);
        Map<String, String> out = new LinkedHashMap<>();
        Integer tab = width(values.get("editor.tabSize"));
        Integer indent = width(values.get("editor.indentSize"));
        if (tab != null) {
            out.put("tab_width", Integer.toString(tab));
        }
        if (indent != null) {
            out.put("indent_size", Integer.toString(indent));
        } else if (tab != null) {
            out.put("indent_size", Integer.toString(tab));
        }
        Object spaces = values.get("editor.insertSpaces");
        if (spaces instanceof Boolean b) {
            out.put("indent_style", b ? "space" : "tab");
        }
        if (Boolean.TRUE.equals(values.get("files.trimTrailingWhitespace"))) {
            out.put("trim_trailing_whitespace", "true");
        }
        if (Boolean.TRUE.equals(values.get("files.insertFinalNewline"))) {
            out.put("insert_final_newline", "true");
        }
        // "auto" (the operating system's own) and anything else say nothing
        Object eol = values.get("files.eol");
        if ("\n".equals(eol)) {
            out.put("end_of_line", "lf");
        } else if ("\r\n".equals(eol)) {
            out.put("end_of_line", "crlf");
        }
        String ruler = firstRuler(values.get("editor.rulers"));
        if (ruler != null) {
            out.put("max_line_length", ruler);
        }
        Object wrap = values.get(WORD_WRAP);
        if ("on".equals(wrap)) {
            out.put(WORD_WRAP, "on");
        } else if ("off".equals(wrap)) {
            out.put(WORD_WRAP, "off");
        }
        return out;
    }

    /** VS Code's word-wrap setting, and the key its {@code on}/{@code off} is handed on under. */
    static final String WORD_WRAP = "editor.wordWrap";

    /**
     * The first of {@code editor.rulers} as an EditorConfig
     * {@code max_line_length}: a column, {@code "off"} for an empty list,
     * or null when the setting is absent, is not a list, or begins with
     * something that is not a column this can draw.
     */
    static String firstRuler(Object rulers) {
        if (!(rulers instanceof org.json.JSONArray list)) {
            return null;
        }
        if (list.isEmpty()) {
            return "off";
        }
        Object first = list.opt(0);
        if (first instanceof JSONObject withColour) {
            first = withColour.opt("column");
        }
        if (first instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && d >= 1 && d <= EditorConfigMargin.MAX_COLUMN) {
                return Integer.toString((int) d);
            }
        }
        return null;
    }

    /**
     * The settings in force for a file of VS Code language
     * {@code languageId}: the top level, with every language block that
     * names it laid over. Pure.
     */
    static Map<String, Object> effective(JSONObject settings, String languageId) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String k : settings.keySet()) {
            if (!k.startsWith("[")) {
                values.put(k, settings.opt(k));
            }
        }
        if (languageId != null) {
            for (String k : settings.keySet()) {
                if (k.startsWith("[") && names(k, languageId) && settings.opt(k) instanceof JSONObject block) {
                    for (String inner : block.keySet()) {
                        values.put(inner, block.opt(inner));
                    }
                }
            }
        }
        return values;
    }

    /**
     * What {@code file}'s project says about formatting on save
     * ({@code editor.formatOnSave}, top level or in the file's language
     * block): true, false, or empty when it says nothing or says something
     * that is not a boolean (3.5.13).
     */
    public static java.util.Optional<Boolean> formatOnSave(File file) {
        File settings = settingsFor(file);
        if (settings == null) {
            return java.util.Optional.empty();
        }
        JSONObject json = parse(settings);
        return json == null ? java.util.Optional.empty() : formatOnSave(json, languageId(file));
    }

    /** {@link #formatOnSave(File)} over parsed settings. Pure. */
    static java.util.Optional<Boolean> formatOnSave(JSONObject settings, String languageId) {
        return effective(settings, languageId).get("editor.formatOnSave") instanceof Boolean b
                ? java.util.Optional.of(b) : java.util.Optional.empty();
    }

    /** Whether a {@code [a][b]} language-block key names {@code languageId}. */
    private static boolean names(String key, String languageId) {
        int i = 0;
        while (i < key.length() && key.charAt(i) == '[') {
            int close = key.indexOf(']', i);
            if (close < 0) {
                return false;
            }
            if (key.substring(i + 1, close).strip().equals(languageId)) {
                return true;
            }
            i = close + 1;
        }
        return false;
    }

    /** A width VS Code would honour, as a whole number from 1 to 32; anything else says nothing. */
    private static Integer width(Object v) {
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && d >= 1 && d <= EditorConfigIndentation.MAX_WIDTH) {
                return (int) d;
            }
        }
        return null;
    }
}
