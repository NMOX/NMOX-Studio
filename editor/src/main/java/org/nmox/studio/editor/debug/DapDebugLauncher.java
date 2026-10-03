package org.nmox.studio.editor.debug;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import org.nmox.studio.core.spi.DebugLauncher;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.lookup.ServiceProvider;

/**
 * The editor's answer to {@link DebugLauncher} (v2.157.0): the platform's
 * Debug ▸ Debug File row and its ⇧⌘F5 reach the same launch the editor's
 * right-click action runs — one room, a second door. Support is decided by
 * MIME, the same four the action registers for; where the platform's
 * resolvers are not loaded (a plain unit test) the extension table below
 * answers instead, and it names only spellings those resolvers map to the
 * same types.
 */
@ServiceProvider(service = DebugLauncher.class)
public final class DapDebugLauncher implements DebugLauncher {

    /** Extension → MIME for the resolver-less case; the resolvers win when present. */
    static final Map<String, String> EXTENSIONS = Map.of(
            "js", "text/javascript", "mjs", "text/javascript", "cjs", "text/javascript",
            "ts", "text/typescript", "mts", "text/typescript", "cts", "text/typescript",
            "py", "text/x-python",
            "go", "text/x-go");

    @Override
    public boolean supports(File file) {
        return DapDebugAction.supportsMime(mimeOf(file));
    }

    @Override
    public void debug(File file) {
        String mime = mimeOf(file);
        if (DapDebugAction.supportsMime(mime)) {
            DapDebugAction.launch(file, mime);
        }
    }

    @Override
    public boolean debug(File file, File workingDir) {
        if (file == null || workingDir == null) {
            return false;
        }
        String mime = mimeOf(file);
        if (!DapDebugAction.supportsMime(mime) || !DapDebugAction.supportsWorkingDir(mime)) {
            return false;
        }
        DapDebugAction.launch(file, mime, workingDir);
        return true;
    }

    @Override
    public boolean debug(File file, File workingDir, java.util.List<String> args, java.util.Map<String, String> env) {
        if (file == null || workingDir == null || args == null || env == null) {
            return false;
        }
        String mime = mimeOf(file);
        if (!DapDebugAction.supportsMime(mime) || !DapDebugAction.supportsWorkingDir(mime)) {
            return false;
        }
        DapDebugAction.launch(file, mime, workingDir, args, env);
        return true;
    }

    /** How a plain launch is started: file, MIME, working folder, args, env, and the folder trust is asked on. */
    @FunctionalInterface
    interface PlainStart {
        void start(File file, String mime, File workingDir, java.util.List<String> args,
                java.util.Map<String, String> env, File trustRoot);
    }

    /** The start of a plain launch, as a seam: a test proves which folder trust is asked on, spawning nothing. */
    static volatile PlainStart plainStart = DapDebugAction::launch;

    /**
     * A launch that names its runtime. A plain one takes the door it always
     * had; a Node one may have no program (the runtime is the whole
     * command); a Python one needs its program, and takes an interpreter
     * but no interpreter arguments — debugpy's launch has a field for the
     * first and this launcher has not been taught the second, so it answers
     * false rather than start the program without them.
     */
    @Override
    public boolean debug(Launch launch) {
        if (launch == null) {
            return false;
        }
        if (launch.plain()) {
            File file = launch.program();
            if (file == null || launch.workingDir() == null || launch.args() == null || launch.env() == null) {
                return false;
            }
            String plainMime = mimeOf(file);
            if (!DapDebugAction.supportsMime(plainMime) || !DapDebugAction.supportsWorkingDir(plainMime)) {
                return false;
            }
            // trust is asked on the folder the configuration belongs to, as
            // launchNode and launchPython ask it — not on the nearest manifest
            // above the program, which can be a parent of the project (or
            // the home folder) and is not the folder the user was shown
            plainStart.start(file, plainMime, launch.workingDir(), launch.args(), launch.env(), launch.workspace());
            return true;
        }
        String mime = launch.program() == null ? null : mimeOf(launch.program());
        switch (launch.language()) {
            case NODE -> {
                if (mime != null && !"text/javascript".equals(mime) && !"text/typescript".equals(mime)) {
                    return false;
                }
                DapDebugAction.launchNode(launch);
                return true;
            }
            case PYTHON -> {
                if (!"text/x-python".equals(mime) || !launch.runtimeArgs().isEmpty()) {
                    return false;
                }
                DapDebugAction.launchPython(launch);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean attachNode(String name, String address, int port, File workingDir, File workspace) {
        if (name == null || workingDir == null || workspace == null || port < 1 || port > 65535
                || !DebugLauncher.isLoopback(address)) {
            return false;
        }
        DapDebugAction.attachNode(name, address, port, workingDir, workspace);
        return true;
    }

    @Override
    public boolean debugPage(String url, File webRoot) {
        if (url == null || url.isBlank() || webRoot == null) {
            return false;
        }
        BrowserDebugAction.launchUrl(url, webRoot);
        return true;
    }

    /** The platform's verdict when it has one, else the extension table's. */
    static String mimeOf(File file) {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file));
        if (fo != null) {
            String mime = fo.getMIMEType();
            if (mime != null && !"content/unknown".equals(mime)) {
                return mime;
            }
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return EXTENSIONS.getOrDefault(ext, "content/unknown");
    }
}
