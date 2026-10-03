package org.nmox.studio.core.spi;

import java.io.File;
import org.openide.util.Lookup;

/**
 * Soft-dependency facade over the editor's language-server catalog
 * for one question: "does this product start a server under this binary name, and
 * is it installed". The tools module's VS Code extensions sheet tells a
 * switcher which language server covers an extension they were
 * recommended, and it must not depend on the editor to ask. The editor
 * publishes the one implementation as a {@code @ServiceProvider}; a null
 * {@link #find()} means the editor module is absent, and the consumer
 * then names the server without claiming a state.
 *
 * <p>Read-only: the provider looks a name up in its own tables and stats
 * the PATH and the project's {@code node_modules/.bin}. It starts
 * nothing. Those stats are disk work, so {@link #server} is called off
 * the event thread.
 */
public interface ServerCatalog {

    /** The editor's provider, or null when the editor module is absent. */
    static ServerCatalog find() {
        return Lookup.getDefault().lookup(ServerCatalog.class);
    }

    /**
     * One server the editor starts.
     *
     * @param binary    the name asked for
     * @param language  the catalog's own name for what it serves, or null
     *                  when the catalog has no entry for a server the editor
     *                  starts all the same
     * @param installed whether the binary was found, on the PATH or in the
     *                  project
     * @param install   the catalog's install hint, or null when it has none
     */
    record Server(String binary, String language, boolean installed, String install) {
    }

    /**
     * The server the editor starts under {@code binary}, or null when it
     * starts none by that name. {@code projectDir} may be null; when
     * given, a server installed into the project counts as installed.
     */
    Server server(String binary, File projectDir);
}
