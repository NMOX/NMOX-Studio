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
