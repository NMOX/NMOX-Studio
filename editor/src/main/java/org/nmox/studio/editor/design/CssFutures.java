package org.nmox.studio.editor.design;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The everyday-2031 CSS vocabulary the platform cannot complete
 * (futures-2031, the CSS half of F2): a byte-level probe of the
 * RELEASE310 css-lib property database (v2.57.0, control terms
 * container-type=2 and text-wrap=4 PRESENT) found anchor positioning,
 * view transitions, {@code @starting-style}, scroll-driven animations,
 * field-sizing and interpolate-size all ABSENT — so typing
 * {@code anchor-name:} in a stylesheet today gets nothing. This is the
 * catalog plus the two pure detectors the completion provider rides:
 * where a PROPERTY name may be typed, and where a VALUE for one of
 * OUR properties may be typed. Hand scans, not regexes — find-sec-bugs
 * reads the natural patterns as ReDoS-prone and the house law is
 * fix-by-idiom, never exclusion (v1.32.0).
 *
 * <p>Deliberately NOT here: names the platform already completes (the
 * probe's control terms, pinned absent by test), and value functions
 * on platform-known properties ({@code anchor()} inside {@code top:})
 * — offering values on someone else's property is a different surface,
 * recorded as the follow-on. The platform's legacy checker may still
 * WARN on these properties (ledger 71: silencing it is externally
 * blocked three ways) — the completion is the half we own.
 */
public final class CssFutures {

    private CssFutures() {
    }

    /** One property: its allowed keyword values and a one-line meaning. */
    public record Property(String name, List<String> values, String doc) {
    }

    private static final Map<String, Property> PROPERTIES = new LinkedHashMap<>();

    private static void p(String name, String doc, String... values) {
        PROPERTIES.put(name, new Property(name, List.of(values), doc));
    }

    static {
        // anchor positioning (CSS Anchor Positioning Level 1)
        p("anchor-name", "Names this element as an anchor others position against",
                "none", "--");
        p("anchor-scope", "Limits which descendants an anchor name is visible to",
                "none", "all", "--");
        p("position-anchor", "The default anchor this element positions against",
                "auto", "--");
        p("position-area", "Which region around the anchor the element occupies",
                "none", "top", "bottom", "left", "right", "start", "end", "center",
                "span-all", "span-left", "span-right", "span-top", "span-bottom",
                "block-start", "block-end", "inline-start", "inline-end",
                "self-start", "self-end", "x-start", "x-end", "y-start", "y-end");
        p("position-try-fallbacks", "Alternative placements when the element would overflow",
                "none", "flip-block", "flip-inline", "flip-start", "--");
        p("position-try-order", "Which fallback wins when several fit",
                "normal", "most-width", "most-height", "most-block-size", "most-inline-size");
        p("position-try", "Shorthand: try-order then try-fallbacks",
                "normal", "flip-block", "flip-inline", "flip-start");
        p("position-visibility", "Hide the element when its anchor is off-screen or it overflows",
                "always", "anchors-valid", "anchors-visible", "no-overflow");
        // view transitions
        p("view-transition-name", "Tags this element for a named view transition",
                "none", "match-element");
        p("view-transition-class", "Shares a transition's pseudo-element styles across elements",
                "none");
        // scroll-driven animations
        p("scroll-timeline-name", "Names a scroll container as an animation timeline",
                "none", "--");
        p("scroll-timeline-axis", "Which scroll axis drives the timeline",
                "block", "inline", "x", "y");
        p("scroll-timeline", "Shorthand: timeline name then axis",
                "none", "--");
        p("view-timeline-name", "Names this element's visibility as a timeline",
                "none", "--");
        p("view-timeline-axis", "Which axis the view timeline tracks",
                "block", "inline", "x", "y");
        p("view-timeline-inset", "Adjusts the view timeline's start and end",
                "auto");
        p("view-timeline", "Shorthand: view timeline name then axis",
                "none", "--");
        p("animation-timeline", "Drives the animation from a timeline instead of time",
                "auto", "none", "scroll()", "view()", "--");
        p("animation-range", "Where along the timeline the animation starts and ends",
                "normal", "cover", "contain", "entry", "exit",
                "entry-crossing", "exit-crossing");
        p("animation-range-start", "Where along the timeline the animation starts",
                "normal", "cover", "contain", "entry", "exit",
                "entry-crossing", "exit-crossing");
        p("animation-range-end", "Where along the timeline the animation ends",
                "normal", "cover", "contain", "entry", "exit",
                "entry-crossing", "exit-crossing");
        // form controls and sizing
        p("field-sizing", "Lets a form control size itself to its content",
                "fixed", "content");
        p("interpolate-size", "Allows animating to/from intrinsic sizes like auto",
                "numeric-only", "allow-keywords");
    }

    /**
     * Platform-KNOWN properties that gain the anchor functions as values
     * — the one place this catalog reaches onto someone else's property,
     * because {@code top: anchor(--card bottom)} is how anchor positioning
     * is actually written and the platform cannot offer the function. The
     * host list is the spec's inset + sizing properties, nothing else.
     */
    static final List<String> ANCHOR_HOSTS = List.of(
            "top", "right", "bottom", "left",
            "inset", "inset-block", "inset-inline",
            "inset-block-start", "inset-block-end",
            "inset-inline-start", "inset-inline-end",
            "width", "height", "min-width", "min-height",
            "max-width", "max-height", "block-size", "inline-size");

    static final List<String> ANCHOR_FUNCTIONS = List.of("anchor(", "anchor-size(");

    private static final Property ANCHOR_HOST_VALUES = new Property("(anchor host)",
            ANCHOR_FUNCTIONS, "position against an anchor");

    /** At-rules the platform's database predates. */
    static final List<String> AT_RULES = List.of(
            "@starting-style", "@position-try", "@view-transition");

    public static Map<String, Property> properties() {
        return PROPERTIES;
    }

    public static Property property(String name) {
        return PROPERTIES.get(name);
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-';
    }

    /**
     * The typed identifier when the caret sits where a PROPERTY NAME
     * belongs: after the last {@code {} or {@code ;} (whitespace in
     * between), never inside a value (a {@code :} after that boundary
     * means we are past the name), never in a selector. Returns the
     * partial (possibly empty) or null. A leading {@code @} is allowed
     * so at-rules complete from the same spot.
     */
    public static String propertyPrefixAt(String before) {
        int i = before.length();
        int nameStart = i;
        while (nameStart > 0 && (isNameChar(before.charAt(nameStart - 1))
                || before.charAt(nameStart - 1) == '@')) {
            nameStart--;
        }
        String partial = before.substring(nameStart);
        // walk back over whitespace to the boundary character
        int cursor = nameStart;
        while (cursor > 0 && Character.isWhitespace(before.charAt(cursor - 1))) {
            cursor--;
        }
        if (cursor == 0) {
            return null; // top of file: a selector, not a declaration
        }
        // the boundary rule IS the whole law: a value position's
        // preceding non-space character is never { or ; (it is the colon,
        // a value token, or a quote), so values are refused here. A
        // second "colon since the boundary" guard was proven an
        // EQUIVALENT MUTANT in v2.57.0 — the span between boundary and
        // partial is whitespace by construction — and deleted.
        char boundary = before.charAt(cursor - 1);
        if (boundary != '{' && boundary != ';') {
            return null;
        }
        return partial;
    }

    /** A value position for one of OUR properties: the property and the
     *  typed partial, or null. */
    public record ValueContext(Property property, String partial) {
    }

    public static ValueContext valueContextAt(String before) {
        int i = before.length();
        int partialStart = i;
        while (partialStart > 0 && (isNameChar(before.charAt(partialStart - 1))
                || before.charAt(partialStart - 1) == '('
                || before.charAt(partialStart - 1) == ')')) {
            partialStart--;
        }
        String partial = before.substring(partialStart);
        int cursor = partialStart;
        while (cursor > 0 && Character.isWhitespace(before.charAt(cursor - 1))) {
            cursor--;
        }
        if (cursor == 0 || before.charAt(cursor - 1) != ':') {
            return null;
        }
        int nameEnd = cursor - 1;
        while (nameEnd > 0 && Character.isWhitespace(before.charAt(nameEnd - 1))) {
            nameEnd--;
        }
        int nameStart = nameEnd;
        while (nameStart > 0 && isNameChar(before.charAt(nameStart - 1))) {
            nameStart--;
        }
        String name = before.substring(nameStart, nameEnd);
        Property prop = PROPERTIES.get(name);
        if (prop != null) {
            return new ValueContext(prop, partial);
        }
        // a platform-known inset/sizing property: offer the anchor
        // functions only (the platform owns the rest of its values)
        return ANCHOR_HOSTS.contains(name)
                ? new ValueContext(ANCHOR_HOST_VALUES, partial) : null;
    }
}
