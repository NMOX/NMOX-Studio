package org.nmox.studio.editor.present;

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import org.openide.windows.TopComponent;

/**
 * Presentation Mode reaches the Terminal (v2.88.0, the fourth surface the
 * room must read: code, page, output — and the shell you type into). The
 * platform terminal's {@code Term} is a public-package JComponent whose
 * {@code setFont} re-lays the grid, so every open terminal is found by
 * walking the open windows' component trees for that class — by name, no
 * new module dependency — its font remembered and bumped on entry, and
 * restored PER TERMINAL on leaving (two terminals at two sizes both come
 * back to their own). Live and never persisted: the terminal's own
 * options (which {@code TerminalPhosphor} writes once) are untouched, so
 * a restart is back to the user's font. A terminal opened while
 * presenting keeps its own size — a written limit.
 */
public final class TerminalFont {

    static final String TERM_CLASS = "org.netbeans.lib.terminalemulator.Term";
    static final Predicate<Component> IS_TERM = c -> isTerm(c.getClass());

    /**
     * The terminal module's component is {@code ActiveTerm extends StreamTerm
     * extends Term} — an exact class-NAME match found nothing in the live
     * walk (walk-40). The hierarchy is walked by name so any subclass
     * counts, still without a module dependency.
     */
    static boolean isTerm(Class<?> type) {
        for (Class<?> t = type; t != null; t = t.getSuperclass()) {
            if (TERM_CLASS.equals(t.getName())) {
                return true;
            }
        }
        return false;
    }

    private static final Map<Component, Font> BEFORE = new WeakHashMap<>();

    private TerminalFont() {
    }

    /** EDT. Bumps or restores every open terminal's font; returns how many followed. */
    static int follow(boolean on) {
        List<Container> roots = new ArrayList<>();
        for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
            roots.add(tc);
        }
        return follow(on, roots, IS_TERM, PresentationMode.DELTA_POINTS);
    }

    /** The pure walk: every component under {@code roots} matching {@code isTerm} is bumped (on) or restored (off). */
    static int follow(boolean on, List<? extends Container> roots, Predicate<Component> isTerm, int delta) {
        int n = 0;
        for (Container root : roots) {
            for (Component term : find(root, isTerm)) {
                if (on) {
                    BEFORE.putIfAbsent(term, term.getFont());
                    term.setFont(OutputFont.bumped(BEFORE.get(term), delta));
                } else {
                    Font before = BEFORE.remove(term);
                    if (before != null) {
                        term.setFont(before);
                    }
                }
                n++;
            }
        }
        return n;
    }

    static List<Component> find(Container root, Predicate<Component> isTerm) {
        List<Component> out = new ArrayList<>();
        collect(root, isTerm, out);
        return out;
    }

    private static void collect(Component c, Predicate<Component> isTerm, List<Component> out) {
        if (isTerm.test(c)) {
            out.add(c);
            return; // a terminal's own children are its own business
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, isTerm, out);
            }
        }
    }
}
