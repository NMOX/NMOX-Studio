package org.nmox.studio.editor.completion;

import javax.swing.text.PlainDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The one document splice behind every completion item. The contract that
 * matters: a stale offset (the user typed on while the popup was open) is
 * a routine race, so it must fail QUIETLY — false back to the caller, no
 * exception escaping toward the platform's red dialog.
 */
class CompletionEditsTest {

    private static PlainDocument doc(String text) throws Exception {
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, text, null);
        return doc;
    }

    @Test
    @DisplayName("replace swaps the prefix range for the completion text")
    void replaceSwapsRange() throws Exception {
        PlainDocument doc = doc("con log");

        boolean applied = CompletionEdits.replace(doc, 0, 3, "console.");

        assertThat(applied).isTrue();
        assertThat(doc.getText(0, doc.getLength())).isEqualTo("console. log");
    }

    @Test
    @DisplayName("zero-length prefix is a pure insert")
    void zeroLengthPrefixInserts() throws Exception {
        PlainDocument doc = doc("ab");

        boolean applied = CompletionEdits.replace(doc, 1, 0, "X");

        assertThat(applied).isTrue();
        assertThat(doc.getText(0, doc.getLength())).isEqualTo("aXb");
    }

    @Test
    @DisplayName("stale offsets past the end fail quietly: false, document untouched, no throw")
    void outOfRangeFailsQuietly() throws Exception {
        PlainDocument doc = doc("short");

        assertThatCode(() -> {
            boolean applied = CompletionEdits.replace(doc, 40, 3, "nope");
            assertThat(applied).isFalse();
        }).doesNotThrowAnyException();
        assertThat(doc.getText(0, doc.getLength())).isEqualTo("short");
    }

    @Test
    @DisplayName("length overrunning the document fails quietly and leaves it untouched")
    void overlongRangeFailsQuietly() throws Exception {
        PlainDocument doc = doc("tiny");

        boolean applied = CompletionEdits.replace(doc, 2, 99, "overrun");

        assertThat(applied).isFalse();
        assertThat(doc.getText(0, doc.getLength())).isEqualTo("tiny");
    }
}
