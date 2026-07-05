package org.nmox.studio.infra;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.model.InfraGraph;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.36 designer-lifecycle regression, pinned on the extracted
 * core (the TopComponent itself needs a display and is pure-Swing-
 * excluded): until that release the designer attached its listeners in
 * the constructor and removed them in componentClosed, so the FIRST
 * close killed autosave and dirty-tracking for good — reopened
 * designers silently stopped saving, and a stale rack binding could
 * write project A's design into project B's .nmoxinfra.json.
 * componentOpened now runs {@code listenerLifecycle.open()} and
 * componentClosed {@code close()}; these tests pin that bookkeeping.
 */
class ListenerLifecycleTest {

    @Test
    @DisplayName("Close detaches, REOPEN RE-ATTACHES, double-open never stacks")
    void listenersFollowTheOpenCloseCycle() {
        // wired exactly like the designer: the actions add/remove a real
        // listener on a real graph, so the counts below are the graph's truth
        InfraGraph graph = new InfraGraph();
        InfraGraph.Listener listener = new InfraGraph.Listener() {
        };
        ListenerLifecycle lifecycle = new ListenerLifecycle(
                () -> graph.addListener(listener),
                () -> graph.removeListener(listener));

        assertThat(graph.listenerCount()).isZero();

        lifecycle.open();
        assertThat(graph.listenerCount()).as("open attaches").isEqualTo(1);
        assertThat(lifecycle.attached()).isTrue();

        lifecycle.open();
        assertThat(graph.listenerCount())
                .as("a second open never stacks (CopyOnWriteArrayList would double-fire)")
                .isEqualTo(1);

        lifecycle.close();
        assertThat(graph.listenerCount()).as("close detaches").isZero();
        assertThat(lifecycle.attached()).isFalse();

        lifecycle.open();
        assertThat(graph.listenerCount())
                .as("REOPEN RE-ATTACHES — the bug was a permanent detach on first close")
                .isEqualTo(1);

        lifecycle.close();
        lifecycle.close(); // a double close removes nothing extra
        assertThat(graph.listenerCount()).isZero();
    }

    @Test
    @DisplayName("The attach/detach actions run exactly once per transition")
    void actionsRunOncePerTransition() {
        AtomicInteger attaches = new AtomicInteger();
        AtomicInteger detaches = new AtomicInteger();
        ListenerLifecycle lifecycle = new ListenerLifecycle(
                attaches::incrementAndGet, detaches::incrementAndGet);

        lifecycle.close(); // close before any open detaches nothing
        assertThat(detaches).hasValue(0);

        lifecycle.open();
        lifecycle.open();
        lifecycle.open();
        assertThat(attaches).as("an open storm attaches once").hasValue(1);

        lifecycle.close();
        lifecycle.close();
        assertThat(detaches).as("a close storm detaches once").hasValue(1);

        lifecycle.open();
        assertThat(attaches).hasValue(2);
        lifecycle.close();
        assertThat(detaches).hasValue(2);
    }
}
