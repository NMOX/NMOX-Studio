package org.nmox.studio.editor.outline;

import java.awt.Color;

/**
 * The vocabulary of things an outline can name, each with a one-letter
 * phosphor badge and a colour drawn from the NMOX palette. Kept small
 * and language-agnostic on purpose: a CSS selector, a Markdown heading
 * and a JS class are different ideas, but the navigator only needs to
 * tell them apart at a glance.
 */
public enum OutlineKind {

    CLASS('C', new Color(126, 217, 87)),
    INTERFACE('I', new Color(126, 217, 87)),
    ENUM('E', new Color(180, 142, 247)),
    FUNCTION('ƒ', new Color(94, 168, 255)),
    METHOD('m', new Color(94, 168, 255)),
    FIELD('•', new Color(220, 196, 120)),
    PROPERTY('•', new Color(220, 196, 120)),
    SELECTOR('§', new Color(255, 140, 200)),
    RULE('@', new Color(255, 140, 200)),
    HEADING('#', new Color(126, 217, 87)),
    KEY('k', new Color(220, 196, 120)),
    SECTION('[', new Color(180, 142, 247)),
    TYPE('T', new Color(126, 217, 87)),
    MODULE('M', new Color(180, 142, 247)),
    TEST('✓', new Color(126, 217, 87)),
    TARGET('»', new Color(94, 168, 255)),
    TODO('!', new Color(255, 170, 60));

    private final char glyph;
    private final Color color;

    OutlineKind(char glyph, Color color) {
        this.glyph = glyph;
        this.color = color;
    }

    public char glyph() {
        return glyph;
    }

    public Color color() {
        return color;
    }
}
