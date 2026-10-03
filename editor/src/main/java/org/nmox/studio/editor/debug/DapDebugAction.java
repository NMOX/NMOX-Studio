package org.nmox.studio.editor.debug;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.api.editor.EditorActionRegistrations;
import org.netbeans.editor.BaseAction;
import org.netbeans.modules.lsp.client.debugger.api.DAPConfiguration;
import org.nmox.studio.core.spi.DebugLauncher;
import org.nmox.studio.editor.debug.dap.DapProxy;
import org.nmox.studio.editor.debug.dap.JsDebugServer;
import org.nmox.studio.core.process.ToolLocator;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.RequestProcessor;

/**
 * Real breakpoint debugging through the platform's DAP client: toggle
 * breakpoints in the gutter, run "Debug File", and the NetBeans
 * debugger UI (variables, call stack, stepping) drives the language's
 * own debug adapter - debugpy for Python on stdio, delve for Go over
 * a local socket, and the vendored js-debug for JavaScript/TypeScript
 * through the {@link org.nmox.studio.editor.debug.dap.DapProxy} that
 * flattens its multi-session protocol.
 */
@EditorActionRegistrations({
    @EditorActionRegistration(name = "nmox-debug-file", mimeType = "text/x-python",
            popupPath = "", popupPosition = 8000),
    @EditorActionRegistration(name = "nmox-debug-file", mimeType = "text/x-go",
            popupPath = "", popupPosition = 8000),
    @EditorActionRegistration(name = "nmox-debug-file", mimeType = "text/javascript",
            popupPath = "", popupPosition = 8000),
    @EditorActionRegistration(name = "nmox-debug-file", mimeType = "text/typescript",
            popupPath = "", popupPosition = 8000)
})
public class DapDebugAction extends BaseAction {

    /** Launches run off the EDT; interruptible daemon so it can never pin shutdown. */
    private static final RequestProcessor RP = new RequestProcessor("nmox-dap", 1, true);

    public DapDebugAction() {
        super("nmox-debug-file");
    }

    @Override
    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        if (target == null) {
            return;
        }
        Document doc = target.getDocument();
        FileObject fo = fileOf(doc);
        if (fo == null) {
            return;
        }
        File file = org.openide.filesystems.FileUtil.toFile(fo);
        String mime = (String) doc.getProperty("mimeType");
        launch(file, mime);
    }

    /** The four MIME types this action debugs — the popup registrations above, as a rule. */
    static boolean supportsMime(String mime) {
        return mime != null && switch (mime) {
            case "text/x-python", "text/x-go", "text/javascript", "text/typescript" -> true;
            default -> false;
        };
    }

    /**
     * The launch, shared by the right-click action and the platform's
     * Debug File row through {@link DapDebugLauncher} (v2.157.0): trust
     * first, then the language's adapter, all off the EDT. Returns at once.
     */
    static void launch(File file, String mime) {
        launch(file, mime, null);
    }

    /**
     * {@link #launch(File, String)} with the program's working directory
     * chosen by the caller (v3.1.0: a {@code .vscode/launch.json}
     * configuration's {@code cwd}); null keeps each adapter's own default.
     * Delve takes the package DIRECTORY as its program and has no separate
     * working directory, so a Go launch with one is not offered here —
     * {@link #supportsWorkingDir} says which MIME types honour it.
     */
    static void launch(File file, String mime, File workingDir) {
        launch(file, mime, workingDir, List.of(), Map.of());
    }

    /**
     * {@link #launch(File, String, File)} with the program's arguments and
     * added environment (3.1.0: a {@code .vscode/launch.json}
     * configuration's {@code args} and {@code env}); both adapters that
     * honour a working directory take them in their launch request.
     */
    static void launch(File file, String mime, File workingDir, List<String> args, Map<String, String> env) {
        if (file == null || !supportsMime(mime)) {
            return;
        }
        if (workingDir != null && !supportsWorkingDir(mime)) {
            return;
        }
        gated(() -> projectRoot(file), () -> {
            switch (mime) {
                case "text/x-python" -> debugPython(file, workingDir, args, env, null);
                case "text/x-go" -> debugGo(file);
                case "text/javascript", "text/typescript" -> debugNode(
                        nodeLaunchRequest(file, workingDir != null ? workingDir : projectRoot(file), args, env),
                        "Node: " + file.getName(), false);
                default -> throw new IllegalStateException(mime);
            }
        });
    }

    /**
     * A Node launch as a {@code .vscode/launch.json} configuration describes
     * one, runtime included ({@link DebugLauncher.Launch}): the program may
     * be absent when the runtime is the whole command ({@code npm run dev}).
     * Trust is asked on the folder the configuration belongs to — the
     * runtime is a program the PROJECT chose — before anything is spawned.
     */
    static void launchNode(DebugLauncher.Launch launch) {
        gated(launch::workspace, () -> debugNode(
                nodeLaunchRequest(launch.name(), launch.program(), launch.workingDir(), launch.args(),
                        launch.env(), launch.runtime(), launch.runtimeArgs(), launch.workspace()),
                "Node: " + (launch.program() != null ? launch.program().getName() : launch.name()), false));
    }

    /** A Python launch with the interpreter its configuration names ({@code python}). */
    static void launchPython(DebugLauncher.Launch launch) {
        gated(launch::workspace, () -> debugPython(launch.program(), launch.workingDir(), launch.args(),
                launch.env(), launch.runtime()));
    }

    /**
     * Attaches to a Node process whose inspector is listening at {@code
     * address}:{@code port} on this machine. Nothing of the project's is
     * started — the adapter is this IDE's own — but the same question is
     * asked first, on the folder the configuration came from: a debugger
     * attached to a program runs what its user evaluates there. Nobody
     * listening is said on the status line, before any adapter is spawned,
     * rather than left to a session that opens and closes.
     */
    static void attachNode(String name, String address, int port, File workspace) {
        gated(() -> workspace, () -> {
            if (!listening(address, port)) {
                throw new Spoken(org.openide.util.NbBundle.getMessage(DapDebugAction.class,
                        "DapDebugAction_nothingListening", address, Integer.toString(port)));
            }
            debugNode(nodeAttachRequest(name, address, port, workspace), "Node: " + name, true);
        });
    }

    /** What a gated launch does once trust is given. */
    private interface Start {
        void run() throws Exception;
    }

    /** A failure whose message is already a whole sentence in the reader's language. */
    private static final class Spoken extends IOException {
        private static final long serialVersionUID = 1L;

        Spoken(String sentence) {
            super(sentence);
        }
    }

    /**
     * The one lane every launch and attach rides: Workspace Trust on {@code
     * trustRoot} FIRST, then {@code start}, off the EDT. Debugging runs the
     * project's code — the same thing the rack gates before it fires a
     * device — so the question is asked once per folder, on the same trust
     * record the rack uses, and a "Keep Safe" answer stops everything before
     * any adapter or debuggee is spawned. No adapter is started anywhere
     * but inside a {@code gated} call ({@code DebugTrustGateTest}).
     */
    private static void gated(java.util.function.Supplier<File> trustRoot, Start start) {
        // a run that grows a second session shows the Sessions window by
        // itself (v2.159.0); registered here, once, so nothing touches the
        // debugger API before the first launch
        SessionsWindowOpener.install();
        RP.post(() -> {
            try {
                if (!org.nmox.studio.rack.service.WorkspaceTrust.requestTrust(trustRoot.get())) {
                    StatusDisplayer.getDefault().setStatusText(
                            org.openide.util.NbBundle.getMessage(DapDebugAction.class, "DapDebugAction_notTrusted"));
                    return;
                }
                start.run();
                showOutput();
            } catch (Spoken said) {
                StatusDisplayer.getDefault().setStatusText(
                        org.nmox.studio.core.util.PlainStatus.text(said.getMessage()));
            } catch (Exception ex) {
                StatusDisplayer.getDefault().setStatusText(
                        org.openide.util.NbBundle.getMessage(DapDebugAction.class, "DapDebugAction_failed", ex.getMessage()));
            }
        });
    }

    /** Whether anything accepts a connection at {@code address}:{@code port}; a loopback question, bounded. */
    public static boolean listening(String address, int port) {
        InetAddress[] candidates;
        try {
            candidates = InetAddress.getAllByName(address);
        } catch (java.net.UnknownHostException unknown) {
            return false;
        }
        for (InetAddress candidate : candidates) {
            if (!candidate.isLoopbackAddress()) {
                continue;
            }
            try (Socket probe = new Socket()) {
                probe.connect(new InetSocketAddress(candidate, port), 1_000);
                return true;
            } catch (IOException refused) {
                // the next spelling of this machine, if there is one
            }
        }
        return false;
    }

    /** The MIME types whose launch honours a caller-chosen working directory. */
    static boolean supportsWorkingDir(String mime) {
        return mime != null && switch (mime) {
            case "text/x-python", "text/javascript", "text/typescript" -> true;
            default -> false;
        };
    }

    /**
     * debugpy's adapter speaks DAP on stdio: the clean case. {@code python}
     * is the interpreter the PROGRAM runs under (a launch configuration's
     * {@code python}), which debugpy takes in its launch request; the
     * adapter itself stays the {@code python3} on the PATH, the one that has
     * debugpy installed.
     */
    private static void debugPython(File file, File workingDir, List<String> args, Map<String, String> env,
            String python) throws IOException {
        File cwd = workingDir != null ? workingDir : file.getParentFile();
        ProcessBuilder pb = new ProcessBuilder(ToolLocator.resolveCommand(
                List.of("python3", "-m", "debugpy.adapter")));
        pb.directory(file.getParentFile());
        pb.environment().put("PATH", ToolLocator.augmentedPath());
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process adapter = pb.start();
        // once launch() hands off, the platform's DAP client owns the
        // adapter's lifecycle; until then a failed configure/launch must
        // not leave the spawned adapter running for the IDE's lifetime
        try {
            DAPConfiguration.create(adapter.getInputStream(), adapter.getOutputStream())
                    .addConfiguration(pythonLaunchRequest(file, cwd, args, env, python))
                    .setSessionName("Python: " + file.getName())
                    .launch();
        } catch (RuntimeException ex) {
            adapter.destroyForcibly();
            throw ex;
        }
    }

    /** delve serves DAP on a TCP port; we connect a socket to it. */
    private static void debugGo(File file) throws IOException, InterruptedException {
        int port = freePort();
        ProcessBuilder pb = new ProcessBuilder(ToolLocator.resolveCommand(
                List.of("dlv", "dap", "--listen=127.0.0.1:" + port)));
        pb.directory(file.getParentFile());
        pb.environment().put("PATH", ToolLocator.augmentedPath());
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process dlv = pb.start();
        // any failure between spawn and a successful launch() hand-off
        // must reap the listening dlv (and its socket) — otherwise every
        // failed attempt piles up an orphaned adapter until IDE exit
        Socket socket = null;
        try {
            socket = connectWithRetry(port, 20);
            DAPConfiguration.create(socket.getInputStream(), socket.getOutputStream())
                    .addConfiguration(Map.of(
                            "type", "go",
                            "request", "launch",
                            "mode", "debug",
                            "program", file.getParentFile().getAbsolutePath()))
                    .setSessionName("Go: " + file.getName())
                    .launch();
            if (!dlv.isAlive()) {
                throw new IOException("delve exited immediately");
            }
        } catch (IOException | InterruptedException | RuntimeException ex) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException closeFailure) {
                    // the forcible kill below tears the connection down anyway
                }
            }
            dlv.destroyForcibly();
            throw ex;
        }
    }

    /**
     * Node scripts debug through the vendored js-debug server. Its parent
     * connection only coordinates — the real target arrives on a child
     * session the platform client can't open — so the streams handed to
     * DAPConfiguration come from the DapProxy that flattens the two, and
     * hands every further target (forked children, worker threads) to the
     * platform as a session of its own.
     *
     * <p>{@code request} is the whole js-debug request, a launch or an
     * attach; {@code attach} says which verb the platform's client sends
     * it with. An attach starts no program: when its session ends the
     * adapter is stopped and the program it was attached to is not — it is
     * no child of the adapter's, so the tree kill never reaches it.
     */
    private static void debugNode(Map<String, Object> request, String sessionName, boolean attach)
            throws IOException, InterruptedException {
        File serverJs = org.openide.modules.InstalledFileLocator.getDefault().locate(
                "jsdebug/js-debug/src/dapDebugServer.js", "org.nmox.studio.editor", false);
        if (serverJs == null) {
            throw new IOException("bundled js-debug adapter missing from this installation");
        }
        JsDebugServer server = JsDebugServer.start(serverJs);
        try {
            DapProxy proxy = DapProxy.start(server.port(), server::stop);
            // auto-attach stays ON (js-debug's default): every child
            // process and worker the program starts becomes a debug session
            // of its own through the proxy's child-session door (v2.156.0)
            DAPConfiguration session = DAPConfiguration.create(proxy.clientInput(), proxy.clientOutput())
                    .addConfiguration(request)
                    .setSessionName(sessionName);
            if (attach) {
                session.attach();
            } else {
                session.launch();
            }
        } catch (IOException | RuntimeException ex) {
            server.stop();
            throw ex;
        }
    }

    /**
     * The js-debug request that attaches to a Node inspector already
     * listening at {@code address}:{@code port}. Everything else is the
     * adapter's default, which is VS Code's: the program is not resumed if
     * it is waiting ({@code --inspect-brk}), and children it has already
     * started are attached too.
     */
    public static Map<String, Object> nodeAttachRequest(String name, String address, int port, File cwd) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("type", "pwa-node");
        out.put("request", "attach");
        out.put("name", name);
        out.put("address", address);
        out.put("port", port);
        out.put("cwd", cwd.getAbsolutePath());
        return out;
    }

    /** The debugpy launch request for {@code program}, run by {@code python} when one is named. */
    public static Map<String, Object> pythonLaunchRequest(File program, File cwd, List<String> args,
            Map<String, String> env, String python) {
        Map<String, Object> out = withArgsAndEnv(Map.of(
                "type", "python",
                "request", "launch",
                "program", program.getAbsolutePath(),
                "cwd", cwd.getAbsolutePath(),
                "console", "internalConsole",
                "justMyCode", true), args, env);
        if (python != null) {
            out.put("python", python);
        }
        return out;
    }

    /**
     * The js-debug launch request for a configuration that names its
     * runtime. {@code runtimeExecutable} and {@code runtimeArgs} are
     * js-debug's own fields and cross as they are: the command it starts is
     * the runtime, its arguments, the program (when there is one) and the
     * program's arguments. {@code program} is null when the runtime is the
     * whole command.
     *
     * <p>{@code __workspaceFolder} is what VS Code itself sends with every
     * request, and it is sent here only when a runtime is named: it is the
     * folder under which js-debug looks in {@code node_modules/.bin} for a
     * runtime it did not find on the PATH (read from the adapter's {@code
     * resolveNodeModulesLocation}), which is how {@code "runtimeExecutable":
     * "tsx"} starts a project's own tsx. A launch that names no runtime is
     * the request it always was.
     */
    public static Map<String, Object> nodeLaunchRequest(String name, File program, File cwd, List<String> args,
            Map<String, String> env, String runtime, List<String> runtimeArgs, File workspace) {
        Map<String, Object> base = new java.util.LinkedHashMap<>();
        base.put("type", "pwa-node");
        base.put("request", "launch");
        base.put("name", program != null ? program.getName() : name);
        if (program != null) {
            base.put("program", program.getAbsolutePath());
        }
        base.put("cwd", cwd.getAbsolutePath());
        base.put("console", "internalConsole");
        base.put("outputCapture", "std");
        Map<String, Object> out = withArgsAndEnv(base, args, env);
        if (runtime != null) {
            out.put("runtimeExecutable", runtime);
            out.put("__workspaceFolder", workspace.getAbsolutePath());
        }
        if (!runtimeArgs.isEmpty()) {
            out.put("runtimeArgs", List.copyOf(runtimeArgs));
        }
        return out;
    }

    /**
     * The js-debug launch request for {@code program}. {@code outputCapture:
     * std} reads the program's stdout and stderr from the process itself:
     * js-debug's default ({@code console}) takes console output from the
     * child session instead, and a program that prints and exits before
     * that session is spliced in printed NOTHING to the Output window -
     * walked in 3.1.0, a newcomer's Debug on hello.js showed only the
     * command line. The cost, measured by the 3.1.0 review on the real
     * adapter: output from forked children and workers arrives in the first
     * session's console rather than each session's own (nothing is lost or
     * doubled; breakpoints and sessions per child are unchanged). Answering
     * js-debug's startDebugging only after the child session was configured
     * was tried first and did not bring the quick program's line back.
     */
    public static Map<String, Object> nodeLaunchRequest(File program, File cwd, List<String> args,
            Map<String, String> env) {
        return nodeLaunchRequest(program.getName(), program, cwd, args, env, null, List.of(), cwd);
    }

    /**
     * A launch request with {@code args} and {@code env} added when there
     * are any: both js-debug and debugpy read {@code args} as the
     * program's argument list and {@code env} as variables added to the
     * inherited environment. Empty ones add nothing, so a plain launch is
     * the request it always was.
     */
    public static Map<String, Object> withArgsAndEnv(Map<String, ?> base, List<String> args, Map<String, String> env) {
        Map<String, Object> out = new java.util.LinkedHashMap<>(base);
        if (args.isEmpty() && env.isEmpty()) {
            return out;
        }
        if (!args.isEmpty()) {
            out.put("args", List.copyOf(args));
        }
        if (!env.isEmpty()) {
            out.put("env", Map.copyOf(env));
        }
        return out;
    }

    /**
     * The debuggee's stdout goes to an Output tab the DAP client creates — but
     * nothing opens that window, so a debugged server's "listening on 3100"
     * banner is invisible until the user hunts for it. Debugging a server
     * without its console is debugging blind. Open Output and let it take the
     * tab it just created; the session's own selection wins from there.
     */
    static void showOutput() {   // shared with BrowserDebugAction: same session, same blindness
        java.awt.EventQueue.invokeLater(() -> {
            org.openide.windows.TopComponent output =
                    org.openide.windows.WindowManager.getDefault()
                            .findTopComponent("output");
            if (output != null) {
                output.open();
                output.requestVisible();
            }
        });
    }

    /** cwd = nearest ancestor holding a project manifest, so requires and
     *  node_modules resolve the way a terminal run from the root would. */
    static File projectRoot(File file) {   // shared with BrowserDebugAction: webRoot = same root
        File root = file.getParentFile();
        for (File d = root; d != null; d = d.getParentFile()) {
            if (org.nmox.studio.rack.devices.ProjectInspector.hasProjectManifest(d)) {
                return d;
            }
        }
        return root;
    }

    private static Socket connectWithRetry(int port, int attempts) throws IOException, InterruptedException {
        IOException last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress("127.0.0.1", port), 500);
                return s;
            } catch (IOException ex) {
                last = ex;
                Thread.sleep(250);
            }
        }
        throw last;
    }

    private static int freePort() throws IOException {
        // loopback-bound like JsDebugServer.freePort — the probe must never
        // open (however briefly) a listener on every interface
        try (java.net.ServerSocket s = new java.net.ServerSocket(
                0, 1, InetAddress.getLoopbackAddress())) {
            return s.getLocalPort();
        }
    }

    static FileObject fileOf(Document doc) {   // shared with BrowserDebugAction
        Object sdp = doc.getProperty(Document.StreamDescriptionProperty);
        if (sdp instanceof DataObject dataObject) {
            return dataObject.getPrimaryFile();
        }
        return sdp instanceof FileObject fo ? fo : null;
    }
}
