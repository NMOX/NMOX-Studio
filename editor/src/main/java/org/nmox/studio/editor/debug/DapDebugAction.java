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
import org.nmox.studio.rack.engine.ToolLocator;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;

/**
 * Real breakpoint debugging through the platform's DAP client: toggle
 * breakpoints in the gutter, run "Debug File", and the NetBeans
 * debugger UI (variables, call stack, stepping) drives the language's
 * own debug adapter - debugpy for Python on stdio, delve for Go over
 * a local socket.
 */
@EditorActionRegistrations({
    @EditorActionRegistration(name = "nmox-debug-file", mimeType = "text/x-python",
            popupText = "Debug File (breakpoints)", popupPath = "", popupPosition = 8000),
    @EditorActionRegistration(name = "nmox-debug-file", mimeType = "text/x-go",
            popupText = "Debug File (breakpoints)", popupPath = "", popupPosition = 8000)
})
public class DapDebugAction extends BaseAction {

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
        new Thread(() -> {
            try {
                if ("text/x-python".equals(mime)) {
                    debugPython(file);
                } else if ("text/x-go".equals(mime)) {
                    debugGo(file);
                }
            } catch (Exception ex) {
                StatusDisplayer.getDefault().setStatusText(
                        "Debug failed: " + ex.getMessage() + " — is the debug adapter installed?");
            }
        }, "nmox-dap-launch").start();
    }

    /** debugpy's adapter speaks DAP on stdio: the clean case. */
    private static void debugPython(File file) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(ToolLocator.resolveCommand(
                List.of("python3", "-m", "debugpy.adapter")));
        pb.directory(file.getParentFile());
        pb.environment().put("PATH", ToolLocator.augmentedPath());
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process adapter = pb.start();
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
        Socket socket = connectWithRetry(port, 20);
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
