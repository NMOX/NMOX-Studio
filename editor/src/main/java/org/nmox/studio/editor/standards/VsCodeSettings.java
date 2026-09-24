package org.nmox.studio.editor.standards;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.nmox.studio.core.util.BoundedReads;
import org.nmox.studio.core.util.Jsonc;
import org.nmox.studio.editor.lsp.LspLanguageIds;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * A repository's {@code .vscode/settings.json}, as far as it says how its
 * files are written (3.1.0). Many projects commit their indentation there
 * rather than in an {@code .editorconfig}; a switcher opening one here
 * got the editor's defaults instead.
 *
 * <p>Four settings are read, and translated into the EditorConfig
 * properties the editor already honours ({@link EditorConfigIndentation},
 * {@link EditorConfig#applyOnSave}):
 * <ul>
 * <li>{@code editor.tabSize} (a number) is the tab width and the
 *     indentation width, as VS Code uses it unless
 *     {@code editor.indentSize} names another number;</li>
 * <li>{@code editor.insertSpaces} chooses spaces or tabs;</li>
 * <li>{@code files.trimTrailingWhitespace} and
 *     {@code files.insertFinalNewline}, when {@code true}. VS Code's
 *     {@code false} means "leave it alone", while EditorConfig's
 *     {@code false} for a final newline means "strip it", so a false is
 *     never translated.</li>
 * </ul>
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
    static final long MAX_BYTES = 1024L * 1024;

    /** Folders walked above the edited file. */
    static final int MAX_DEPTH = 16;

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
     * its repository, or null. Only a file under a repository's root (the
     * folder holding {@code .git}) is read: walking on towards the
     * filesystem root would apply a {@code /tmp/.vscode} anyone on the
     * machine can write (the 3.1.0 review). The nearest wins, as opening
     * that folder in VS Code would.
     */
    static File settingsFor(File file) {
        String home = System.getProperty("user.home");
        File found = null;
        File dir = file.getParentFile();
        for (int depth = 0; dir != null && depth < MAX_DEPTH; depth++, dir = dir.getParentFile()) {
            if (home != null && dir.getAbsolutePath().equals(new File(home).getAbsolutePath())) {
                return null; // reached home with no repository around the file
            }
            File candidate = new File(new File(dir, ".vscode"), "settings.json");
            if (found == null && candidate.isFile()) {
                found = candidate;
            }
            if (new File(dir, ".git").exists()) {
                return found; // the repository's root: settings above it are somebody else's
            }
        }
        return null;
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
    static String languageId(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jsx")) {
            return "javascriptreact";
        }
        if (name.endsWith(".tsx")) {
            return "typescriptreact";
        }
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
        return fo == null ? null : LspLanguageIds.forMime(fo.getMIMEType());
    }

    /**
     * The EditorConfig properties {@code settings} states for a file of
     * VS Code language {@code languageId} (null: the top level only).
     * Pure.
     */
    static Map<String, String> translate(JSONObject settings, String languageId) {
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
        return out;
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
