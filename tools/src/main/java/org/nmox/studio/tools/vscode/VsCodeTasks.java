package org.nmox.studio.tools.vscode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nmox.studio.core.util.BoundedReads;
import org.nmox.studio.core.util.Containment;

/**
 * A project's {@code .vscode/tasks.json}, read and resolved (v3.1.0): the
 * pure half behind "Run task: …" in Quick Search. Nothing here spawns; it
 * answers "which tasks does this file declare" and, for one task, "what
 * exactly would run, where, with which environment — or why not".
 *
 * <p><b>What is honoured.</b> The {@code 2.0.0} schema's {@code tasks[]}:
 * {@code label} (an entry without one is skipped — nothing could name it),
 * {@code type} {@code shell} / {@code process} / {@code npm}, {@code
 * command} and {@code args} (a string, or VS Code's {@code {value,
 * quoting}} object), {@code options.cwd} and {@code options.env}, the
 * file-level {@code options} as defaults, and the {@code osx} / {@code
 * linux} / {@code windows} override objects merged over the base — only
 * the running OS's. {@code group} and {@code isBackground} are read for
 * ranking and labelling only.
 *
 * <p><b>What is refused, out loud.</b> VS Code can supply values this IDE
 * cannot: {@code ${input:…}} asks the user through a VS Code prompt,
 * {@code ${file}} means VS Code's active editor, {@code ${config:…}} its
 * settings, {@code ${command:…}} an extension's command. A task using any
 * of them is listed and, on Enter, refused naming the variable — running
 * it with the variable blank would run a DIFFERENT command than the file
 * says. {@code dependsOn} likewise: running the task without the task it
 * depends on would run it in a state its author never tested, so it is
 * refused naming the dependency rather than silently skipped. A task type
 * contributed by an extension ({@code gulp}, {@code typescript}, …) is
 * refused naming the type. A working folder outside the project is refused
 * by {@link Containment}, the one home of that decision.
 *
 * <p><b>What it reads.</b> The file comes through {@link BoundedReads}
 * (a clone brings it and a keystroke in Quick Search reads it), capped at
 * {@link #MAX_BYTES}, cached by path + mtime + size. tasks.json is JSONC:
 * comments and trailing commas are stripped (outside strings) before
 * org.json parses it. A file that does not parse lists nothing and logs
 * once per mtime at INFO, because silence in a search list is correct but
 * a user asking "why are my tasks missing" deserves an answer somewhere.
 */
public final class VsCodeTasks {

    private static final Logger LOG = Logger.getLogger(VsCodeTasks.class.getName());

    /** An honest tasks.json is kilobytes; a megabyte can only be a mistake or malice. */
    static final long MAX_BYTES = 1024L * 1024;

    /** Where VS Code keeps a folder's tasks, relative to the folder. */
    static final String RELATIVE_PATH = ".vscode/tasks.json";

    /** {@code ${…}}: the variable syntax. The body is everything up to the first '}'. */
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([^}]*)\\}");

    private VsCodeTasks() {
    }

    /** The three OS keys VS Code's schema knows. */
    enum Os {
        WINDOWS("windows"), MAC("osx"), LINUX("linux");

        final String key;

        Os(String key) {
            this.key = key;
        }

        static Os current() {
            if (org.openide.util.BaseUtilities.isWindows()) {
                return WINDOWS;
            }
            return org.openide.util.BaseUtilities.isMac() ? MAC : LINUX;
        }
    }

    /** One {@code command} or {@code args} entry: its text and its declared quoting, or null. */
    record Value(String text, String quoting) {
    }

    /** One task as the file declares it, after the running OS's override was merged. */
    record TaskDef(String label, String type, Value command, List<Value> args,
            String cwd, Map<String, String> env, String script, String path,
            List<String> dependsOn, String group, boolean background) {
    }

    /** What pressing Enter on a task would do. */
    sealed interface Resolved permits Launch, NpmLaunch, Refused {
    }

    /** Spawn {@code argv} in {@code dir} with {@code env} added. */
    record Launch(List<String> argv, File dir, Map<String, String> env) implements Resolved {
    }

    /** Hand {@code script} to the NPM Service lane in {@code dir}. */
    record NpmLaunch(File dir, String script) implements Resolved {
    }

    /** Why a task cannot run here; {@code detail} is what the sentence names. */
    record Refused(Reason reason, String detail) implements Resolved {
    }

    /** The refusals a task can meet, each rendered by the provider in the reader's language. */
    enum Reason {
        /** A {@code ${…}} only VS Code can fill; detail = the variable as written. */
        VARIABLE,
        /** {@code dependsOn}; detail = the dependency labels. */
        DEPENDS_ON,
        /** {@code options.cwd} escapes the project; detail = the folder as written. */
        CWD_OUTSIDE,
        /** {@code options.cwd} names nothing on disk; detail = the folder as written. */
        CWD_MISSING,
        /** An extension-contributed task type; detail = the type. */
        TYPE,
        /** No command (or no npm script) to run; detail = blank. */
        NO_COMMAND
    }

    /* ------------------------------------------------------------------ reading */

    private record Cached(long mtime, long size, List<TaskDef> tasks) {
    }

    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    /**
     * The tasks of {@code project}'s {@code .vscode/tasks.json} for the
     * running OS, or an empty list when there is none or it cannot be read.
     */
    static List<TaskDef> read(File project) {
        return read(project, Os.current());
    }

    static List<TaskDef> read(File project, Os os) {
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
            return hit.tasks();
        }
        List<TaskDef> tasks;
        try {
            tasks = parse(BoundedReads.read(file, MAX_BYTES), os);
        } catch (IOException | JSONException | IllegalArgumentException unreadable) {
            // cached as empty for this mtime, so the log line is written
            // once per version of the file rather than once per keystroke
            LOG.log(Level.INFO, "{0} lists no tasks: {1}",
                    new Object[] {file.getAbsolutePath(), unreadable.getMessage()});
            tasks = List.of();
        }
        CACHE.put(key, new Cached(mtime, size, tasks));
        return tasks;
    }

    /** The tasks in {@code text}; throws when it is not a JSON object once JSONC is stripped. */
    static List<TaskDef> parse(String text, Os os) {
        JSONObject root = new JSONObject(stripJsonc(text));
        JSONObject globalOptions = mergeOptions(root.optJSONObject("options"),
                osBlock(root, os) == null ? null : osBlock(root, os).optJSONObject("options"));
        JSONArray array = root.optJSONArray("tasks");
        if (array == null) {
            return List.of();
        }
        List<TaskDef> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject raw = array.optJSONObject(i);
            if (raw == null) {
                continue;
            }
            JSONObject task = mergeOs(raw, os);
            String label = task.optString("label", "").strip();
            if (label.isEmpty()) {
                // VS Code 2.0.0 names a task by its label; without one nothing
                // could list it, and a guessed name would be ours, not theirs
                continue;
            }
            JSONObject options = mergeOptions(globalOptions, task.optJSONObject("options"));
            String type = task.optString("type", "process").strip();
            if (type.isEmpty()) {
                type = "process";
            }
            List<Value> args = new ArrayList<>();
            JSONArray rawArgs = task.optJSONArray("args");
            if (rawArgs != null) {
                for (int a = 0; a < rawArgs.length(); a++) {
                    Value v = value(rawArgs.opt(a));
                    if (v != null) {
                        args.add(v);
                    }
                }
            }
            out.add(new TaskDef(label, type.toLowerCase(Locale.ROOT), value(task.opt("command")),
                    List.copyOf(args),
                    options == null ? null : stringOrNull(options.opt("cwd")),
                    envOf(options),
                    stringOrNull(task.opt("script")),
                    stringOrNull(task.opt("path")),
                    dependsOn(task.opt("dependsOn")),
                    group(task.opt("group")),
                    task.optBoolean("isBackground", false)));
        }
        return List.copyOf(out);
    }

    private static JSONObject osBlock(JSONObject o, Os os) {
        return o.optJSONObject(os.key);
    }

    /** {@code task} with the running OS's override object laid over it (options merged one level deep). */
    static JSONObject mergeOs(JSONObject task, Os os) {
        JSONObject override = osBlock(task, os);
        if (override == null) {
            return task;
        }
        JSONObject merged = new JSONObject();
        for (String k : task.keySet()) {
            merged.put(k, task.get(k));
        }
        for (String k : override.keySet()) {
            if (!"options".equals(k)) {
                merged.put(k, override.get(k));
            }
        }
        JSONObject options = mergeOptions(task.optJSONObject("options"), override.optJSONObject("options"));
        if (options != null) {
            merged.put("options", options);
        }
        return merged;
    }

    /** {@code over} laid over {@code base}: cwd replaced, env merged key by key. */
    private static JSONObject mergeOptions(JSONObject base, JSONObject over) {
        if (base == null) {
            return over;
        }
        if (over == null) {
            return base;
        }
        JSONObject merged = new JSONObject();
        for (String k : base.keySet()) {
            merged.put(k, base.get(k));
        }
        for (String k : over.keySet()) {
            merged.put(k, over.get(k));
        }
        JSONObject env = new JSONObject();
        for (JSONObject side : new JSONObject[] {base.optJSONObject("env"), over.optJSONObject("env")}) {
            if (side != null) {
                for (String k : side.keySet()) {
                    env.put(k, side.get(k));
                }
            }
        }
        if (!env.isEmpty()) {
            merged.put("env", env);
        }
        return merged;
    }

    private static Map<String, String> envOf(JSONObject options) {
        JSONObject env = options == null ? null : options.optJSONObject("env");
        if (env == null) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String k : new java.util.TreeSet<>(env.keySet())) {
            Object v = env.opt(k);
            if (v != null && v != JSONObject.NULL) {
                out.put(k, String.valueOf(v));
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /** A string, or VS Code's {@code {value, quoting}} object; arrays of strings join with spaces. */
    private static Value value(Object o) {
        if (o == null || o == JSONObject.NULL) {
            return null;
        }
        if (o instanceof JSONObject obj) {
            Object v = obj.opt("value");
            String text;
            if (v instanceof JSONArray arr) {
                List<String> parts = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    parts.add(String.valueOf(arr.opt(i)));
                }
                text = String.join(" ", parts);
            } else if (v == null || v == JSONObject.NULL) {
                return null;
            } else {
                text = String.valueOf(v);
            }
            String quoting = obj.optString("quoting", "").strip();
            return new Value(text, quoting.isEmpty() ? null : quoting);
        }
        if (o instanceof JSONArray) {
            return null;
        }
        return new Value(String.valueOf(o), null);
    }

    private static String stringOrNull(Object o) {
        return o instanceof String s && !s.isBlank() ? s : null;
    }

    private static List<String> dependsOn(Object o) {
        if (o instanceof String s && !s.isBlank()) {
            return List.of(s.strip());
        }
        List<String> out = new ArrayList<>();
        if (o instanceof JSONArray arr) {
            for (int i = 0; i < arr.length(); i++) {
                Object d = arr.opt(i);
                if (d instanceof String s && !s.isBlank()) {
                    out.add(s.strip());
                } else if (d instanceof JSONObject obj) {
                    // the { "type": "npm", "script": "build" } dependency form
                    String named = obj.optString("label", obj.optString("script", "")).strip();
                    if (!named.isEmpty()) {
                        out.add(named);
                    }
                }
            }
        }
        return List.copyOf(out);
    }

    /** {@code "build"}, or the {@code kind} of {@code {"kind": "build", "isDefault": true}}. */
    private static String group(Object o) {
        if (o instanceof String s) {
            return s.strip();
        }
        if (o instanceof JSONObject obj) {
            return obj.optString("kind", "").strip();
        }
        return "";
    }

    /**
     * JSONC → JSON: {@code //} and {@code /* *}{@code /} comments and
     * trailing commas removed, everything inside a string untouched. A
     * comment's line break is kept so a parser's error names the real line.
     */
    static String stripJsonc(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '"') {
                int start = i++;
                while (i < n) {
                    char s = text.charAt(i);
                    if (s == '\\') {
                        i += 2;
                        continue;
                    }
                    i++;
                    if (s == '"') {
                        break;
                    }
                }
                out.append(text, start, Math.min(i, n));
                continue;
            }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                while (i < n && text.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                int stop = end < 0 ? n : end + 2;
                for (int k = i; k < stop; k++) {
                    if (text.charAt(k) == '\n') {
                        out.append('\n');
                    }
                }
                i = stop;
                continue;
            }
            if (c == '}' || c == ']') {
                // a comma before the closer, with only whitespace between
                int k = out.length() - 1;
                while (k >= 0 && Character.isWhitespace(out.charAt(k))) {
                    k--;
                }
                if (k >= 0 && out.charAt(k) == ',') {
                    out.deleteCharAt(k);
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /* ---------------------------------------------------------------- resolving */

    /**
     * What Enter on {@code task} would do in {@code project}: the argv, its
     * directory and environment, the npm hand-off, or the refusal. Pure but
     * for two filesystem questions about the working folder (does it stay
     * inside, does it exist), so it belongs off the EDT.
     *
     * @param env the process environment for {@code ${env:NAME}} (a seam for tests)
     */
    static Resolved resolve(TaskDef task, File project, Os os, UnaryOperator<String> env) {
        if (!task.dependsOn().isEmpty()) {
            return new Refused(Reason.DEPENDS_ON, String.join(", ", task.dependsOn()));
        }
        // every string the task would use, checked before anything is built
        List<String> used = new ArrayList<>();
        if (task.command() != null) {
            used.add(task.command().text());
        }
        task.args().forEach(a -> used.add(a.text()));
        if (task.cwd() != null) {
            used.add(task.cwd());
        }
        used.addAll(task.env().values());
        if (task.script() != null) {
            used.add(task.script());
        }
        if (task.path() != null) {
            used.add(task.path());
        }
        for (String s : used) {
            String unknown = unsupportedVariable(s);
            if (unknown != null) {
                return new Refused(Reason.VARIABLE, unknown);
            }
        }
        UnaryOperator<String> sub = s -> substitute(s, project, env);
        switch (task.type()) {
            case "npm" -> {
                if (task.script() == null) {
                    return new Refused(Reason.NO_COMMAND, "");
                }
                String folder = task.path() == null ? null : sub.apply(task.path());
                Object dir = workingDir(project, folder);
                if (dir instanceof Refused r) {
                    return r;
                }
                return new NpmLaunch((File) dir, sub.apply(task.script()));
            }
            case "shell", "process" -> {
                if (task.command() == null || task.command().text().isBlank()) {
                    return new Refused(Reason.NO_COMMAND, "");
                }
                Object dir = workingDir(project, task.cwd() == null ? null : sub.apply(task.cwd()));
                if (dir instanceof Refused r) {
                    return r;
                }
                Map<String, String> vars = new LinkedHashMap<>();
                task.env().forEach((k, v) -> vars.put(k, sub.apply(v)));
                String command = sub.apply(task.command().text());
                List<String> args = new ArrayList<>();
                for (Value a : task.args()) {
                    args.add(sub.apply(a.text()));
                }
                List<String> argv = "shell".equals(task.type())
                        ? shellArgv(os, command, args, task.args())
                        : processArgv(command, args);
                return new Launch(List.copyOf(argv), (File) dir, Collections.unmodifiableMap(vars));
            }
            default -> {
                return new Refused(Reason.TYPE, task.type());
            }
        }
    }

    /** The first {@code ${…}} in {@code s} this IDE cannot supply, as written, or null. */
    static String unsupportedVariable(String s) {
        Matcher m = VARIABLE.matcher(s);
        while (m.find()) {
            String name = m.group(1);
            if (!supported(name)) {
                return m.group();
            }
        }
        // an unterminated ${ is not a variable VS Code would fill either
        int open = s.indexOf("${");
        if (open >= 0 && s.indexOf('}', open) < 0) {
            return s.substring(open);
        }
        return null;
    }

    private static boolean supported(String name) {
        return switch (name) {
            case "workspaceFolder", "workspaceRoot", "workspaceFolderBasename", "cwd",
                    "pathSeparator", "/" -> true;
            default -> name.startsWith("env:") && name.length() > "env:".length();
        };
    }

    /**
     * Every supported variable replaced. {@code ${cwd}} is the project
     * folder: VS Code means "the directory VS Code started in", which for
     * a folder opened in it is that folder. {@code ${pathSeparator}} and
     * {@code ${/}} are the OS's file separator, as VS Code defines them.
     * An unset {@code ${env:NAME}} is the empty string, as in VS Code.
     */
    static String substitute(String s, File project, UnaryOperator<String> env) {
        Matcher m = VARIABLE.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String value = switch (name) {
                case "workspaceFolder", "workspaceRoot", "cwd" -> project.getAbsolutePath();
                case "workspaceFolderBasename" -> project.getName();
                case "pathSeparator", "/" -> File.separator;
                default -> {
                    String v = name.startsWith("env:") ? env.apply(name.substring(4)) : null;
                    yield v == null ? "" : v;
                }
            };
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * The directory {@code folder} (already substituted) names inside
     * {@code project}, or a {@link Refused}. Blank means the project
     * itself, VS Code's default. An absolute folder is judged by the one
     * containment guard after it is made relative to the project.
     */
    static Object workingDir(File project, String folder) {
        if (folder == null || folder.isBlank()) {
            return project;
        }
        File dir = inside(project, folder);
        if (dir == null) {
            return new Refused(Reason.CWD_OUTSIDE, folder);
        }
        if (!dir.isDirectory()) {
            return new Refused(Reason.CWD_MISSING, folder);
        }
        return dir;
    }

    /**
     * The file or folder {@code path} (already substituted, not blank) names
     * inside {@code project}, or null when it names somewhere else. A
     * relative path is judged by {@link Containment}, the one home of that
     * decision; an absolute one — what {@code ${workspaceFolder}/…} becomes
     * — is first made relative to the project, and one outside the project
     * is outside whatever it would resolve to. Shared with {@code
     * VsCodeLaunch}, whose {@code program}, {@code cwd} and {@code webRoot}
     * are the same question.
     */
    static File inside(File project, String path) {
        String relative = path;
        Path asPath;
        try {
            asPath = Path.of(path);
        } catch (java.nio.file.InvalidPathException bad) {
            return null;
        }
        if (asPath.isAbsolute()) {
            Path base = project.getAbsoluteFile().toPath().normalize();
            Path target = asPath.normalize();
            if (target.equals(base)) {
                return project;
            }
            if (!target.startsWith(base)) {
                return null;
            }
            relative = base.relativize(target).toString();
        }
        if (Path.of(relative).normalize().toString().isEmpty()) {
            return project;
        }
        return Containment.resolve(project, relative);
    }

    /** {@code process}: the program and its arguments, as they are. ToolLocator resolves the program at the spawn. */
    static List<String> processArgv(String command, List<String> args) {
        List<String> argv = new ArrayList<>();
        argv.add(command);
        argv.addAll(args);
        return argv;
    }

    /**
     * {@code shell}: the command line VS Code would hand the default shell.
     * The command is the user's shell text and is passed as written (it may
     * be {@code npm run build && echo done}); each argument is quoted for
     * the shell unless it is plainly safe, honouring a declared {@code
     * quoting} of {@code strong}, {@code weak} or {@code escape}.
     */
    static List<String> shellArgv(Os os, String command, List<String> args, List<Value> declared) {
        StringBuilder line = new StringBuilder(command);
        for (int i = 0; i < args.size(); i++) {
            String quoting = declared.get(i).quoting();
            line.append(' ').append(os == Os.WINDOWS
                    ? cmdQuote(args.get(i), quoting)
                    : shQuote(args.get(i), quoting));
        }
        return os == Os.WINDOWS
                ? List.of("cmd.exe", "/d", "/s", "/c", "\"" + line + "\"")
                : List.of("/bin/sh", "-c", line.toString());
    }

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_@%+=:,./-]+");

    static String shQuote(String arg, String quoting) {
        if ("escape".equals(quoting)) {
            StringBuilder out = new StringBuilder();
            for (char c : arg.toCharArray()) {
                if (!SAFE.matcher(String.valueOf(c)).matches()) {
                    out.append('\\');
                }
                out.append(c);
            }
            return out.toString();
        }
        if ("weak".equals(quoting)) {
            return "\"" + arg.replace("\\", "\\\\").replace("\"", "\\\"").replace("`", "\\`") + "\"";
        }
        if (!"strong".equals(quoting) && SAFE.matcher(arg).matches()) {
            return arg;
        }
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    static String cmdQuote(String arg, String quoting) {
        if (quoting == null && SAFE.matcher(arg).matches()) {
            return arg;
        }
        return "\"" + arg.replace("\"", "\"\"") + "\"";
    }

    /** The command as the file wrote it, one line: what the search row shows. */
    static String display(TaskDef task) {
        if ("npm".equals(task.type())) {
            return task.script() == null ? "npm" : "npm: " + task.script();
        }
        StringBuilder out = new StringBuilder(task.command() == null ? "" : task.command().text());
        for (Value a : task.args()) {
            out.append(' ').append(a.text());
        }
        return out.toString().strip();
    }

    /** Test seam: forget every cached file. */
    static void clearCache() {
        CACHE.clear();
    }
}
