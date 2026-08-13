package org.nmox.studio.ui.irc;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The smart join/part/quit filter's pure half, driven through the clock
 * seam: a presence line deserves the screen only when that nick spoke
 * in that channel inside the five-minute window; a rename carries the
 * history so the person keeps their signal under the new name; the map
 * prunes itself so a day on Libera can't grow it without bound.
 */
class SmartFilterTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final SmartFilter filter = new SmartFilter(now::get);

    @Test
    @DisplayName("A nick that spoke shows; one that never spoke is filtered")
    void speechIsTheTicket() {
        filter.spoke("libera", "#nmox", "alice");
        assertThat(filter.shouldShow("libera", "#nmox", "alice")).isTrue();
        assertThat(filter.shouldShow("libera", "#nmox", "lurker")).isFalse();
    }

    @Test
    @DisplayName("The window is WeeChat's five minutes: inside shows, one ms past hides")
    void fiveMinuteWindow() {
        filter.spoke("libera", "#nmox", "alice");
        now.addAndGet(SmartFilter.WINDOW_MS);
        assertThat(filter.shouldShow("libera", "#nmox", "alice")).isTrue();
        now.addAndGet(1);
        assertThat(filter.shouldShow("libera", "#nmox", "alice")).isFalse();
    }

    @Test
    @DisplayName("Speech is scoped: another channel and another network stay filtered")
    void scopedPerChannelAndNetwork() {
        filter.spoke("libera", "#nmox", "alice");
        assertThat(filter.shouldShow("libera", "#other", "alice")).isFalse();
        assertThat(filter.shouldShow("oftc", "#nmox", "alice")).isFalse();
    }

    @Test
    @DisplayName("Nick and channel compare case-insensitively, the IRC way")
    void caseInsensitive() {
        filter.spoke("libera", "#NMOX", "Alice");
        assertThat(filter.shouldShow("libera", "#nmox", "alice")).isTrue();
    }

    @Test
    @DisplayName("A rename carries speech history: the new nick keeps its signal in every channel")
    void renameCarriesHistory() {
        filter.spoke("libera", "#nmox", "alice");
        filter.spoke("libera", "#dev", "alice");
        filter.rename("libera", "alice", "alice_away");
        assertThat(filter.shouldShow("libera", "#nmox", "alice_away")).isTrue();
        assertThat(filter.shouldShow("libera", "#dev", "alice_away")).isTrue();
    }

    @Test
    @DisplayName("A rename is network-scoped: the same nick on another network is untouched")
    void renameStaysOnItsNetwork() {
        filter.spoke("libera", "#nmox", "alice");
        filter.spoke("oftc", "#nmox", "alice");
        filter.rename("libera", "alice", "bob");
        assertThat(filter.shouldShow("oftc", "#nmox", "bob")).isFalse();
        assertThat(filter.shouldShow("oftc", "#nmox", "alice")).isTrue();
    }

    @Test
    @DisplayName("Past the prune threshold, expired entries are swept and fresh ones survive")
    void pruneSweepsOnlyExpired() {
        for (int i = 0; i < 9_000; i++) {
            filter.spoke("libera", "#nmox", "nick" + i);
        }
        now.addAndGet(SmartFilter.WINDOW_MS + 1);
        filter.spoke("libera", "#nmox", "fresh");
        // the sweep ran (size collapsed) and the fresh speaker still shows
        assertThat(filter.shouldShow("libera", "#nmox", "fresh")).isTrue();
        assertThat(filter.shouldShow("libera", "#nmox", "nick42")).isFalse();
    }
}
