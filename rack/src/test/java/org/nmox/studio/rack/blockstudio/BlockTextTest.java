package org.nmox.studio.rack.blockstudio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Block Studio's palette speaks the reader's language, and its canvas shows
 * the user's own content untouched.
 *
 * <p>The second half is the one worth a test. A block's canvas face is
 * usually the user's content — a tag they typed, a string they wrote, an
 * attribute they set — and that must appear exactly as entered whatever
 * language the IDE speaks. Only a piece with NO parameters has a face made
 * of our prose.
 */
class BlockTextTest {

    private final Locale started = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(started);
    }

    /**
     * Pieces whose German reads exactly like the English, because it IS the
     * German word. Blessed in writing rather than worked around: equalling
     * the English is evidence of an untranslated key, not proof of one
     * (the v2.127.0 rule), so each of these is a decision somebody made.
     */
    private static final List<String> SAME_WORD_IN_GERMAN =
            List.of("ELEMENT", "TEXT", "SLOT", "TIMER");

    @Test
    @DisplayName("every piece on the palette is named in the reader's language")
    void everyPieceSpeaks() {
        Locale.setDefault(Locale.GERMAN);
        List<String> english = new ArrayList<>();
        for (BlockKind k : BlockKind.values()) {
            if (SAME_WORD_IN_GERMAN.contains(k.name())) {
                // still checked, just for presence rather than difference
                assertThat(BlockText.of(k)).as("%s must still carry a key", k)
                        .isEqualTo(k.display());
                continue;
            }
            if (BlockText.of(k).equals(k.display())) {
                english.add(k.name());
            }
        }
        assertThat(english).as("pieces a German reader would still meet in English")
                .isEmpty();
    }

    @Test
    @DisplayName("the English record never moves")
    void englishStaysTheRecord() {
        Locale.setDefault(Locale.GERMAN);
        assertThat(BlockKind.ON_EVENT.display())
                .as("the word the docs, the tutorial and this codebase use")
                .isEqualTo("On event");
    }

    @Test
    @DisplayName("a block's face is the user's own content, in every language")
    void theFaceIsTheUsersContent() {
        Locale.setDefault(Locale.GERMAN);
        Block element = new Block("b1", BlockKind.ELEMENT);
        element.setParam("tag", "section");
        assertThat(BlockText.face(element))
                .as("a tag the user typed is not ours to translate")
                .isEqualTo("section");

        Block text = new Block("b2", BlockKind.TEXT);
        text.setParam("text", "Hallo Welt");
        assertThat(BlockText.face(text)).contains("Hallo Welt");
    }

    @Test
    @DisplayName("today every face is the user's content, and the prose branch waits")
    void noPieceIsParameterlessToday() {
        List<BlockKind> bare = new ArrayList<>();
        for (BlockKind k : BlockKind.values()) {
            if (k.params().isEmpty()) {
                bare.add(k);
            }
        }
        // Measured, not assumed: all fifteen pieces carry at least one
        // parameter, so Block.face() has never once returned its kind's
        // English — and neither does BlockText.face. The guard stays because
        // Block.face() has the same one and the two must agree; this test is
        // what keeps that written down rather than discovered later.
        assertThat(bare).as("if a parameterless piece is added, its face becomes "
                + "PROSE — BlockText.face already translates it, and this "
                + "sentence is how the next author finds that out").isEmpty();

        Locale.setDefault(Locale.GERMAN);
        for (BlockKind k : BlockKind.values()) {
            Block b = new Block("b", k);
            assertThat(BlockText.face(b)).as("%s", k).isEqualTo(b.face());
        }
    }
}
