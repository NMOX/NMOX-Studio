package org.nmox.studio.ui.actions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.api.sendopts.CommandException;
import org.netbeans.api.sendopts.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code nmox --wait} and {@code nmox --diff}, the IDE's half (3.2.0): the
 * request a terminal command writes, the answers the IDE writes back into
 * the same folder, and the arming rule that keeps an editor still opening
 * from reading as one already closed.
 */
class EditRequestTest {

    @TempDir
    Path tmp;

    private final List<EditRequest> shown = new ArrayList<>();
    private BiConsumer<File, EditRequest> savedShower;
    private Predicate<Object> savedShowing;
    private java.util.function.Consumer<String> savedStatus;
    private final List<String> statuses = new ArrayList<>();

    @BeforeEach
    void seams() {
        savedShower = EditRequestOption.shower;
        EditRequestOption.shower = (folder, r) -> shown.add(r);
        savedShowing = EditRequestWatcher.showing;
        // the status line is one line shared by every test in the fork: never write to it from here
        savedStatus = EditRequestWatcher.status;
        EditRequestWatcher.status = statuses::add;
    }

    @AfterEach
    void restore() {
        EditRequestOption.shower = savedShower;
        EditRequestWatcher.showing = savedShowing;
        EditRequestWatcher.answerAll();
        EditRequestWatcher.status = savedStatus;
    }

    private Path file(String name) throws Exception {
        Path f = tmp.resolve(name);
        Files.writeString(f, "x\n");
        return f;
    }

    private Path folder(String request) throws Exception {
        Path dir = Files.createDirectory(tmp.resolve("nmox-request.Ab12Cd"));
        Files.writeString(dir.resolve("request"), request, StandardCharsets.UTF_8);
        return dir;
    }

    private void run(String... args) throws CommandException {
        CommandLine.create(EditRequestOption.class).process(args,
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(), new ByteArrayOutputStream(), tmp.toFile());
    }

    @Test
    @DisplayName("a waiting request with a file at a line and a comparison parses in order")
    void parsesWhatTheLaunchersWrite() throws Exception {
        Path msg = file("COMMIT_EDITMSG");
        Path a = file("a.js");
        Path b = file("b.js");
        EditRequest r = EditRequest.parse("nmox-request 1\nwait\nopen\n" + msg + "\n42\ndiff\n" + a + "\n" + b + "\n");
        assertThat(r.waits()).isTrue();
        assertThat(r.items()).containsExactly(
                new EditRequest.Open(msg.toFile(), 42),
                new EditRequest.Diff(a.toFile(), b.toFile()));
    }

    @Test
    @DisplayName("without the wait line the request does not wait, and an empty line means no line")
    void noWaitNoLine() throws Exception {
        Path f = file("notes.md");
        EditRequest r = EditRequest.parse("nmox-request 1\nopen\n" + f + "\n\n");
        assertThat(r.waits()).isFalse();
        assertThat(r.items()).containsExactly(new EditRequest.Open(f.toFile(), 0));
    }

    @Test
    @DisplayName("a path with spaces and shell characters survives, because nothing splits a line")
    void pathsAreWholeLines() throws Exception {
        Path f = file("it's a $file & more.txt");
        EditRequest r = EditRequest.parse("nmox-request 1\nopen\n" + f + "\n\n");
        assertThat(((EditRequest.Open) r.items().get(0)).file()).isEqualTo(f.toFile());
    }

    @Test
    @DisplayName("anything nmox never writes is refused with a sentence")
    void refusals() throws Exception {
        Path f = file("a.txt");
        assertThatThrownBy(() -> EditRequest.parse("hello\nopen\n" + f + "\n\n"))
                .isInstanceOf(EditRequest.Refused.class).hasMessageContaining("not one nmox wrote");
        assertThatThrownBy(() -> EditRequest.parse("nmox-request 1\nwait\n"))
                .isInstanceOf(EditRequest.Refused.class).hasMessageContaining("names no file");
        assertThatThrownBy(() -> EditRequest.parse("nmox-request 1\nopen\nrelative.txt\n\n"))
                .isInstanceOf(EditRequest.Refused.class).hasMessageContaining("not absolute");
        assertThatThrownBy(() -> EditRequest.parse("nmox-request 1\nopen\n" + tmp.resolve("gone.txt") + "\n\n"))
                .isInstanceOf(EditRequest.Refused.class).hasMessageContaining("no such file");
        assertThatThrownBy(() -> EditRequest.parse("nmox-request 1\nopen\n" + tmp + "\n\n"))
                .as("a folder is not a file to wait for")
                .isInstanceOf(EditRequest.Refused.class).hasMessageContaining("no such file");
        assertThatThrownBy(() -> EditRequest.parse("nmox-request 1\nopen\n" + f + "\nforty\n"))
                .isInstanceOf(EditRequest.Refused.class).hasMessageContaining("not a number");
        assertThatThrownBy(() -> EditRequest.parse("nmox-request 1\ndiff\n" + f + "\n"))
                .isInstanceOf(EditRequest.Refused.class).hasMessageContaining("middle of diff");
        assertThatThrownBy(() -> EditRequest.parse("nmox-request 1\nrun\nrm\n-rf\n"))
                .isInstanceOf(EditRequest.Refused.class).hasMessageContaining("never writes");
        StringBuilder many = new StringBuilder("nmox-request 1\n");
        for (int i = 0; i <= EditRequest.MAX_ITEMS; i++) {
            many.append("open\n").append(f).append("\n\n");
        }
        assertThatThrownBy(() -> EditRequest.parse(many.toString()))
                .isInstanceOf(EditRequest.Refused.class).hasMessageContaining("more than");
    }

    @Test
    @DisplayName("git's /dev/null (an added or a deleted file) arrives as an empty line: that side is null, never both")
    void anEmptySideIsNothing() throws Exception {
        Path f = file("b.txt");
        EditRequest added = EditRequest.parse("nmox-request 1\ndiff\n\n" + f + "\n");
        assertThat(added.items()).containsExactly(new EditRequest.Diff(null, f.toFile()));
        EditRequest deleted = EditRequest.parse("nmox-request 1\ndiff\n" + f + "\n\n");
        assertThat(deleted.items()).containsExactly(new EditRequest.Diff(f.toFile(), null));
        assertThatThrownBy(() -> EditRequest.parse("nmox-request 1\ndiff\n\n\n"))
                .hasMessage("the request compares nothing with nothing");
    }

    @Test
    @DisplayName("a side over 16 MiB is refused before the diff view would load it whole")
    void hugeSideRefused() throws Exception {
        Path big = tmp.resolve("big.log");
        try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(big.toFile(), "rw")) {
            f.setLength(EditRequest.MAX_COMPARED + 1);
        }
        Path small = file("small.txt");
        assertThatThrownBy(() -> EditRequest.parse("nmox-request 1\ndiff\n" + big + "\n" + small + "\n"))
                .hasMessageContaining("too large to compare");
    }

    @Test
    @DisplayName("the diff bar steps inside the list; before any choice the first difference is the one shown")
    void diffSteps() {
        // the controller reports -1 while its divider already reads 1/N: the
        // first Next must move, not land on what is on screen (the walk's find)
        assertThat(DiffWindow.step(-1, 3, 1)).isEqualTo(1);
        assertThat(DiffWindow.step(-1, 3, -1)).isZero();
        assertThat(DiffWindow.step(0, 3, 1)).isEqualTo(1);
        assertThat(DiffWindow.step(2, 3, 1)).as("stays on the last").isEqualTo(2);
        assertThat(DiffWindow.step(0, 3, -1)).as("stays on the first").isZero();
        assertThat(DiffWindow.step(0, 0, 1)).as("nothing to step to").isEqualTo(-1);
        assertThat(DiffWindow.position(1, 5)).isEqualTo("Difference 2 of 5");
        assertThat(DiffWindow.position(-1, 5)).isEqualTo("Difference 1 of 5");
        assertThat(DiffWindow.position(-1, 0)).isEqualTo("The files are the same");
    }

    @Test
    @DisplayName("a NUL in the first bytes is binary, git's own rule, so the view shows its placeholder instead of bytes")
    void binaryByNul() {
        assertThat(DiffWindow.looksBinary("hello\n".getBytes(java.nio.charset.StandardCharsets.UTF_8))).isFalse();
        assertThat(DiffWindow.looksBinary(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 1})).isTrue();
        assertThat(DiffWindow.looksBinary(new byte[0])).as("an empty file is text").isFalse();
    }

    @Test
    @DisplayName("an accepted request is answered with this JVM's process id and handed on")
    void acceptedCarriesThePid() throws Exception {
        Path f = file("COMMIT_EDITMSG");
        Path dir = folder("nmox-request 1\nwait\nopen\n" + f + "\n\n");
        run("--nmox-request", dir.toString());
        assertThat(Files.readString(dir.resolve("accepted")).strip())
                .isEqualTo(Long.toString(ProcessHandle.current().pid()));
        assertThat(dir.resolve("refused")).doesNotExist();
        assertThat(shown).hasSize(1);
    }

    @Test
    @DisplayName("a refused request is answered with the sentence, exit code 2, and shows nothing")
    void refusedIsAnswered() throws Exception {
        Path dir = folder("nmox-request 1\nopen\n" + tmp.resolve("gone.txt") + "\n\n");
        assertThatThrownBy(() -> run("--nmox-request", dir.toString()))
                .isInstanceOf(CommandException.class)
                .extracting(e -> ((CommandException) e).getExitCode()).isEqualTo(2);
        assertThat(Files.readString(dir.resolve("refused"))).contains("no such file");
        assertThat(dir.resolve("accepted")).doesNotExist();
        assertThat(shown).isEmpty();
    }

    @Test
    @DisplayName("a folder nmox did not make is refused and nothing is written into it")
    void onlyARequestFolderIsAnswered() throws Exception {
        Path f = file("a.txt");
        Path other = Files.createDirectory(tmp.resolve("Documents"));
        Files.writeString(other.resolve("request"), "nmox-request 1\nopen\n" + f + "\n\n");
        assertThatThrownBy(() -> run("--nmox-request", other.toString()))
                .isInstanceOf(CommandException.class);
        try (var list = Files.list(other)) {
            assertThat(list.map(p -> p.getFileName().toString())).containsExactly("request");
        }
        assertThatThrownBy(() -> run("--nmox-request", "nmox-request.relative"))
                .as("a relative folder is refused").isInstanceOf(CommandException.class);
    }

    @Test
    @DisplayName("a request file larger than nmox ever writes is refused unread")
    void oversizeIsRefused() throws Exception {
        Path dir = folder("nmox-request 1\n" + "x".repeat(EditRequestOption.MAX_BYTES));
        assertThatThrownBy(() -> run("--nmox-request", dir.toString())).isInstanceOf(CommandException.class);
        assertThat(Files.readString(dir.resolve("refused"))).contains("larger");
    }

    @Test
    @DisplayName("a target is done only after it was seen open and then closed")
    void armingBeforeClosing() {
        Object editor = new Object();
        EditRequestWatcher.Session s = new EditRequestWatcher.Session(tmp.toFile(), "COMMIT_EDITMSG", List.of(editor));
        assertThat(s.update(t -> false)).as("still opening: not done").isFalse();
        assertThat(s.update(t -> true)).as("open").isFalse();
        assertThat(s.update(t -> false)).as("closed after it was open").isTrue();
    }

    @Test
    @DisplayName("a request with two targets is done when both have closed, and says so in its folder")
    void doneWhenAllClosed() throws Exception {
        Path f = file("a.txt");
        Path dir = folder("nmox-request 1\nwait\nopen\n" + f + "\n\n");
        Object one = new Object();
        Object two = new Object();
        java.util.Set<Object> open = new java.util.HashSet<>(List.of(one, two));
        EditRequestWatcher.showing = open::contains;
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            EditRequestWatcher.Session s = new EditRequestWatcher.Session(dir.toFile(), "a, b", List.of(one, two));
            EditRequestWatcher.track(s);
            EditRequestWatcher.recheck();
            open.remove(one);
            EditRequestWatcher.recheck();
        });
        assertThat(dir.resolve("done")).as("one of two still open").doesNotExist();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            open.remove(two);
            EditRequestWatcher.recheck();
        });
        assertThat(dir.resolve("done")).as("both closed").exists();
        assertThat(EditRequestWatcher.waiting()).isZero();
        assertThat(statuses).last().asString().contains("a, b");
    }

    @Test
    @DisplayName("quitting the IDE answers every request still waiting, so git goes on")
    void quittingAnswers() throws Exception {
        Path dir = folder("nmox-request 1\nwait\n");
        javax.swing.SwingUtilities.invokeAndWait(() -> EditRequestWatcher.track(
                new EditRequestWatcher.Session(dir.toFile(), "x", List.of(new Object()))));
        EditRequestWatcher.answerAll();
        assertThat(dir.resolve("done")).exists();
    }
}
