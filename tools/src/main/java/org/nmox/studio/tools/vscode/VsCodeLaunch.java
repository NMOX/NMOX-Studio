package org.nmox.studio.tools.vscode;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nmox.studio.core.util.BoundedReads;
import org.nmox.studio.tools.vscode.VsCodeTasks.Os;

/**
 * A project's {@code .vscode/launch.json}, read and resolved (v3.1.0): the
 * pure half behind "Debug: …" in Quick Search, sibling of {@link
 * VsCodeTasks} and sharing its JSONC stripper, its per-OS override merge,
 * its variable rules and its containment question. Nothing here spawns; it
 * answers "which configurations does this file declare" and, for one,
 * "what exactly would the debugger start — or why not".
 *
 * <p><b>What the debugger can honour, measured.</b> The IDE's breakpoint
 * debugger ({@code core.spi.DebugLauncher}, published by the editor) starts
 * three kinds of session, and each takes only what this class passes on:
 * a Node program ({@code pwa-node} launch of one file, with a working
 * directory), a Python program ({@code debugpy} launch of one file, with a
 * working directory), and a page in a Chromium-family browser ({@code
 * pwa-chrome} launch of a URL, with a web root). Nothing else crosses: no
 * arguments, no environment, no runtime, no pre-launch task. So a
 * configuration maps only when every field it sets is one of those, or one
 * that shapes the debugger's VIEW and never what runs ({@link #VIEW_ONLY}).
 *
 * <p><b>What is refused, out loud.</b> Every other field — {@code args},
 * {@code env}, {@code envFile}, {@code runtimeExecutable}, {@code
 * runtimeArgs}, {@code preLaunchTask}, {@code port}, anything this class
 * has not been taught — is refused naming the field, because a program
 * started without its arguments or its environment is a different program
 * from the one the file describes. {@code "request": "attach"} is refused
 * (the debugger only launches), as is a {@code type} it has no adapter for
 * ({@code go}, {@code cppdbg}, {@code msedge}: Edge is not whichever
 * Chromium browser is installed), a compound (it starts several sessions at
 * once), a variable only VS Code can fill ({@code ${file}}, {@code
 * ${input:…}}), a path outside the project, a missing program, and a
 * program the chosen type does not run.
 *
 * <p><b>What it reads.</b> The file comes through {@link BoundedReads},
 * capped at {@link #MAX_BYTES}, cached by path + mtime + size; a file that
 * does not parse lists nothing and logs once per version at INFO.
 */
public final class VsCodeLaunch {

    private static final Logger LOG = Logger.getLogger(VsCodeLaunch.class.getName());

    /** An honest launch.json is kilobytes; a megabyte can only be a mistake or malice. */
    static final long MAX_BYTES = 1024L * 1024;

    /** Where VS Code keeps a folder's debug configurations, relative to the folder. */
    static final String RELATIVE_PATH = ".vscode/launch.json";

    /** Keys every configuration carries that say what it IS rather than what it runs. */
    static final Set<String> STRUCTURAL = Set.of("type", "request", "name", "osx", "linux", "windows");

    /**
     * Keys that shape what the debugger SHOWS — which frames it skips,
     * where it looks for source maps, which console the output goes to —
     * and never what runs. Accepted and not applied; the docs say so. A key
     * earns a place here only if a program run without it is the same
     * program.
     */
    static final Set<String> VIEW_ONLY = Set.of("presentation", "internalConsoleOptions", "console",
            "skipFiles", "smartStep", "showAsyncStacks", "sourceMaps", "outFiles", "trace",
            "justMyCode");

    /** The debugger's three kinds of session. */
    enum Kind {
        NODE, PYTHON, CHROME
    }

    /** The fields each kind passes on to its session. */
    private static final Map<Kind, Set<String>> HONOURED = Map.of(
            Kind.NODE, Set.of("program", "cwd"),
            Kind.PYTHON, Set.of("program", "cwd"),
            Kind.CHROME, Set.of("url", "file", "webRoot"));

    /** One configuration (or compound) as the file declares it, after the running OS's override. */
    record Config(String name, String type, String request, boolean compound,
            Map<String, String> strings, List<String> keys) {
    }

    /** What pressing Enter on a configuration would do. */
    sealed interface Resolved permits DebugFile, DebugPage, Refused {
    }

    /** Debug {@code program} with {@code cwd} as its working directory. */
    record DebugFile(Kind kind, File program, File cwd) implements Resolved {
    }

    /** Open {@code url} in a browser under the debugger, sources mapped from {@code webRoot}. */
    record DebugPage(String url, File webRoot) implements Resolved {
    }

    /** Why a configuration cannot start here; {@code detail} is what the sentence names. */
    record Refused(Reason reason, String detail) implements Resolved {
    }

    /** The refusals a configuration can meet, each rendered by the provider in the reader's language. */
    enum Reason {
        /** A {@code type} with no adapter here; detail = the type. */
        TYPE,
        /** Anything but {@code "request": "launch"}; detail = the request. */
        REQUEST,
        /** Fields the debugger cannot pass on; detail = their names, comma-separated. */
        FIELDS,
        /** A {@code ${…}} only VS Code can fill; detail = the variable as written. */
        VARIABLE,
        /** A compound; detail = blank. */
        COMPOUND,
        /** No program (or no url/file for a page); detail = blank. */
        NO_TARGET,
        /** A path outside the project; detail = the path as written. */
        OUTSIDE,
        /** A path inside the project that is not there; detail = the path as written. */
        MISSING,
        /** A program the chosen kind does not run; detail = the program as written. */
        PROGRAM_KIND,
        /** A page address that is not http, https or a project file; detail = the address. */
        URL
    }

    private VsCodeLaunch() {
    }

    /* ------------------------------------------------------------------ reading */

    private record Cached(long mtime, long size, List<Config> configs) {
    }

    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    /** The configurations of {@code project}'s launch.json for the running OS; empty when none. */
    static List<Config> read(File project) {
        return read(project, Os.current());
    }

    static List<Config> read(File project, Os os) {
        if (project == null) {
            return List.of();
        }
        File file = new File(project, RELATIVE_PATH);
        if (!file.isFile()) {
            return List.of();
        }
        long mtime = file.lastModified();
        long size = file.length();
        String key = file.getAbsolutePath() + "|" + os;
        Cached hit = CACHE.get(key);
        if (hit != null && hit.mtime() == mtime && hit.size() == size) {
            return hit.configs();
        }
        List<Config> configs;
        try {
            configs = parse(BoundedReads.read(file, MAX_BYTES), os);
        } catch (IOException | JSONException | IllegalArgumentException unreadable) {
            // cached as empty for this version, so the log line is written
            // once per version of the file rather than once per keystroke
            LOG.log(Level.INFO, "{0} lists no debug configurations: {1}",
                    new Object[] {file.getAbsolutePath(), unreadable.getMessage()});
            configs = List.of();
        }
        CACHE.put(key, new Cached(mtime, size, configs));
        return configs;
    }

    /** The configurations and compounds in {@code text}; throws when it is not a JSON object. */
    static List<Config> parse(String text, Os os) {
        JSONObject root = new JSONObject(VsCodeTasks.stripJsonc(text));
        List<Config> out = new ArrayList<>();
        JSONArray configurations = root.optJSONArray("configurations");
        if (configurations != null) {
            for (int i = 0; i < configurations.length(); i++) {
                JSONObject raw = configurations.optJSONObject(i);
                if (raw == null) {
                    continue;
                }
                JSONObject config = VsCodeTasks.mergeOs(raw, os);
                String name = config.optString("name", "").strip();
                if (name.isEmpty()) {
                    // VS Code lists a configuration by its name; without one
                    // nothing could list it, and a guessed name would be ours
                    continue;
                }
                Map<String, String> strings = new TreeMap<>();
                for (String k : config.keySet()) {
                    Object v = config.opt(k);
                    if (v instanceof String s) {
                        strings.put(k, s);
                    }
                }
                out.add(new Config(name, config.optString("type", "").strip().toLowerCase(Locale.ROOT),
                        config.optString("request", "").strip().toLowerCase(Locale.ROOT), false,
                        java.util.Collections.unmodifiableMap(strings),
                        List.copyOf(new TreeSet<>(config.keySet()))));
            }
        }
        JSONArray compounds = root.optJSONArray("compounds");
        if (compounds != null) {
            for (int i = 0; i < compounds.length(); i++) {
                JSONObject c = compounds.optJSONObject(i);
                String name = c == null ? "" : c.optString("name", "").strip();
                if (!name.isEmpty()) {
                    out.add(new Config(name, "", "", true, Map.of(), List.of()));
                }
            }
        }
        return List.copyOf(out);
    }

    /* ---------------------------------------------------------------- resolving */

    /** The kind a {@code type} maps to, or null when the debugger has no adapter for it. */
    static Kind kindOf(String type) {
        return switch (type) {
            case "node", "pwa-node" -> Kind.NODE;
            case "python", "debugpy" -> Kind.PYTHON;
            case "chrome", "pwa-chrome" -> Kind.CHROME;
            default -> null;
        };
    }

    /**
     * What Enter on {@code config} would do in {@code project}: the file or
     * page to debug, or the refusal. Pure but for filesystem questions
     * about the paths it names, so it belongs off the EDT. The order of the
     * checks is the order a reader would ask them: is this something the
     * debugger starts at all, does it set anything we would drop, does it
     * need a value only VS Code has, and only then where its paths lead.
     *
     * @param env the process environment for {@code ${env:NAME}} (a seam for tests)
     */
    static Resolved resolve(Config config, File project, UnaryOperator<String> env) {
        if (config.compound()) {
            return new Refused(Reason.COMPOUND, "");
        }
        if (!"launch".equals(config.request())) {
            return new Refused(Reason.REQUEST, config.request());
        }
        Kind kind = kindOf(config.type());
        if (kind == null) {
            return new Refused(Reason.TYPE, config.type().isEmpty() ? "?" : config.type());
        }
        Set<String> honoured = HONOURED.get(kind);
        List<String> dropped = new ArrayList<>();
        for (String key : config.keys()) {
            if (!STRUCTURAL.contains(key) && !VIEW_ONLY.contains(key) && !honoured.contains(key)) {
                dropped.add(key);
            }
        }
        for (String key : honoured) {
            // an honoured field that is not a string (a number, an array)
            // cannot be passed on as written either
            if (config.keys().contains(key) && !config.strings().containsKey(key) && !dropped.contains(key)) {
                dropped.add(key);
            }
        }
        if (kind == Kind.CHROME && config.strings().containsKey("url") && config.strings().containsKey("file")) {
            // VS Code opens one page; with both named, which one it opens is
            // not something this file says plainly enough to guess
            dropped.add("file");
        }
        if (!dropped.isEmpty()) {
            dropped.sort(null);
            return new Refused(Reason.FIELDS, String.join(", ", dropped));
        }
        for (String key : new TreeSet<>(honoured)) {
            String value = config.strings().get(key);
            if (value != null) {
                String unknown = VsCodeTasks.unsupportedVariable(value);
                if (unknown != null) {
                    return new Refused(Reason.VARIABLE, unknown);
                }
            }
        }
        UnaryOperator<String> sub = s -> VsCodeTasks.substitute(s, project, env);
        return kind == Kind.CHROME ? page(config, project, sub) : program(kind, config, project, sub);
    }

    private static Resolved program(Kind kind, Config config, File project, UnaryOperator<String> sub) {
        String written = config.strings().get("program");
        if (written == null || written.isBlank()) {
            return new Refused(Reason.NO_TARGET, "");
        }
        File program = VsCodeTasks.inside(project, sub.apply(written));
        if (program == null) {
            return new Refused(Reason.OUTSIDE, written);
        }
        if (!program.isFile()) {
            return new Refused(Reason.MISSING, written);
        }
        if (!runs(kind, program.getName())) {
            return new Refused(Reason.PROGRAM_KIND, written);
        }
        // VS Code's own default for both adapters is ${workspaceFolder}
        Object cwd = folder(project, config.strings().get("cwd"), sub);
        if (cwd instanceof Refused r) {
            return r;
        }
        return new DebugFile(kind, program, (File) cwd);
    }

    private static Resolved page(Config config, File project, UnaryOperator<String> sub) {
        Object webRoot = folder(project, config.strings().get("webRoot"), sub);
        if (webRoot instanceof Refused r) {
            return r;
        }
        String url = config.strings().get("url");
        String file = config.strings().get("file");
        if ((url == null || url.isBlank()) && (file == null || file.isBlank())) {
            return new Refused(Reason.NO_TARGET, "");
        }
        if (file != null && !file.isBlank()) {
            File page = VsCodeTasks.inside(project, sub.apply(file));
            if (page == null) {
                return new Refused(Reason.OUTSIDE, file);
            }
            if (!page.isFile()) {
                return new Refused(Reason.MISSING, file);
            }
            return new DebugPage(page.toURI().toString(), (File) webRoot);
        }
        String address = sub.apply(url).strip();
        URI uri;
        try {
            uri = new URI(address);
        } catch (URISyntaxException bad) {
            return new Refused(Reason.URL, url);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        switch (scheme) {
            case "http", "https" -> {
                if (uri.getHost() == null || uri.getHost().isBlank()) {
                    return new Refused(Reason.URL, url);
                }
                return new DebugPage(address, (File) webRoot);
            }
            case "file" -> {
                // a file URL is a path, and a path is judged by containment
                File page;
                try {
                    page = VsCodeTasks.inside(project, new File(uri).getPath());
                } catch (IllegalArgumentException notAFile) {
                    return new Refused(Reason.URL, url);
                }
                if (page == null) {
                    return new Refused(Reason.OUTSIDE, url);
                }
                if (!page.isFile()) {
                    return new Refused(Reason.MISSING, url);
                }
                return new DebugPage(page.toURI().toString(), (File) webRoot);
            }
            default -> {
                return new Refused(Reason.URL, url);
            }
        }
    }

    /** A folder field ({@code cwd}, {@code webRoot}): blank means the project, VS Code's default. */
    private static Object folder(File project, String written, UnaryOperator<String> sub) {
        if (written == null || written.isBlank()) {
            return project;
        }
        File dir = VsCodeTasks.inside(project, sub.apply(written));
        if (dir == null) {
            return new Refused(Reason.OUTSIDE, written);
        }
        if (!dir.isDirectory()) {
            return new Refused(Reason.MISSING, written);
        }
        return dir;
    }

    /** Whether {@code kind}'s adapter runs a file of this name — the editor's own MIME table, by extension. */
    static boolean runs(Kind kind, String fileName) {
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (kind) {
            case NODE -> Set.of("js", "mjs", "cjs", "ts", "mts", "cts").contains(ext);
            case PYTHON -> "py".equals(ext);
            case CHROME -> false;
        };
    }

    /** What the search row shows after the name: the program or page as the file wrote it. */
    static String display(Config config) {
        if (config.compound()) {
            return "";
        }
        for (String key : new String[] {"program", "url", "file"}) {
            String v = config.strings().get(key);
            if (v != null && !v.isBlank()) {
                return v.strip();
            }
        }
        return "";
    }

    /** Test seam: forget every cached file. */
    static void clearCache() {
        CACHE.clear();
    }
}
