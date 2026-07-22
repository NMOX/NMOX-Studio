package org.nmox.studio.editor.languages;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.netbeans.api.lexer.Language;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lexer-resolution guard: the re-entrant path that once overflowed the
 * stack (and poisoned {@code Exceptions$OwnLevel} for the whole session) now
 * returns null on the second entry, and the normal non-re-entrant path is a
 * transparent pass-through.
 */
class LexersTest {

    @Test
    @DisplayName("A resolver that re-enters for the same mime is broken with null, not an overflow")
    void reEntrantResolutionReturnsNullInsteadOfOverflowing() {
        AtomicInteger calls = new AtomicInteger();
        // mimics Language.find falling back into the CSL kit, which calls the
        // config's getLexerLanguage → Lexers.find(sameMime) again. Without the
        // guard this recurses until the stack blows.
        Function<String, Language<?>> reentrant = new Function<>() {
            @Override
            public Language<?> apply(String mime) {
                calls.incrementAndGet();
                return Lexers.findWith(mime, this);
            }
        };

        Language<?> result = Lexers.findWith("text/x-nim", reentrant);

        assertThat(result).as("the cycle resolves to null, never a StackOverflowError").isNull();
        assertThat(calls.get())
                .as("the resolver runs exactly once; the re-entry short-circuits")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Non-re-entrant resolution passes the resolver's value straight through")
    void normalResolutionIsTransparent() {
        // a resolver that does NOT re-enter — the guard must not alter its result
        Language<?> sentinel = null; // Language has no cheap public instance; null is a valid lexer answer
        Function<String, Language<?>> plain = mime -> sentinel;
        assertThat(Lexers.findWith("text/x-rust", plain)).isSameAs(sentinel);
    }

    @Test
    @DisplayName("The in-flight set is cleared after each resolution, so a later call re-resolves")
    void guardDoesNotLatchAcrossCalls() {
        AtomicInteger calls = new AtomicInteger();
        Function<String, Language<?>> counting = mime -> {
            calls.incrementAndGet();
            return null;
        };
        Lexers.findWith("text/x-nim", counting);
        Lexers.findWith("text/x-nim", counting);
        assertThat(calls.get())
                .as("each top-level call resolves; the guard only blocks re-entry within one call")
                .isEqualTo(2);
    }
}
