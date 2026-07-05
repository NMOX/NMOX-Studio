package org.nmox.studio.apiclient.api;

import java.util.List;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.nmox.studio.rack.service.ServingRegistry;

/**
 * The one hop between the rack's {@link ServingRegistry} and API
 * Studio's EDT: registry notifications arrive on the registry's own
 * background thread, the snapshot is taken THERE (never on the EDT),
 * and only the plain list crosses to the EDT consumer.
 *
 * <p>Lifecycle mirrors the NpmExplorer precedent: attach in
 * componentOpened, detach in componentClosed — both idempotent, so
 * open/close cycles never stack listeners.
 */
public final class ServingBridge implements ServingRegistry.Listener {

    private final ServingRegistry registry;
    private final Consumer<List<ServingRegistry.Serving>> onEdt;
    private boolean attached;

    public ServingBridge(ServingRegistry registry,
            Consumer<List<ServingRegistry.Serving>> onEdt) {
        this.registry = registry;
        this.onEdt = onEdt;
    }

    public void attach() {
        if (!attached) {
            attached = true;
            registry.addListener(this);
        }
    }

    public void detach() {
        if (attached) {
            attached = false;
            registry.removeListener(this);
        }
    }

    /**
     * One delivery outside a registry event — call OFF the EDT right
     * after opening the tab, so a server already running is seen.
     */
    public void refresh() {
        servingChanged();
    }

    /** Registry thread: snapshot here, marshal the result. */
    @Override
    public void servingChanged() {
        List<ServingRegistry.Serving> snapshot = registry.snapshot();
        SwingUtilities.invokeLater(() -> onEdt.accept(snapshot));
    }
}
