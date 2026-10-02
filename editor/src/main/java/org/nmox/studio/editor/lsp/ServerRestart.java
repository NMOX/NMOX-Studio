package org.nmox.studio.editor.lsp;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.util.Lookup;

/**
 * Sends every open editor to its language servers again (3.5.10).
 *
 * <p>The platform's client tells a server about a document when the
 * document's editor appears, and not again. A server that starts later, for
 * a project that has just been trusted, would be running and know no file.
 * The client keeps a method for exactly this,
 * {@code TextDocumentSyncServerCapabilityHandler.refreshOpenedFilesInServers()},
 * which it calls itself after a server is connected by hand: for each open
 * editor it asks for the file's servers, which starts any that are not
 * running, sends {@code didOpen} to those that have not had the file, and
 * registers the background tasks. A provider that returned no server is not
 * counted as a failed start (read from {@code LSPBindings.buildBindings}),
 * so a server refused before the grant is simply asked again.
 *
 * <p>The class is not in a package the client exports, so it is reached
 * through the system class loader by name, the way {@code OutputFont} and
 * the git chip reach theirs. {@code ServerRestartSeamTest} fails the build
 * when a platform bump moves or renames it; at run time a miss is reported
 * to the caller, which then says what the user has to do instead.
 */
final class ServerRestart {

    static final String HANDLER = "org.netbeans.modules.lsp.client.bindings.TextDocumentSyncServerCapabilityHandler";
    static final String METHOD = "refreshOpenedFilesInServers";

    private ServerRestart() {
    }

    /** Starts servers for the open editors; false when the platform's method could not be called. Off the event thread: it starts processes. */
    static boolean reopenEditorsInServers() {
        try {
            ClassLoader system = Lookup.getDefault().lookup(ClassLoader.class);
            if (system == null) {
                system = ServerRestart.class.getClassLoader();
            }
            Class.forName(HANDLER, true, system).getMethod(METHOD).invoke(null);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            Logger.getLogger(ServerRestart.class.getName()).log(Level.INFO,
                    "the open editors could not be sent to their language servers again", ex);
            return false;
        }
    }
}
