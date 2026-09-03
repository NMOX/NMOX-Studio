package org.nmox.studio.core.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadsTest {

    @Test
    @DisplayName("daemon() yields an unstarted, named daemon thread; startDaemon runs it")
    void namedDaemon() throws Exception {
        Thread t = Threads.daemon(() -> { }, "nmox-test-worker");
        assertThat(t.isDaemon()).isTrue();
        assertThat(t.getName()).isEqualTo("nmox-test-worker");
        assertThat(t.isAlive()).isFalse();
        CountDownLatch ran = new CountDownLatch(1);
        Thread s = Threads.startDaemon(ran::countDown, "nmox-test-runner");
        assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(s.isDaemon()).isTrue();
        assertThat(s.getName()).isEqualTo("nmox-test-runner");
    }
}
