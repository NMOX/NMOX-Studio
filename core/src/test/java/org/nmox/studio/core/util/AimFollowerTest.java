package org.nmox.studio.core.util;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.spi.ProjectAim;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ambient-selection helper (v1.235.0, the ledger-29 remainder;
 * moved from rack to core in v2.186.0 to close ledger 72): publication
 * only while showing, listener attach/detach symmetric with the
 * showing/hidden hooks, a close that resets the equality guard so a
 * reopen re-publishes the same aim — and, new with the move, an honest
 * absence when no {@link ProjectAim} provider is registered (a build
 * without the rack: no listener, no publication, and a later
 * {@code showing()} tries again).
 */
class AimFollowerTest {

    /** A stand-in provider: only the aim and its listeners are real. */
    private static final class FakeAim implements ProjectAim {

        private final List<Listener> listeners = new CopyOnWriteArrayList<>();
        private volatile File dir;

        FakeAim(File dir) {
            this.dir = dir;
        }

        void aimAt(File next) {
            dir = next;
            listeners.forEach(Listener::projectChanged);
        }

        int subscriptions() {
            return listeners.size();
        }

        /**
         * Delivers to a listener that has since been removed — the shape
         * a provider produces when its notification is already in flight
         * (mid-iteration over a CopyOnWriteArrayList) as the window
         * hides. ProjectAim fires on the aimer's thread, so this really
         * can race {@code hidden()}.
         */
        void fireStale(Listener gone) {
            gone.projectChanged();
        }

        @Override
        public File projectDir() {
            return dir;
        }

        @Override
        public void aim(File d) {
            aimAt(d);
        }

        @Override
        public List<File> recentProjects() {
            return List.of();
        }

        /** The last listener subscribed — kept so a stale delivery can be staged. */
        volatile Listener lastAdded;

        @Override
        public void addListener(Listener listener) {
            lastAdded = listener;
            if (!listeners.contains(listener)) {
                listeners.add(listener); // the adapter's never-double-deliver contract
            }
        }

        @Override
        public void removeListener(Listener listener) {
            listeners.remove(listener);
        }

        @Override
        public void addManifestListener(Consumer<List<Path>> listener) {
        }

        @Override
        public void removeManifestListener(Consumer<List<Path>> listener) {
        }
    }

    /** Tiny poll loop — the publisher resolves on its own RP lane. */
    private static void await(java.util.function.BooleanSupplier cond) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!cond.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 5s");
            }
            Thread.sleep(20);
        }
    }

    /** A fake resolver so no DataObject machinery is needed. */
    private static AimFollower follower(ProjectAim aim, List<File> published) {
        AimFollower f = new AimFollower(n -> {
        }, () -> aim);
        // swap the publisher's resolver via the package-private seam:
        // resolve to a marker node and record which dir was asked for
        f.publisherForTest().resolver = dir -> {
            published.add(dir);
            return new AbstractNode(Children.LEAF);
        };
        return f;
    }

    @Test
    @DisplayName("showing publishes the aim; hidden stops following; reopen re-publishes")
    void lifecycle(@TempDir File aimA, @TempDir File aimB) throws Exception {
        FakeAim aim = new FakeAim(aimA);
        List<File> published = new CopyOnWriteArrayList<>();
        AimFollower f = follower(aim, published);

        f.showing();
        await(() -> published.contains(aimA));

        // aim moves while showing: the follower publishes the new aim
        aim.aimAt(aimB);
        await(() -> published.contains(aimB));

        // hidden: an aim move publishes NOTHING (the v1.38.0 law)
        f.hidden();
        published.clear();
        aim.aimAt(aimA);
        Thread.sleep(150);
        assertThat(published).as("a hidden window resolves nothing").isEmpty();

        // closed resets the guard: a reopen re-publishes even the SAME aim
        f.showing();
        await(() -> published.contains(aimA));
        f.closed();
        published.clear();
        f.showing();
        await(() -> published.contains(aimA));
    }

    @Test
    @DisplayName("double showing still detaches with ONE hidden — attach is idempotent")
    void attachIsIdempotent(@TempDir File aimA, @TempDir File aimB) throws Exception {
        FakeAim aim = new FakeAim(aimA);
        List<File> published = new CopyOnWriteArrayList<>();
        AimFollower f = follower(aim, published);

        // two showings then ONE hidden: if showing() attached a second
        // listener, it would survive the detach and publish below —
        // the leak the attached flag exists to prevent
        f.showing();
        f.showing();
        // drain the showing() publish first — resolution is async on the
        // publisher's RP, and a late-landing record would fake a leak
        await(() -> published.contains(aimA));
        f.hidden();
        published.clear();
        aim.aimAt(aimB);
        Thread.sleep(150);
        assertThat(published)
                .as("one hidden() must fully detach even after two showing()s")
                .isEmpty();
    }

    @Test
    @DisplayName("hidden unsubscribes from the provider — listener symmetry, not just a flag")
    void hiddenUnsubscribes(@TempDir File aimA) throws Exception {
        FakeAim aim = new FakeAim(aimA);
        List<File> published = new CopyOnWriteArrayList<>();
        AimFollower f = follower(aim, published);

        f.showing();
        await(() -> published.contains(aimA));
        assertThat(aim.subscriptions()).as("showing subscribes exactly once").isEqualTo(1);

        f.hidden();
        assertThat(aim.subscriptions())
                .as("a hidden window leaves no listener behind — a window opened and "
                        + "hidden a hundred times must not accumulate a hundred zombies")
                .isZero();

        f.showing();
        assertThat(aim.subscriptions()).as("re-showing re-subscribes once").isEqualTo(1);
        f.closed();
        assertThat(aim.subscriptions()).as("closed detaches too").isZero();
    }

    @Test
    @DisplayName("an in-flight aim event landing after hidden() publishes nothing")
    void staleEventAfterHiddenPublishesNothing(@TempDir File aimA, @TempDir File aimB)
            throws Exception {
        // The detach alone is not the whole guard. ProjectAim fires on
        // the AIMER's thread, and a provider whose notification is
        // already mid-iteration will still call a listener removed
        // during that pass — so a window that has just gone hidden can
        // be handed one last event. The volatile showing flag, cleared
        // BEFORE the detach, is what refuses it; without that flag a
        // hidden tab would resolve a filesystem node (the v1.38.0 law).
        FakeAim aim = new FakeAim(aimA);
        List<File> published = new CopyOnWriteArrayList<>();
        AimFollower f = follower(aim, published);

        f.showing();
        await(() -> published.contains(aimA));
        ProjectAim.Listener inFlight = aim.lastAdded;
        assertThat(inFlight).as("the follower subscribed").isNotNull();

        f.hidden();
        published.clear();
        aim.dir = aimB;        // the aim moved
        aim.fireStale(inFlight); // …and the already-queued event lands

        Thread.sleep(200);
        assertThat(published)
                .as("a hidden window refuses even an event delivered straight to "
                        + "its listener — the flag, not only the unsubscribe")
                .isEmpty();
    }

    @Test
    @DisplayName("no provider: nothing is published, and a later showing() still works")
    void noProviderIsHonestAbsence(@TempDir File aimA) throws Exception {
        // the rack-less build (API Studio's own module tests, a stripped
        // platform): ProjectAim.find() is null, so the window is simply
        // selection-less — never an exception, never a half-attached state
        FakeAim[] provider = {null};
        List<File> published = new CopyOnWriteArrayList<>();
        AimFollower f = new AimFollower(n -> {
        }, () -> provider[0]);
        f.publisherForTest().resolver = dir -> {
            published.add(dir);
            return new AbstractNode(Children.LEAF);
        };

        f.showing();
        Thread.sleep(150);
        assertThat(published).as("no provider, no aim, no resolution").isEmpty();
        f.hidden();
        f.closed(); // must not throw with nothing attached

        // the provider arrives (the rack module turns on) — the next
        // showing() attaches and publishes, because a null lookup never
        // latched the follower into a permanent off state
        provider[0] = new FakeAim(aimA);
        f.showing();
        await(() -> published.contains(aimA));
        assertThat(provider[0].subscriptions()).isEqualTo(1);
    }
}
