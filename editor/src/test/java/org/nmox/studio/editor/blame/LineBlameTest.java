package org.nmox.studio.editor.blame;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.process.ProcessSupport;
import org.openide.util.RequestProcessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The lane behind the note: one spawn per file version, nothing spawned
 * where blame has nothing to say, and only the newest request answers.
 * The spawn is seamed; the repository is real on disk (a {@code .git}
 * directory with a HEAD and a ref, the contract {@code GitFacts} reads).
 */
class LineBlameTest {

    @TempDir
    Path tmp;

    private RequestProcessor lane;
    private final AtomicInteger spawns = new AtomicInteger();
    private final List<List<String>> argvs = new CopyOnWriteArrayList<>();
    private final List<File> dirs = new CopyOnWriteArrayList<>();
    private Path repo;
    private Path file;

    @BeforeEach
    void setUp() throws IOException {
        lane = new RequestProcessor("line-blame-test", 1);
        repo = tmp.resolve("repo");
        Files.createDirectories(repo.resolve(".git/refs/heads"));
        Files.writeString(repo.resolve(".git/HEAD"), "ref: refs/heads/main\n");
        Files.writeString(repo.resolve(".git/refs/heads/main"), "a".repeat(40) + "\n");
        Files.createDirectories(repo.resolve("src"));
        file = repo.resolve("src/app.js");
        Files.writeString(file, "one\ntwo\n");
    }

    @AfterEach
    void tearDown() {
        lane.shutdown();
    }

    private LineBlame blameAnswering(String porcelain) {
        return new LineBlame((argv, dir, timeout) -> {
            spawns.incrementAndGet();
            argvs.add(argv);
            dirs.add(dir);
            return new ProcessSupport.BoundedResult(0, porcelain, "", false, false);
        }, lane);
    }

    private static String twoLines() {
        return BlamePorcelainTest.A + " 1 1 1\nauthor Ada\nauthor-time 1\nsummary First\n\tone\n"
                + BlamePorcelainTest.B + " 2 2 1\nauthor Grace\nauthor-time 2\nsummary Second\n\ttwo\n";
    }

    @Test
    @DisplayName("the argv is fixed words, --no-textconv, and the file's own name after --, in its directory")
    void argvIsFixed() {
        LineBlame blame = blameAnswering(twoLines());
        assertThat(blame.lookup(file.toFile(), 1).author()).isEqualTo("Ada");
        assertThat(argvs).containsExactly(
                List.of("git", "blame", "--porcelain", "--no-textconv", "--", "app.js"));
        assertThat(dirs).containsExactly(file.getParent().toFile());
    }

    @Test
    @DisplayName("moving the caret costs a lookup, not a process: one spawn per file version")
    void oneSpawnPerVersion() throws IOException {
        LineBlame blame = blameAnswering(twoLines());
        assertThat(blame.lookup(file.toFile(), 1).author()).isEqualTo("Ada");
        assertThat(blame.lookup(file.toFile(), 2).author()).isEqualTo("Grace");
        assertThat(blame.lookup(file.toFile(), 1).author()).isEqualTo("Ada");
        assertThat(spawns).hasValue(1);
        // a save changes the version: git is asked again
        Files.writeString(file, "one\ntwo\nthree\n");
        blame.lookup(file.toFile(), 1);
        assertThat(spawns).hasValue(2);
        // a commit moves the branch ref: asked again even though the file is unchanged
        Files.writeString(repo.resolve(".git/refs/heads/main"), "b".repeat(40) + "\n");
        blame.lookup(file.toFile(), 1);
        assertThat(spawns).hasValue(3);
    }

    @Test
    @DisplayName("a failed blame (an untracked file) is remembered too: no respawn per caret move")
    void failureIsCached() {
        LineBlame blame = new LineBlame((argv, dir, timeout) -> {
            spawns.incrementAndGet();
            return new ProcessSupport.BoundedResult(128, "", "fatal: no such path 'app.js' in HEAD", false, false);
        }, lane);
        assertThat(blame.lookup(file.toFile(), 1)).isNull();
        assertThat(blame.lookup(file.toFile(), 2)).isNull();
        assertThat(spawns).hasValue(1);
    }

    @Test
    @DisplayName("a truncated answer is no answer, not a partial one")
    void truncatedIsNothing() {
        LineBlame blame = new LineBlame((argv, dir, timeout) ->
                new ProcessSupport.BoundedResult(0, twoLines(), "", false, true), lane);
        assertThat(blame.lookup(file.toFile(), 1)).isNull();
    }

    @Test
    @DisplayName("outside a repository, a missing file, a file over the bound: nothing spawns")
    void nothingSpawns() throws IOException {
        LineBlame blame = blameAnswering(twoLines());
        Path loose = tmp.resolve("loose/notes.txt");
        Files.createDirectories(loose.getParent());
        Files.writeString(loose, "x\n");
        assertThat(blame.lookup(loose.toFile(), 1)).isNull();
        assertThat(blame.lookup(repo.resolve("src/missing.js").toFile(), 1)).isNull();
        assertThat(blame.lookup(null, 1)).isNull();
        Path big = repo.resolve("src/bundle.js");
        Files.write(big, new byte[(int) LineBlame.MAX_FILE_BYTES + 1]);
        assertThat(blame.lookup(big.toFile(), 1)).isNull();
        assertThat(spawns).hasValue(0);
    }

    @Test
    @DisplayName("newest wins: a slow blame of a file the user left never answers")
    void newestWins() throws Exception {
        Path other = repo.resolve("src/other.js");
        Files.writeString(other, "x\n", StandardCharsets.UTF_8);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        LineBlame blame = new LineBlame((argv, dir, timeout) -> {
            spawns.incrementAndGet();
            if (argv.get(argv.size() - 1).equals("app.js")) {
                entered.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return new ProcessSupport.BoundedResult(0, twoLines(), "", false, false);
        }, lane);
        List<LineBlame.Answer> answers = new CopyOnWriteArrayList<>();
        long first = blame.request(file.toFile(), 1, answers::add);
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        long second = blame.request(other.toFile(), 2, answers::add);
        assertThat(blame.isCurrent(first)).isFalse();
        release.countDown();
        lane.post(() -> { }).waitFinished();
        assertThat(answers).extracting(LineBlame.Answer::file).containsExactly(other.toFile());
        assertThat(answers.get(0).generation()).isEqualTo(second);
        assertThat(answers.get(0).entry().author()).isEqualTo("Grace");
        assertThat(blame.isCurrent(second)).isTrue();
    }

    @Test
    @DisplayName("a request superseded before the lane reaches it spawns nothing; cancel drops the pending answer")
    void supersededBeforeStartDoesNotSpawn() throws Exception {
        CountDownLatch hold = new CountDownLatch(1);
        lane.post(() -> {
            try {
                hold.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        LineBlame blame = blameAnswering(twoLines());
        List<LineBlame.Answer> answers = new CopyOnWriteArrayList<>();
        blame.request(file.toFile(), 1, answers::add);
        blame.request(file.toFile(), 2, answers::add);
        blame.cancel();
        hold.countDown();
        lane.post(() -> { }).waitFinished();
        assertThat(answers).isEmpty();
        assertThat(spawns).hasValue(0);
    }

    @Test
    @DisplayName("the product instance spawns through the bounded runner with a real timeout")
    void productTimeout() {
        assertThat(LineBlame.TIMEOUT).isLessThanOrEqualTo(Duration.ofSeconds(10));
        assertThat(LineBlame.create()).isNotNull();
    }
}
