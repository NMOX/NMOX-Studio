package org.nmox.studio.editor.standards;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * The section-glob matcher, held to spec.editorconfig.org (the examples
 * below are the spec's and editorconfig-core-test's glob cases) and to the
 * two properties the 3.1 review found missing: a hostile glob cannot make
 * a match take exponential time, and a malformed one never throws.
 */
class EditorConfigGlobTest {

    private static boolean m(String glob, String path) {
        return EditorConfig.glob(glob).matches(path);
    }

    @Test
    @DisplayName("* matches within one path segment, never across /")
    void star() {
        assertThat(m("a*e.c", "ace.c")).isTrue();
        assertThat(m("a*e.c", "abcde.c")).isTrue();
        assertThat(m("a*e.c", "ae.c")).isTrue();
        assertThat(m("a*e.c", "dir/ae.c")).as("no slash: any depth").isTrue();
        assertThat(m("a*e.c", "a/b/e.c")).isFalse();
        assertThat(m("a*e.c", "abd.c")).isFalse();
    }

    @Test
    @DisplayName("** matches across /")
    void starStar() {
        for (String p : List.of("a/z.c", "amnz.c", "am/nz.c", "a/mnz.c", "amn/z.c", "a/mn/z.c")) {
            assertThat(m("a**z.c", p)).as(p).isTrue();
        }
        assertThat(m("a**z.c", "a/mn/z.d")).isFalse();
        assertThat(m("src/**/x.js", "src/a/b/x.js")).isTrue();
        assertThat(m("***.js", "a/b.js")).as("a run of stars is one **").isTrue();
    }

    @Test
    @DisplayName("? matches one character, never /")
    void question() {
        assertThat(m("som?.c", "some.c")).isTrue();
        assertThat(m("som?.c", "som.c")).isFalse();
        assertThat(m("som?.c", "someo.c")).isFalse();
        assertThat(m("a?b", "a/b")).isFalse();
    }

    @Test
    @DisplayName("[seq], [!seq], ranges, a leading ] as a member, never /")
    void brackets() {
        assertThat(m("[ab].a", "a.a")).isTrue();
        assertThat(m("[ab].a", "b.a")).isTrue();
        assertThat(m("[ab].a", "c.a")).isFalse();
        assertThat(m("[!ab].b", "c.b")).isTrue();
        assertThat(m("[!ab].b", "a.b")).isFalse();
        assertThat(m("[d-g].c", "f.c")).isTrue();
        assertThat(m("[d-g].c", "h.c")).isFalse();
        assertThat(m("[!d-g].d", "h.d")).isTrue();
        assertThat(m("[!d-g].d", "e.d")).isFalse();
        assertThat(m("[a-cf-h].e", "b.e")).isTrue();
        assertThat(m("[a-cf-h].e", "g.e")).isTrue();
        assertThat(m("[a-cf-h].e", "d.e")).isFalse();
        assertThat(m("[-]x", "-x")).as("a lone - is a member").isTrue();
        assertThat(m("[a-]x", "-x")).as("a trailing - is a member").isTrue();
        assertThat(m("[]ab].g", "].g")).as("] first is a member").isTrue();
        assertThat(m("[!]ab].g", "].g")).isFalse();
        assertThat(m("[!]ab].g", "c.g")).isTrue();
        assertThat(m("a[!b]c", "a/c")).as("a negated class still never matches /").isFalse();
        assertThat(m("[\\]x].k", "].k")).as("an escaped ] inside a class").isTrue();
    }

    @Test
    @DisplayName("A bracket that would span / is literal, as the spec's tests require")
    void bracketWithSlashIsLiteral() {
        assertThat(m("ab[e/]cd.i", "ab[e/]cd.i")).isTrue();
        assertThat(m("ab[e/]cd.i", "abecd.i")).isFalse();
        assertThat(m("ab[e/]cd.i", "ab/cd.i")).isFalse();
    }

    @Test
    @DisplayName("{a,b} alternatives are globs, nest, and may be empty")
    void braces() {
        assertThat(m("*.{py,js,html}", "test.py")).isTrue();
        assertThat(m("*.{py,js,html}", "dir/test.html")).isTrue();
        assertThat(m("*.{py,js,html}", "test.css")).isFalse();
        assertThat(m("{*.js,*.ts}", "a.ts")).as("an alternative is a glob").isTrue();
        assertThat(m("{a,{b,c}}.x", "c.x")).as("nested").isTrue();
        assertThat(m("{a,{b,c}}.x", "d.x")).isFalse();
        assertThat(m("{,a}.x", ".x")).as("an empty alternative").isTrue();
        assertThat(m("{,a}.x", "a.x")).isTrue();
        assertThat(m("{word,{also},this}.g", "{also}.g")).as("a no-comma inner brace is literal").isTrue();
        assertThat(m("{word,{also},this}.g", "this.g")).isTrue();
    }

    @Test
    @DisplayName("{single}, {} and an unclosed { are literal")
    void literalBraces() {
        assertThat(m("{single}.b", "{single}.b")).isTrue();
        assertThat(m("{single}.b", "single.b")).isFalse();
        assertThat(m("{}.c", "{}.c")).isTrue();
        assertThat(m("{.foo", "{.foo")).isTrue();
        assertThat(m("a}b", "a}b")).isTrue();
    }

    @Test
    @DisplayName("{n1..n2} matches any integer in the range written without leading zeros")
    void numericRanges() {
        for (String n : List.of("3", "15", "60", "120")) {
            assertThat(m("{3..120}", n)).as(n).isTrue();
        }
        for (String n : List.of("1", "121", "060", "0", "-5", "abc")) {
            assertThat(m("{3..120}", n)).as(n).isFalse();
        }
        assertThat(m("{-3..3}", "-3")).isTrue();
        assertThat(m("{-3..3}", "0")).isTrue();
        assertThat(m("{-3..3}", "-4")).isFalse();
        assertThat(m("{-3..3}", "-0")).as("-0 is not how zero is written").isFalse();
        assertThat(m("{5..1}", "3")).as("a reversed range is the same range").isTrue();
        assertThat(m("v{1..3}0.txt", "v20.txt")).as("the number ends where the glob says").isTrue();
        assertThat(m("v{1..1000}.txt", "v999.txt"))
                .as("a range the old code refused past 500 elements").isTrue();
    }

    @Test
    @DisplayName("\\ escapes the next character")
    void escapes() {
        assertThat(m("a\\*b", "a*b")).isTrue();
        assertThat(m("a\\*b", "axb")).isFalse();
        assertThat(m("\\{a,b\\}", "{a,b}")).isTrue();
        assertThat(m("\\{a,b\\}", "a")).isFalse();
        assertThat(m("a\\", "a\\")).as("a trailing backslash is itself").isTrue();
    }

    @Test
    @DisplayName("No slash: any depth; a slash anywhere anchors to the config's directory")
    void anchoring() {
        assertThat(m("x.js", "deep/down/x.js")).isTrue();
        assertThat(m("sub/x.js", "sub/x.js")).isTrue();
        assertThat(m("sub/x.js", "a/sub/x.js")).isFalse();
        assertThat(m("{a/x,y}.js", "q/y.js")).as("a slash inside braces anchors too").isFalse();
        assertThat(m("{a/x,y}.js", "a/x.js")).isTrue();
    }

    /** The review's case: {@code [**a**a…**b]} against {@code aaaa…a.js}. */
    private static String hostileGlob(int groups) {
        return "**a".repeat(groups) + "**b";
    }

    /**
     * Forty a's: a backtracker's work is the number of ways to place the
     * groups among them, worst at half — C(40,20) ≈ 1.4×10¹¹ for twenty.
     */
    private static final String HOSTILE_PATH = "a".repeat(40) + ".js";

    @Test
    @DisplayName("A hostile glob's work is polynomial: counted, not timed")
    void hostileGlobWorkIsBounded() {
        long w10 = -EditorConfig.glob(hostileGlob(10)).evaluate(HOSTILE_PATH) - 1;
        long w20 = -EditorConfig.glob(hostileGlob(20)).evaluate(HOSTILE_PATH) - 1;
        long w40 = -EditorConfig.glob(hostileGlob(40)).evaluate(HOSTILE_PATH) - 1;
        assertThat(w20).as("a miss reports its work").isPositive();
        // a backtracker grows combinatorially in the groups; this grows with the glob
        assertThat(w40).as("doubling the glob at most ~doubles the work (%d -> %d)", w20, w40)
                .isLessThanOrEqualTo(3 * w20);
        assertThat(w20).as("(%d -> %d)", w10, w20).isLessThanOrEqualTo(3 * w10);
        // twenty groups — the backtracker's 1.4×10¹¹ — costs a few thousand visits
        assertThat(w20).isLessThan(20_000);
        // and never past the stated bound: states × (length + 1) × (2 + number width)
        long states = 2L * 40 + 3;
        assertThat(w40).isLessThanOrEqualTo(states * (HOSTILE_PATH.length() + 1)
                * (2 + EditorConfigGlob.MAX_NUMBER_WIDTH));
    }

    @Test
    @DisplayName("A hostile .editorconfig resolves promptly through the real read path")
    void hostileConfigResolvesPromptly(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve(".editorconfig"), "root = true\n[" + hostileGlob(20)
                + "]\nindent_size = 9\n[*]\nindent_size = 2\n");
        File victim = tmp.resolve(HOSTILE_PATH).toFile();
        // the regex translation took 2.3 s over C(28,12) ≈ 3×10⁷ placements; this
        // shape is C(40,20) ≈ 1.4×10¹¹, hours for a backtracker — so ten seconds is
        // four orders of magnitude of margin, not a race against a slow runner
        Map<String, String> props = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> EditorConfig.propertiesFor(victim));
        assertThat(props).containsEntry("indent_size", "2");
    }

    @Test
    @DisplayName("Malformed and hostile globs never throw")
    void neverThrows() {
        for (String g : List.of("[[]", "[", "]", "{", "}", "[!", "[!]", "{a,", "\\", "[a-", "{1..}",
                "{..3}", "{99999999999999999999..1}", "[]", "{{{{", "}}}}", "**[", "[z-a]")) {
            assertThatCode(() -> EditorConfig.glob(g).matches("a/[/b.js")).as(g).doesNotThrowAnyException();
        }
        assertThat(m("[[]", "[")).as("a class holding [").isTrue();
        Random r = new Random(31);
        String alphabet = "*?[]{},!-.\\/a1";
        for (int i = 0; i < 2000; i++) {
            StringBuilder g = new StringBuilder();
            for (int k = r.nextInt(24); k > 0; k--) {
                g.append(alphabet.charAt(r.nextInt(alphabet.length())));
            }
            String glob = g.toString();
            assertThatCode(() -> EditorConfig.glob(glob).matches("a1/b.a-1"))
                    .as(glob).doesNotThrowAnyException();
        }
    }

    /** {@code {,,,…}x}: MAX_ALTERNATIVES + 1 empty alternatives, then an x. */
    private static String tooManyAlternatives() {
        return "{" + ",".repeat(EditorConfigGlob.MAX_ALTERNATIVES) + "}x";
    }

    @Test
    @DisplayName("Past its bounds a glob is refused, says why, and matches nothing")
    void refusals() {
        // one alternative past the cap, short enough to pass the length cap
        EditorConfigGlob tooMany = EditorConfig.glob(tooManyAlternatives());
        assertThat(tooMany.refusal()).contains("alternatives");
        assertThat(tooMany.matches("x")).as("would match, if it were not refused").isFalse();
        assertThat(EditorConfig.glob("{" + ",".repeat(EditorConfigGlob.MAX_ALTERNATIVES - 1) + "}x")
                .matches("x")).as("exactly at the cap is fine").isTrue();

        EditorConfigGlob deep = EditorConfig.glob("{a,".repeat(EditorConfigGlob.MAX_NESTING + 1)
                + "b" + "}".repeat(EditorConfigGlob.MAX_NESTING + 1));
        assertThat(deep.refusal()).contains("nested");
        assertThat(deep.matches("b")).isFalse();

        EditorConfigGlob longGlob = EditorConfig.glob("a".repeat(EditorConfigGlob.MAX_GLOB_LENGTH + 1));
        assertThat(longGlob.refusal()).contains("longer");
        assertThat(longGlob.matches("a".repeat(EditorConfigGlob.MAX_GLOB_LENGTH + 1))).isFalse();

        assertThat(EditorConfig.glob("{a,b}").refusal()).isNull();
    }

    @Test
    @DisplayName("A refused section applies to nothing, the rest still apply, and it is logged once")
    void refusedSectionLoggedOnce(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve(".editorconfig"), "root = true\n[*]\nindent_size = 2\n["
                + tooManyAlternatives() + "]\nindent_size = 7\n[[[]]\nindent_style = tab\n");
        List<LogRecord> seen = new ArrayList<>();
        Handler tap = new Handler() {
            @Override
            public void publish(LogRecord r) {
                seen.add(r);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger log = Logger.getLogger(EditorConfig.class.getName());
        Level before = log.getLevel();
        log.setLevel(Level.ALL);
        log.addHandler(tap);
        try {
            File file = tmp.resolve("x").toFile();
            for (int i = 0; i < 3; i++) {
                assertThat(EditorConfig.propertiesFor(file))
                        .containsEntry("indent_size", "2")
                        .doesNotContainKey("indent_style");
            }
        } finally {
            log.removeHandler(tap);
            log.setLevel(before);
        }
        assertThat(seen).as("three resolves, one refusal, one log line").hasSize(1);
        assertThat(String.valueOf(seen.get(0).getParameters()[2])).contains("alternatives");
    }
}
