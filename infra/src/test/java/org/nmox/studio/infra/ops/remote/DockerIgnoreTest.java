package org.nmox.studio.infra.ops.remote;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * The one-sided rule: what this class leaves out, docker leaves out too.
 * The examples are the ones docker's own documentation gives for a
 * {@code .dockerignore}, plus the forms that are deliberately not applied.
 */
class DockerIgnoreTest {

    @Test
    @DisplayName("docker's documented examples: a name at the root, one level down, two levels down, one character")
    void documentedExamples() {
        DockerIgnore d = DockerIgnore.parse("# comment\n*/temp*\n*/*/temp*\ntemp?\n");
        assertThat(d.excludes("somedir/temporary.txt")).as("*/temp* : one level below the root").isTrue();
        assertThat(d.excludes("somedir/temp")).isTrue();
        assertThat(d.excludes("somedir/subdir/temporary.txt")).as("*/*/temp* : two levels below").isTrue();
        assertThat(d.excludes("tempa")).as("temp? : one character").isTrue();
        assertThat(d.excludes("tempb")).isTrue();
        assertThat(d.excludes("temp")).as("temp? needs the one character").isFalse();
        assertThat(d.excludes("temporary.txt")).as("*/temp* does not reach the root").isFalse();
        assertThat(d.excludes("a/b/c/temporary.txt")).as("nor three levels down").isFalse();
        assertThat(d.passedOver()).isEmpty();
        assertThat(d.hasExceptions()).isFalse();
    }

    @Test
    @DisplayName("a name is anchored at the context root; ** reaches any depth; a trailing ** is everything under, not the folder itself")
    void anchoringAndDepth() {
        DockerIgnore d = DockerIgnore.parse("docs\n/build/\n*.log\n**/*.tmp\ndata/**\n**/cache/**/blob\n");
        assertThat(d.excludes("docs")).isTrue();
        assertThat(d.excludes("src/docs")).as("a bare name is not found at depth").isFalse();
        assertThat(d.excludes("build")).as("leading and trailing slashes are cleaned").isTrue();
        assertThat(d.excludes("a.log")).isTrue();
        assertThat(d.excludes("src/a.log")).as("*.log is the root's only").isFalse();
        assertThat(d.excludes("x.tmp")).as("**/ includes no folders at all").isTrue();
        assertThat(d.excludes("a/b/c/x.tmp")).isTrue();
        assertThat(d.excludes("data")).as("data/** is what is under data").isFalse();
        assertThat(d.excludes("data/big.bin")).isTrue();
        assertThat(d.excludes("data/a/b/big.bin")).isTrue();
        assertThat(d.excludes("cache/blob")).isTrue();
        assertThat(d.excludes("x/cache/y/z/blob")).isTrue();
        assertThat(d.excludes("x/cache/y/z/blob2")).isFalse();
    }

    @Test
    @DisplayName("the Dockerfile and the ignore file always travel: docker sends them whatever the file says")
    void theTwoFilesDockerNeeds() {
        DockerIgnore d = DockerIgnore.parse("*\n");
        assertThat(d.excludes("src")).isTrue();
        assertThat(d.excludes("Dockerfile")).isFalse();
        assertThat(d.excludes(".dockerignore")).isFalse();
        assertThat(d.excludes("dockerfile")).as("docker takes the lower-case name when there is no other").isFalse();
        assertThat(d.excludes("sub/Dockerfile")).as("only the context's own").isFalse();
        assertThat(DockerIgnore.parse("**\n").excludes("sub/Dockerfile")).isTrue();
    }

    @Test
    @DisplayName("one exception line and none of the file is applied: half of an exception rule is a different image")
    void exceptionsSwitchItOff() {
        DockerIgnore d = DockerIgnore.parse("*.md\n!README.md\nnode_modules\n");
        assertThat(d.hasExceptions()).isTrue();
        assertThat(d.isEmpty()).isTrue();
        assertThat(d.excludes("CHANGELOG.md")).as("docker excludes it; the upload carries it and docker drops it there").isFalse();
        assertThat(d.excludes("node_modules")).isFalse();
    }

    @Test
    @DisplayName("a form this class does not vouch for is passed over by name, and the other lines still apply")
    void uncertainFormsArePassedOver() {
        DockerIgnore d = DockerIgnore.parse("[a-c]*.txt\nback\\\\slash\n../outside\n.\n/\na**b\ndist\n");
        assertThat(d.passedOver()).containsExactly("[a-c]*.txt", "back\\\\slash", "../outside", ".", "/", "a**b");
        assertThat(d.excludes("a.txt")).isFalse();
        assertThat(d.excludes("dist")).isTrue();
        assertThat(DockerIgnore.parse(null)).isSameAs(DockerIgnore.NONE);
        assertThat(DockerIgnore.parse("  \n# only a comment\n").isEmpty()).isTrue();
        assertThat(DockerIgnore.parse("x".repeat(600)).passedOver()).as("a line too long to be a path").hasSize(1);
    }

    @Test
    @DisplayName("a star is a star when the name holds one, and matching is case-sensitive as docker's is")
    void globDetails() {
        assertThat(DockerIgnore.segmentMatches("*", "*ab")).isTrue();
        assertThat(DockerIgnore.segmentMatches("a*b*c", "aXbYc")).isTrue();
        assertThat(DockerIgnore.segmentMatches("a*b*c", "aXbY")).isFalse();
        assertThat(DockerIgnore.segmentMatches("*.LOG", "a.log")).isFalse();
        assertThat(DockerIgnore.segmentMatches("?", "")).isFalse();
        assertThat(DockerIgnore.segmentMatches("", "")).isTrue();
        assertThat(DockerIgnore.segmentMatches("a*", "a")).isTrue();
    }

    @Test
    @DisplayName("a hostile line cannot make a deploy think forever: many stars, many **, a long path")
    void boundedOnHostileLines() {
        String stars = "*a".repeat(200) + "b";
        String deep = "**/a/".repeat(15) + "**/b";
        DockerIgnore d = DockerIgnore.parse(stars + "\n" + deep + "\n");
        String name = "a".repeat(250);
        String path = "a/".repeat(30) + "c";
        // not a stopwatch on ordinary work: memoized this is microseconds, and without the memo or with a
        // regular expression it is longer than the build — the leash is five seconds so a slow runner never meets it
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            for (int i = 0; i < 200; i++) {
                assertThat(d.excludes(name)).isFalse();
                assertThat(d.excludes(path)).isFalse();
            }
        });
    }

    private static String ch(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    @Test
    @DisplayName("an exception line is seen wherever docker sees one: behind a byte-order mark, behind a no-break space")
    void exceptionsAreSeenAsDockerSeesThem() {
        assertThat(DockerIgnore.parse(ch(0xFEFF) + "!README.md\n*.md\n").hasExceptions())
                .as("docker takes a byte-order mark off the first line").isTrue();
        assertThat(DockerIgnore.parse("*.md\n" + ch(0xA0) + "!README.md\n").hasExceptions())
                .as("Go's TrimSpace takes a no-break space; Java's strip() does not").isTrue();
        assertThat(DockerIgnore.parse("*.md\n \t !README.md\n").hasExceptions()).isTrue();
        DockerIgnore bomOnly = DockerIgnore.parse(ch(0xFEFF) + "docs\n");
        assertThat(bomOnly.excludes("docs")).as("the mark is not part of the first name").isTrue();
        DockerIgnore later = DockerIgnore.parse("docs\n" + ch(0xFEFF) + "!keep\n");
        assertThat(later.hasExceptions()).as("only the FIRST line loses a mark; later it is a character of a name").isFalse();
        assertThat(later.excludes("docs")).isTrue();
    }

    @Test
    @DisplayName("a line ends at a line feed and nowhere else: a form feed or a lone carriage return does not make two names of one")
    void linesEndWhereDockersEnd() {
        DockerIgnore formFeed = DockerIgnore.parse("docs\fbuild\ndist\n");
        assertThat(formFeed.excludes("docs")).as("docker reads ONE pattern here, which names neither folder").isFalse();
        assertThat(formFeed.excludes("build")).isFalse();
        assertThat(formFeed.excludes("dist")).isTrue();
        assertThat(formFeed.passedOver()).containsExactly("docs?build");

        DockerIgnore loneReturn = DockerIgnore.parse("docs\rbuild\n");
        assertThat(loneReturn.excludes("docs")).isFalse();
        assertThat(loneReturn.excludes("build")).isFalse();

        DockerIgnore windows = DockerIgnore.parse("docs\r\nbuild\r\n");
        assertThat(windows.excludes("docs")).as("a carriage return before the line feed is the line's end").isTrue();
        assertThat(windows.excludes("build")).isTrue();
        assertThat(windows.passedOver()).isEmpty();

        DockerIgnore separator = DockerIgnore.parse("docs" + ch(0x2028) + "build\n");
        assertThat(separator.excludes("docs")).as("U+2028 ends a line for Java's \\R, not for docker").isFalse();
        assertThat(separator.excludes("build")).isFalse();
    }

    @Test
    @DisplayName("trimming is Go's: a separator control is a character of the name, and spaces around a name are not")
    void trimmingIsGos() {
        DockerIgnore control = DockerIgnore.parse(ch(0x1C) + "data\n");
        assertThat(control.excludes("data")).as("Java's strip() would take U+001C and exclude data; docker does not").isFalse();
        assertThat(control.passedOver()).containsExactly("?data");
        assertThat(DockerIgnore.parse(" \t dist \t\n").excludes("dist")).isTrue();
        assertThat(DockerIgnore.parse(ch(0xA0) + "dist" + ch(0x3000) + "\n").excludes("dist")).isTrue();
        assertThat(DockerIgnore.trimAsDockerDoes(" " + ch(0x85) + "a b" + ch(0x2003))).isEqualTo("a b");

        DockerIgnore indented = DockerIgnore.parse("  #notes\ndist\n");
        assertThat(indented.excludes("#notes")).as("neither docker's reading (a file called #notes) nor a comment's is applied").isFalse();
        assertThat(indented.excludes("dist")).isTrue();
        assertThat(indented.passedOver()).isEmpty();
    }

    @Test
    @DisplayName("a name docker's releases read differently is the host's to apply: + ( ) | { } ^")
    void releaseDependentCharactersArePassedOver() {
        DockerIgnore d = DockerIgnore.parse("c++\nlib(old)\na|b\nx{1}\n^start*\nclose]\nprice$\nv1.2\ndist\n");
        assertThat(d.passedOver()).containsExactly("c++", "lib(old)", "a|b", "x{1}", "^start*", "close]");
        assertThat(d.excludes("c++")).isFalse();
        assertThat(d.excludes("lib(old)")).isFalse();
        assertThat(d.excludes("price$")).as("every release escapes a dollar").isTrue();
        assertThat(d.excludes("v1.2")).as("and a dot").isTrue();
        assertThat(d.excludes("v1x2")).isFalse();
        assertThat(d.excludes("dist")).isTrue();
        assertThat(DockerIgnore.parse("bad" + ch(0xFFFD) + "bytes\n").passedOver())
                .as("bytes that were not text: the name on disk is not known").containsExactly("bad?bytes");
    }

    @Test
    @DisplayName("a passed-over line is repeated in the log as one short printable line")
    void passedOverLinesAreSafeToRepeat() {
        String shown = DockerIgnore.parse("x".repeat(600) + "\n").passedOver().get(0);
        assertThat(shown).hasSize(81).endsWith(ch(0x2026)).startsWith("xxxx");
        assertThat(DockerIgnore.parse("a" + ch(7) + "b[" + ch(0x1B) + "[31m\n").passedOver()).containsExactly("a?b[?[31m");
        String astral = DockerIgnore.parse("[" + "x".repeat(78) + ch(0x1F600) + "tail\n").passedOver().get(0);
        assertThat(astral).as("never half of a character").isEqualTo("[" + "x".repeat(78) + ch(0x2026));
        assertThat(DockerIgnore.parse("[" + "y".repeat(79) + "\n").passedOver().get(0)).as("exactly the limit is whole").hasSize(80);
    }
}
