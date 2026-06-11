package org.nmox.studio.editor.typing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PairLogicTest {

    @Test
    @DisplayName("Openers auto-close before whitespace, EOL and closers")
    void autoCloseRules() {
        assertThat(PairLogic.shouldAutoClose('(', (char) 0)).isTrue();   // end of file
        assertThat(PairLogic.shouldAutoClose('(', ' ')).isTrue();
        assertThat(PairLogic.shouldAutoClose('{', ')')).isTrue();        // nested call
        assertThat(PairLogic.shouldAutoClose('(', 'a')).isFalse();      // typing before code
        assertThat(PairLogic.shouldAutoClose('"', '"')).isFalse();      // before a quote
        assertThat(PairLogic.shouldAutoClose('`', ' ')).isTrue();       // template literal
        assertThat(PairLogic.shouldAutoClose('x', ' ')).isFalse();      // not a pair char
    }

    @Test
    @DisplayName("Closers and quotes type over their twins")
    void typeOverRules() {
        assertThat(PairLogic.shouldTypeOver(')', ')')).isTrue();
        assertThat(PairLogic.shouldTypeOver('}', '}')).isTrue();
        assertThat(PairLogic.shouldTypeOver('"', '"')).isTrue();
        assertThat(PairLogic.shouldTypeOver('`', '`')).isTrue();
        assertThat(PairLogic.shouldTypeOver(')', '}')).isFalse();
        assertThat(PairLogic.shouldTypeOver('(', '(')).isFalse();       // openers never type over
    }

    @Test
    @DisplayName("Backspace removes both halves of an empty pair")
    void emptyPairRules() {
        assertThat(PairLogic.isEmptyPair('(', ')')).isTrue();
        assertThat(PairLogic.isEmptyPair('{', '}')).isTrue();
        assertThat(PairLogic.isEmptyPair('"', '"')).isTrue();
        assertThat(PairLogic.isEmptyPair('(', ']')).isFalse();
        assertThat(PairLogic.isEmptyPair('"', '\'')).isFalse();
    }
}
