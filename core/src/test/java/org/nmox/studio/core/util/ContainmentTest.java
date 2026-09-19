package org.nmox.studio.core.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two policies of ledger 111, each decided once and pinned here.
 * The four surfaces that used to spell this themselves keep their own
 * refusal SENTENCES; what they no longer keep is their own rule.
 */
class ContainmentTest {

    /** A platform without symlinks has nothing to prove in these two. */
    private static boolean linked(Path from, Path to) {
        try {
            Files.createSymbolicLink(from, to);
            return true;
        } catch (UnsupportedOperationException | java.io.IOException noSymlinks) {
            return false;
        }
    }

    @Test
    @DisplayName("ordinary relative paths resolve inside, traversal escapes are refused")
    void theEverydayCases(@TempDir Path tmp) throws Exception {
        File root = Files.createDirectories(tmp.resolve("root")).toFile();
        Files.writeString(tmp.resolve("SECRET.txt"), "not yours");

        assertThat(Containment.resolve(root, "hello.txt")).isNotNull();
        assertThat(Containment.resolve(root, "src/Main.elm")).isNotNull();
        assertThat(Containment.resolve(root, "docker/nginx.conf")).isNotNull();

        assertThat(Containment.resolve(root, "../SECRET.txt")).isNull();
        assertThat(Containment.resolve(root, "../../../.zshrc")).isNull();
        assertThat(Containment.resolve(root, "a/../../b")).isNull();
        assertThat(Containment.resolve(root, "..")).isNull();

        // a guard that cannot be asked must refuse
        assertThat(Containment.resolve(root, null)).isNull();
        assertThat(Containment.resolve(null, "x")).isNull();
        assertThat(Containment.resolve(root, "   ")).isNull();
    }

    @Test
    @DisplayName("an absolute-looking relative is JOINED under the root, not honored as absolute")
    void absoluteIsJoined(@TempDir Path tmp) throws Exception {
        // new File(root, "/etc/passwd") lands at root/etc/passwd — the
        // platform's own rule, kept because it is what every caller
        // already had and it is contained
        File root = Files.createDirectories(tmp.resolve("root")).toFile();
        File resolved = Containment.resolve(root, "/etc/passwd");
        assertThat(resolved).isNotNull();
        // plain path comparison: AssertJ's Path assertions consult the
        // real filesystem, and these targets deliberately do not exist
        assertThat(resolved.toPath().startsWith(root.getCanonicalFile().toPath())).isTrue();
        assertThat(resolved.toPath().endsWith(Path.of("etc", "passwd"))).isTrue();
    }

    @Test
    @DisplayName("THE SYMLINK LAW: the answer is the RESOLVED path, never the path as spelled")
    void answersTheResolvedPath(@TempDir Path tmp) throws Exception {
        File root = Files.createDirectories(tmp.resolve("root")).toFile();
        Files.createDirectories(root.toPath().resolve("real"));
        if (!linked(root.toPath().resolve("link"), root.toPath().resolve("real"))) {
            return;
        }
        Files.writeString(root.toPath().resolve("real/note.txt"), "the real file");

        File resolved = Containment.resolve(root, "link/note.txt");
        assertThat(resolved).isNotNull();
        // the defect this law exists for: LearningSpace canonicalized
        // BOTH sides to decide, then returned new File(dir, path) — so
        // the check judged one file and the caller opened another. The
        // guard hands back the file it actually judged.
        assertThat(resolved.getPath())
                .as("the guard must answer the file it judged, not the spelling")
                .doesNotContain("link")
                .contains("real");
        assertThat(Files.readString(resolved.toPath())).isEqualTo("the real file");
    }

    @Test
    @DisplayName("THE SYMLINK LAW: a link leaving the root is refused, a link staying inside is not")
    void refusesLinksThatLeave(@TempDir Path tmp) throws Exception {
        File root = Files.createDirectories(tmp.resolve("root")).toFile();
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "not yours");
        if (!linked(root.toPath().resolve("escape"), outside)) {
            return;
        }
        Files.createDirectories(root.toPath().resolve("inside"));
        if (!linked(root.toPath().resolve("staying"), root.toPath().resolve("inside"))) {
            return;
        }

        assertThat(Containment.resolve(root, "escape/secret.txt")).isNull();
        assertThat(Containment.resolve(root, "escape/anything-new.txt")).isNull();
        assertThat(Containment.resolve(root, "staying/fine.txt")).isNotNull();
    }

    @Test
    @DisplayName("THE ROOT-EQUALS-ROOT LAW: the root itself is not a file inside the root")
    void refusesTheRootItself(@TempDir Path tmp) throws Exception {
        File root = Files.createDirectories(tmp.resolve("root")).toFile();
        // every caller asks "which FILE inside the root?" — three then
        // call isFile(), the fourth writes bytes. Accepting the root
        // only moves the refusal into an isFile() false or a raw
        // "Is a directory" from the OS, which no longer names
        // containment and no longer belongs to the speaking surface.
        assertThat(Containment.resolve(root, "")).isNull();
        assertThat(Containment.resolve(root, ".")).isNull();
        assertThat(Containment.resolve(root, "sub/..")).isNull();
        assertThat(Containment.resolve(root, "./")).isNull();
    }

    @Test
    @DisplayName("a sibling whose name merely starts with the root's is not inside it")
    void siblingPrefixIsNotContainment(@TempDir Path tmp) throws Exception {
        // the segment-wise rule: /a/bc never "starts with" /a/b. The
        // string-prefix spelling needed a trailing separator to get
        // this right, and one of the four surfaces had to remember it
        File root = Files.createDirectories(tmp.resolve("site")).toFile();
        Files.createDirectories(tmp.resolve("site-backup"));
        Files.writeString(tmp.resolve("site-backup/secret.txt"), "not yours");
        assertThat(Containment.resolve(root, "../site-backup/secret.txt")).isNull();
    }

    @Test
    @DisplayName("resolvePath answers the same decision, shaped for java.nio callers")
    void pathShapeAgrees(@TempDir Path tmp) throws Exception {
        File root = Files.createDirectories(tmp.resolve("root")).toFile();
        assertThat(Containment.resolvePath(root, "Dockerfile"))
                .isEqualTo(Containment.resolve(root, "Dockerfile").toPath());
        assertThat(Containment.resolvePath(root, "../escape")).isNull();
    }
}
