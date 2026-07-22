package org.nmox.studio.editor.languages;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import org.netbeans.api.lexer.Language;

/**
 * The one safe way a CSL {@code DefaultLanguageConfig} resolves its lexer
 * language for a TextMate-backed mime.
 *
 * <p>Every language config here answers {@code getLexerLanguage()} with the
 * lexer {@code Language} registered for its own mime. On the normal path the
 * TextMate grammar provider claims that mime, so {@link Language#find} returns
 * the grammar's language. But if the CSL provider is consulted before the
 * TextMate one has claimed the mime (a startup/document-load ordering race),
 * {@code Language.find(mime)} falls back into the CSL editor kit, which asks
 * the config for its lexer language again — {@code getLexerLanguage → find →
 * kit → getLexerLanguage → …} — a direct, single-threaded cycle that blows the
 * stack.
 *
 * <p>That {@code StackOverflowError} is not merely the loss of one file's
 * lexer: it struck inside the static initializer of
 * {@code org.openide.util.Exceptions$OwnLevel} once, poisoning it for the
 * whole session, so afterwards every benign exception the platform logs
 * surfaced as a red "Could not initialize class …OwnLevel" error. Found live
 * on a Nim learning space (v1.109.0).
 *
 * <p>This guard breaks the cycle: a re-entrant call for the same mime on the
 * same thread returns {@code null} instead of recursing. The CSL kit handles a
 * null lexer gracefully (TextMate highlighting is a separate layer and is
 * unaffected), and the next non-re-entrant resolution succeeds — so the only
 * observable change is that a fatal overflow becomes a quiet, momentary null.
 */
public final class Lexers {

    /** Mimes whose resolution is in flight on this thread — the cycle detector. */
    private static final ThreadLocal<Set<String>> IN_FLIGHT =
            ThreadLocal.withInitial(HashSet::new);

    private Lexers() {
    }

    /** The lexer language for {@code mime}, or null if resolving it re-enters. */
    public static Language<?> find(String mime) {
        return findWith(mime, Language::find);
    }

    /**
     * Cycle-guarded resolution with an injectable resolver (the production
     * resolver is {@link Language#find}; tests supply a recursive one to prove
     * the guard converts an overflow into a null).
     */
    static Language<?> findWith(String mime, Function<String, Language<?>> resolver) {
        Set<String> inFlight = IN_FLIGHT.get();
        if (!inFlight.add(mime)) {
            // re-entrant: the CSL fallback looped back before the TextMate
            // provider claimed this mime — null breaks the cycle, no overflow
            return null;
        }
        try {
            return resolver.apply(mime);
        } finally {
            inFlight.remove(mime);
        }
    }
}
