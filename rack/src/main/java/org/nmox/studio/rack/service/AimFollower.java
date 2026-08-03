package org.nmox.studio.rack.service;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.nmox.studio.rack.model.Rack;
import org.openide.nodes.Node;

/**
 * The ambient aim selection, packaged (v1.235.0 — the ledger-29
 * remainder): a suite window that shows no finer selection of its own
 * calls three hooks and the AIMED project's node becomes its activated
 * nodes — so the platform's project-sensitive actions (Test Project
 * ^F6, the Team menu, git verbs) work while that window is focused,
 * exactly as they already did on the Rack, Project Studio and
 * Workbench (v1.45.0). Before this, pressing ^F6 on the Welcome tab
 * greyed for every kind: the action reads the global selection, and
 * the Welcome tab had none to offer.
 *
 * <p>Owns the whole v1.45.0 discipline so six windows don't each
 * re-implement it: publication only while SHOWING (the v1.38.0 law —
 * a hidden default-open tab does zero filesystem resolution at boot),
 * aim-change listener attached on showing and detached on hidden
 * (listener symmetry), equality-guarded storm-safe resolution via
 * {@link AimNodePublisher}, and a guard reset on close so a reopened
 * window re-publishes even for the same aim.
 *
 * <p>The windows that CANNOT ride this: API Studio, Contract Studio
 * and the Infra Designer dropped their rack dependency on purpose
 * (v1.46.0 soft-dependency surgery) — reaching this class would
 * re-add it. They stay selection-less until an aim-node facade joins
 * core.spi; recorded in the tech-debt ledger rather than half-done.
 */
public final class AimFollower {

    private final AimNodePublisher publisher;
    private final Supplier<File> aim;
    private final Rack.Listener listener = new Rack.Listener() {
        @Override
        public void projectChanged() {
            if (showing) {
                publisher.publish(aim.get());
            }
        }
    };

    /** Volatile: project switches complete off the EDT. */
    private volatile boolean showing;
    private boolean attached;

    /**
     * @param sink receives the resolved aim node on the EDT — pass
     * {@code n -> setActivatedNodes(new Node[]{n})}
     */
    public AimFollower(Consumer<Node> sink) {
        this(sink, () -> RackService.getDefault().getRack());
    }

    /** Test seam: inject the rack the follower reads and listens to. */
    AimFollower(Consumer<Node> sink, Supplier<Rack> rack) {
        this.publisher = new AimNodePublisher(sink);
        this.rackSupplier = rack;
        this.aim = () -> rackSupplier.get().getProjectDir();
    }

    private final Supplier<Rack> rackSupplier;

    /** Call from {@code componentShowing()}. */
    public void showing() {
        showing = true;
        if (!attached) {
            rackSupplier.get().addListener(listener);
            attached = true;
        }
        publisher.publish(aim.get());
    }

    /** Call from {@code componentHidden()}. */
    public void hidden() {
        showing = false;
        if (attached) {
            rackSupplier.get().removeListener(listener);
            attached = false;
        }
    }

    /** Call from {@code componentClosed()} — also detaches. */
    public void closed() {
        hidden();
        publisher.reset();
    }

    /** Test seam: the publisher, so tests can swap its resolver. */
    AimNodePublisher publisherForTest() {
        return publisher;
    }
}
