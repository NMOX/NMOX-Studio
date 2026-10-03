package org.nmox.studio.editor.snippets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.snippets.SnippetBody.Refused;
import org.nmox.studio.editor.snippets.SnippetBody.Transform;
import org.nmox.studio.editor.snippets.SnippetBody.Variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code /regex/format/flags}: what VS Code's transform answers for the
 * common cases, and the four bounds a stranger's pattern is held to.
 */
class SnippetTransformsTest {

    /** The transform of {@code ${X/…/…/…}}, parsed by the real parser. */
    private static Transform transform(String regexFormatFlags) throws Refused {
        return ((Variable) SnippetBody.parse("${X/" + regexFormatFlags + "}").nodes().get(0)).transform();
    }

    private static String apply(String regexFormatFlags, String input) throws Refused {
        return SnippetTransforms.apply(transform(regexFormatFlags), input);
    }

    @Test
    @DisplayName("the documented one: a file name without its extension")
    void fileNameWithoutExtension() throws Refused {
        assertThat(apply("(.*)\\..+$/$1/", "user.service.ts")).isEqualTo("user.service");
        assertThat(apply("(.*)\\..+$/$1/", "Makefile")).as("no match: the text stays").isEqualTo("Makefile");
    }

    @Test
    @DisplayName("upcase, downcase, capitalize, camelcase and pascalcase")
    void modifiers() throws Refused {
        assertThat(apply("(.*)/${1:/upcase}/", "title")).isEqualTo("TITLE");
        assertThat(apply("(.*)/${1:/downcase}/", "TITLE")).isEqualTo("title");
        assertThat(apply("(.*)/${1:/capitalize}/", "user card")).isEqualTo("User card");
        assertThat(apply("(.*)/${1:/pascalcase}/", "user-profile_card")).isEqualTo("UserProfileCard");
        assertThat(apply("(.*)/${1:/camelcase}/", "User-profile card")).isEqualTo("userProfileCard");
        assertThat(apply("^(.)(.*)$/${1:/upcase}$2/", "widget")).isEqualTo("Widget");
    }

    @Test
    @DisplayName("case folds the same under a Turkish reader")
    void caseIsNotTheReaders() throws Refused {
        java.util.Locale before = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertThat(apply("(.*)/${1:/upcase}/", "title")).isEqualTo("TITLE");
            assertThat(apply("(.*)/${1:/downcase}/", "TITLE")).isEqualTo("title");
        } finally {
            java.util.Locale.setDefault(before);
        }
    }

    @Test
    @DisplayName("without g the first match is replaced; with g every match, and the text between stays")
    void globalFlag() throws Refused {
        assertThat(apply("-/_/", "a-b-c")).isEqualTo("a_b-c");
        assertThat(apply("-/_/g", "a-b-c")).isEqualTo("a_b_c");
        assertThat(apply("[-_](\\w)/${1:/upcase}/g", "user-profile_card")).isEqualTo("userProfileCard");
    }

    @Test
    @DisplayName("i, m and s are honoured")
    void otherFlags() throws Refused {
        assertThat(apply("ABC/x/i", "abc")).isEqualTo("x");
        assertThat(apply("^b/x/m", "a\nb")).isEqualTo("a\nx");
        assertThat(apply("a.b/x/s", "a\nb")).isEqualTo("x");
        assertThat(apply("a.b/x/", "a\nb")).isEqualTo("a\nb");
    }

    @Test
    @DisplayName("conditionals: +if, ?if:else, -else, and an else alone when nothing matched at all")
    void conditionals() throws Refused {
        assertThat(apply("(async )?(.*)/${1:+await }$2/", "async load")).isEqualTo("await load");
        assertThat(apply("(async )?(.*)/${1:+await }$2/", "load")).isEqualTo("load");
        assertThat(apply("(x)?(.*)/${1:?yes:no}/", "x1")).isEqualTo("yes");
        assertThat(apply("(x)?(.*)/${1:?yes:no}/", "1")).isEqualTo("no");
        assertThat(apply("(\\d+)/${1:-none}/", "")).as("no match, and an else branch: the format alone")
                .isEqualTo("none");
        assertThat(apply("(\\d+)/${1:none}/", "abc")).isEqualTo("none");
    }

    @Test
    @DisplayName("a group the pattern does not have is empty, as in VS Code")
    void missingGroupIsEmpty() throws Refused {
        assertThat(apply("(a)/[$1$2$9]/", "a")).isEqualTo("[a]");
    }

    @Test
    @DisplayName("a group that repeats a repeat is refused when the file is read")
    void repeatedRepeatsAreRefused() {
        for (String hostile : new String[] {"(a+)+$", "(\\w*)*x", "((ab)+c)*d", "(a{2,})+", "(?:a+)+", "(.*,)*"}) {
            assertThat(SnippetTransforms.repeatsARepeat(hostile)).as(hostile).isTrue();
        }
    }

    @Test
    @DisplayName("patterns snippets really use are not refused")
    void ordinaryPatternsPass() {
        for (String ordinary : new String[] {"(.*)\\..+$", "^(.)(.*)$", "[-_](\\w)", "(?:^|[-_])(\\w)",
            "([a-z]*)-*([a-z]*)", "(a+)?b", "(ab){2,4}c+", "[(+)+]+x", "\\(a+\\)+", "(a|b)+c", "(.*)", "[]+)]+"}) {
            assertThat(SnippetTransforms.repeatsARepeat(ordinary)).as(ordinary).isFalse();
        }
    }

    @Test
    @DisplayName("refusals name the flag, the modifier, the length and the compiler's complaint")
    void refusalsSpeak() {
        assertThat(SnippetTransforms.refusal(new Transform("a", java.util.List.of(), "gy")))
                .contains("the flag y");
        assertThat(SnippetTransforms.refusal(new Transform("(.*)",
                java.util.List.of(new SnippetBody.FormatGroup(1, "kebabcase", null, null)), "")))
                .contains("/kebabcase");
        assertThat(SnippetTransforms.refusal(new Transform("a".repeat(513), java.util.List.of(), "")))
                .contains("513 characters");
        assertThat(SnippetTransforms.refusal(new Transform("[^]", java.util.List.of(), "")))
                .contains("does not compile");
        assertThat(SnippetTransforms.refusal(new Transform("(.*)\\..+$", java.util.List.of(), "g"))).isNull();
    }

    @Test
    @DisplayName("a pattern the shape check cannot recognise still stops at the clock")
    void theClockStopsWhatTheShapeCheckCannot() throws Refused {
        Transform polynomial = transform("(.*)(.*)(.*)(.*)(.*)(.*)!!/x/");
        assertThat(SnippetTransforms.refusal(polynomial)).as("nothing in it repeats a repeat").isNull();
        long start = System.nanoTime();
        assertThatThrownBy(() -> SnippetTransforms.apply(polynomial, "a".repeat(400)))
                .isInstanceOf(Refused.class).hasMessageContaining("did not finish within 50 ms");
        assertThat((System.nanoTime() - start) / 1_000_000L)
                .as("stopped by the clock, not by finishing").isLessThan(5_000);
        assertThat(SnippetTransforms.apply(polynomial, "aa!!")).as("the same pattern on a kind input").isEqualTo("x");
    }

    @Test
    @DisplayName("more text than the bound is refused before any matching")
    void inputIsBounded() throws Refused {
        Transform any = transform("(.*)/$1/");
        assertThatThrownBy(() -> SnippetTransforms.apply(any, "x".repeat(SnippetTransforms.MAX_INPUT_CHARS + 1)))
                .isInstanceOf(Refused.class).hasMessageContaining("10001 characters");
        assertThat(SnippetTransforms.apply(any, "x".repeat(SnippetTransforms.MAX_INPUT_CHARS))).hasSize(10_000);
    }

    @Test
    @DisplayName("a transform that multiplies its text past the bound is refused")
    void outputIsBounded() throws Refused {
        Transform grower = transform("x/" + "y".repeat(200) + "/g");
        assertThatThrownBy(() -> SnippetTransforms.apply(grower, "x".repeat(1_000)))
                .isInstanceOf(Refused.class).hasMessageContaining("grew the text past");
    }
}
