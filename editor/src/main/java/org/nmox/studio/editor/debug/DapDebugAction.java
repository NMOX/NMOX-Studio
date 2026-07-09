package org.nmox.studio.editor.debug;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
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
            popupText = "Debug File (breakpoints)", popupPath = "", popupPosition = 8000),
    @EditorActionRegistration(name = "nmox-debug-file", mimeType = "text/x-go",
            popupText = "Debug File (breakpoints)", popupPath = "", popupPosition = 8000),
    @EditorActionRegistration(name = "nmox-debug-file", mimeType = "text/javascript",
            popupText = "Debug File (breakpoints)", popupPath = "", popupPosition = 8000),
    @EditorActionRegistration(name = "nmox-debug-file", mimeType = "text/typescript",
            popupText = "Debug File (breakpoints)", popupPath = "", popupPosition = 8000)
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
        RP.post(() -> {
            try {
                // Debugging runs the project's code — the same thing the rack
                // gates before it fires a device. Ask once per folder, on the
                // same trust record the rack uses; a "Keep Safe" answer stops
                // the launch before any adapter or debuggee is spawned.
                if (!org.nmox.studio.rack.service.WorkspaceTrust.requestTrust(projectRoot(file))) {
                    StatusDisplayer.getDefault().setStatusText(
                            "Debug cancelled — workspace not trusted.");
                    return;
                }
                switch (mime) {
                    case "text/x-python" -> debugPython(file);
                    case "text/x-go" -> debugGo(file);
                    case "text/javascript", "text/typescript" -> debugNode(file);
                    default -> {
                    }
                }
            } catch (Exception ex) {
                StatusDisplayer.getDefault().setStatusText(
                        "Debug failed: " + ex.getMessage() + " — is the debug adapter installed?");
            }
        });
    }

    /** debugpy's adapter speaks DAP on stdio: the clean case. */
    private static void debugPython(File file) throws IOException {
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
                    .addConfiguration(Map.of(
                            "type", "python",
                            "request", "launch",
                            "program", file.getAbsolutePath(),
                            "cwd", file.getParentFile().getAbsolutePath(),
                            "console", "internalConsole",
                            "justMyCode", true))
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
     * DAPConfiguration come from the DapProxy that flattens the two.
     */
    private static void debugNode(File file) throws IOException, InterruptedException {
        File serverJs = org.openide.modules.InstalledFileLocator.getDefault().locate(
                "jsdebug/js-debug/src/dapDebugServer.js", "org.nmox.studio.editor", false);
        if (serverJs == null) {
            throw new IOException("bundled js-debug adapter missing from this installation");
        }
        File root = projectRoot(file);
        JsDebugServer server = JsDebugServer.start(serverJs);
        try {
            DapProxy proxy = DapProxy.start(server.port(), server::stop);
            DAPConfiguration.create(proxy.clientInput(), proxy.clientOutput())
                    .addConfiguration(Map.of(
                            "type", "pwa-node",
                            "request", "launch",
                            "name", file.getName(),
                            "program", file.getAbsolutePath(),
                            "cwd", root.getAbsolutePath(),
                            "console", "internalConsole",
                            // one child per session; user subprocesses run
                            // undebugged instead of pausing for an attach
                            // that will never come
                            "autoAttachChildProcesses", false))
                    .setSessionName("Node: " + file.getName())
                    .launch();
        } catch (IOException | RuntimeException ex) {
            server.stop();
            throw ex;
        }
    }

    /** cwd = nearest ancestor holding a project manifest, so requires and
     *  node_modules resolve the way a terminal run from the root would. */
    private static File projectRoot(File file) {
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
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static FileObject fileOf(Document doc) {
        Object sdp = doc.getProperty(Document.StreamDescriptionProperty);
        if (sdp instanceof DataObject dataObject) {
            return dataObject.getPrimaryFile();
        }
        return sdp instanceof FileObject fo ? fo : null;
    }
}
