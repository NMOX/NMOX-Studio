package org.nmox.studio.core.util;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VS Code's glob dialect, example by example: the ones its documentation
 * gives ("Glob Patterns Reference") and the ones its own matcher's rules
 * imply. A pattern in a repository's settings is a stranger's text, so the
 * refusals and the bounds are tested as hard as the matches.
 */
class VsCodeGlobTest {

    private static boolean match(String pattern, String path) {
        VsCodeGlob g = VsCodeGlob.compile(pattern, false);
        assertThat(g.refusal()).as("%s is read", pattern).isNull();
        return g.matches(path);
    }

    private static void yes(String pattern, String... paths) {
        for (String p : paths) {
            assertThat(match(pattern, p)).as("%s matches %s", pattern, p).isTrue();
        }
    }

    private static void no(String pattern, String... paths) {
        for (String p : paths) {
            assertThat(match(pattern, p)).as("%s does not match %s", pattern, p).isFalse();
        }
    }

    @Test
    @DisplayName("* is zero or more characters inside one path segment")
    void star() {
        yes("*.js", "foo.js", ".js");
        no("*.js", "foo.jss", "folder/foo.js", "some.js/test");
        yes("html.*", "html.js", "html.txt");
        no("html.*", "htm.txt");
        yes("*.*", "html.js");
        no("*.*", "htm");
        yes("node_modules/test/*.js", "node_modules/test/foo.js");
        no("node_modules/test/*.js", "folder/foo.js", "node_modules/test/sub/foo.js");
    }

    @Test
    @DisplayName("? is exactly one character inside a segment")
    void questionMark() {
        yes("*.j?", "foo.js", "foo.jp", "foo.je");
        no("*.j?", "foo.jso", "foo.jpeg", "foo.j", "foo.j/");
        no("a?c", "a/c");
    }

    @Test
    @DisplayName("** is any number of path segments, none included")
    void globstar() {
        yes("**/*.js", "foo.js", "folder/foo.js", "a/b/c/foo.js");
        no("**/*.js", "foo.jss", "some.js/test");
        yes("**/project.json", "project.json", "some/folder/project.json");
        no("**/project.json", "some/folder/file_project.json", "some/rrproject.json");
        yes("some/**/*.js", "some/foo.js", "some/folder/foo.js", "some/a/b/foo.js");
        no("some/**/*.js", "something/foo.js", "something/folder/foo.js");
        yes("**/node_modules/**/*.js", "node_modules/foo.js", "node_modules/some/folder/foo.js",
                "a/node_modules/b/foo.js");
        no("**/node_modules/**/*.js", "foo.js", "folder/foo.js", "node_modules/some/folder/foo.ts");
        yes("**", "foo.js", "a/b/c", "");
        yes("**/**", "foo.js", "a/b/c");
        yes("**/**/*.js", "foo.js", "folder/foo.js");
        no("**/**/*.js", "foo.jss");
    }

    @Test
    @DisplayName("a ** that ends a pattern matches everything beneath the folder, and the folder itself")
    void trailingGlobstar() {
        yes("test/**", "test", "test/foo.js", "test/other/foo.js");
        no("test/**", "est/other/foo.js", "testing", "a/test/foo.js");
        yes("**/node_modules/**", "node_modules", "a/node_modules", "node_modules/foo", "foo/node_modules/foo/bar");
        no("**/node_modules/**", "node_modules_old", "a/node_modulesx/b");
    }

    @Test
    @DisplayName("a pattern is matched against the whole path: without **/ it names entries of the settings' own folder")
    void anchoredToTheFolder() {
        yes("node_modules", "node_modules");
        no("node_modules", "node_module", "test/node_modules", "packages/web/node_modules");
        yes("*.log", "debug.log");
        no("*.log", "logs/debug.log");
        yes("out/dist", "out/dist");
        no("out/dist", "a/out/dist", "out/dist/x.js");
        yes("**/out/dist", "out/dist", "a/out/dist", "a/b/out/dist");
        no("**/out/dist", "xout/dist");
    }

    @Test
    @DisplayName("a trailing slash says nothing more; a leading one never matches a relative path")
    void slashesAtTheEnds() {
        yes("**/node_modules/", "node_modules", "a/node_modules");
        yes("dist/", "dist");
        no("/dist", "dist", "a/dist");
    }

    @Test
    @DisplayName("{} groups alternatives, which may hold slashes and **")
    void braces() {
        yes("{**/*.html,**/*.txt}", "a.html", "docs/a.txt", "a/b/c.html");
        no("{**/*.html,**/*.txt}", "a.htm", "a.txt/b");
        yes("*.{html,js}", "foo.js", "foo.html");
        no("*.{html,js}", "folder/foo.js", "foo.jss", "some.js/test");
        yes("*.{html}", "foo.html");
        no("*.{html}", "foo.js");
        yes("{node_modules,testing}", "node_modules", "testing");
        no("{node_modules,testing}", "node_module", "dtesting");
        yes("**/{foo,bar}", "foo", "bar", "test/foo", "other/more/bar");
        yes("{foo,bar}/**", "foo", "bar", "foo/test", "bar/other/more");
        yes("{**/*.d.ts,**/*.js}", "foo.js", "testing/foo.js", "foo.d.ts", "testing/foo.d.ts");
        no("{**/*.d.ts,**/*.js}", "foo.d", "testing/foo.d");
        yes("{**/*.d.ts,**/*.js,path/simple.jgs}", "path/simple.jgs");
        no("{**/*.d.ts,**/*.js,path/simple.jgs}", "a/path/simple.jgs");
        yes("prefix/{**/*.d.ts,**/*.js,foo.[0-9]}", "prefix/foo.5", "prefix/foo.js", "prefix/a/b.d.ts");
        no("prefix/{**/*.d.ts,**/*.js,foo.[0-9]}", "prefix/bar.5", "prefix/foo.f");
        yes("**/{.git,node_modules}/**", ".git", "a/node_modules/b");
    }

    @Test
    @DisplayName("[] is one character of a range, [!] and [^] one character outside it")
    void brackets() {
        yes("example.[0-9]", "example.0", "example.1", "example.9");
        no("example.[0-9]", "example.a", "example.10", "example.");
        yes("example.[!0-9]", "example.a", "example.b");
        no("example.[!0-9]", "example.0");
        yes("foo.[^0-9]", "foo.f");
        no("foo.[^0-9]", "foo.5", "bar.f");
        yes("foo.[0!^*?]", "foo.0", "foo.!", "foo.^", "foo.*", "foo.?");
        no("foo.[0!^*?]", "foo.5", "foo.8");
        yes("foo.[[]", "foo.[");
        yes("foo.[]]", "foo.]");
        yes("foo.[][!]", "foo.]", "foo.[", "foo.!");
        yes("foo.[]-]", "foo.]", "foo.-");
        yes("[a-c]x", "ax", "bx", "cx");
        no("[a-c]x", "dx", "-x");
        yes("[a-]x", "ax", "-x");
    }

    @Test
    @DisplayName("VS Code's own quirk is kept: a negated class is a regular-expression class, and matches a separator")
    void negatedClassMatchesASeparator() {
        yes("a[!b]c", "axc", "a/c");
        no("a[b]c", "a/c");
    }

    @Test
    @DisplayName("both separators separate, as in VS Code on Windows")
    void backslashIsASeparator() {
        yes("**/*.js", "a\\b\\foo.js");
        no("*.js", "a\\foo.js");
        yes("out/dist", "out\\dist");
    }

    @Test
    @DisplayName("case is ignored only when the caller says the file system ignores it")
    void caseSensitivity() {
        assertThat(VsCodeGlob.compile("**/Node_Modules", false).matches("a/node_modules")).isFalse();
        assertThat(VsCodeGlob.compile("**/Node_Modules", true).matches("a/node_modules")).isTrue();
        assertThat(VsCodeGlob.compile("*.[a-c]", true).matches("x.B")).isTrue();
        assertThat(VsCodeGlob.compile("*.[a-c]", false).matches("x.B")).isFalse();
        assertThat(VsCodeGlob.compile("*.[!a-c]", true).matches("x.B")).as("B is in the range once case is ignored").isFalse();
    }

    @Test
    @DisplayName("the pattern is trimmed, as VS Code trims it")
    void trimmed() {
        yes("  **/dist  ", "a/dist");
        assertThat(VsCodeGlob.compile("  **/dist ", false).pattern()).isEqualTo("**/dist");
    }

    @Test
    @DisplayName("what cannot be read exactly is refused by name and matches nothing")
    void refusals() {
        String[][] cases = {
            {"", "empty"},
            {"   ", "empty"},
            {"foo{bar", "a { with no }"},
            {"foo}bar", "a } with no {"},
            {"foo[bar", "a [ with no ]"},
            {"foo]bar", "a ] with no ["},
            {"a[/]c", "a [ with no ]"},
            {"{a,{b,c}}", "braces inside braces"},
            {"x.[z-a]", "the range z-a runs backwards"},
        };
        for (String[] c : cases) {
            VsCodeGlob g = VsCodeGlob.compile(c[0], false);
            assertThat(g.refusal()).as("the refusal of '%s'", c[0]).isEqualTo(c[1]);
            assertThat(g.matches("foo")).isFalse();
            assertThat(g.matches("")).isFalse();
            assertThat(g.states()).isZero();
        }
        assertThat(VsCodeGlob.compile(null, false).refusal()).isEqualTo("empty");
    }

    @Test
    @DisplayName("a pattern past the length cap is refused; a path past its cap is not matched")
    void caps() {
        String longPattern = "**/" + "a".repeat(VsCodeGlob.MAX_LENGTH);
        assertThat(VsCodeGlob.compile(longPattern, false).refusal())
                .isEqualTo("longer than " + VsCodeGlob.MAX_LENGTH + " characters");
        String atCap = "**/" + "a".repeat(VsCodeGlob.MAX_LENGTH - 3);
        assertThat(VsCodeGlob.compile(atCap, false).refusal()).isNull();

        VsCodeGlob all = VsCodeGlob.compile("**", false);
        assertThat(all.matches("a".repeat(VsCodeGlob.MAX_PATH))).isTrue();
        assertThat(all.matches("a".repeat(VsCodeGlob.MAX_PATH + 1))).as("too long to be judged").isFalse();
        assertThat(all.matches(null)).isFalse();
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("a pattern built to make a backtracking matcher crawl is matched in a blink")
    void neverBacktracks() {
        // every "**/a" can start at every "a/": exponential for a backtracking
        // matcher, and the path ends in the one thing the pattern does not
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            pattern.append("**/a/");
        }
        pattern.append("b");
        VsCodeGlob g = VsCodeGlob.compile(pattern.toString(), false);
        assertThat(g.refusal()).isNull();
        String path = "a/".repeat(500) + "c";
        for (int i = 0; i < 20; i++) {
            assertThat(g.matches(path)).isFalse();
        }
        assertThat(g.matches("a/".repeat(40) + "b")).isTrue();
        assertThat(g.matches("a/".repeat(39) + "b")).as("one a short").isFalse();

        VsCodeGlob stars = VsCodeGlob.compile("*a".repeat(100) + "b", false);
        assertThat(stars.matches("a".repeat(1000))).isFalse();
        assertThat(stars.matches("a".repeat(1000) + "b")).isTrue();
    }

    @Test
    @DisplayName("the cost of a match is its states: a pattern's count is what a cap can add up")
    void statesAreCounted() {
        assertThat(VsCodeGlob.compile("a", false).states()).isEqualTo(2);
        assertThat(VsCodeGlob.compile("**/node_modules", false).states()).isBetween(15, 40);
    }

    @Test
    @DisplayName("the split VS Code splits with: not inside braces or brackets, no empty tail")
    void splitGlobAware() {
        assertThat(VsCodeGlob.split("a/b", '/')).containsExactly("a", "b");
        assertThat(VsCodeGlob.split("a/", '/')).containsExactly("a");
        assertThat(VsCodeGlob.split("/a", '/')).containsExactly("", "a");
        assertThat(VsCodeGlob.split("{a/b,c}/d", '/')).containsExactly("{a/b,c}", "d");
        assertThat(VsCodeGlob.split("a[/]b/c", '/')).containsExactly("a[/]b", "c");
        assertThat(VsCodeGlob.split("a,", ',')).containsExactly("a");
        assertThat(VsCodeGlob.split(",a", ',')).containsExactly("", "a");
    }

    @Test
    @DisplayName("an empty alternative before a comma is kept, one after it is not - VS Code's split")
    void emptyAlternatives() {
        yes("a{,b}", "a", "ab");
        yes("a{b,}", "ab");
        no("a{b,}", "a");
        yes("a{}b", "ab");
    }
}
