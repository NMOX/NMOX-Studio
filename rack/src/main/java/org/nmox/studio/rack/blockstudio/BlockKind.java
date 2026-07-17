package org.nmox.studio.rack.blockstudio;

import java.util.List;

/**
 * The vocabulary of Block Studio: every piece the palette offers, with
 * its display face and the parameters it carries. Which kinds may nest
 * inside which is {@link BlockRules}' business; what each kind means in
 * generated code is {@link BlockCodegen}'s.
 *
 * <p>Categories color the palette and the canvas (a hue per family, the
 * Scratch idiom): STRUCTURE builds the shadow-DOM tree, CONTENT fills
 * it, STATE declares component fields, LOGIC reacts to events.
 */
public enum BlockKind {

    /** The root piece: one custom element. Param: its tag name. */
    COMPONENT("Component", Category.STRUCTURE, List.of(
            new Param("tag", "my-widget"))),

    /** A shadow-DOM element. Param: the HTML tag. */
    ELEMENT("Element", Category.STRUCTURE, List.of(
            new Param("tag", "div"))),

    /** A text node; {name} interpolates a declared state field. */
    TEXT("Text", Category.CONTENT, List.of(
            new Param("text", "Hello"))),

    /** An attribute on the parent element. */
    SET_ATTR("Attribute", Category.CONTENT, List.of(
            new Param("name", "class"),
            new Param("value", "box"))),

    /** Inline CSS on the parent element. */
    STYLE("Style", Category.CONTENT, List.of(
            new Param("css", "padding: 8px"))),

    /** A component state field (a private class field). */
    STATE("State", Category.STATE, List.of(
            new Param("name", "count"),
            new Param("initial", "0"))),

    /** An event listener on the parent element; children are actions. */
    ON_EVENT("On event", Category.LOGIC, List.of(
            new Param("event", "click"))),

    /** Assign a state field; {name} in the expression reads a field. */
    SET_STATE("Set state", Category.LOGIC, List.of(
            new Param("name", "count"),
            new Param("expr", "{count} + 1"))),

    /** Toggle a CSS class on the listening element. */
    TOGGLE_CLASS("Toggle class", Category.LOGIC, List.of(
            new Param("class", "active"))),

    /** console.log; {name} interpolates a state field. */
    LOG("Log", Category.LOGIC, List.of(
            new Param("message", "count is {count}"))),

    /** Run child actions only when the comparison holds. */
    IF_STATE("If state", Category.LOGIC, List.of(
            new Param("name", "count"),
            new Param("op", ">"),
            new Param("value", "3")));

    /** Palette families; each paints in its own hue. */
    public enum Category { STRUCTURE, CONTENT, STATE, LOGIC }

    /** One editable parameter: its key and the default a new block gets. */
    public record Param(String key, String defaultValue) { }

    private final String display;
    private final Category category;
    private final List<Param> params;

    BlockKind(String display, Category category, List<Param> params) {
        this.display = display;
        this.category = category;
        this.params = params;
    }

    public String display() {
        return display;
    }

    public Category category() {
        return category;
    }

    public List<Param> params() {
        return params;
    }
}
