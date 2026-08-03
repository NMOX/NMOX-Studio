package org.nmox.studio.rack.service;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ambient-selection helper (v1.235.0, the ledger-29 remainder):
 * publication only while showing, listener attach/detach symmetric
 * with the showing/hidden hooks, and a close that resets the equality
 * guard so a reopen re-publishes the same aim.
 */
class AimFollowerTest {

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
    private static AimFollower follower(Rack rack, List<File> published) {
        AimFollower f = new AimFollower(n -> {
        }, () -> rack);
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
        Rack rack = new Rack();
        rack.setProjectDir(aimA);
        List<File> published = new CopyOnWriteArrayList<>();
        AimFollower f = follower(rack, published);

        f.showing();
        await(() -> published.contains(aimA));

        // aim moves while showing: the follower publishes the new aim
        rack.setProjectDir(aimB);
        await(() -> published.contains(aimB));

        // hidden: an aim move publishes NOTHING (the v1.38.0 law)
        f.hidden();
        published.clear();
        rack.setProjectDir(aimA);
        Thread.sleep(150);
        assertThat(published).isEmpty();

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
        Rack rack = new Rack();
        rack.setProjectDir(aimA);
        List<File> published = new CopyOnWriteArrayList<>();
        AimFollower f = follower(rack, published);

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
        rack.setProjectDir(aimB);
        Thread.sleep(150);
        assertThat(published)
                .as("one hidden() must fully detach even after two showing()s")
                .isEmpty();
    }
}
