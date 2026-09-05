package org.nmox.studio.core.spi;

import java.io.File;
import java.util.List;
import org.openide.util.Lookup;

/**
 * The project-symbol seam (v2.78.0): the editor module's Go to Symbol
 * index (v2.49.0, ProjectSymbols over the Navigator outline) published
 * for consumers that must not depend on the editor — the rack's Agent
 * Port, whose {@code find_symbol} tool lets an agent ask "where is
 * {@code checkout}?" without walking the tree itself. Same shape as
 * {@link ProjectAim}/{@link LiveServings}: a soft dependency, absence is
 * an honest lookup miss and the consumer says so.
 *
 * <p>Read-only by construction: the provider reads files (bounded, the
 * index's own caps) and never writes or spawns.
 */
public interface SymbolIndex {

    /** The editor's provider, or null when the editor module is absent. */
    static SymbolIndex find() {
        return Lookup.getDefault().lookup(SymbolIndex.class);
    }

    /** One hit: the symbol, its outline kind, the file relative to the
     *  root, and the 1-based line. */
    record Hit(String name, String kind, String file, int line) {
    }

    /** The hits, bounded by the caller's limit, and whether the index
     *  itself was partial (the walk hit its file cap) — a partial index
     *  must never read as a complete one. */
    record Answer(List<Hit> hits, boolean truncated) {
    }

    /**
     * Symbols under {@code root} whose folded name starts with, then
     * contains, the folded {@code query} (leading stylesheet sigils
     * stripped, non-identifier characters dropped — the ⌘I bridge's own
     * folding), at most {@code limit}. A blank query answers nothing.
     */
    Answer search(File root, String query, int limit);
}
