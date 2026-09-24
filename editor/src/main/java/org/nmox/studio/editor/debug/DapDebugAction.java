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
        // a run that grows a second session shows the Sessions window by
        // itself (v2.159.0); registered here, once, so nothing touches the
        // debugger API before the first launch
        SessionsWindowOpener.install();
        RP.post(() -> {
            try {
                // Debugging runs the project's code — the same thing the rack
                // gates before it fires a device. Ask once per folder, on the
                // same trust record the rack uses; a "Keep Safe" answer stops
                // the launch before any adapter or debuggee is spawned.
                if (!org.nmox.studio.rack.service.WorkspaceTrust.requestTrust(projectRoot(file))) {
                    StatusDisplayer.getDefault().setStatusText(
                            org.openide.util.NbBundle.getMessage(DapDebugAction.class, "DapDebugAction_notTrusted"));
                    return;
                }
                switch (mime) {
                    case "text/x-python" -> debugPython(file, workingDir, args, env);
                    case "text/x-go" -> debugGo(file);
                    case "text/javascript", "text/typescript" -> debugNode(file, workingDir, args, env);
                    default -> {
                        return;
                    }
                }
                showOutput();
            } catch (Exception ex) {
                StatusDisplayer.getDefault().setStatusText(
                        org.openide.util.NbBundle.getMessage(DapDebugAction.class, "DapDebugAction_failed", ex.getMessage()));
            }
        });
    }

    /** The MIME types whose launch honours a caller-chosen working directory. */
    static boolean supportsWorkingDir(String mime) {
        return mime != null && switch (mime) {
            case "text/x-python", "text/javascript", "text/typescript" -> true;
            default -> false;
        };
    }

    /** debugpy's adapter speaks DAP on stdio: the clean case. */
    private static void debugPython(File file, File workingDir, List<String> args, Map<String, String> env)
            throws IOException {
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
                    .addConfiguration(withArgsAndEnv(Map.of(
                            "type", "python",
                            "request", "launch",
                            "program", file.getAbsolutePath(),
                            "cwd", cwd.getAbsolutePath(),
                            "console", "internalConsole",
                            "justMyCode", true), args, env))
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
     */
    private static void debugNode(File file, File workingDir, List<String> args, Map<String, String> env)
            throws IOException, InterruptedException {
        File serverJs = org.openide.modules.InstalledFileLocator.getDefault().locate(
                "jsdebug/js-debug/src/dapDebugServer.js", "org.nmox.studio.editor", false);
        if (serverJs == null) {
            throw new IOException("bundled js-debug adapter missing from this installation");
        }
        File root = workingDir != null ? workingDir : projectRoot(file);
        JsDebugServer server = JsDebugServer.start(serverJs);
        try {
            DapProxy proxy = DapProxy.start(server.port(), server::stop);
            DAPConfiguration.create(proxy.clientInput(), proxy.clientOutput())
                    .addConfiguration(nodeLaunchRequest(file, root, args, env))
                            // auto-attach stays ON (js-debug's default): every
                            // child process and worker the program starts
                            // becomes a debug session of its own through the
                            // proxy's child-session door (v2.156.0)
                    .setSessionName("Node: " + file.getName())
                    .launch();
        } catch (IOException | RuntimeException ex) {
            server.stop();
            throw ex;
        }
    }

    /**
     * The js-debug launch request for {@code program}. {@code outputCapture:
     * std} reads the program's stdout and stderr from the process itself:
     * js-debug's default ({@code console}) takes console output from the
     * child session instead, and a program that prints and exits before
     * that session is spliced in printed NOTHING to the Output window -
     * walked in 3.1.0, a newcomer's Debug on hello.js showed only the
     * command line. The Output window shows text either way, so nothing
     * richer is lost.
     */
    public static Map<String, Object> nodeLaunchRequest(File program, File cwd, List<String> args,
            Map<String, String> env) {
        return withArgsAndEnv(Map.of(
                "type", "pwa-node",
                "request", "launch",
                "name", program.getName(),
                "program", program.getAbsolutePath(),
                "cwd", cwd.getAbsolutePath(),
                "console", "internalConsole",
                "outputCapture", "std"), args, env);
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
