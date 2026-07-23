package org.nmox.studio.editor.debug;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.api.editor.EditorActionRegistrations;
import org.netbeans.editor.BaseAction;
import org.netbeans.modules.lsp.client.debugger.api.DAPConfiguration;
import org.nmox.studio.editor.debug.dap.DapProxy;
import org.nmox.studio.editor.debug.dap.JsDebugServer;
import org.nmox.studio.rack.service.ServingRegistry;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.modules.Places;
import org.openide.util.RequestProcessor;

/**
 * "Debug in Chrome (breakpoints)": launch a Chromium-family browser at the
 * project's page and hit IDE breakpoints in browser-side JavaScript.
 *
 * Same machinery as {@link DapDebugAction}'s Node path — the vendored
 * js-debug adapter behind the {@link DapProxy} multiplexer — with a
 * {@code pwa-chrome} launch instead of {@code pwa-node}. Recon against the
 * real adapter (v1.43.0) pinned the shape: one {@code startDebugging}
 * reverse request for the page target arrives on the PARENT link; worker
 * targets arrive as further {@code startDebugging} on the CHILD link, which
 * the proxy answers and ignores (a worker so ignored sits paused — see the
 * ledger). The browser is spawned by js-debug as the adapter's direct child
 * at {@code configurationDone} time, so the adapter's kill-tree covers it;
 * a client {@code disconnect} alone also tears the whole browser down
 * (js-debug defaults {@code cleanUp: wholeBrowser, killBehavior: forceful},
 * verified live — zero browser processes 3s after disconnect).
 */
@EditorActionRegistrations({
    @EditorActionRegistration(name = "nmox-debug-browser", mimeType = "text/html",
            popupText = "Debug in Chrome (breakpoints)", popupPath = "", popupPosition = 8000),
    @EditorActionRegistration(name = "nmox-debug-browser", mimeType = "text/javascript",
            popupText = "Debug in Chrome (breakpoints)", popupPath = "", popupPosition = 8100),
    @EditorActionRegistration(name = "nmox-debug-browser", mimeType = "text/typescript",
            popupText = "Debug in Chrome (breakpoints)", popupPath = "", popupPosition = 8100)
})
public class BrowserDebugAction extends BaseAction {

    private static final Logger LOG = Logger.getLogger(BrowserDebugAction.class.getName());
    /** Launches run off the EDT; interruptible daemon so it can never pin shutdown. */
    private static final RequestProcessor RP = new RequestProcessor("nmox-dap-browser", 1, true);

    /** Throwaway Chrome profiles alive right now. The JsDebugServer reaper
     *  kills the adapter+browser trees on IDE force-quit but never ran this
     *  class's profile cleanup, so a profile dir leaked per interrupted
     *  session (ledger 55 L5). Best-effort by design: a file Chrome still
     *  holds at hook time survives — disk cost only, never correctness. */
    private static final java.util.Set<Path> LIVE_PROFILES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> LIVE_PROFILES.forEach(BrowserDebugAction::deleteRecursively),
                "nmox-browser-profile-reaper"));
    }

    public BrowserDebugAction() {
        super("nmox-debug-browser");
    }

    @Override
    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        if (target == null) {
            return;
        }
        Document doc = target.getDocument();
        FileObject fo = DapDebugAction.fileOf(doc);
        if (fo == null) {
            return;
        }
        File file = org.openide.filesystems.FileUtil.toFile(fo);
        RP.post(() -> {
            try {
                File root = DapDebugAction.projectRoot(file);
                // Browser debugging runs the project's code in a browser we
                // control — the same act the rack gates. Ask BEFORE anything
                // is spawned; "Keep Safe" stops the launch cold.
                if (!org.nmox.studio.rack.service.WorkspaceTrust.requestTrust(root)) {
                    StatusDisplayer.getDefault().setStatusText(
                            "Debug cancelled — workspace not trusted.");
                    return;
                }
                File browser = BrowserLocator.find();
                if (browser == null) {
                    StatusDisplayer.getDefault().setStatusText(
                            "No Chromium-family browser found — install Google Chrome "
                            + "(or Microsoft Edge) to debug in a browser.");
                    return;
                }
                String url = pickUrl(root, file,
                        ServingRegistry.getDefault().snapshot());
                if (url == null) {
                    StatusDisplayer.getDefault().setStatusText(
                            "No live server for this project — start one in the rack "
                            + "(IGNITION, NPM) or debug the .html file directly.");
                    return;
                }
                debugChrome(file, root, browser, url);
                DapDebugAction.showOutput();
            } catch (Exception ex) {
                StatusDisplayer.getDefault().setStatusText(
                        "Browser debug failed: " + ex.getMessage());
            }
        });
    }

    /**
     * Where to point the browser — most-live source first:
     * a rack serve device already announcing a URL for this exact project
     * wins (breakpoints then bind inside the site the user is actually
     * running, dev-server transforms and all); failing that, a serving
     * whose project dir contains the file (the monorepo lane); with no
     * live server an .html file opens as a {@code file://} URL — js-debug
     * maps file URLs against webRoot, verified in recon — and a bare
     * .js/.ts file with no server has no page to load it, so we return
     * null and the caller says so instead of guessing.
     */
    static String pickUrl(File root, File file, List<ServingRegistry.Serving> servings) {
        for (ServingRegistry.Serving s : servings) {
            if (s.kind() == ServingRegistry.Kind.WEB && root.equals(s.projectDir())) {
                return s.url();
            }
        }
        String filePath = file.getAbsolutePath();
        for (ServingRegistry.Serving s : servings) {
            if (s.kind() == ServingRegistry.Kind.WEB && s.projectDir() != null
                    && filePath.startsWith(s.projectDir().getAbsolutePath() + File.separator)) {
                return s.url();
            }
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html") || name.endsWith(".htm")) {
            return file.toURI().toString();
        }
        return null;
    }

    /**
     * The pwa-chrome launch, shaped by the recon transcript. NOT headless:
     * the user watches (and drives) the page — the paused renderer IS the
     * feature. The profile is a fresh throwaway under the userdir cache,
     * never the user's real Chrome profile: reusing it would fold the
     * launch into any already-running Chrome (whose DevTools pipe we don't
     * own), and the user's logged-in sessions don't belong in a browser
     * the debugger will force-kill.
     */
    private static void debugChrome(File file, File root, File browser, String url)
            throws IOException, InterruptedException {
        File serverJs = org.openide.modules.InstalledFileLocator.getDefault().locate(
                "jsdebug/js-debug/src/dapDebugServer.js", "org.nmox.studio.editor", false);
        if (serverJs == null) {
            throw new IOException("bundled js-debug adapter missing from this installation");
        }
        Path profile = Files.createTempDirectory(
                Places.getCacheSubdirectory("browser-debug").toPath(), "profile-");
        LIVE_PROFILES.add(profile);
        JsDebugServer server = JsDebugServer.start(serverJs);
        // session over (terminated, disconnect, or a dropped link): stop()
        // confirms the adapter+browser tree dead — bounded — THEN the
        // profile is deleted, so nothing still holds files inside it
        Runnable cleanup = () -> {
            server.stop();
            deleteRecursively(profile);
            LIVE_PROFILES.remove(profile);
        };
        try {
            DapProxy proxy = DapProxy.start(server.port(), cleanup);
            DAPConfiguration.create(proxy.clientInput(), proxy.clientOutput())
                    .addConfiguration(Map.of(
                            "type", "pwa-chrome",
                            "request", "launch",
                            "name", file.getName(),
                            "url", url,
                            "webRoot", root.getAbsolutePath(),
                            "runtimeExecutable", browser.getAbsolutePath(),
                            "userDataDir", profile.toString()))
                    .setSessionName("Chrome: " + file.getName())
                    .launch();
        } catch (IOException | RuntimeException ex) {
            cleanup.run();
            throw ex;
        }
    }

    /** Best-effort profile removal; a leftover costs disk, never correctness. */
    private static void deleteRecursively(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ex) {
                    LOG.log(Level.FINE, "profile file not deleted", ex);
                }
            });
        } catch (IOException ex) {
            LOG.log(Level.FINE, "profile dir not walked", ex);
        }
    }
}
