package org.nmox.studio.editor.symbols;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.nmox.studio.core.spi.SymbolIndex;
import org.nmox.studio.editor.symbols.ProjectSymbols.Symbol;
import org.openide.util.lookup.ServiceProvider;

/**
 * The editor's {@link SymbolIndex} provider (v2.78.0): the Go to Symbol
 * index (v2.49.0) behind the core seam, so the Agent Port's
 * {@code find_symbol} answers from the SAME index the ⌘I bridge and the
 * jumpto dialog read — one vocabulary, three doors. Prefix matches lead,
 * then substring matches, both over the bridge's folding
 * ({@link SymbolMatch#sigilFree}, {@link SymbolMatch#identifierFold});
 * a blank query answers nothing rather than everything.
 */
@ServiceProvider(service = SymbolIndex.class)
public final class SymbolIndexProvider implements SymbolIndex {

    private final ProjectSymbols index = new ProjectSymbols();
    private final Function<Path, String> mimeOf;

    public SymbolIndexProvider() {
        this(NmoxSymbolProvider::mimeOf);
    }

    /** Test seam: a mime resolver that needs no platform. */
    SymbolIndexProvider(Function<Path, String> mimeOf) {
        this.mimeOf = mimeOf;
    }

    @Override
    public Answer search(File root, String query, int limit) {
        if (root == null || query == null || query.isBlank() || limit <= 0) {
            return new Answer(List.of(), false);
        }
        String needle = fold(query);
        Path base = root.toPath();
        List<Symbol> all = index.refresh(base, mimeOf, v -> false);
        List<Hit> prefix = new ArrayList<>();
        List<Hit> inner = new ArrayList<>();
        for (Symbol s : all) {
            String folded = fold(s.name());
            if (folded.startsWith(needle)) {
                prefix.add(hit(base, s));
            } else if (folded.contains(needle)) {
                inner.add(hit(base, s));
            }
        }
        List<Hit> hits = new ArrayList<>(prefix);
        hits.addAll(inner);
        if (hits.size() > limit) {
            hits = new ArrayList<>(hits.subList(0, limit));
        }
        return new Answer(List.copyOf(hits), index.wasTruncated());
    }

    private static Hit hit(Path base, Symbol s) {
        String rel;
        try {
            rel = base.relativize(s.file()).toString().replace(File.separatorChar, '/');
        } catch (IllegalArgumentException differentRoot) {
            rel = s.file().toString();
        }
        return new Hit(s.name(), s.kind().name(), rel, s.line() + 1);
    }

    static String fold(String name) {
        return SymbolMatch.identifierFold(SymbolMatch.sigilFree(name.strip())).toLowerCase(Locale.ROOT);
    }
}
