package org.nmox.studio.core.spi;

import java.io.File;
import org.openide.util.Lookup;

/**
 * Soft-dependency facade over the editor's breakpoint debugger
 * (v2.157.0): the project layer's {@code ActionProvider} answers the
 * platform's own <b>Debug File</b> row (Debug menu, ⇧⌘F5 in every keymap
 * profile) by handing the file here, so the debugger has a door a keyboard
 * can reach without the tools module depending on the editor. The editor
 * publishes the one implementation as a {@code @ServiceProvider}; a null
 * {@link #find()} means the editor module is absent and the command is
 * simply not offered.
 *
 * <p>The implementation owns the whole launch — the Workspace Trust
 * prompt before any spawn, the named lane off the EDT, the adapter per
 * language — exactly as the editor's right-click action does; this facade
 * adds a second door to the same room, never a second room.
 */
public interface DebugLauncher {

    /** True when {@code file} is one this debugger can launch (by MIME). */
    boolean supports(File file);

    /** Starts a debug session for {@code file}; returns at once. */
    void debug(File file);

    /**
     * Starts a debug session for {@code file} with {@code workingDir} as
     * the program's working directory (v3.1.0: a {@code .vscode/launch.json}
     * configuration's {@code cwd}); returns at once. The launcher owns the
     * same trust prompt and lane as {@link #debug(File)}.
     *
     * @return false, having started nothing, when this launcher cannot
     *         honour a working directory for that file — the caller then
     *         refuses out loud rather than debugging somewhere else
     */
    default boolean debug(File file, File workingDir) {
        return false;
    }

    /**
     * {@link #debug(File, File)} with the program's command-line arguments
     * and environment variables added to the ones it inherits (3.1.0: a
     * {@code .vscode/launch.json} configuration's {@code args} and
     * {@code env}). Additive: a launcher that has not been taught them
     * answers false for any non-empty one, having started nothing.
     *
     * @return false, having started nothing, when this launcher cannot
     *         pass them on for that file
     */
    default boolean debug(File file, File workingDir, java.util.List<String> args,
            java.util.Map<String, String> env) {
        return args.isEmpty() && env.isEmpty() && debug(file, workingDir);
    }

    /** The languages a {@link Launch} can describe. */
    enum Language {
        NODE, PYTHON
    }

    /**
     * A program to debug, as a {@code .vscode/launch.json} configuration
     * describes one: everything {@link #debug(File, File, java.util.List,
     * java.util.Map)} takes, plus the RUNTIME that starts it. One value, so
     * that the next thing a launch can say is a field here and not another
     * overload.
     *
     * @param language    which adapter starts it
     * @param name        the configuration's name: the session's title when
     *                    there is no program to name it after
     * @param program     the file to debug; null when the runtime is the
     *                    whole command ({@code npm run dev}), Node only
     * @param workingDir  the program's working directory
     * @param workspace   the folder the configuration belongs to: where a
     *                    runtime named without a path is looked for after
     *                    the PATH ({@code node_modules/.bin}), as VS Code does
     * @param args        the program's arguments
     * @param env         variables added to the inherited environment
     * @param runtime     the program that runs it — a name looked up on the
     *                    PATH, or an absolute path; null for the adapter's
     *                    own default ({@code node}, the adapter's Python)
     * @param runtimeArgs arguments to the runtime, before the program; Node only
     */
    record Launch(Language language, String name, File program, File workingDir, File workspace,
            java.util.List<String> args, java.util.Map<String, String> env,
            String runtime, java.util.List<String> runtimeArgs) {

        public Launch {
            java.util.Objects.requireNonNull(language);
            java.util.Objects.requireNonNull(name);
            java.util.Objects.requireNonNull(workingDir);
            java.util.Objects.requireNonNull(workspace);
            args = java.util.List.copyOf(args);
            env = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(env));
            runtimeArgs = java.util.List.copyOf(runtimeArgs);
        }

        /** True when it asks for nothing beyond a program, its folder, its args and its env. */
        public boolean plain() {
            return program != null && runtime == null && runtimeArgs.isEmpty();
        }

        /**
         * The variables' NAMES and never their values: an {@code envFile}
         * holds secrets, and a record's own {@code toString} would print
         * them into whatever log or failure message it reached.
         */
        @Override
        public String toString() {
            return "Launch[" + language + ", " + name + ", program=" + program + ", workingDir=" + workingDir
                    + ", args=" + args.size() + ", env=" + env.keySet() + ", runtime=" + runtime
                    + ", runtimeArgs=" + runtimeArgs + "]";
        }
    }

    /**
     * Starts a debug session for {@code launch}; returns at once, with the
     * same trust prompt and lane as {@link #debug(File)}. Additive: a
     * launcher that has not been taught a runtime answers false for any
     * launch that names one, having started nothing, and hands a {@link
     * Launch#plain() plain} one to the door it already has.
     *
     * @return false, having started nothing, when this launcher cannot
     *         start that launch exactly as it is described
     */
    default boolean debug(Launch launch) {
        return launch.plain()
                && debug(launch.program(), launch.workingDir(), launch.args(), launch.env());
    }

    /**
     * Attaches the debugger to a Node process that is already running with
     * its inspector open ({@code node --inspect}) on THIS machine: a {@code
     * .vscode/launch.json} configuration with {@code "request": "attach"}.
     * Nothing is started and, when the session ends, nothing is stopped:
     * the program was the user's before and stays theirs after. Returns at
     * once.
     *
     * @param name      the configuration's name, the session's title
     * @param address   a loopback address ({@code localhost}, {@code
     *                  127.0.0.1}, {@code ::1}); the caller has refused
     *                  anything else, and so does the launcher
     * @param port      the inspector's port
     * @param workspace the folder the configuration belongs to
     * @return false, having attached to nothing, when this launcher cannot
     *         attach
     */
    default boolean attachNode(String name, String address, int port, File workspace) {
        return false;
    }

    /**
     * Whether {@code address} names this machine, as an attach may: the
     * three spellings a launch configuration uses, and nothing resolved —
     * a name that merely resolves to loopback today is still a name
     * somebody else's resolver answers for. The one home of the rule, for
     * the reader that refuses and the launcher that refuses again.
     */
    static boolean isLoopback(String address) {
        return address != null && switch (address.toLowerCase(java.util.Locale.ROOT)) {
            case "localhost", "127.0.0.1", "::1" -> true;
            default -> false;
        };
    }

    /**
     * Opens {@code url} in a browser under the debugger, mapping the page's
     * scripts to sources under {@code webRoot} (v3.1.0: a {@code
     * .vscode/launch.json} Chrome configuration); returns at once, with the
     * same trust prompt as the editor's Debug in Chrome.
     *
     * @return false, having started nothing, when this launcher has no
     *         browser debugger
     */
    default boolean debugPage(String url, File webRoot) {
        return false;
    }

    /** The editor's implementation, or null when the editor is absent. */
    static DebugLauncher find() {
        return Lookup.getDefault().lookup(DebugLauncher.class);
    }
}
