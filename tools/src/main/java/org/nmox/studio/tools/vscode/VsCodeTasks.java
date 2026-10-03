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
 * the running OS's. {@code group} is read for ranking only. {@code
 * problemMatcher} is kept as the file wrote it and bound to its folders
 * when the task resolves ({@link Launch#matching}); what a matcher means
 * is {@link VsCodeProblemMatchers}'s. {@code isBackground} says how a
 * task is waited for: until it exits, or — a watcher — until its problem
 * matcher says it is ready. The file's top-level {@code inputs[]} are read beside
 * the tasks ({@link InputDef}), and each task's {@code dependsOn} /
 * {@code dependsOrder} — the order they ask for is {@link
 * VsCodeTaskPlan}'s to decide, not this class's.
 *
 * <p><b>Variables.</b> The project's own ({@code ${workspaceFolder}},
 * {@code ${workspaceFolderBasename}}, {@code ${cwd}}, {@code ${/}},
 * {@code ${env:NAME}}, {@code ${userHome}}); the editor's, from an
 * {@link EditorContext} the provider read on the event thread at Enter
 * ({@code ${file}} and its family, {@code ${lineNumber}}, {@code
 * ${columnNumber}}, {@code ${selectedText}}), with the meanings of VS
 * Code's Variables Reference — a path relative to the workspace folder is
 * Node's {@code path.relative}, an extension is Node's {@code
 * path.extname}; and {@code ${input:id}}, from the answers the user gave
 * to the file's {@code promptString} and {@code pickString} inputs. Every
 * value is substituted in ONE pass: a selection that contains
 * {@code ${file}} stays the text it is.
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
 * cannot: {@code ${config:…}} its settings, {@code ${command:…}} and a
 * {@code "type": "command"} input an extension's command. A task using
 * one is listed and, on Enter, refused naming the variable — running it
 * with the variable blank would run a DIFFERENT command than the file
 * says. The same holds for a value that is not there to give: a
 * {@code ${file}} with no file open in the editor (or a file that is not
 * on disk), a {@code ${selectedText}} with nothing selected or more than
 * {@link #MAX_SELECTED_TEXT} characters selected, an {@code ${input:id}}
 * the file's {@code inputs} do not define, or define without the
 * attribute VS Code requires. A task type contributed by an extension
 * ({@code gulp}, {@code typescript}, …) is refused naming the type. A
 * working folder outside the project is refused by {@link Containment},
 * the one home of that decision — {@code ${file}} may name a file
 * anywhere (the user can have any file open) and that is fine for an
 * argument, but {@code "cwd": "${fileDirname}"} of a file outside the
 * project is outside the project.
 *
 * <p><b>What is never shown.</b> A {@code "password": true} input's answer
 * and the editor's selection are the user's own text. They reach the
 * process and nothing else: {@link Launch#shown} is the launch line with
 * both left as the file wrote them, and it is what the Output window's
 * header, the flight recorder and the Agent Port's run history read. A
 * refusal names a folder or a shell the same way. And a password is an
 * argument, an environment value or part of a shell line — never a NAME:
 * a task that uses one as its program, its shell, its folder or an npm
 * script (each of which something prints) is refused.
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

    /**
     * The longest {@code ${selectedText}} a task is handed. A selection is
     * an argument on a command line; one past this is a file, and the
     * task is refused naming the variable rather than run with a part.
     */
    static final int MAX_SELECTED_TEXT = 10_000;

    /** The variables that name the file open in the editor (a launch configuration's too: {@link VsCodeEditorVariables}). */
    static final java.util.Set<String> FILE_VARIABLES = java.util.Set.of(
            "file", "relativeFile", "relativeFileDirname", "fileBasename",
            "fileBasenameNoExtension", "fileDirname", "fileDirnameBasename", "fileExtname");

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

    /**
     * One task as the file declares it, after the running OS's override was
     * merged. {@code dependsOn} holds the labels the task names; {@code
     * sequence} is {@code "dependsOrder": "sequence"} (anything else is
     * VS Code's default, parallel); {@code foreignDependency} is the first
     * {@code dependsOn} entry written as a task-identifier OBJECT
     * ({@code {"type": "npm", "script": "build"}}), as the file wrote it,
     * or null — VS Code resolves that form against tasks its extensions
     * detect, which this IDE has no list of. {@code problemMatchers} is
     * the task's {@code problemMatcher} as the file wrote it, one entry
     * per matcher: a name ({@code "$tsc"}) or the JSON text of an inline
     * matcher object — {@link VsCodeProblemMatchers} reads them.
     */
    record TaskDef(String label, String type, Value command, List<Value> args,
            String cwd, Map<String, String> env, String script, String path,
            List<String> dependsOn, String group, boolean background, ShellOpt shell,
            boolean sequence, String foreignDependency, List<String> problemMatchers) {

        /** A task that declares no problem matcher. */
        TaskDef(String label, String type, Value command, List<Value> args,
                String cwd, Map<String, String> env, String script, String path,
                List<String> dependsOn, String group, boolean background, ShellOpt shell,
                boolean sequence, String foreignDependency) {
            this(label, type, command, args, cwd, env, script, path, dependsOn, group, background, shell,
                    sequence, foreignDependency, List.of());
        }

        /** A task whose dependencies are labels and run in parallel. */
        TaskDef(String label, String type, Value command, List<Value> args,
                String cwd, Map<String, String> env, String script, String path,
                List<String> dependsOn, String group, boolean background, ShellOpt shell) {
            this(label, type, command, args, cwd, env, script, path, dependsOn, group, background, shell,
                    false, null);
        }

        /** A task with no {@code options.shell}. */
        TaskDef(String label, String type, Value command, List<Value> args,
                String cwd, Map<String, String> env, String script, String path,
                List<String> dependsOn, String group, boolean background) {
            this(label, type, command, args, cwd, env, script, path, dependsOn, group, background, null);
        }
    }

    /** One {@code pickString} option: its value, and the label VS Code shows before it (or null). */
    record InputOption(String label, String value) {

        /** What the list shows: VS Code's {@code label: value}, or the bare value. */
        String display() {
            return label == null || label.isEmpty() ? value : label + ": " + value;
        }
    }

    /**
     * One entry of the file's top-level {@code inputs[]}. {@code type} is as
     * written ({@code promptString}, {@code pickString}, {@code command}, or
     * anything else — the resolver refuses what it cannot ask). {@code
     * problem} names the attribute VS Code requires and this entry lacks
     * ({@code description}; for a {@code pickString} also {@code options},
     * or an option's {@code value}), or is null.
     */
    record InputDef(String id, String type, String description, String defaultValue,
            boolean password, List<InputOption> options, String problem) {

        boolean prompt() {
            return "promptString".equals(type);
        }

        boolean pick() {
            return "pickString".equals(type);
        }
    }

    /** What one {@code tasks.json} declares for the running OS: its tasks and its inputs by id. */
    record TasksFile(List<TaskDef> tasks, Map<String, InputDef> inputs) {

        static final TasksFile EMPTY = new TasksFile(List.of(), Map.of());
    }

    /**
     * The editor at the moment Enter was pressed, read by the provider on
     * the event thread: the file the active editor tab holds (null when no
     * file is open there, or the file is not on disk), the caret's line
     * and column counted from 1 (0 when there is no editor to have a
     * caret), and the selection — null or empty when nothing is selected,
     * and never more than {@link #MAX_SELECTED_TEXT} + 1 characters, which
     * is all the resolver needs to refuse one that is too long.
     */
    record EditorContext(Path file, int line, int column, String selectedText) {

        static final EditorContext NONE = new EditorContext(null, 0, 0, null);

        /** The selection's length, never its text: the selection is the user's own. */
        @Override
        public String toString() {
            return "EditorContext[file=" + file + ", line=" + line + ", column=" + column + ", selected="
                    + (selectedText == null ? "none" : selectedText.length() + " chars") + "]";
        }
    }

    /**
     * Where the values a project cannot give come from: the editor, the
     * user's home folder, the file's inputs and the answers to them.
     * {@code answers} null means the questions have not been asked yet —
     * a variable check then passes an input that is declared and askable;
     * a map means they have, and an input with no answer in it refuses
     * ({@link Reason#INPUT_UNANSWERED}).
     */
    record Vars(EditorContext editor, String userHome, Map<String, InputDef> inputs,
            Map<String, String> answers) {

        /** No editor, no inputs: what a task resolves against when it is asked about alone. */
        static Vars none() {
            return new Vars(EditorContext.NONE, System.getProperty("user.home", ""), Map.of(), Map.of());
        }

        /**
         * The answers' ids and the editor, never an answer or the
         * selection: an answer may be a password, and a record's own
         * {@code toString} would print it into any log or assertion
         * message that names this value.
         */
        @Override
        public String toString() {
            return "Vars[editor=" + editor + ", userHome=" + userHome + ", inputs=" + inputs.keySet()
                    + ", answers=" + (answers == null ? "not asked" : answers.keySet()) + "]";
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
    sealed interface Resolved permits Launch, NpmLaunch, Aggregate, Refused {
    }

    /**
     * Spawn {@code argv} in {@code dir} with {@code env} added. {@code
     * shown} is the launch line to print and record in place of the argv
     * — the same line with a password input's answer and the editor's
     * selection left as the file wrote them — or null when the argv
     * carries neither and may be shown as it is. {@code matching} is what
     * the process's output is read for: the task's problem matchers,
     * bound to their folders ({@link VsCodeProblemMatchers.Applied#NONE}
     * for a task that declares none).
     */
    record Launch(List<String> argv, File dir, Map<String, String> env, String shown,
            VsCodeProblemMatchers.Applied matching) implements Resolved {

        /** A launch whose output is read for nothing. */
        Launch(List<String> argv, File dir, Map<String, String> env, String shown) {
            this(argv, dir, env, shown, VsCodeProblemMatchers.Applied.NONE);
        }

        /** A launch whose argv may be shown as it is. */
        Launch(List<String> argv, File dir, Map<String, String> env) {
            this(argv, dir, env, null);
        }

        /**
         * The line a reader may see and the variables' names, never the
         * argv of a launch that has a {@link #shown} line nor an
         * environment value: either may carry a password, a token from the
         * user's environment or the selection.
         */
        @Override
        public String toString() {
            return "Launch[" + (shown != null ? "shown=" + shown : "argv=" + argv) + ", dir=" + dir
                    + ", env=" + env.keySet() + ", matching=" + matching + "]";
        }
    }

    /**
     * Hand {@code script} to the NPM Service lane in {@code dir}; {@code
     * matching} as for a {@link Launch}.
     */
    record NpmLaunch(File dir, String script, VsCodeProblemMatchers.Applied matching) implements Resolved {

        /** A script whose output is read for nothing. */
        NpmLaunch(File dir, String script) {
            this(dir, script, VsCodeProblemMatchers.Applied.NONE);
        }
    }

    /**
     * A task with {@code dependsOn} and no command of its own: VS Code's
     * way of naming a group of tasks. Nothing of its own runs; it is done
     * when the tasks it depends on are.
     */
    record Aggregate() implements Resolved {
    }

    /**
     * Why a task cannot run here; {@code detail} is what the sentence
     * names, {@code extra} a second name where the sentence needs one
     * (else empty).
     */
    record Refused(Reason reason, String detail, String extra) implements Resolved {

        Refused(Reason reason, String detail) {
            this(reason, detail, "");
        }
    }

    /** The refusals a task can meet, each rendered by the provider in the reader's language. */
    enum Reason {
        /** A {@code ${…}} only VS Code can fill; detail = the variable as written. */
        VARIABLE,
        /** A file or caret variable with no file open in the editor (or one not on disk); detail = the variable. */
        NEEDS_FILE,
        /** {@code ${selectedText}} with nothing selected; detail = the variable. */
        NEEDS_SELECTION,
        /** {@code ${selectedText}} over {@link #MAX_SELECTED_TEXT}; detail = the variable. */
        SELECTION_TOO_LONG,
        /** An {@code ${input:id}} the file's {@code inputs} do not define; detail = the id. */
        INPUT_UNDEFINED,
        /** An input of a type this IDE cannot ask ({@code command}, or one VS Code does not define); detail = the id, extra = the type. */
        INPUT_TYPE,
        /** An input without an attribute VS Code requires; detail = the id, extra = the attribute. */
        INPUT_INCOMPLETE,
        /** An input the user was asked for and did not answer (Cancel); detail = the id. */
        INPUT_UNANSWERED,
        /** A password input where a name is printed (a program, a shell, a folder, an npm script); detail = the variable. */
        PASSWORD_SHOWN,
        /** {@code dependsOn} names a label the file does not define; detail = the label. */
        DEPENDENCY_MISSING,
        /** {@code dependsOn} names a label the file defines more than once; detail = the label. */
        DEPENDENCY_AMBIGUOUS,
        /** {@code dependsOn} holds a task-identifier object; detail = the object as written. */
        DEPENDENCY_OBJECT,
        /** The task depends on itself; detail = the labels of the loop, in order. */
        DEPENDENCY_CYCLE,
        /** A background task something waits for; detail = the label of the task that waits. */
        DEPENDENCY_BACKGROUND,
        /** More tasks in one run than {@link VsCodeTaskPlan#MAX_TASKS}; detail = blank. */
        CHAIN_TOO_LONG,
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
        SHELL_UNSUPPORTED,
        /**
         * A {@code shell} task run in cmd.exe whose {@code command} line
         * holds a value nobody in the file chose — the editor's file name,
         * the selection, an input's answer — with a character cmd.exe reads
         * as part of the command even inside double quotes ({@code %},
         * {@code !}, a double quote, a line break). Every other shell gets
         * such a value quoted ({@link #quotedCommand}). Detail = the
         * variable as written, never its value.
         */
        UNQUOTED_VALUE,
        /**
         * An {@code options.env} entry no process can be handed: a name
         * that is empty or holds {@code =} or a NUL, or a value that holds
         * a NUL. Detail = the name (control characters shown as U+FFFD),
         * never the value.
         */
        ENV_INVALID,
        /**
         * On Windows, a {@code process} task whose program is a batch file
         * ({@code .cmd}, {@code .bat}) and one of whose arguments holds a
         * character cmd.exe reads as syntax ({@code & | < > ^ % !}, a quote,
         * a line break): Windows runs a batch file through cmd.exe, which
         * parses the arguments again. Detail = the program as written.
         */
        BATCH_ARGUMENT
    }

    /* ------------------------------------------------------------------ reading */

    private record Cached(long mtime, long size, TasksFile file) {
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
        return readFile(project, os).tasks();
    }

    /** {@link #read(File)} with the file's {@code inputs} beside its tasks. */
    static TasksFile readFile(File project) {
        return readFile(project, Os.current());
    }

    static TasksFile readFile(File project, Os os) {
        if (project == null) {
            return TasksFile.EMPTY;
        }
        File file = new File(project, RELATIVE_PATH);
        if (!file.isFile()) {
            return TasksFile.EMPTY;
        }
        long mtime = file.lastModified();
        long size = file.length();
        String key = file.getAbsolutePath() + "|" + os;
        Cached hit = CACHE.get(key);
        if (hit != null && hit.mtime() == mtime && hit.size() == size) {
            return hit.file();
        }
        TasksFile tasks;
        try {
            tasks = parseFile(BoundedReads.read(file, MAX_BYTES), os);
        } catch (IOException | JSONException | IllegalArgumentException unreadable) {
            // cached as empty for this mtime, so the log line is written
            // once per version of the file rather than once per keystroke
            LOG.log(Level.INFO, "{0} lists no tasks: {1}",
                    new Object[] {file.getAbsolutePath(), unreadable.getMessage()});
            tasks = TasksFile.EMPTY;
        }
        CACHE.put(key, new Cached(mtime, size, tasks));
        return tasks;
    }

    /** The tasks in {@code text}; throws when it is not a JSON object once JSONC is stripped. */
    static List<TaskDef> parse(String text, Os os) {
        return parseFile(text, os).tasks();
    }

    /** The tasks and inputs in {@code text}; throws when it is not a JSON object once JSONC is stripped. */
    static TasksFile parseFile(String text, Os os) {
        JSONObject root = new JSONObject(stripJsonc(text));
        JSONObject globalOptions = mergeOptions(root.optJSONObject("options"),
                osBlock(root, os) == null ? null : osBlock(root, os).optJSONObject("options"));
        Map<String, InputDef> inputs = inputsOf(root.optJSONArray("inputs"));
        JSONArray array = root.optJSONArray("tasks");
        if (array == null) {
            return new TasksFile(List.of(), inputs);
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
                    shellOf(options),
                    // VS Code's DependsOrder.fromString: "sequence", else parallel
                    "sequence".equalsIgnoreCase(String.valueOf(task.opt("dependsOrder")).strip()),
                    foreignDependency(task.opt("dependsOn")),
                    problemMatchers(task.opt("problemMatcher"))));
        }
        return new TasksFile(List.copyOf(out), inputs);
    }

    /**
     * The file's {@code inputs[]} by id. An entry without a string id is
     * skipped (nothing could refer to it); of two with one id the later
     * wins, as VS Code's lookup takes the last match.
     */
    private static Map<String, InputDef> inputsOf(JSONArray array) {
        if (array == null) {
            return Map.of();
        }
        Map<String, InputDef> out = new LinkedHashMap<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject raw = array.optJSONObject(i);
            if (raw == null || !(raw.opt("id") instanceof String id) || id.isEmpty()) {
                continue;
            }
            String type = raw.opt("type") instanceof String t ? t : "";
            String description = raw.opt("description") instanceof String d ? d : null;
            String problem = description == null ? "description" : null;
            List<InputOption> options = new ArrayList<>();
            if ("pickString".equals(type)) {
                JSONArray rawOptions = raw.optJSONArray("options");
                if (rawOptions == null) {
                    problem = problem == null ? "options" : problem;
                } else {
                    for (int o = 0; o < rawOptions.length(); o++) {
                        Object option = rawOptions.opt(o);
                        if (option instanceof String value) {
                            options.add(new InputOption(null, value));
                        } else if (option instanceof JSONObject obj && obj.opt("value") instanceof String value) {
                            options.add(new InputOption(obj.opt("label") instanceof String l ? l : null, value));
                        } else {
                            problem = problem == null ? "value" : problem;
                        }
                    }
                    if (options.isEmpty() && problem == null) {
                        // an empty list is a question with no possible answer
                        problem = "options";
                    }
                }
            }
            out.remove(id); // the later entry takes the earlier one's place AND position
            out.put(id, new InputDef(id, type, description,
                    raw.opt("default") instanceof String def ? def : null,
                    Boolean.TRUE.equals(raw.opt("password")), List.copyOf(options), problem));
        }
        return Collections.unmodifiableMap(out);
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

    /** The labels {@code dependsOn} names — a string, or the strings of an array — in the file's order. */
    private static List<String> dependsOn(Object o) {
        if (o instanceof String s && !s.isBlank()) {
            return List.of(s.strip());
        }
        List<String> out = new ArrayList<>();
        if (o instanceof JSONArray arr) {
            for (int i = 0; i < arr.length(); i++) {
                if (arr.opt(i) instanceof String s && !s.isBlank()) {
                    out.add(s.strip());
                }
            }
        }
        return List.copyOf(out);
    }

    /**
     * {@code problemMatcher} as written — a name, a matcher object, or an
     * array of either — one entry per matcher, in the file's order: the
     * name itself, or an object's JSON text (which starts with a brace,
     * as no name does). Anything else in the array is not a matcher VS
     * Code would read either, and is kept as the text it is so that the
     * run can say it was not applied.
     */
    private static List<String> problemMatchers(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof JSONArray arr) {
            // one more than the run reads, so that it can say the list was cut
            for (int i = 0; i < arr.length() && out.size() <= VsCodeProblemMatchers.MAX_MATCHERS; i++) {
                addProblemMatcher(out, arr.opt(i));
            }
        } else {
            addProblemMatcher(out, o);
        }
        return List.copyOf(out);
    }

    private static void addProblemMatcher(List<String> out, Object o) {
        if (o instanceof String s) {
            // a name is never taken for an object: a leading brace is dropped from one
            out.add(s.strip().startsWith("{") ? "?" + s.strip() : s.strip());
        } else if (o instanceof JSONObject obj) {
            out.add(obj.toString());
        } else if (o != null && o != JSONObject.NULL) {
            out.add("?" + o);
        }
    }

    /** How much of a task-identifier object a refusal quotes. */
    private static final int MAX_FOREIGN = 120;

    /**
     * The first {@code dependsOn} entry written as an object — VS Code's
     * {@code {"type": "npm", "script": "build"}} task identifier — as JSON
     * (keys in alphabetical order, clipped), or null when every entry is a
     * label.
     */
    private static String foreignDependency(Object o) {
        JSONObject foreign = null;
        if (o instanceof JSONObject obj) {
            foreign = obj;
        } else if (o instanceof JSONArray arr) {
            for (int i = 0; i < arr.length() && foreign == null; i++) {
                foreign = arr.optJSONObject(i);
            }
        }
        if (foreign == null) {
            return null;
        }
        // org.json keeps no order; alphabetical is at least the same every time
        StringBuilder text = new StringBuilder("{");
        for (String key : new java.util.TreeSet<>(foreign.keySet())) {
            text.append(text.length() > 1 ? ", " : "").append(JSONObject.quote(key)).append(": ")
                    .append(JSONObject.valueToString(foreign.get(key)));
        }
        String written = text.append('}').toString();
        return written.codePointCount(0, written.length()) <= MAX_FOREIGN ? written
                : written.substring(0, written.offsetByCodePoints(0, MAX_FOREIGN)) + "\u2026";
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

    /**
     * {@link #resolve(TaskDef, File, Host, Vars)} for a task asked about
     * alone: no editor, no inputs. A task that needs either is refused
     * naming the variable.
     */
    static Resolved resolve(TaskDef task, File project, Host host) {
        return resolve(task, project, host, Vars.none());
    }

    /**
     * {@link #resolve(TaskDef, File, Os, UnaryOperator)} against a whole
     * {@link Host}, with the editor's variables and the inputs' answers
     * from {@code vars}. The task's {@code dependsOn} is NOT this
     * method's: it answers for the one task, and {@link VsCodeTaskPlan}
     * decides what runs before it.
     */
    static Resolved resolve(TaskDef task, File project, Host host, Vars vars) {
        UnaryOperator<String> env = host.env();
        // every string the task would use, checked before anything is built
        List<String> used = usedStrings(task);
        for (String s : used) {
            Refused problem = variableProblem(s, vars);
            if (problem != null) {
                return problem;
            }
        }
        // a password is for the process to read. Where it would be a NAME —
        // the program a failed launch names, the shell, the folder, the
        // script the npm lane prints — it would be printed, so it is refused
        List<String> names = new ArrayList<>();
        if (task.command() != null && "process".equals(task.type())) {
            names.add(task.command().text());
        }
        names.add(task.cwd());
        names.add(task.script());
        names.add(task.path());
        names.add(task.shell() == null ? null : task.shell().executable());
        for (String name : names) {
            String wouldShow = name == null ? null : passwordVariable(name, vars);
            if (wouldShow != null) {
                return new Refused(Reason.PASSWORD_SHOWN, wouldShow);
            }
        }
        UnaryOperator<String> sub = s -> substitute(s, project, env, vars, false);
        // the same text for a reader: a password and the selection as the file wrote them
        UnaryOperator<String> written = s -> substitute(s, project, env, vars, true);
        switch (task.type()) {
            case "npm" -> {
                if (task.script() == null) {
                    return new Refused(Reason.NO_COMMAND, "");
                }
                String folder = task.path() == null ? null : sub.apply(task.path());
                Object dir = workingDir(project, folder);
                if (dir instanceof Refused r) {
                    return new Refused(r.reason(), written.apply(task.path()));
                }
                return new NpmLaunch((File) dir, sub.apply(task.script()), matching(task, project, env));
            }
            case "shell", "process" -> {
                if (task.command() == null || task.command().text().isBlank()) {
                    // no command and something to wait for: VS Code's group of tasks
                    return task.dependsOn().isEmpty() ? new Refused(Reason.NO_COMMAND, "") : new Aggregate();
                }
                Object dir = workingDir(project, task.cwd() == null ? null : sub.apply(task.cwd()));
                if (dir instanceof Refused r) {
                    return new Refused(r.reason(), written.apply(task.cwd()));
                }
                Map<String, String> environment = new LinkedHashMap<>();
                task.env().forEach((k, v) -> environment.put(k, sub.apply(v)));
                // a name or value no process can be handed: ProcessBuilder
                // throws past every launch path's IOException catch, so it is
                // refused here, by its name (never its value)
                for (Map.Entry<String, String> e : environment.entrySet()) {
                    if (!environmentEntryValid(e.getKey(), e.getValue())) {
                        return new Refused(Reason.ENV_INVALID, printable(e.getKey()));
                    }
                }
                Object argv = argvOf(task, host, project, env, vars, false);
                if (argv instanceof Refused r) {
                    // a shell the file named is named back as the file (and a reader) may see it
                    boolean aboutTheShell = r.reason() == Reason.SHELL_MISSING
                            || r.reason() == Reason.SHELL_UNSUPPORTED;
                    return aboutTheShell && task.shell() != null && task.shell().executable() != null
                            ? new Refused(r.reason(), written.apply(task.shell().executable()))
                            : r;
                }
                @SuppressWarnings("unchecked")
                List<String> built = (List<String>) argv;
                if ("process".equals(task.type()) && batchArgument(host, project, built)) {
                    return new Refused(Reason.BATCH_ARGUMENT, written.apply(task.command().text()));
                }
                // the line a reader is shown: a password, the selection and
                // the user's environment left as the file wrote them, and on
                // Windows the PowerShell line itself rather than its base64.
                // Null only when that line IS the argv joined.
                Object readable = argvOf(task, host, project, env, vars, true);
                @SuppressWarnings("unchecked")
                String line = readable instanceof List<?> ? String.join(" ", (List<String>) readable)
                        : display(task);
                String shown = line.equals(String.join(" ", built)) ? null : line;
                return new Launch(List.copyOf(built), (File) dir, Collections.unmodifiableMap(environment), shown,
                        matching(task, project, env));
            }
            default -> {
                return new Refused(Reason.TYPE, task.type());
            }
        }
    }

    /**
     * What {@code task}'s output is read for: its problem matchers, each
     * bound to the folder its file names are relative to. A matcher's
     * folder may use the project's variables ({@code ${workspaceFolder}},
     * {@code ${cwd}}, {@code ${env:NAME}}) and no others — {@link
     * VsCodeProblemMatchers#read} has already set aside one that does.
     */
    private static VsCodeProblemMatchers.Applied matching(TaskDef task, File project, UnaryOperator<String> env) {
        return VsCodeProblemMatchers.apply(task.problemMatchers(), task.background(), project,
                folder -> substitute(folder, project, env));
    }

    /** Every string of {@code task} a variable could sit in, in the order the file's reader meets them. */
    static List<String> usedStrings(TaskDef task) {
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
        return used;
    }

    /**
     * The argv of a {@code shell} or {@code process} task with {@code sub}
     * applied, or a {@link Refused}; {@code forReader} as for {@link
     * #shellArgv(Host, ShellOpt, File, String, List, List, boolean)}.
     */
    private static Object argvOf(TaskDef task, Host host, File project, UnaryOperator<String> env, Vars vars,
            boolean forReader) {
        UnaryOperator<String> sub = s -> substitute(s, project, env, vars, forReader);
        List<String> args = new ArrayList<>();
        for (Value a : task.args()) {
            args.add(sub.apply(a.text()));
        }
        if (!"shell".equals(task.type())) {
            return processArgv(sub.apply(task.command().text()), args);
        }
        ShellOpt declared = task.shell() == null ? null : new ShellOpt(
                task.shell().executable() == null ? null : sub.apply(task.shell().executable()),
                task.shell().args() == null ? null
                        : task.shell().args().stream().map(sub).toList());
        String written = task.command().text();
        return shellArgv(host, declared, project,
                dialect -> quotedCommand(written, dialect, project, env, vars, forReader),
                args, task.args(), forReader);
    }

    /** The three command-line languages a {@code shell} task can be handed to. */
    enum Dialect {
        /** sh, bash, zsh, fish and the rest on macOS and Linux. */
        POSIX,
        /** PowerShell 7 and Windows PowerShell. */
        POWERSHELL,
        /** cmd.exe. */
        CMD
    }

    /**
     * The variables whose value nobody in the file chose: the editor's
     * file and its family, the selection, an input's answer. In a {@code
     * shell} task's {@code command} — a line the shell parses — such a
     * value is quoted ({@link #quotedCommand}).
     */
    private static boolean foreignValue(String name) {
        return FILE_VARIABLES.contains(name) || "selectedText".equals(name) || inputId(name) != null;
    }

    /** One plain word to a POSIX shell: left unquoted, so an ordinary line reads as written. */
    private static final Pattern POSIX_WORD = Pattern.compile("[A-Za-z0-9_@%+=:,./-]+");

    /** One plain word to PowerShell: a Windows path's backslash and drive colon, no {@code @} or {@code ,}. */
    private static final Pattern POWERSHELL_WORD = Pattern.compile("[A-Za-z0-9_%+=:./\\\\-]+");

    /** One plain word to cmd.exe: as PowerShell's, without the {@code %} cmd expands. */
    private static final Pattern CMD_WORD = Pattern.compile("[A-Za-z0-9_+=:./\\\\-]+");

    /** What cmd.exe reads as part of the command even inside double quotes. */
    private static final String CMD_LIVE_IN_QUOTES = "%!\"\r\n";

    /**
     * A {@code shell} task's command line with its variables filled in, in
     * the language of the shell that will read it, or a {@link Refused}.
     *
     * <p>The file's own text is the file's: {@code npm run build && echo
     * done} keeps its {@code &&}. A value nobody in the file chose ({@link
     * #foreignValue}) — a file named {@code x$(touch PWNED).js}, a
     * selection, an answer — is QUOTED for that shell unless it is already
     * one plain word, so {@code python ${file}} runs the file whatever its
     * name holds, a space or {@code $(…)}: single quotes with {@code '}
     * written {@code '\''} for a POSIX shell, single quotes with every
     * single-quote delimiter doubled for PowerShell, double quotes for
     * cmd.exe. VS Code itself pastes the value in as it is, which splits a
     * path at its space and runs what a name says; quoting runs the same
     * program on the same file and never the name. cmd.exe expands
     * {@code %…%} and {@code !…!} and ends a quoted run at a double quote
     * whatever surrounds them, so a value holding one there is refused by
     * name ({@link Reason#UNQUOTED_VALUE}). The reader's line ({@code
     * forReader}) carries the same quoting, with a password and the
     * selection left as the file wrote them.
     */
    static Object quotedCommand(String command, Dialect dialect, File project, UnaryOperator<String> env,
            Vars vars, boolean forReader) {
        Matcher m = VARIABLE.matcher(command);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = substitute(m.group(), project, env, vars, forReader);
            boolean asWritten = forReader && value.equals(m.group());
            if (foreignValue(m.group(1)) && !asWritten && !value.isEmpty()) {
                switch (dialect) {
                    case POSIX -> value = POSIX_WORD.matcher(value).matches() ? value : shQuote(value, "strong");
                    case POWERSHELL -> value = POWERSHELL_WORD.matcher(value).matches()
                            ? value : psQuote(value, "strong");
                    case CMD -> {
                        if (!CMD_WORD.matcher(value).matches()) {
                            for (int i = 0; i < value.length(); i++) {
                                if (CMD_LIVE_IN_QUOTES.indexOf(value.charAt(i)) >= 0) {
                                    return new Refused(Reason.UNQUOTED_VALUE, m.group());
                                }
                            }
                            // cmdQuote doubles a backslash run that ends at the closing quote
                            value = cmdQuote(value, "strong");
                        }
                    }
                }
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Whether {@code name}={@code value} can be handed to a process: the
     * JDK refuses a name that is empty or holds {@code =} or a NUL, and a
     * value that holds a NUL, with an {@link IllegalArgumentException}.
     */
    static boolean environmentEntryValid(String name, String value) {
        return !name.isEmpty() && name.indexOf('=') < 0 && name.indexOf('\0') < 0
                && (value == null || value.indexOf('\0') < 0);
    }

    /** {@code s} with every control character shown as U+FFFD, for a sentence that names it. */
    static String printable(String s) {
        StringBuilder out = new StringBuilder(s.length());
        s.codePoints().forEach(c -> out.appendCodePoint(Character.isISOControl(c) ? 0xFFFD : c));
        return out.toString();
    }

    /** The characters cmd.exe reads as syntax in a batch file's arguments. */
    private static final String BATCH_SYNTAX = "&|<>^%!\"\r\n";

    /**
     * Whether a Windows {@code process} task's argv names a batch file and
     * hands it an argument cmd.exe would parse as syntax. Windows starts a
     * {@code .cmd} or {@code .bat} through cmd.exe, which reads the whole
     * command line again; the JDK's quoting of each argument does not
     * survive that (the "BatBadBut" class), so an {@code &} in a file name
     * or an input's answer would end the argument and run what follows.
     */
    static boolean batchArgument(Host host, File project, List<String> argv) {
        if (host.os() != Os.WINDOWS || argv.isEmpty()) {
            return false;
        }
        String program = argv.get(0);
        String resolved;
        if (isAbsolute(Os.WINDOWS, program)) {
            resolved = program;
        } else if (program.indexOf('/') < 0 && program.indexOf('\\') < 0) {
            resolved = host.onPath().apply(program);
        } else {
            File inProject = inside(project, program);
            resolved = inProject == null ? program : inProject.getPath();
        }
        String name = (resolved == null ? program : resolved).toLowerCase(Locale.ROOT);
        if (!name.endsWith(".cmd") && !name.endsWith(".bat")) {
            return false;
        }
        for (String arg : argv.subList(1, argv.size())) {
            for (int i = 0; i < arg.length(); i++) {
                if (BATCH_SYNTAX.indexOf(arg.charAt(i)) >= 0) {
                    return true;
                }
            }
        }
        return false;
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
     * Why the first variable in {@code s} that cannot be filled cannot be,
     * or null when every one can: {@link #unsupportedVariable} widened by
     * what {@code vars} can give — the editor's file, caret and selection,
     * the user's home, and the file's inputs.
     */
    static Refused variableProblem(String s, Vars vars) {
        Matcher m = VARIABLE.matcher(s);
        while (m.find()) {
            Refused problem = problemOf(m.group(1), m.group(), vars);
            if (problem != null) {
                return problem;
            }
        }
        int open = s.indexOf("${");
        if (open >= 0 && s.indexOf('}', open) < 0) {
            return new Refused(Reason.VARIABLE, s.substring(open));
        }
        return null;
    }

    private static Refused problemOf(String name, String asWritten, Vars vars) {
        if (supported(name) || "userHome".equals(name)) {
            return null;
        }
        EditorContext editor = vars.editor();
        if (FILE_VARIABLES.contains(name)) {
            return editor.file() == null ? new Refused(Reason.NEEDS_FILE, asWritten) : null;
        }
        if ("lineNumber".equals(name) || "columnNumber".equals(name)) {
            return editor.line() < 1 || editor.column() < 1 ? new Refused(Reason.NEEDS_FILE, asWritten) : null;
        }
        if ("selectedText".equals(name)) {
            if (editor.line() < 1) {
                return new Refused(Reason.NEEDS_FILE, asWritten);
            }
            String selected = editor.selectedText();
            if (selected == null || selected.isEmpty()) {
                // VS Code: "Make sure to have some text selected in the active editor."
                return new Refused(Reason.NEEDS_SELECTION, asWritten);
            }
            return selected.length() > MAX_SELECTED_TEXT ? new Refused(Reason.SELECTION_TOO_LONG, asWritten) : null;
        }
        String id = inputId(name);
        if (id != null) {
            InputDef def = vars.inputs().get(id);
            if (def == null) {
                return new Refused(Reason.INPUT_UNDEFINED, id);
            }
            if (!def.prompt() && !def.pick()) {
                return new Refused(Reason.INPUT_TYPE, id, def.type());
            }
            if (def.problem() != null) {
                return new Refused(Reason.INPUT_INCOMPLETE, id, def.problem());
            }
            // no answer is never a blank: an input nobody answered refuses
            return vars.answers() != null && vars.answers().get(id) == null
                    ? new Refused(Reason.INPUT_UNANSWERED, id) : null;
        }
        return new Refused(Reason.VARIABLE, asWritten);
    }

    /** The id of an {@code input:id} variable name, or null when {@code name} is not one. */
    private static String inputId(String name) {
        return name.startsWith("input:") && name.length() > "input:".length()
                ? name.substring("input:".length()) : null;
    }

    /** The ids of the {@code ${input:…}} variables in {@code s}, in order, each as often as it is written. */
    static List<String> inputIds(String s) {
        List<String> out = new ArrayList<>();
        Matcher m = VARIABLE.matcher(s);
        while (m.find()) {
            String id = inputId(m.group(1));
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    /** The first {@code ${input:…}} in {@code s} whose input is a password, as written, or null. */
    private static String passwordVariable(String s, Vars vars) {
        Matcher m = VARIABLE.matcher(s);
        while (m.find()) {
            if (passwordInput(m.group(1), vars)) {
                return m.group();
            }
        }
        return null;
    }

    private static boolean passwordInput(String name, Vars vars) {
        String id = inputId(name);
        InputDef def = id == null ? null : vars.inputs().get(id);
        return def != null && def.password();
    }


    /**
     * Every supported variable replaced. {@code ${cwd}} is the project
     * folder: VS Code means "the directory VS Code started in", which for
     * a folder opened in it is that folder. {@code ${pathSeparator}} and
     * {@code ${/}} are the OS's file separator, as VS Code defines them.
     * An unset {@code ${env:NAME}} is the empty string, as in VS Code.
     * The variables only an editor or the user can fill are NOT supported
     * here (this is what {@code launch.json} resolves with); see {@link
     * #substitute(String, File, UnaryOperator, Vars, boolean)}.
     */
    static String substitute(String s, File project, UnaryOperator<String> env) {
        Matcher m = VARIABLE.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(projectValue(m.group(1), project, env)));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String projectValue(String name, File project, UnaryOperator<String> env) {
        return switch (name) {
            case "workspaceFolder", "workspaceRoot", "cwd" -> project.getAbsolutePath();
            case "workspaceFolderBasename" -> project.getName();
            case "pathSeparator", "/" -> File.separator;
            default -> {
                String v = name.startsWith("env:") ? env.apply(name.substring(4)) : null;
                yield v == null ? "" : v;
            }
        };
    }

    /**
     * {@link #substitute(String, File, UnaryOperator)} with the editor's
     * variables, {@code ${userHome}} and the inputs' answers from {@code
     * vars} — in ONE pass, so a value is never read for variables again.
     * Call it only on a string {@link #variableProblem} passed.
     *
     * @param forReader true leaves a password input, {@code
     *        ${selectedText}} and every {@code ${env:NAME}} as the file
     *        wrote them: the text for a header, a log or a refusal, never
     *        for the process. The user's environment holds tokens, and the
     *        launch line reaches the Output window, the flight recorder's
     *        journal, the Agent Port's run history and KVASIR
     */
    static String substitute(String s, File project, UnaryOperator<String> env, Vars vars, boolean forReader) {
        Matcher m = VARIABLE.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String value;
            if (forReader && ("selectedText".equals(name) || passwordInput(name, vars)
                    || name.startsWith("env:"))) {
                value = m.group();
            } else if ("userHome".equals(name)) {
                value = vars.userHome() == null ? "" : vars.userHome();
            } else if (inputId(name) != null) {
                String answer = vars.answers() == null ? null : vars.answers().get(inputId(name));
                value = answer == null ? "" : answer;
            } else {
                String fromEditor = editorValue(name, project, vars.editor());
                value = fromEditor != null ? fromEditor : projectValue(name, project, env);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * The value of an editor variable as VS Code's Variables Reference
     * defines it, or null when {@code name} is not one (or the editor has
     * nothing to give — {@link #variableProblem} refuses that first).
     */
    static String editorValue(String name, File project, EditorContext editor) {
        switch (name) {
            case "lineNumber":
                return editor.line() < 1 ? null : String.valueOf(editor.line());
            case "columnNumber":
                return editor.column() < 1 ? null : String.valueOf(editor.column());
            case "selectedText":
                return editor.selectedText();
            default:
                break;
        }
        if (!FILE_VARIABLES.contains(name) || editor.file() == null) {
            return null;
        }
        Path file = editor.file().toAbsolutePath().normalize();
        Path dir = file.getParent() == null ? file : file.getParent();
        Path base = project.toPath().toAbsolutePath().normalize();
        String basename = file.getFileName() == null ? "" : file.getFileName().toString();
        return switch (name) {
            case "file" -> file.toString();
            case "relativeFile" -> relative(base, file);
            case "relativeFileDirname" -> {
                String relative = relative(base, dir);
                yield relative.isEmpty() ? "." : relative;
            }
            case "fileBasename" -> basename;
            case "fileBasenameNoExtension" -> basename.substring(0, basename.length() - extname(basename).length());
            case "fileExtname" -> extname(basename);
            case "fileDirname" -> dir.toString();
            case "fileDirnameBasename" -> dir.getFileName() == null ? "" : dir.getFileName().toString();
            default -> null;
        };
    }

    /**
     * Node's {@code path.relative(from, to)}: the way from {@code from} to
     * {@code to}, with {@code ..} steps when {@code to} is not under it,
     * and {@code to} itself when no way exists (two Windows drives).
     */
    static String relative(Path from, Path to) {
        try {
            return from.relativize(to).toString();
        } catch (IllegalArgumentException differentRoots) {
            return to.toString();
        }
    }

    /**
     * Node's {@code path.extname}: from the last dot of the name to its
     * end, the dot included — and nothing for a name with no dot or whose
     * only dot is its first character ({@code .bashrc} has no extension).
     */
    static String extname(String basename) {
        int dot = basename.lastIndexOf('.');
        return dot <= 0 ? "" : basename.substring(dot);
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
            if (!target.startsWith(base)) {
                // the two may be spellings of one place: a project opened
                // through a symlink and a path written through its real
                // location (or the other way round). Canonical to canonical
                // decides; what is outside then is outside, and Containment
                // still judges the relative path below.
                try {
                    base = project.getCanonicalFile().toPath();
                    target = asPath.toFile().getCanonicalFile().toPath();
                } catch (IOException | SecurityException unresolvable) {
                    return null;
                }
            }
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
        return shellArgv(host, declared, project, dialect -> command, args, quoting, false);
    }

    /**
     * {@link #shellArgv(Host, ShellOpt, File, String, List, List)} with the
     * command line asked for in the language of the shell this decides
     * ({@link #quotedCommand}): a {@link Refused} from it is the answer.
     * With {@code forReader} the argv a READER is shown rather than the
     * one that runs: on Windows PowerShell gets the line itself after
     * {@code -Command} in place of its {@code -EncodedCommand} base64,
     * which says nothing to a person reading the Output window's header.
     */
    static Object shellArgv(Host host, ShellOpt declared, File project,
            java.util.function.Function<Dialect, Object> commandFor,
            List<String> args, List<Value> quoting, boolean forReader) {
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
            Object command = commandFor.apply(Dialect.POSIX);
            if (command instanceof Refused r) {
                return r;
            }
            List<String> argv = new ArrayList<>();
            argv.add(exe);
            argv.addAll(shellArgs);
            argv.add(line((String) command, args, quoting, VsCodeTasks::shQuote));
            return argv;
        }
        String last = shellArgs.isEmpty() ? "" : shellArgs.get(shellArgs.size() - 1);
        List<String> before = shellArgs.isEmpty() ? List.of() : shellArgs.subList(0, shellArgs.size() - 1);
        if (("pwsh".equals(name) || "powershell".equals(name))
                && (last.equalsIgnoreCase("-Command") || last.equalsIgnoreCase("-c"))) {
            Object command = commandFor.apply(Dialect.POWERSHELL);
            if (command instanceof Refused r) {
                return r;
            }
            String line = line((String) command, args, quoting, VsCodeTasks::psQuote);
            List<String> argv = new ArrayList<>();
            argv.add(exe);
            argv.addAll(before);
            if (forReader) {
                argv.add(last);
                argv.add(line);
                return argv;
            }
            argv.add("-EncodedCommand");
            argv.add(Base64.getEncoder().encodeToString(line.getBytes(StandardCharsets.UTF_16LE)));
            return argv;
        }
        if ("cmd".equals(name) && (last.equalsIgnoreCase("/c") || last.equalsIgnoreCase("/k"))) {
            Object command = commandFor.apply(Dialect.CMD);
            if (command instanceof Refused r) {
                return r;
            }
            String line = line((String) command, args, quoting, VsCodeTasks::cmdQuote);
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
