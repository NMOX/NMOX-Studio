package org.nmox.studio.rack.blockstudio;

import java.util.MissingResourceException;
import org.openide.util.NbBundle;

/**
 * Block Studio's vocabulary, in the reader's language.
 *
 * <p>{@link BlockKind} holds the English record — the word that names each
 * piece in the docs, in the tutorial and in this codebase — and the palette,
 * the canvas and the validator render it. That is the v2.101.0 rule spelled
 * by where the file sits (v2.132.0): the enum stays in the model, this sits
 * beside the surfaces that paint it.
 *
 * <p>A missing key returns the English unchanged, which is what makes this
 * safe to add one language at a time and what would keep a future kind
 * readable before anybody translates it.
 */
public final class BlockText {

    private BlockText() {
    }

    /** "Element" / "Element" / "元素" — the piece's name on the palette. */
    public static String of(BlockKind kind) {
        try {
            return NbBundle.getMessage(BlockText.class, "BlockKind_" + kind.name());
        } catch (MissingResourceException untranslated) {
            return kind.display();
        }
    }

    /**
     * The canvas's one-line face for a block.
     *
     * <p>Only the no-parameter case is PROSE — every other branch of
     * {@link Block#face()} returns the user's own content (a tag, a string,
     * an attribute), which must be shown exactly as they typed it.
     */
    public static String face(Block block) {
        return block.kind().params().isEmpty() ? of(block.kind()) : block.face();
    }
}
