package org.nmox.studio.core.spi;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.openide.util.Lookup;

/**
 * The aimed-project seam, published by the rack module and looked up by
 * every studio that follows the aim (API Studio, Contract Studio,
 * DB Studio, Infra Designer, the Workbench, the NPM explorer).
 *
 * <p>This interface exists so those consumers can treat the rack as a
 * SOFT dependency: they compile against core only and branch on
 * {@link #find()} returning null. Before v1.46.0 the same optionality
 * was implemented by importing rack classes and catching
 * {@code RuntimeException | LinkageError} around every call — which
 * worked, but used classloader failure for control flow and hid real
 * breakage (tech-debt ledger 30). Absence is now an honest lookup miss:
 * the feature is quietly off, exactly as the old catch branches behaved.
 *
 * <p>The surface mirrors ONLY what those consumers actually call on
 * {@code RackService}/{@code Rack}; it is not a general rack API.
 * Threading and coalescing semantics are the provider's
 * ({@code RackService}) — documented per method below, unchanged by the
 * facade.
 */
public interface ProjectAim {

    /**
     * The provider registered by the rack module, or null when the rack
     * is absent (plain unit tests, a stripped platform). Callers branch
     * on null; they must not cache across module lifecycle events.
     */
    static ProjectAim find() {
        return Lookup.getDefault().lookup(ProjectAim.class);
    }

    /**
     * The directory the IDE is aimed at. Never null while the provider
     * exists (the rack holds a construction default before any aim).
     */
    File projectDir();

    /**
     * Aims the whole IDE at {@code dir} — rack, studios, workbench and
     * recent list all follow ({@code RackService.openProject} semantics,
     * including the live-process switch guard). Non-directories no-op.
     */
    void aim(File dir);

    /** The recent-projects list, most recent first; never null. */
    List<File> recentProjects();

    /** Fires when the aimed project changes; thread is the aimer's. */
    interface Listener {
        void projectChanged();
    }

    /**
     * Subscribes to aim changes. Adding a listener already subscribed is
     * a no-op — consumers guard with attached flags, and the adapter
     * must never double-deliver ({@code Rack}'s listener list would).
     */
    void addListener(Listener listener);

    /** Unsubscribes; unknown listeners no-op. */
    void removeListener(Listener listener);

    /**
     * Manifest-pulse events for the aimed project (includes .env
     * changes): the coalesced batch of changed manifest paths, delivered
     * off-EDT ({@code RackService.addManifestListener} semantics).
     */
    void addManifestListener(Consumer<List<Path>> listener);

    /** Unsubscribes from manifest events; unknown listeners no-op. */
    void removeManifestListener(Consumer<List<Path>> listener);
}
