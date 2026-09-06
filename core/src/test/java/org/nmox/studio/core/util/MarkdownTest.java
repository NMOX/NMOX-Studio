package org.nmox.studio.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownTest {

    @Test
    @DisplayName("three backticks unless the body carries a run that long — then one more (CommonMark closes on any run ≥ the opener)")
    void fenceRule() {
        assertThat(Markdown.fenceFor("plain")).isEqualTo("```");
        assertThat(Markdown.fenceFor("a `code` span")).isEqualTo("```");
        assertThat(Markdown.fenceFor("```js\nx\n```")).isEqualTo("````");
        assertThat(Markdown.fenceFor("`````")).isEqualTo("``````");
    }
}
