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

    /**
     * A platform that refuses symbolic links has nothing to prove in the
     * link tests — but it must SAY so. This used to answer a boolean the
     * callers turned into a bare {@code return}, which made the whole
     * test body vanish with a green tick: the v2.186.0 {@code argvPinned}
     * defect, a test that quietly does nothing. An assumption skips it in
     * the open instead.
     */
    private static void assumeLinked(Path from, Path to) {
        try {
            Files.createSymbolicLink(from, to);
        } catch (UnsupportedOperationException | java.io.IOException noSymlinks) {
            org.junit.jupiter.api.Assumptions.abort(
                    "this platform will not create symbolic links: " + noSymlinks);
        }
    }

    @Test
    @DisplayName("ordinary relative paths resolve inside, traversal escapes are refused")
    void theEverydayCases(@TempDir Path tmp) throws Exception {
        File root = Files.createDirectories(tmp.resolve("root")).toFile();
        Files.writeString(tmp.resolve("SECRET.txt"), "not yours");

        // the ANSWER, not merely non-null: the resolution re-appends the
        // components it had to pop off to find an existing ancestor, and
        // re-appending them in the wrong order still returns a contained
        // file, so only the spelling can catch it
        assertThat(Containment.resolve(root, "hello.txt").toPath())
                .isEqualTo(root.getCanonicalFile().toPath().resolve("hello.txt"));
        assertThat(Containment.resolve(root, "src/Main.elm").toPath())
                .isEqualTo(root.getCanonicalFile().toPath().resolve(Path.of("src", "Main.elm")));
        assertThat(Containment.resolve(root, "docker/nginx.conf").toPath())
                .isEqualTo(root.getCanonicalFile().toPath().resolve(Path.of("docker", "nginx.conf")));

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
        assumeLinked(root.toPath().resolve("link"), root.toPath().resolve("real"));
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
        assumeLinked(root.toPath().resolve("escape"), outside);
        Files.createDirectories(root.toPath().resolve("inside"));
        assumeLinked(root.toPath().resolve("staying"), root.toPath().resolve("inside"));

        assertThat(Containment.resolve(root, "escape/secret.txt")).isNull();
        assertThat(Containment.resolve(root, "escape/anything-new.txt")).isNull();
        assertThat(Containment.resolve(root, "staying/fine.txt")).isNotNull();

        // THE ANSWER, not merely non-null. A platform that resolved no
        // link at all would return root/staying/fine.txt, which is still
        // contained and still passes the line above — which is how the
        // absent-leaf hole could sit under a green assertion. The link
        // must be GONE from the answer, whether or not the leaf exists.
        Path canonicalRoot = root.getCanonicalFile().toPath();
        assertThat(Containment.resolve(root, "staying/fine.txt").toPath())
                .as("an absent leaf behind an inside link still resolves the link")
                .isEqualTo(canonicalRoot.resolve(Path.of("inside", "fine.txt")));
        assertThat(Containment.resolve(root, "staying/deep/new.txt").toPath())
                .as("a whole absent tail is re-appended to the RESOLVED ancestor")
                .isEqualTo(canonicalRoot.resolve(Path.of("inside", "deep", "new.txt")));
    }

    @Test
    @DisplayName("THE ABSENT-LEAF LAW: the answer does not depend on whether the leaf exists yet")
    void theAnswerDoesNotDependOnTheLeafExisting(@TempDir Path tmp) throws Exception {
        // The windows-latest defect of ledger 111, stated as the property
        // it broke rather than as the platform that broke it: asking the
        // PLATFORM to canonicalize a path whose last component does not
        // exist is asking a question the platforms answer differently,
        // and three of the four callers are about to CREATE that file.
        // Here the two answers must be the same file, on every platform.
        File root = Files.createDirectories(tmp.resolve("root")).toFile();
        Path inside = Files.createDirectories(root.toPath().resolve("inside"));
        assumeLinked(root.toPath().resolve("link"), inside);

        File whileAbsent = Containment.resolve(root, "link/appears-later.txt");
        Files.writeString(inside.resolve("appears-later.txt"), "now it exists");
        File oncePresent = Containment.resolve(root, "link/appears-later.txt");

        assertThat(whileAbsent).isNotNull();
        assertThat(oncePresent).isNotNull();
        assertThat(whileAbsent.toPath())
                .as("creating the file must not change where the guard says it is")
                .isEqualTo(oncePresent.toPath());
        assertThat(whileAbsent.toPath())
                .isEqualTo(root.getCanonicalFile().toPath().resolve(Path.of("inside", "appears-later.txt")));

        // and the same symmetry on the refusing side: an escape is an
        // escape before the file is there, which is the write case —
        // DockerRecipes names a file that does not exist yet
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        assumeLinked(root.toPath().resolve("escape"), outside);
        assertThat(Containment.resolve(root, "escape/not-written-yet.txt")).isNull();
        Files.writeString(outside.resolve("not-written-yet.txt"), "x");
        assertThat(Containment.resolve(root, "escape/not-written-yet.txt")).isNull();
    }

    @Test
    @DisplayName("a .. inside the ABSENT tail cannot climb out after the prefix is canonicalized")
    void dotDotInsideTheTailCannotClimbOut(@TempDir Path tmp) throws Exception {
        // The tail is re-appended to a canonical prefix, and Path's
        // startsWith is purely segment-wise: base/ghost/../../x DOES
        // start with base. Only the normalize folds it back out, so this
        // is what makes dropping the normalize a hole rather than a tidy.
        File root = Files.createDirectories(tmp.resolve("root")).toFile();
        Files.createDirectories(tmp.resolve("site-backup"));
        Files.writeString(tmp.resolve("site-backup/secret.txt"), "not yours");

        assertThat(Containment.resolve(root, "ghost/../../site-backup/secret.txt")).isNull();
        assertThat(Containment.resolve(root, "ghost/deeper/../../../SECRET")).isNull();

        // and it does not over-refuse: a .. that stays inside is fine,
        // and lands where an open() would
        assertThat(Containment.resolve(root, "ghost/../kept.txt").toPath())
                .isEqualTo(root.getCanonicalFile().toPath().resolve("kept.txt"));
    }

    @Test
    @DisplayName("THE BROKEN-LINK LAW: a dangling link is judged by its TARGET, not its spelling")
    void brokenLinksAreJudgedByTheirTarget(@TempDir Path tmp) throws Exception {
        // This test used to assert the opposite, and was written to
        // RECORD the ceiling rather than to close it: a link whose
        // target does not exist cannot be canonicalized by any platform,
        // so the path was answered on where the link's NAME sat. The
        // recorded hazard was a later one — "followed out if that target
        // were created" — and the live one was the reverse, immediate,
        // and on a write path (see writeThroughADanglingLeafEscaped).
        File root = Files.createDirectories(tmp.resolve("root")).toFile();
        assumeLinked(root.toPath().resolve("dangling"), tmp.resolve("nowhere"));

        assertThat(Containment.resolve(root, "dangling"))
                .as("a dangling link as the FINAL component — the write case")
                .isNull();
        assertThat(Containment.resolve(root, "dangling/x.txt"))
                .as("a dangling link in the MIDDLE, with an absent tail behind it")
                .isNull();

        // a .. behind a broken link still folds, and still cannot escape
        assertThat(Containment.resolve(root, "dangling/../../SECRET")).isNull();
    }

    @Test
    @DisplayName("THE BROKEN-LINK LAW: a dangling link pointing back INSIDE the root is kept")
    void brokenLinksThatStayInsideAreNotRefused(@TempDir Path tmp) throws Exception {
        // The rule ledger 117 proposed — refuse when the canonicalized
        // ancestor is itself a symlink — would refuse this, and this is
        // contained: the symlink policy promises a link pointing back
        // inside is fine, and a link being BROKEN does not move it.
        File root = Files.createDirectories(tmp.resolve("root")).toFile();
        Path canonicalRoot = root.getCanonicalFile().toPath();
        assumeLinked(root.toPath().resolve("later"), canonicalRoot.resolve("not-yet"));

        assertThat(Containment.resolve(root, "later"))
                .as("a dangling link is only an escape when its TARGET escapes")
                .isNotNull();
        assertThat(Containment.resolve(root, "later").toPath())
                .as("and the answer is the target it records, never the link's name —"
                        + " the check and the write must name the same file")
                .isEqualTo(canonicalRoot.resolve("not-yet"));
    }

    @Test
    @DisplayName("a CYCLE of dangling links is refused rather than followed forever")
    void aCycleOfBrokenLinksIsRefused(@TempDir Path tmp) throws Exception {
        File root = Files.createDirectories(tmp.resolve("root")).toFile();
        Path a = root.toPath().resolve("a");
        Path b = root.toPath().resolve("b");
        assumeLinked(a, b);
        assumeLinked(b, a);

        // neither resolves, each records the other: a guard that cannot
        // answer must refuse, and must do it without spinning
        assertThat(Containment.resolve(root, "a")).isNull();
        assertThat(Containment.resolve(root, "a/deeper.txt")).isNull();
    }

    @Test
    @DisplayName("a write through a dangling LEAF cannot land outside the root")
    void writeThroughADanglingLeafEscaped(@TempDir Path tmp) throws Exception {
        // The defect this law exists for, in the shape that made it live
        // rather than latent: Files.writeString opens with CREATE, which
        // FOLLOWS a dangling link and creates its target. Measured on the
        // shipped guard — it answered "contained" and the write created
        // outside/pwned.txt holding these exact bytes.
        File root = Files.createDirectories(tmp.resolve("root")).toFile();
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        assumeLinked(root.toPath().resolve("Dockerfile"), outside.resolve("pwned.txt"));

        File verdict = Containment.resolve(root, "Dockerfile");
        assertThat(verdict)
                .as("the guard must refuse BEFORE a caller can open it")
                .isNull();

        // and the escape it prevents, demonstrated against the unguarded
        // spelling so the assertion above is known to be load-bearing
        Files.writeString(root.toPath().resolve("Dockerfile"), "FROM scratch\n");
        assertThat(outside.resolve("pwned.txt"))
                .as("an unguarded write through the same link lands outside — which"
                        + " is what the null verdict above is standing in front of")
                .exists();
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
