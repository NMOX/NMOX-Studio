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
}
