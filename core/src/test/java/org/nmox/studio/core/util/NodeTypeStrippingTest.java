package org.nmox.studio.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NodeTypeStrippingTest {

    @Test
    @DisplayName("Only TypeScript entries get the flag; declarations never count as entries")
    void argv() {
        assertThat(NodeTypeStripping.argv("index.ts")).containsExactly("node", "--experimental-strip-types", "index.ts");
        assertThat(NodeTypeStripping.argv("src/main.mts")).containsExactly("node", "--experimental-strip-types", "src/main.mts");
        assertThat(NodeTypeStripping.argv("index.js")).containsExactly("node", "index.js");
        assertThat(NodeTypeStripping.isTypeScript("types.d.ts")).isFalse();
        assertThat(NodeTypeStripping.isTypeScript(null)).isFalse();
    }

    @Test
    @DisplayName("Node's 'bad option' refusal of the flag becomes the human wall; other lines pass through")
    void wall() {
        String w = NodeTypeStripping.wall("node: bad option: --experimental-strip-types");
        assertThat(w).startsWith("↳").contains("Node 22.6 or newer").contains("build step");
        assertThat(NodeTypeStripping.wall("node: bad option: --frobnicate")).isNull();
        assertThat(NodeTypeStripping.wall("ExperimentalWarning: Type Stripping is an experimental feature")).isNull();
        assertThat(NodeTypeStripping.wall(null)).isNull();
    }
}
