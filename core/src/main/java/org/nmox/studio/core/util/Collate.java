package org.nmox.studio.core.util;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;

/**
 * Two orderings, and the difference between them is who is reading
 * (v2.104.0) — the {@link Clocks} split, applied to alphabetical order.
 *
 * <p>A list a PERSON reads should be in their language's alphabetical
 * order. Sorting by code point is not alphabetical order in any language
 * that has letters past ASCII: measured here before this class was
 * written, {@code Ćwiczenie} lands after {@code Zamknij} in Polish,
 * {@code Ändern} after {@code Zoom} in German, {@code Ідея} after
 * {@code Явище} in Ukrainian, and {@code Ăn} after {@code Xem} in
 * Vietnamese. Every accented word is exiled to the end of the list, which
 * is exactly where a reader will not look for it.
 *
 * <p>A list a MACHINE reads — a wire order tests pin, a deterministic
 * drop-in order, a set of keys compared between two runs — must not move
 * with the reader at all, and for those the natural {@code String} order is
 * the right answer. So neither is right by default, and each site says
 * which it is.
 *
 * <p>Collators are stateful and not thread-safe, so one is built per call
 * rather than cached — which also means the order follows a live language
 * switch (v2.103.0), the same reason {@link Clocks#displayFormatter()}
 * builds per call.
 */
public final class Collate {

    private Collate() {
    }

    /**
     * The reader's own alphabetical order, ignoring case and accents as
     * their language does. Fresh per call: collators are not thread-safe,
     * and the language can change while the IDE runs.
     */
    public static Collator display() {
        return display(Locale.getDefault());
    }

    /** {@link #display()} in a given language — the seam the tests drive. */
    public static Collator display(Locale locale) {
        Collator c = Collator.getInstance(locale);
        // TERTIARY would order "a" before "A"; a list of names read by a
        // person wants case to be a tiebreak, not a section break
        c.setStrength(Collator.SECONDARY);
        return c;
    }

    /** Orders anything by a name a person reads, in that person's language. */
    public static <T> Comparator<T> byDisplayName(Function<T, String> name) {
        Collator collator = display();
        return (a, b) -> {
            int byName = collator.compare(nullSafe(name.apply(a)), nullSafe(name.apply(b)));
            // a collator calls "resume" and "résumé" equal at SECONDARY
            // strength; a list must still have ONE order, so fall through to
            // the code points rather than letting sort order wobble
            return byName != 0 ? byName
                    : nullSafe(name.apply(a)).compareTo(nullSafe(name.apply(b)));
        };
    }

    /**
     * The order that does not move for anyone: code points. For a wire
     * order, a drop-in reading order, or anything two machines compare —
     * never for a list on screen.
     */
    public static <T> Comparator<T> stableBy(Function<T, String> key) {
        return Comparator.comparing(t -> nullSafe(key.apply(t)));
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
