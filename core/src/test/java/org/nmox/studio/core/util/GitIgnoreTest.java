package org.nmox.studio.core.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.nmox.studio.core.util.GitIgnore.Verdict;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Git's own ignore semantics, rule by rule, from the gitignore(5) page —
 * and the side every doubt errs on: a rule this class cannot read exactly
 * as git does is dropped, because an over-match would tell the platform's
 * git module that a tracked path is ignored.
 */
class GitIgnoreTest {

    private static boolean ignored(String text, String path, boolean dir) {
        GitIgnore g = GitIgnore.parse(text);
        return GitIgnore.isIgnored(path, dir, GitIgnore.empty(),
                d -> d.isEmpty() ? g : GitIgnore.empty());
    }

    @Test
    @DisplayName("A bare name matches at any depth; a trailing slash matches directories only")
    void namesAndDirectories() {
        assertThat(ignored("node_modules/\n", "node_modules", true)).isTrue();
        assertThat(ignored("node_modules/\n", "packages/web/node_modules/x.js", false)).isTrue();
        assertThat(ignored("dist/\n", "dist", false)).as("a FILE named dist").isFalse();
        assertThat(ignored("*.log\n", "a/b/debug.log", false)).isTrue();
        assertThat(ignored("*.log\n", "a/b/debug.logs", false)).isFalse();
    }

    @Test
    @DisplayName("A slash at the start or in the middle anchors to the file's directory")
    void anchoring() {
        assertThat(ignored("/build\n", "build", true)).isTrue();
        assertThat(ignored("/build\n", "src/build", true)).isFalse();
        assertThat(ignored("docs/generated\n", "docs/generated/a.md", false)).isTrue();
        assertThat(ignored("docs/generated\n", "x/docs/generated/a.md", false)).isFalse();
    }

    @Test
    @DisplayName("** leading, trailing and between, each as git reads it")
    void doubleStar() {
        assertThat(ignored("**/foo\n", "foo", false)).isTrue();
        assertThat(ignored("**/foo\n", "a/b/foo", false)).isTrue();
        assertThat(ignored("abc/**\n", "abc/x/y", false)).isTrue();
        assertThat(GitIgnore.parse("abc/**\n").match("abc", true))
                .as("trailing ** is everything INSIDE, not the directory itself")
                .isEqualTo(Verdict.NONE);
        assertThat(ignored("a/**/b\n", "a/b", false)).isTrue();
        assertThat(ignored("a/**/b\n", "a/x/y/b", false)).isTrue();
        assertThat(ignored("a/**/b\n", "c/a/x/b", false)).isFalse();
    }

    @Test
    @DisplayName("* ? and [...] stay inside one segment")
    void wildcardsWithinASegment() {
        assertThat(ignored("src/*.js\n", "src/a.js", false)).isTrue();
        assertThat(ignored("src/*.js\n", "src/sub/a.js", false)).as("* never crosses /").isFalse();
        assertThat(ignored("file?.txt\n", "file1.txt", false)).isTrue();
        assertThat(ignored("file?.txt\n", "file10.txt", false)).isFalse();
        assertThat(ignored("[abc].txt\n", "b.txt", false)).isTrue();
        assertThat(ignored("[!abc].txt\n", "b.txt", false)).isFalse();
        assertThat(ignored("[a-c]x\n", "bx", false)).isTrue();
        assertThat(ignored("[a-c]x\n", "dx", false)).isFalse();
    }

    @Test
    @DisplayName("The last matching rule wins, and ! brings a file back")
    void negation() {
        assertThat(ignored("*.log\n!keep.log\n", "keep.log", false)).isFalse();
        assertThat(ignored("*.log\n!keep.log\n", "drop.log", false)).isTrue();
        assertThat(ignored("!keep.log\n*.log\n", "keep.log", false))
                .as("order matters: the later rule decides").isTrue();
    }

    @Test
    @DisplayName("Nothing inside an excluded directory can be re-included")
    void parentExclusionIsFinal() {
        assertThat(ignored("build/\n!build/keep.txt\n", "build/keep.txt", false)).isTrue();
        assertThat(ignored("build/*\n!build/keep.txt\n", "build/keep.txt", false))
                .as("excluding the CONTENTS is not excluding the directory").isFalse();
    }

    @Test
    @DisplayName("A deeper .gitignore overrides a higher one; info/exclude is lowest")
    void precedenceAcrossFiles() {
        Map<String, GitIgnore> files = new HashMap<>();
        files.put("", GitIgnore.parse("*.gen.js\n"));
        files.put("keep", GitIgnore.parse("!*.gen.js\n"));
        assertThat(GitIgnore.isIgnored("keep/a.gen.js", false, GitIgnore.empty(),
                d -> files.getOrDefault(d, GitIgnore.empty()))).isFalse();
        assertThat(GitIgnore.isIgnored("other/a.gen.js", false, GitIgnore.empty(),
                d -> files.getOrDefault(d, GitIgnore.empty()))).isTrue();

        GitIgnore exclude = GitIgnore.parse("secret.txt\n");
        assertThat(GitIgnore.isIgnored("secret.txt", false, exclude, d -> GitIgnore.empty())).isTrue();
        assertThat(GitIgnore.isIgnored("secret.txt", false, exclude,
                d -> d.isEmpty() ? GitIgnore.parse("!secret.txt\n") : GitIgnore.empty()))
                .as(".gitignore outranks info/exclude").isFalse();
    }

    @Test
    @DisplayName("Comments, blanks, escapes, trailing spaces and CRLF read as git reads them")
    void lexicalRules() {
        GitIgnore g = GitIgnore.parse("# a comment\r\n\r\n\\#hash\r\n\\!bang\r\ntrail   \r\nesc\\ \r\n");
        assertThat(g.size()).isEqualTo(4);
        assertThat(g.match("#hash", false)).isEqualTo(Verdict.IGNORED);
        assertThat(g.match("!bang", false)).isEqualTo(Verdict.IGNORED);
        assertThat(g.match("trail", false)).isEqualTo(Verdict.IGNORED);
        assertThat(g.match("esc ", false)).as("an escaped trailing space is kept").isEqualTo(Verdict.IGNORED);
        assertThat(g.match("# a comment", false)).isEqualTo(Verdict.NONE);
    }

    @Test
    @DisplayName("A line this class cannot read exactly is dropped alone — never approximated")
    void unreadableRulesAreDropped() {
        // an unterminated class, a POSIX class and a dangling escape: git
        // gives each a meaning this subset does not reproduce, so they go
        GitIgnore g = GitIgnore.parse("[abc\n[[:alpha:]]x\nfoo\\\nkept\n");
        assertThat(g.size()).isEqualTo(1);
        assertThat(g.match("kept", false)).isEqualTo(Verdict.IGNORED);
        assertThat(g.match("ax", false)).isEqualTo(Verdict.NONE);
    }

    @Test
    @DisplayName("Bounded: rules past the cap and over-long lines are not read")
    void bounded() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < GitIgnore.MAX_RULES + 50; i++) {
            sb.append("r").append(i).append('\n');
        }
        assertThat(GitIgnore.parse(sb.toString()).size()).isEqualTo(GitIgnore.MAX_RULES);
        assertThat(GitIgnore.parse("x".repeat(GitIgnore.MAX_LINE + 1) + "\n").size()).isZero();
        String deep = "a/".repeat(GitIgnore.MAX_DEPTH + 1) + "f";
        assertThat(ignored("f\n", deep, false)).as("past the depth cap nothing is judged").isFalse();
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    @DisplayName("A pathological pattern costs pattern × name, never exponential")
    void noCatastrophicBacktracking() {
        String pat = "*a".repeat(30) + "b";
        String name = "a".repeat(4000);
        assertThat(GitIgnore.segmentMatches(pat, name)).isFalse();
        String[] p = new String[40];
        java.util.Arrays.fill(p, "**");
        p[39] = "z";
        String[] s = new String[60];
        java.util.Arrays.fill(s, "d");
        assertThat(GitIgnore.segmentsMatch(p, s)).isFalse();
    }

    // ---- the 3.2 review: each case below was answered "ignored" while
    // ---- git kept the file, measured against git check-ignore

    @Test
    @DisplayName("A dropped negation makes the file doubtful, and a doubtful chain never says ignored")
    void droppedNegationIsDoubt() {
        assertThat(ignored("*.log\n!*[[:digit:]].log\n", "a1.log", false))
                .as("git keeps a1.log; the POSIX-class negation was dropped").isFalse();
        assertThat(ignored("*.cfg\n![[:alpha:]]*.cfg\n", "app.cfg", false)).isFalse();
        assertThat(GitIgnore.parse("*.log\n![[:digit:]]\n").doubtful()).isTrue();
        assertThat(GitIgnore.parse("*.log\n[[:digit:]]x\n").doubtful())
                .as("a dropped EXCLUSION under-matches on its own: no doubt").isFalse();
        assertThat(ignored("*.log\n[[:digit:]]x\n", "a.log", false)).isTrue();
        assertThat(GitIgnore.parse("!" + "x".repeat(GitIgnore.MAX_LINE + 1) + "\n").doubtful())
                .as("an over-long negation").isTrue();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < GitIgnore.MAX_RULES; i++) {
            sb.append("r").append(i).append('\n');
        }
        assertThat(GitIgnore.parse(sb + "plain\n").doubtful()).isFalse();
        assertThat(GitIgnore.parse(sb + "plain\n!keep\n").doubtful())
                .as("a negation past the rule cap").isTrue();
        assertThat(GitIgnore.unknown().doubtful()).as("a file that was not read").isTrue();
        assertThat(GitIgnore.isIgnored("x.log", false, GitIgnore.empty(),
                d -> d.isEmpty() ? GitIgnore.parse("*.log\n") : GitIgnore.empty())).isTrue();
        assertThat(GitIgnore.isIgnored("sub/x.log", false, GitIgnore.empty(),
                d -> d.isEmpty() ? GitIgnore.parse("*.log\n")
                        : d.equals("sub") ? GitIgnore.unknown() : GitIgnore.empty()))
                .as("an unread nested file might re-include").isFalse();
        assertThat(GitIgnore.isIgnored("x.log", false, GitIgnore.unknown(),
                d -> d.isEmpty() ? GitIgnore.parse("*.log\n") : GitIgnore.empty()))
                .as("doubt in info/exclude counts too").isFalse();
    }

    @Test
    @DisplayName("A negation matches whatever the case, because git folds case where core.ignorecase is on")
    void negationIsCaseBlind() {
        assertThat(ignored("*.log\n!Keep.log\n", "keep.log", false))
                .as("macOS and Windows repositories keep keep.log").isFalse();
        assertThat(ignored("*.log\n!Keep.log\n", "other.log", false)).isTrue();
        assertThat(ignored("Build/\n", "build", true))
                .as("an exclusion stays exact: under-matching where git folds").isFalse();
        assertThat(ignored("*.LOG\n", "a.log", false)).isFalse();
    }

    @Test
    @DisplayName("? and [...] match one byte, as git's wildmatch does")
    void bytesNotCharacters() {
        assertThat(ignored("?x\n", "éx", false)).as("é is two bytes").isFalse();
        assertThat(ignored("[é]x\n", "éx", false)).isFalse();
        assertThat(ignored("??x\n", "éx", false)).isTrue();
        assertThat(ignored("éx\n", "éx", false)).as("literal text still matches").isTrue();
        assertThat(ignored("*é\n", "caféé", false)).isTrue();
    }

    @Test
    @DisplayName("A space after an escaped backslash is trailing, and trimmed")
    void escapedBackslashThenSpace() {
        GitIgnore g = GitIgnore.parse("foo\\\\ \n");
        assertThat(g.match("foo\\ ", false)).isEqualTo(Verdict.NONE);
        assertThat(g.match("foo\\", false)).isEqualTo(Verdict.IGNORED);
        assertThat(GitIgnore.parse("bar\\\\\\ \n").match("bar\\ ", false))
                .as("three backslashes: the space is escaped").isEqualTo(Verdict.IGNORED);
    }
}
