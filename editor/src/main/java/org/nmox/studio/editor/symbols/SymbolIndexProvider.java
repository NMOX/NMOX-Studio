package org.nmox.studio.editor.symbols;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.nmox.studio.core.spi.SymbolIndex;
import org.nmox.studio.editor.outline.OutlineModel;
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

    @Override
    public Outline outline(File root, String file) {
        if (root == null || file == null || file.isBlank()) {
            return new Outline(List.of(), "no file named");
        }
        Path base;
        Path target;
        try {
            base = root.toPath().toRealPath();
            Path raw = Path.of(file);
            target = (raw.isAbsolute() ? raw : base.resolve(raw)).toRealPath();
        } catch (java.io.IOException | java.nio.file.InvalidPathException missing) {
            return new Outline(List.of(), "no such file: " + file);
        }
        // containment is checked on REAL paths: a/../../b and a symlink out
        // of the project both resolve outside and are refused, never read
        if (!target.startsWith(base)) {
            return new Outline(List.of(), "outside the aimed project: " + file);
        }
        if (!java.nio.file.Files.isRegularFile(target)) {
            return new Outline(List.of(), "not a file: " + file);
        }
        try {
            if (java.nio.file.Files.size(target) > ProjectSymbols.MAX_FILE_BYTES) {
                return new Outline(List.of(), "file larger than " + (ProjectSymbols.MAX_FILE_BYTES / 1024) + " KB: " + file);
            }
            String mime = mimeOf.apply(target);
            if (mime == null || OutlineModel.familyOf(mime) == null) {
                return new Outline(List.of(), "no outline for this file type: " + file);
            }
            String text = java.nio.file.Files.readString(target);
            List<Node> nodes = new ArrayList<>();
            for (OutlineModel.Item item : OutlineModel.extract(mime, text)) {
                nodes.add(new Node(item.name(), item.kind().name(),
                        item.detail() == null ? "" : item.detail(), item.line() + 1, item.depth()));
            }
            return new Outline(List.copyOf(nodes), null);
        } catch (java.io.IOException | java.io.UncheckedIOException unreadable) {
            return new Outline(List.of(), "unreadable: " + file);
        }
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
