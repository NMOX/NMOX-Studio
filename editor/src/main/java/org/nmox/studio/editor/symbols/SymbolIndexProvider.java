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
            // An ABSOLUTE file is honored rather than joined under the root,
            // and that is deliberate rather than leftover (ledger 117). The
            // other side of this seam is the Agent Port, and a SIBLING tool
            // of the same server hands agents absolute paths: EditorState
            // reports every open tab as file.getAbsolutePath(), so
            // editor_state.activeFile and ide_context.activeFile are
            // absolute. "Outline what I am editing" is the obvious next
            // call, and joining that string under the root would answer
            // "no such file" about a file the server had just named.
            //
            // It is also why this site keeps its own containment spelling
            // instead of riding core.util.Containment, whose policy — the
            // right one for the writers it was built for — joins an
            // absolute-looking name under the root (its javadoc pins that
            // with a test). DebugEntries went the joining way in v2.186.0
            // for a reason that does not reach here: npm's spec says `main`
            // is relative, so an absolute start-script target is a
            // machine-specific path nobody portable writes. An absolute
            // path here is not a user's spelling at all — it is the
            // product's own output coming back.
            target = (raw.isAbsolute() ? raw : base.resolve(raw)).toRealPath();
        } catch (java.io.IOException | java.nio.file.InvalidPathException missing) {
            return new Outline(List.of(), "no such file: " + file);
        }
        // containment is checked on REAL paths: a/../../b and a symlink out
        // of the project both resolve outside and are refused, never read.
        // The four refusals below are four different sentences, which is
        // the second reason the shared guard does not fit: it answers one
        // null for "escapes" and for "names the root itself", and this
        // surface says "outside the aimed project" and "not a file" about
        // those — a merged answer would be wrong about the root.
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
            // Only the null-mime half of this guard was ever live.
            // OutlineModel.family() ends in `default -> "generic"` and can
            // never return null, so `familyOf(mime) == null` was a refusal
            // that could not speak. It was written for a model that declines
            // types it does not know; this model deliberately knows them all,
            // falling back to a generic brace-and-indent read that is useful
            // on an unfamiliar TEXT file. Deleting the dead half rather than
            // inventing a refusal to justify it: a file the generic reader
            // cannot decode still refuses, through readString below, naming
            // the real reason.
            if (mime == null) {
                return new Outline(List.of(), "no file type for: " + file);
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
