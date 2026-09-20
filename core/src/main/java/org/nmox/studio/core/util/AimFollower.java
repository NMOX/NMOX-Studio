package org.nmox.studio.core.util;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.nmox.studio.core.spi.ProjectAim;
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
 * <p>Owns the whole v1.45.0 discipline so no window re-implements it:
 * publication only while SHOWING (the v1.38.0 law — a hidden
 * default-open tab does zero filesystem resolution at boot), aim-change
 * listener attached on showing and detached on hidden (listener
 * symmetry), equality-guarded storm-safe resolution via
 * {@link AimNodePublisher}, and a guard reset on close so a reopened
 * window re-publishes even for the same aim. Showing and following are
 * the same fact, so they are the same field — see {@link #following}.
 *
 * <p><b>Why it lives in core (v2.186.0, tech-debt ledger 72).</b> It
 * was written as {@code rack.service.AimFollower}, and that address —
 * not anything about the code — is what kept API Studio, Contract
 * Studio and the Infra Designer selection-less for five hundred
 * releases: those three dropped their rack Maven dependency on purpose
 * in the v1.46.0 soft-dependency surgery, so reaching this helper would
 * have re-added it. The ledger deferred the fix until "a small aim-node
 * facade in core.spi" earned a second consumer, on the assumption that
 * such a facade was the cost. It was not: the seam this needs,
 * {@link ProjectAim}, has existed since v1.46.0 and already carries
 * {@code projectDir()} and aim-change listeners, and core already
 * depended on {@code org-openide-nodes} and {@code org-openide-loaders}.
 * So the whole fix is an address change — one implementation, moved
 * below the wall rather than copied above it (the v2.131.0 law: the
 * defect is the second home).
 *
 * <p>The rack stays a SOFT dependency for every consumer: this class
 * looks the provider up and branches on null (v1.46.0's law — optional
 * means a lookup, not a caught {@code LinkageError}). With no provider
 * there is no aim to follow, so nothing is published, no listener is
 * attached, and a later {@link #showing()} tries again — a window in a
 * rack-less build is simply selection-less, exactly as it was before.
 */
public final class AimFollower {

    private final AimNodePublisher publisher;
    private final Supplier<ProjectAim> aimSource;
    private final ProjectAim.Listener listener = new ProjectAim.Listener() {
        @Override
        public void projectChanged() {
            ProjectAim aim = following;
            if (aim != null) {
                publish(aim);
            }
        }
    };

    /**
     * The provider this follower is subscribed to and publishing for, or
     * null while it is not following.
     *
     * <p>ONE field, and volatile, for two reasons. {@link ProjectAim}
     * fires on the AIMER's thread, so the gate that keeps a hidden
     * window from resolving anything has to be visible across threads —
     * and a provider whose notification is already mid-iteration will
     * still reach a listener removed during that pass, so the
     * unsubscribe alone cannot be that gate. A separate boolean beside
     * this reference would be a second gate for one law, which is how a
     * mutant lives behind the other (the v2.37.0 lesson). The reference
     * is held rather than re-looked-up so attach and detach always name
     * the SAME instance: the rack's adapter keys its listener wrappers
     * by identity, and a provider swapped between showing and hidden
     * would otherwise leak a subscription.
     */
    private volatile ProjectAim following;

    /**
     * @param sink receives the resolved aim node on the EDT — pass
     * {@code n -> setActivatedNodes(new Node[]{n})}
     */
    public AimFollower(Consumer<Node> sink) {
        this(sink, ProjectAim::find);
    }

    /**
     * Follows an explicit provider instead of the default Lookup's.
     *
     * <p>Public because the windows that own a follower live in modules
     * with no rack on their classpath, so their tests cannot register a
     * real provider — and registering a fake one globally would break
     * the {@code RackSoftDependencyTest} that pins those modules'
     * rack-lessness. The {@code WorkspaceTrust.clearForTest} precedent
     * (v1.102.0): a seam a cross-module test must reach is public and
     * says so.
     *
     * @param sink receives the resolved aim node on the EDT
     * @param aim the provider source, consulted at each use — never
     * cached across module lifecycle events, per {@link ProjectAim#find()}
     */
    public AimFollower(Consumer<Node> sink, Supplier<ProjectAim> aim) {
        this.publisher = new AimNodePublisher(sink);
        this.aimSource = aim;
    }

    /** Call from {@code componentShowing()}. */
    public void showing() {
        ProjectAim aim = following;
        if (aim == null) {
            aim = aimSource.get();
            if (aim == null) {
                return; // no rack: nothing to follow, and a later showing() retries
            }
            aim.addListener(listener);
            following = aim; // subscribe first, then start publishing
        }
        publish(aim);
    }

    /** Call from {@code componentHidden()}. */
    public void hidden() {
        ProjectAim aim = following;
        if (aim != null) {
            // stop publishing BEFORE unsubscribing: an event already in
            // flight finds a null reference and is refused, where the
            // unsubscribe alone would arrive too late for it
            following = null;
            aim.removeListener(listener);
        }
    }

    /** Call from {@code componentClosed()} — also detaches. */
    public void closed() {
        hidden();
        publisher.reset();
    }

    /** The aimed directory's node, resolved off the EDT and delivered on it. */
    private void publish(ProjectAim aim) {
        File dir = aim.projectDir();
        if (dir != null) {
            publisher.publish(dir);
        }
    }

    /** Test seam: the publisher, so tests can swap its resolver. */
    AimNodePublisher publisherForTest() {
        return publisher;
    }
}
