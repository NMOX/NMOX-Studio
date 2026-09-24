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
