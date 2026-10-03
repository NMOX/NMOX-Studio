package org.nmox.studio.tools.vscode;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
import org.nmox.studio.core.spi.DebugLauncher;
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
 * four kinds of session, and each takes only what this class passes on:
 * <ul>
 *   <li>a Node program ({@code pwa-node} launch): its {@code program} in
 *       its {@code cwd}, with its {@code args}, its {@code env} and {@code
 *       envFile}, started by its {@code runtimeExecutable} with its {@code
 *       runtimeArgs} — the vendored js-debug takes both in its own launch
 *       request, and with a runtime named the {@code program} may be absent
 *       ({@code npm run dev} is a whole command);</li>
 *   <li>a Python program ({@code debugpy} launch): its {@code program} in
 *       its {@code cwd}, with {@code args}, {@code env} and {@code
 *       envFile}, run by the interpreter its {@code python} names;</li>
 *   <li>a Node process that is already running ({@code pwa-node} attach):
 *       its inspector {@code port} (9229 unless written) at a loopback
 *       {@code address}, with its {@code cwd};</li>
 *   <li>a page in a Chromium-family browser ({@code pwa-chrome} launch of a
 *       {@code url} or {@code file}, with a {@code webRoot}).</li>
 * </ul>
 * A configuration maps only when every field it sets is one of those, or
 * one that shapes the debugger's VIEW and never what runs ({@link
 * #VIEW_ONLY}).
 *
 * <p><b>The environment.</b> {@code envFile} is read here ({@link
 * VsCodeEnvFile}: bounded, inside the project, plain lines only) and its
 * variables are added first, then {@code env}'s over them — VS Code's
 * rule, an explicit {@code env} entry wins. VS Code quietly ignores an
 * {@code envFile} that is not there; this class refuses it by name,
 * because the configuration asked for variables and a program started
 * without them is not the one it describes. Values are never part of a
 * refusal or of a record's {@code toString}.
 *
 * <p><b>The runtime.</b> A {@code runtimeExecutable} (or {@code python})
 * written without a path separator is a NAME, passed on as it is: the
 * adapter looks it up on the PATH and then in the project's {@code
 * node_modules/.bin}, as it does under VS Code. Written as an absolute
 * path ({@code ${workspaceFolder}/node_modules/.bin/tsx}) it must be a
 * file that is there. A relative path is refused: VS Code looks such a
 * thing up as a name, from a folder that is not the project.
 *
 * <p><b>The file being looked at.</b> {@code ${file}} and its siblings
 * ({@link VsCodeEditorVariables}) are filled from the file the caller says
 * the editor shows, in every value that is substituted at all; with no
 * file open the configuration is refused naming the variable. A {@code
 * program} they name is held to the same rules as one written out.
 *
 * <p><b>What is refused, out loud.</b> Every other field — {@code
 * preLaunchTask}, {@code postDebugTask}, {@code restart}, {@code
 * processId}, anything this class has not been taught — is refused naming
 * the field, because a program started without them is a different program
 * from the one the file describes; so are an {@code args} or {@code
 * runtimeArgs} that is not a list of strings (VS Code's one-string form is
 * split by a shell this debugger does not run), an {@code env} with a
 * value that is not a string (a {@code null} there unsets a variable,
 * which cannot be passed on) and a {@code port} that is not a port. An
 * attach to an address that is not this machine is refused (this is not
 * remote development), as is an attach of any type but Node, a {@code
 * type} with no adapter ({@code go}, {@code cppdbg}, {@code msedge}: Edge
 * is not whichever Chromium browser is installed), a compound (it starts
 * several sessions at once), a variable only VS Code can fill ({@code
 * ${input:…}}, {@code ${command:…}}), a path outside the project, a
 * missing program, and a program the chosen type does not run.
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

    /** The debugger's kinds of adapter. */
    enum Kind {
        NODE, PYTHON, CHROME
    }

    /** The field a pre-launch task is named in; honoured only when the caller says it runs tasks. */
    static final String PRE_LAUNCH_TASK = "preLaunchTask";

    /** The port Node's inspector opens when {@code --inspect} names none, and VS Code's default. */
    static final int DEFAULT_INSPECT_PORT = 9229;


    private static final Set<String> NODE_LAUNCH = Set.of("program", "cwd", "args", "env", "envFile",
            "runtimeExecutable", "runtimeArgs");
    private static final Set<String> PYTHON_LAUNCH = Set.of("program", "cwd", "args", "env", "envFile",
            "python");
    private static final Set<String> NODE_ATTACH = Set.of("port", "address", "cwd");
    private static final Set<String> CHROME_LAUNCH = Set.of("url", "file", "webRoot");

    /** The fields a session of {@code kind} passes on. */
    private static Set<String> honoured(Kind kind, boolean attach) {
        return switch (kind) {
            case NODE -> attach ? NODE_ATTACH : NODE_LAUNCH;
            case PYTHON -> PYTHON_LAUNCH;
            case CHROME -> CHROME_LAUNCH;
        };
    }

    /**
     * One configuration (or compound) as the file declares it, after the
     * running OS's override. {@code strings} holds every field written as a
     * string — and {@code port} written as a whole number, as its digits,
     * since VS Code takes either. {@code args}, {@code env} and {@code
     * runtimeArgs} are null when written in a shape that cannot be passed on.
     */
    record Config(String name, String type, String request, boolean compound,
            Map<String, String> strings, List<String> keys, List<String> args, Map<String, String> env,
            List<String> runtimeArgs) {

        /** A configuration with no {@code runtimeArgs}. */
        Config(String name, String type, String request, boolean compound,
                Map<String, String> strings, List<String> keys, List<String> args, Map<String, String> env) {
            this(name, type, request, compound, strings, keys, args, env, List.of());
        }

        /** A configuration with no {@code args}, {@code env} or {@code runtimeArgs}. */
        Config(String name, String type, String request, boolean compound,
                Map<String, String> strings, List<String> keys) {
            this(name, type, request, compound, strings, keys, List.of(), Map.of(), List.of());
        }
    }

    /** What pressing Enter on a configuration would do. */
    sealed interface Resolved permits DebugFile, AttachNode, DebugPage, Refused {
    }

    /**
     * Debug {@code program} with {@code cwd} as its working directory,
     * {@code args} and {@code env} added, started by {@code runtime} with
     * {@code runtimeArgs}. {@code runtime} is null for the adapter's own
     * (node, the adapter's Python); {@code program} is null only for a Node
     * launch whose runtime is the whole command. {@code env} already holds
     * the {@code envFile}'s variables under the explicit ones.
     */
    record DebugFile(Kind kind, File program, File cwd, List<String> args, Map<String, String> env,
            String runtime, List<String> runtimeArgs) implements Resolved {

        DebugFile(Kind kind, File program, File cwd, List<String> args, Map<String, String> env) {
            this(kind, program, cwd, args, env, null, List.of());
        }

        DebugFile(Kind kind, File program, File cwd) {
            this(kind, program, cwd, List.of(), Map.of(), null, List.of());
        }

        /** The variables' names, never their values: an env file's values are secrets. */
        @Override
        public String toString() {
            return "DebugFile[" + kind + ", program=" + program + ", cwd=" + cwd + ", args=" + args
                    + ", env=" + env.keySet() + ", runtime=" + runtime + ", runtimeArgs=" + runtimeArgs + "]";
        }
    }

    /** Attach to the Node inspector listening at {@code address}:{@code port} on this machine. */
    record AttachNode(String address, int port, File cwd) implements Resolved {
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
        /** A request that is neither launch nor a Node attach; detail = the request. */
        REQUEST,
        /** Fields the debugger cannot pass on; detail = their names, comma-separated. */
        FIELDS,
        /** A {@code ${…}} only VS Code can fill; detail = the variable as written. */
        VARIABLE,
        /** A {@code ${file}}-family variable with no file open in the editor; detail = the variable as written. */
        NO_FILE,
        /** A compound; detail = blank. */
        COMPOUND,
        /** No program (or no url/file for a page); detail = blank. */
        NO_TARGET,
        /** A path outside the project; detail = the path as written. */
        OUTSIDE,
        /** A path that is not there; detail = the path as written. */
        MISSING,
        /** An env file too large or not readable; detail = the path as written. */
        UNREADABLE,
        /** An env file with a line VS Code would read differently; detail = {@code path:line}, never a value. */
        ENV_LINE,
        /** A program the chosen kind does not run; detail = the program as written. */
        PROGRAM_KIND,
        /** An attach to an address that is not this machine; detail = the address as written. */
        ADDRESS,
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
                    } else if ("port".equals(k) && v instanceof Integer whole) {
                        // VS Code writes a port as a number and accepts a
                        // string; one spelling from here on
                        strings.put(k, Integer.toString(whole));
                    }
                }
                out.add(new Config(name, config.optString("type", "").strip().toLowerCase(Locale.ROOT),
                        config.optString("request", "").strip().toLowerCase(Locale.ROOT), false,
                        Collections.unmodifiableMap(strings),
                        List.copyOf(new TreeSet<>(config.keySet())),
                        argsOf(config.opt("args")), envOf(config.opt("env")),
                        argsOf(config.opt("runtimeArgs"))));
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

    /**
     * {@code args} (or {@code runtimeArgs}) as a list of strings: empty
     * when absent, null when it is anything else (VS Code's one-string form
     * among them), so the resolver can refuse it by name.
     */
    static List<String> argsOf(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof JSONArray array)) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            if (!(array.opt(i) instanceof String s)) {
                return null;
            }
            out.add(s);
        }
        return List.copyOf(out);
    }

    /**
     * {@code env} as names and string values: empty when absent, null when
     * it is not an object of strings (a {@code null} value unsets a
     * variable in VS Code, which cannot be passed on).
     */
    static Map<String, String> envOf(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof JSONObject object)) {
            return null;
        }
        Map<String, String> out = new TreeMap<>();
        for (String k : object.keySet()) {
            if (!(object.opt(k) instanceof String s) || k.isBlank()) {
                return null;
            }
            out.put(k, s);
        }
        return Collections.unmodifiableMap(out);
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

    /** {@link #resolve(Config, File, UnaryOperator, Path)} with no file open in the editor. */
    static Resolved resolve(Config config, File project, UnaryOperator<String> env) {
        return resolve(config, project, env, null);
    }

    /** {@link #resolve(Config, File, UnaryOperator, Path, boolean)} for a caller that runs no tasks. */
    static Resolved resolve(Config config, File project, UnaryOperator<String> env, Path editorFile) {
        return resolve(config, project, env, editorFile, false);
    }

    /**
     * What Enter on {@code config} would do in {@code project}: the file,
     * process or page to debug, or the refusal. Pure but for filesystem
     * questions about the paths it names (and the one bounded read of an
     * {@code envFile}), so it belongs off the EDT. The order of the checks
     * is the order a reader would ask them: is this something the debugger
     * starts at all, does it set anything we would drop, does it need a
     * value nobody here has, and only then where its paths lead.
     *
     * @param env        the process environment for {@code ${env:NAME}} (a seam for tests)
     * @param editorFile the file the editor shows, for {@code ${file}} and
     *                   its siblings; null when none is open
     * @param tasksRun   whether the CALLER runs a configuration's {@link
     *                   #preLaunchTask} before it hands the result on. False
     *                   everywhere today, so the field is refused by name
     *                   like any other this debugger cannot honour; a caller
     *                   that passes true has taken the task on itself, and a
     *                   label is then all this class asks of the field
     */
    static Resolved resolve(Config config, File project, UnaryOperator<String> env, Path editorFile,
            boolean tasksRun) {
        if (config.compound()) {
            return new Refused(Reason.COMPOUND, "");
        }
        boolean attach = "attach".equals(config.request());
        if (!attach && !"launch".equals(config.request())) {
            return new Refused(Reason.REQUEST, config.request());
        }
        Kind kind = kindOf(config.type());
        if (kind == null) {
            return new Refused(Reason.TYPE, config.type().isEmpty() ? "?" : config.type());
        }
        if (attach && kind != Kind.NODE) {
            // the Python and Chrome adapters are only ever launched here
            return new Refused(Reason.REQUEST, config.request());
        }
        Set<String> honoured = honoured(kind, attach);
        List<String> dropped = new ArrayList<>();
        for (String key : config.keys()) {
            boolean task = tasksRun && PRE_LAUNCH_TASK.equals(key);
            if (!STRUCTURAL.contains(key) && !VIEW_ONLY.contains(key) && !honoured.contains(key) && !task) {
                dropped.add(key);
            }
        }
        for (String key : honoured) {
            // an honoured field that is not a string (a number, an array)
            // cannot be passed on as written either; args, env and
            // runtimeArgs have their own shapes, read at parse time
            boolean malformed = switch (key) {
                case "args" -> config.args() == null;
                case "env" -> config.env() == null;
                case "runtimeArgs" -> config.runtimeArgs() == null;
                default -> config.keys().contains(key) && !config.strings().containsKey(key);
            };
            if (malformed && !dropped.contains(key)) {
                dropped.add(key);
            }
        }
        if (tasksRun && config.keys().contains(PRE_LAUNCH_TASK) && preLaunchTask(config) == null) {
            // VS Code's object form names a task by type and script; only a label is a label
            dropped.add(PRE_LAUNCH_TASK);
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
        // every value below is one of an honoured field: a field of another
        // kind was refused above, so the three lists are empty unless honoured
        List<String> written = new ArrayList<>();
        for (String key : new TreeSet<>(honoured)) {
            String value = config.strings().get(key);
            if (value != null) {
                written.add(value);
            }
        }
        written.addAll(config.args());
        written.addAll(config.env().values());
        written.addAll(config.runtimeArgs());
        for (String value : written) {
            String unknown = VsCodeEditorVariables.unsupported(value);
            if (unknown != null) {
                return new Refused(Reason.VARIABLE, unknown);
            }
        }
        if (editorFile == null) {
            for (String value : written) {
                String needsFile = VsCodeEditorVariables.first(value);
                if (needsFile != null) {
                    return new Refused(Reason.NO_FILE, needsFile);
                }
            }
        }
        UnaryOperator<String> sub = s -> VsCodeEditorVariables.substitute(s, project, env, editorFile);
        if (attach) {
            return attach(config, project, sub);
        }
        return kind == Kind.CHROME ? page(config, project, sub) : program(kind, config, project, sub);
    }

    /**
     * The label of the task {@code config} wants run before it starts, as
     * written, or null when it names none (or names one in VS Code's object
     * form). The seam for a caller that runs tasks: resolve with {@code
     * tasksRun} true, run this, then hand the result on.
     */
    static String preLaunchTask(Config config) {
        String label = config.strings().get(PRE_LAUNCH_TASK);
        return label == null || label.isBlank() ? null : label;
    }

    private static Resolved program(Kind kind, Config config, File project, UnaryOperator<String> sub) {
        String runtimeField = kind == Kind.NODE ? "runtimeExecutable" : "python";
        Object runtime = runtime(config.strings().get(runtimeField), runtimeField, sub);
        if (runtime instanceof Refused r) {
            return r;
        }
        String written = config.strings().get("program");
        File program = null;
        if (written == null || written.isBlank()) {
            // a Node runtime can be the whole command (npm run dev);
            // nothing else can stand without a program
            if (kind != Kind.NODE || runtime == null) {
                return new Refused(Reason.NO_TARGET, "");
            }
        } else {
            program = VsCodeTasks.inside(project, sub.apply(written));
            if (program == null) {
                return new Refused(Reason.OUTSIDE, written);
            }
            if (!program.isFile()) {
                return new Refused(Reason.MISSING, written);
            }
            if (!runs(kind, program.getName())) {
                return new Refused(Reason.PROGRAM_KIND, written);
            }
        }
        // VS Code's own default for both adapters is ${workspaceFolder}
        Object cwd = folder(project, config.strings().get("cwd"), sub);
        if (cwd instanceof Refused r) {
            return r;
        }
        Object env = environment(kind, config, project, sub);
        if (env instanceof Refused r) {
            return r;
        }
        List<String> args = config.args().stream().map(sub).toList();
        List<String> runtimeArgs = config.runtimeArgs().stream().map(sub).toList();
        @SuppressWarnings("unchecked")
        Map<String, String> variables = (Map<String, String>) env;
        return new DebugFile(kind, program, (File) cwd, args, variables, (String) runtime, runtimeArgs);
    }

    /**
     * The runtime a field names: null when the field is absent, the name or
     * absolute path to pass on, or a {@link Refused}. See the class comment
     * for why a relative path is refused.
     */
    private static Object runtime(String written, String field, UnaryOperator<String> sub) {
        if (written == null) {
            return null;
        }
        String value = sub.apply(written).strip();
        if (value.isEmpty()) {
            return new Refused(Reason.FIELDS, field);
        }
        if (value.indexOf('/') < 0 && value.indexOf('\\') < 0) {
            return value;
        }
        Path path;
        try {
            path = Path.of(value);
        } catch (InvalidPathException bad) {
            return new Refused(Reason.FIELDS, field);
        }
        if (!path.isAbsolute()) {
            return new Refused(Reason.FIELDS, field);
        }
        File file = path.normalize().toFile();
        if (!executableThere(file, Os.current())) {
            return new Refused(Reason.MISSING, written);
        }
        return file.getPath();
    }

    /** The extensions Windows adds to a program named without one. */
    private static final List<String> WINDOWS_EXTENSIONS = List.of(".exe", ".cmd", ".bat", ".com");

    /**
     * Whether {@code file} names a program that is there. On Windows a
     * program is routinely named without its extension ({@code …\node}
     * for {@code node.exe}) and the adapter tries the usual ones, so this
     * does too rather than refuse what would have started.
     */
    static boolean executableThere(File file, Os os) {
        if (file.isFile()) {
            return true;
        }
        if (os != Os.WINDOWS || file.getName().contains(".")) {
            return false;
        }
        for (String ext : WINDOWS_EXTENSIONS) {
            if (new File(file.getPath() + ext).isFile()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The variables a launch adds to the inherited environment: the {@code
     * envFile}'s first, the explicit {@code env} over them — or a {@link
     * Refused} naming the file (and, for a line VS Code would read
     * differently, its number; never a value).
     */
    private static Object environment(Kind kind, Config config, File project, UnaryOperator<String> sub) {
        Map<String, String> merged = new TreeMap<>();
        String written = config.strings().get("envFile");
        if (written != null) {
            if (written.isBlank()) {
                return new Refused(Reason.FIELDS, "envFile");
            }
            File file = VsCodeTasks.inside(project, sub.apply(written));
            if (file == null) {
                return new Refused(Reason.OUTSIDE, written);
            }
            if (!file.isFile()) {
                return new Refused(Reason.MISSING, written);
            }
            VsCodeEnvFile.Result read = VsCodeEnvFile.read(file, kind == Kind.PYTHON);
            if (read instanceof VsCodeEnvFile.Unplain unplain) {
                return new Refused(Reason.ENV_LINE, written + ":" + unplain.line());
            }
            if (!(read instanceof VsCodeEnvFile.Loaded loaded)) {
                return new Refused(Reason.UNREADABLE, written);
            }
            merged.putAll(loaded.vars());
        }
        // VS Code's rule: what the configuration says outright wins over the file
        config.env().forEach((k, v) -> merged.put(k, sub.apply(v)));
        return Collections.unmodifiableMap(merged);
    }

    private static Resolved attach(Config config, File project, UnaryOperator<String> sub) {
        String address = "localhost";
        String writtenAddress = config.strings().get("address");
        if (writtenAddress != null) {
            address = sub.apply(writtenAddress).strip();
            if (address.isEmpty()) {
                return new Refused(Reason.FIELDS, "address");
            }
            if (!DebugLauncher.isLoopback(address)) {
                return new Refused(Reason.ADDRESS, writtenAddress);
            }
        }
        int port = DEFAULT_INSPECT_PORT;
        String writtenPort = config.strings().get("port");
        if (writtenPort != null) {
            port = portOf(sub.apply(writtenPort).strip());
            if (port < 0) {
                return new Refused(Reason.FIELDS, "port");
            }
        }
        Object cwd = folder(project, config.strings().get("cwd"), sub);
        if (cwd instanceof Refused r) {
            return r;
        }
        return new AttachNode(address, port, (File) cwd);
    }

    /** {@code text} as a TCP port, or -1 when it is not one. */
    static int portOf(String text) {
        if (text.isEmpty() || text.length() > 5 || !text.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return -1;
        }
        int port = Integer.parseInt(text);
        return port >= 1 && port <= 65535 ? port : -1;
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

    /**
     * What the search row shows after the name, as the file wrote it: the
     * program or page; for an attach, the address and port; for a launch
     * whose runtime is the whole command, that command.
     */
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
        if ("attach".equals(config.request())) {
            return config.strings().getOrDefault("address", "localhost").strip() + ":"
                    + config.strings().getOrDefault("port", Integer.toString(DEFAULT_INSPECT_PORT)).strip();
        }
        String runtime = config.strings().get("runtimeExecutable");
        if (runtime != null && !runtime.isBlank()) {
            List<String> runtimeArgs = config.runtimeArgs();
            return runtimeArgs == null || runtimeArgs.isEmpty()
                    ? runtime.strip()
                    : runtime.strip() + " " + String.join(" ", runtimeArgs);
        }
        return "";
    }

    /** Test seam: forget every cached file. */
    static void clearCache() {
        CACHE.clear();
    }
}
