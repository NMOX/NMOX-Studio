package org.nmox.studio.ui.browser.fx.order;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Makes the two initializers of the fixture pair meet: each says it has
 * begun and waits a moment for the other to begin before it reaches for the
 * other's class. Loaded once per test in a loader of its own.
 */
public final class PairGate {

    private static final CountDownLatch NODE_BEGUN = new CountDownLatch(1);
    private static final CountDownLatch HELPER_BEGUN = new CountDownLatch(1);

    private PairGate() {
    }

    static void nodeBegan() {
        NODE_BEGUN.countDown();
        await(HELPER_BEGUN);
    }

    static void helperBegan() {
        HELPER_BEGUN.countDown();
        await(NODE_BEGUN);
    }

    private static void await(CountDownLatch other) {
        try {
            other.await(400, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
