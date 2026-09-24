package org.nmox.studio.tools.vscode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nmox.studio.core.process.ToolLocator;
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
 * <p><b>Which shell a {@code shell} task runs in</b> is VS Code's own
 * answer (read from its {@code terminalTaskSystem.ts} and terminal-profile
 * code, v3.1.0), because running the line in a different shell is running
 * a different command. With no {@code options.shell}: on macOS and Linux
 * the user's {@code $SHELL} (when it names an absolute, executable file —
 * else {@code /bin/sh}) with {@code -c}; on macOS a zsh, bash or fish also
 * gets {@code -l}, the login shell VS Code's default macOS profiles start.
 * On Windows, PowerShell ({@code pwsh} when installed, else Windows
 * PowerShell) with {@code -Command}, the profile loaded as VS Code loads
 * it. With {@code options.shell.executable}: that shell with exactly its
 * {@code args} — VS Code adds nothing then, so neither do we. With only
 * {@code options.shell.args}: the default shell with those arguments and
 * the default flag added if absent. See {@link #shellArgv}.
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

    /**
     * {@code options.shell} as the file declares it: either half may be
     * null (absent), {@code args} is null when the file gives none.
     */
    record ShellOpt(String executable, List<String> args) {
    }

    /** One task as the file declares it, after the running OS's override was merged. */
    record TaskDef(String label, String type, Value command, List<Value> args,
            String cwd, Map<String, String> env, String script, String path,
            List<String> dependsOn, String group, boolean background, ShellOpt shell) {

        /** A task with no {@code options.shell}. */
        TaskDef(String label, String type, Value command, List<Value> args,
                String cwd, Map<String, String> env, String script, String path,
                List<String> dependsOn, String group, boolean background) {
            this(label, type, command, args, cwd, env, script, path, dependsOn, group, background, null);
        }
    }

    /**
     * What resolving a task asks of the machine, as a seam so a test on any
     * OS can play any OS: which OS, the environment ({@code $SHELL},
     * {@code ${env:NAME}}), whether a file is an executable program, and
     * where a bare program name lives on the search path (null when it is
     * nowhere).
     */
    record Host(Os os, UnaryOperator<String> env, Predicate<File> executable,
            UnaryOperator<String> onPath) {

        /** This machine. */
        static Host system() {
            return of(Os.current(), System::getenv);
        }

        /** {@code os} and {@code env} as given, the filesystem and search path as they are. */
        static Host of(Os os, UnaryOperator<String> env) {
            return new Host(os, env, f -> f.isFile() && f.canExecute(), name -> {
                String found = ToolLocator.resolve(name);
                // resolve() hands the bare name back when it found nothing
                return found.equals(name) ? null : found;
            });
        }
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
        NO_COMMAND,
        /** {@code options.shell.executable} names no program found; detail = the shell as written. */
        SHELL_MISSING,
        /**
         * On Windows, a shell this IDE cannot hand a command line to
         * faithfully (anything but PowerShell with {@code -Command} or
         * cmd.exe with {@code /c}); detail = the shell as written.
         */
        SHELL_UNSUPPORTED
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
                    task.optBoolean("isBackground", false),
                    shellOf(options)));
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

    /** {@code options.shell}, or null when the options carry none. */
    private static ShellOpt shellOf(JSONObject options) {
        JSONObject shell = options == null ? null : options.optJSONObject("shell");
        if (shell == null) {
            return null;
        }
        String executable = stringOrNull(shell.opt("executable"));
        List<String> args = null;
        JSONArray raw = shell.optJSONArray("args");
        if (raw != null) {
            args = new ArrayList<>();
            for (int i = 0; i < raw.length(); i++) {
                Object a = raw.opt(i);
                if (a != null && a != JSONObject.NULL) {
                    args.add(String.valueOf(a));
                }
            }
            args = List.copyOf(args);
        }
        return executable == null && args == null ? null : new ShellOpt(executable, args);
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

    /** JSONC → JSON; one home since 3.1.0, {@link org.nmox.studio.core.util.Jsonc#strip}. */
    static String stripJsonc(String text) {
        return org.nmox.studio.core.util.Jsonc.strip(text);
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
        return resolve(task, project, Host.of(os, env));
    }

    /** {@link #resolve(TaskDef, File, Os, UnaryOperator)} against a whole {@link Host}. */
    static Resolved resolve(TaskDef task, File project, Host host) {
        UnaryOperator<String> env = host.env();
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
        if (task.shell() != null) {
            if (task.shell().executable() != null) {
                used.add(task.shell().executable());
            }
            if (task.shell().args() != null) {
                used.addAll(task.shell().args());
            }
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
                List<String> argv;
                if ("shell".equals(task.type())) {
                    ShellOpt declared = task.shell() == null ? null : new ShellOpt(
                            task.shell().executable() == null ? null : sub.apply(task.shell().executable()),
                            task.shell().args() == null ? null
                                    : task.shell().args().stream().map(sub).toList());
                    Object shell = shellArgv(host, declared, project, command, args, task.args());
                    if (shell instanceof Refused r) {
                        return r;
                    }
                    @SuppressWarnings("unchecked")
                    List<String> built = (List<String>) shell;
                    argv = built;
                } else {
                    argv = processArgv(command, args);
                }
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

    /** Where Windows PowerShell lives under {@code %SystemRoot%} when nothing else is found. */
    static final String WINDOWS_POWERSHELL = "System32\\WindowsPowerShell\\v1.0\\powershell.exe";

    /**
     * {@code shell}: the argv that runs the command line in the shell VS
     * Code would use, or a {@link Refused} (SHELL_MISSING, SHELL_UNSUPPORTED).
     *
     * <p>The command is the user's shell text and is passed as written (it
     * may be {@code npm run build && echo done}); each argument is quoted
     * for THAT shell unless it is plainly safe, honouring a declared
     * {@code quoting} of {@code strong}, {@code weak} or {@code escape}.
     *
     * <p>The shell and its arguments follow VS Code's
     * {@code terminalTaskSystem.ts} (read, v3.1.0):
     * <ul>
     * <li>no {@code options.shell}: the default shell ({@link #defaultShell})
     *     with its profile's arguments ({@code -l} for a macOS zsh, bash or
     *     fish) and the flag that runs a command ({@code -c}, or
     *     {@code -Command} for PowerShell);</li>
     * <li>{@code options.shell.executable}: that program, with exactly
     *     {@code options.shell.args} (none when absent) — VS Code adds no
     *     flag once the user names the shell;</li>
     * <li>{@code options.shell.args} alone: the default shell with those
     *     arguments, the flag appended when they lack it.</li>
     * </ul>
     *
     * <p>Windows gets two faithful transports and refuses the rest. Java
     * cannot hand a Windows program a raw command line — it re-quotes each
     * argument by heuristics — so PowerShell receives the line as
     * {@code -EncodedCommand} (UTF-16LE base64: the exact script text
     * {@code -Command} would have read, no quoting layer at all), and
     * cmd.exe as {@code /s /c "line"}, whose outer quotes cmd strips. Any
     * other shell on Windows is refused by name rather than run through a
     * quoting guess.
     */
    static Object shellArgv(Host host, ShellOpt declared, File project, String command,
            List<String> args, List<Value> quoting) {
        String exe;
        List<String> shellArgs;
        String asWritten;
        if (declared != null && declared.executable() != null) {
            asWritten = declared.executable();
            exe = locate(host, project, asWritten);
            if (exe == null) {
                return new Refused(Reason.SHELL_MISSING, asWritten);
            }
            shellArgs = declared.args() == null ? List.of() : declared.args();
        } else {
            exe = defaultShell(host);
            asWritten = exe;
            String name = shellName(exe);
            List<String> flag = commandFlag(host.os(), name);
            List<String> combined = new ArrayList<>();
            if (declared != null && declared.args() != null) {
                combined.addAll(declared.args());
                for (String f : flag) {
                    if (combined.stream().noneMatch(a -> a.equalsIgnoreCase(f))) {
                        combined.add(f);
                    }
                }
            } else {
                combined.addAll(profileArgs(host.os(), name));
                combined.addAll(flag);
            }
            shellArgs = combined;
        }
        String name = shellName(exe);
        if (host.os() != Os.WINDOWS) {
            List<String> argv = new ArrayList<>();
            argv.add(exe);
            argv.addAll(shellArgs);
            argv.add(line(command, args, quoting, VsCodeTasks::shQuote));
            return argv;
        }
        String last = shellArgs.isEmpty() ? "" : shellArgs.get(shellArgs.size() - 1);
        List<String> before = shellArgs.isEmpty() ? List.of() : shellArgs.subList(0, shellArgs.size() - 1);
        if (("pwsh".equals(name) || "powershell".equals(name))
                && (last.equalsIgnoreCase("-Command") || last.equalsIgnoreCase("-c"))) {
            String line = line(command, args, quoting, VsCodeTasks::psQuote);
            List<String> argv = new ArrayList<>();
            argv.add(exe);
            argv.addAll(before);
            argv.add("-EncodedCommand");
            argv.add(Base64.getEncoder().encodeToString(line.getBytes(StandardCharsets.UTF_16LE)));
            return argv;
        }
        if ("cmd".equals(name) && (last.equalsIgnoreCase("/c") || last.equalsIgnoreCase("/k"))) {
            String line = line(command, args, quoting, VsCodeTasks::cmdQuote);
            List<String> argv = new ArrayList<>();
            argv.add(exe);
            argv.addAll(before);
            if (before.stream().noneMatch(a -> a.equalsIgnoreCase("/s"))) {
                argv.add("/s");
            }
            argv.add(last);
            // a closing quote after a backslash reads as \" to Java's own
            // Windows quoting heuristic, which would then re-quote the whole
            // argument; a trailing space is nothing to cmd and ends that
            argv.add("\"" + (line.endsWith("\\") ? line + " " : line) + "\"");
            return argv;
        }
        return new Refused(Reason.SHELL_UNSUPPORTED, asWritten);
    }

    /** The command then each argument quoted by {@code quote}, space-separated. */
    private static String line(String command, List<String> args, List<Value> declared,
            java.util.function.BinaryOperator<String> quote) {
        StringBuilder line = new StringBuilder(command);
        for (int i = 0; i < args.size(); i++) {
            line.append(' ').append(quote.apply(args.get(i), declared.get(i).quoting()));
        }
        return line.toString();
    }

    /**
     * The shell VS Code would pick with no {@code options.shell}. POSIX: the
     * user's {@code $SHELL} when it names an absolute, executable file that
     * is not a refusing placeholder ({@code /bin/false}, a {@code nologin}),
     * else {@code /bin/sh}. Windows: PowerShell — {@code pwsh} (PowerShell
     * 7) when it is on the search path, as VS Code prefers it, else Windows
     * PowerShell under {@code %SystemRoot%}, else {@code powershell.exe} by
     * name for the spawn to find or report.
     */
    static String defaultShell(Host host) {
        if (host.os() == Os.WINDOWS) {
            String pwsh = host.onPath().apply("pwsh");
            if (pwsh != null) {
                return pwsh;
            }
            String root = host.env().apply("SystemRoot");
            if (root != null && !root.isBlank()) {
                File ps = new File(root, WINDOWS_POWERSHELL);
                if (host.executable().test(ps)) {
                    return ps.getPath();
                }
            }
            String ps = host.onPath().apply("powershell");
            return ps != null ? ps : "powershell.exe";
        }
        String shell = host.env().apply("SHELL");
        if (shell != null && shell.startsWith("/")) {
            String base = shellName(shell);
            if (!"false".equals(base) && !base.contains("nologin") && host.executable().test(new File(shell))) {
                return shell;
            }
        }
        return "/bin/sh";
    }

    /**
     * {@code options.shell.executable} (substituted) as a program to run, or
     * null when it names nothing: an absolute path must be an executable
     * file, a bare name is looked up on the search path, and a relative path
     * is read inside the project.
     */
    static String locate(Host host, File project, String executable) {
        if (executable == null || executable.isBlank()) {
            return null;
        }
        if (isAbsolute(host.os(), executable)) {
            return host.executable().test(new File(executable)) ? executable : null;
        }
        if (executable.indexOf('/') < 0 && executable.indexOf('\\') < 0) {
            return host.onPath().apply(executable);
        }
        File inProject = inside(project, executable);
        return inProject != null && host.executable().test(inProject) ? inProject.getPath() : null;
    }

    /**
     * Absolute on {@code os}, whatever OS this JVM runs on: a Windows path
     * starts with a drive or a UNC prefix, a POSIX one with a slash.
     */
    static boolean isAbsolute(Os os, String path) {
        if (os == Os.WINDOWS) {
            return path.matches("(?s)([A-Za-z]:[\\\\/]|[\\\\/]{2}).*") || new File(path).isAbsolute();
        }
        return path.startsWith("/");
    }

    /** {@code /usr/bin/zsh} → {@code zsh}, {@code C:\…\pwsh.exe} → {@code pwsh}: lower case, no {@code .exe}. */
    static String shellName(String exe) {
        String base = exe;
        int cut = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (cut >= 0) {
            base = base.substring(cut + 1);
        }
        base = base.toLowerCase(Locale.ROOT);
        return base.endsWith(".exe") ? base.substring(0, base.length() - 4) : base;
    }

    /** VS Code's default-profile arguments: macOS starts a zsh, bash or fish as a login shell. */
    static List<String> profileArgs(Os os, String shellName) {
        return os == Os.MAC && ("zsh".equals(shellName) || "bash".equals(shellName) || "fish".equals(shellName))
                ? List.of("-l")
                : List.of();
    }

    /** The flag VS Code adds so the shell runs one command line: {@code -c}, {@code -Command}, {@code /d /c}. */
    static List<String> commandFlag(Os os, String shellName) {
        if (os != Os.WINDOWS) {
            return List.of("-c");
        }
        if ("pwsh".equals(shellName) || "powershell".equals(shellName)) {
            return List.of("-Command");
        }
        if ("cmd".equals(shellName)) {
            return List.of("/d", "/c");
        }
        return List.of();
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

    /**
     * Plainly safe for PowerShell unquoted. Narrower than {@link #SAFE}:
     * {@code @} splats and {@code ,} builds an array there; a backslash is
     * an ordinary path character, so {@code out\} needs no quotes at all.
     */
    private static final Pattern PS_SAFE = Pattern.compile("[A-Za-z0-9_%+=:./\\\\-]+");

    /** PowerShell's single-quote delimiters: the ASCII one and the four typographic ones it also accepts. */
    private static final String PS_SINGLE = "'\u2018\u2019\u201A\u201B";

    /** PowerShell's double-quote delimiters. */
    private static final String PS_DOUBLE = "\"\u201C\u201D\u201E";

    /**
     * An argument for a PowerShell line: strong is single-quoted with every
     * single-quote delimiter doubled (nothing expands), weak is
     * double-quoted with the backtick and every double-quote delimiter
     * backtick-escaped ({@code $var} still expands — that is what weak
     * means), escape backticks each character that is not plainly safe.
     */
    static String psQuote(String arg, String quoting) {
        if ("escape".equals(quoting) && arg.chars().noneMatch(Character::isISOControl)) {
            StringBuilder out = new StringBuilder();
            for (char c : arg.toCharArray()) {
                if (!PS_SAFE.matcher(String.valueOf(c)).matches()) {
                    out.append('`');
                }
                out.append(c);
            }
            return out.toString();
        }
        if ("weak".equals(quoting)) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : arg.toCharArray()) {
                if (c == '`' || PS_DOUBLE.indexOf(c) >= 0) {
                    out.append('`');
                }
                out.append(c);
            }
            return out.append('"').toString();
        }
        if (!"strong".equals(quoting) && !"escape".equals(quoting) && PS_SAFE.matcher(arg).matches()) {
            return arg;
        }
        StringBuilder out = new StringBuilder("'");
        for (char c : arg.toCharArray()) {
            if (PS_SINGLE.indexOf(c) >= 0) {
                out.append(c);
            }
            out.append(c);
        }
        return out.append('\'').toString();
    }

    /**
     * An argument for the {@code cmd.exe /s /c "…"} line. cmd itself does
     * not read backslashes; the program it starts parses its command line
     * by the standard Windows rule, where backslashes are literal EXCEPT
     * before a quote: {@code 2n} backslashes then a quote are {@code n}
     * backslashes and a delimiter. So every run of backslashes that ends at
     * a quote this method writes — an embedded {@code ""} or the closing
     * one — is doubled, and {@code out\} arrives as {@code out\}, not as
     * {@code out"} swallowing the rest of the line. An embedded quote is
     * written {@code ""}, which keeps cmd's own quote state in step (a
     * {@code \"} would flip it and expose {@code &} and {@code |}).
     */
    static String cmdQuote(String arg, String quoting) {
        if (quoting == null && SAFE.matcher(arg).matches()) {
            return arg;
        }
        StringBuilder out = new StringBuilder("\"");
        int backslashes = 0;
        for (char c : arg.toCharArray()) {
            if (c == '\\') {
                backslashes++;
                continue;
            }
            if (c == '"') {
                out.append("\\".repeat(backslashes * 2)).append("\"\"");
            } else {
                out.append("\\".repeat(backslashes)).append(c);
            }
            backslashes = 0;
        }
        return out.append("\\".repeat(backslashes * 2)).append('"').toString();
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
