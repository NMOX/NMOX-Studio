package org.nmox.studio.rack.blockstudio.search;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ⌘I half of the v1.79.0 studio-law parity: pure matching and the
 * published-snapshot round-trip (the provider itself just wraps these).
 */
class BlockSearchProviderTest {

    @AfterEach
    void resetPublished() {
        BlockSearchProvider.reset();
    }

    @Test
    @DisplayName("'blocks', 'component' and the tag match; noise doesn't")
    void searchMatching() {
        var snap = new BlockSearchProvider.Snapshot("my-counter", 6);
        assertThat(BlockSearchProvider.matches("blocks", snap)).isTrue();
        assertThat(BlockSearchProvider.matches("BLOCK", snap)).isTrue();
        assertThat(BlockSearchProvider.matches("component", snap)).isTrue();
        assertThat(BlockSearchProvider.matches("my-counter", snap)).isTrue();
        assertThat(BlockSearchProvider.matches("counter", snap)).isTrue();
        assertThat(BlockSearchProvider.matches("kubernetes", snap)).isFalse();
        assertThat(BlockSearchProvider.matches("", snap)).isFalse();
        assertThat(BlockSearchProvider.matches(null, snap)).isFalse();
        // no snapshot yet: the generic entries still reach the studio
        assertThat(BlockSearchProvider.matches("blocks", null)).isTrue();
    }

    @Test
    @DisplayName("Label carries tag and piece count; publish/reset round-trips")
    void searchLabelAndPublish() {
        assertThat(BlockSearchProvider.label(null)).contains("Block Studio");
        BlockSearchProvider.publish("my-widget", 1);
        assertThat(BlockSearchProvider.label(BlockSearchProvider.currentSnapshot()))
                .isEqualTo("Block Studio — <my-widget> (1 piece)");
        BlockSearchProvider.publish("my-widget", 6);
        assertThat(BlockSearchProvider.label(BlockSearchProvider.currentSnapshot()))
                .isEqualTo("Block Studio — <my-widget> (6 pieces)");
    }
}
