package org.nmox.studio.ui.actions;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A request is read off the EDT before any of it opens (ledger 124, closed
 * after 3.2.0): the DataObject lookups and the diff's binary sniff happen in
 * {@code prepare}, so the EDT turn that opens and tracks does none of OUR
 * disk work (the platform's diff view still reads its two files there, when
 * it shows them), and a file that went missing between the launcher and the IDE refuses the
 * whole request before any tab opens.
 */
class EditRequestPrepareTest {

    @Test
    @DisplayName("every item is resolved ahead: open files as DataObjects, diffs as sniffed sources")
    void resolvesAhead(@TempDir Path tmp) throws Exception {
        Path msg = Files.writeString(tmp.resolve("COMMIT_EDITMSG"), "x\n");
        Path a = Files.writeString(tmp.resolve("a.txt"), "a\n");
        Path b = Files.write(tmp.resolve("b.bin"), new byte[] {1, 0, 2});
        EditRequest r = EditRequest.parse("nmox-request 1\nwait\nopen\n" + msg + "\n3\ndiff\n" + a + "\n" + b + "\n");
        EditRequestWatcher.Prepared p = EditRequestWatcher.prepare(r);
        assertThat(p.failure()).isNull();
        assertThat(p.items()).hasSize(2);
        assertThat(p.items().get(0)).isInstanceOfSatisfying(EditRequestWatcher.ResolvedOpen.class, o -> {
            assertThat(o.dob().getPrimaryFile().getNameExt()).isEqualTo("COMMIT_EDITMSG");
            assertThat(o.line()).isEqualTo(3);
        });
        assertThat(p.items().get(1)).isInstanceOfSatisfying(DiffWindow.Prepared.class,
                d -> assertThat(d.r().getMIMEType()).as("the sniff ran ahead").isEqualTo("application/octet-stream"));
    }

    @Test
    @DisplayName("a file gone between the launcher and the IDE refuses the request before anything opens")
    void goneFileRefusesWhole(@TempDir Path tmp) throws Exception {
        Path first = Files.writeString(tmp.resolve("first.txt"), "1\n");
        Path second = Files.writeString(tmp.resolve("second.txt"), "2\n");
        EditRequest r = EditRequest.parse("nmox-request 1\nopen\n" + first + "\n\nopen\n" + second + "\n\n");
        Files.delete(second);
        EditRequestWatcher.Prepared p = EditRequestWatcher.prepare(r);
        assertThat(p.failure()).isNotNull();
        assertThat(p.failedFile()).isEqualTo("second.txt");
        Path folder = Files.createDirectories(tmp.resolve("request"));
        java.util.List<String> said = new java.util.ArrayList<>();
        var before = EditRequestWatcher.status;
        EditRequestWatcher.status = said::add;
        try {
            EditRequestWatcher.show(folder.toFile(), r, p);
        } finally {
            EditRequestWatcher.status = before;
        }
        assertThat(folder.resolve("refused")).as("the launcher is answered").exists();
        assertThat(Files.readString(folder.resolve("refused"))).contains("second.txt");
        assertThat(said).singleElement().asString().contains("second.txt");
        assertThat(EditRequestWatcher.requested(p.items().get(0) instanceof EditRequestWatcher.ResolvedOpen o
                ? o.dob() : null)).as("the file that could be found was not opened either").isFalse();
    }

    @Test
    @DisplayName("an unchecked failure on the lane refuses the request instead of leaving git waiting")
    void uncheckedFailureRefuses(@TempDir Path tmp) throws Exception {
        Path a = Files.writeString(tmp.resolve("a.txt"), "a\n");
        // parse refuses what is not there, so no parsed request reaches the
        // lane malformed; one built directly with a null file makes the lane
        // throw where a future bug would, and must still come back refused
        EditRequest r = new EditRequest(true, java.util.List.of(
                new EditRequest.Open(a.toFile(), 0), new EditRequest.Open(null, 0)));
        EditRequestWatcher.Prepared p = EditRequestWatcher.prepare(r);
        assertThat(p.failure()).as("refused, not thrown").isNotNull();
        assertThat(p.failure().getCause()).isInstanceOf(RuntimeException.class);
        assertThat(p.failedFile()).isEqualTo("?");
    }

    @Test
    @DisplayName("a request's files count as asked for from the moment it is accepted until it has opened them")
    void pendingFromAcceptToOpen(@TempDir Path tmp) throws Exception {
        Path msg = Files.writeString(tmp.resolve("MERGE_MSG"), "m\n");
        EditRequest r = EditRequest.parse("nmox-request 1\nwait\nopen\n" + msg + "\n\n");
        assertThat(EditRequestWatcher.pending(msg.toFile())).isFalse();
        EditRequestWatcher.register(r);
        EditRequestWatcher.register(r);
        assertThat(EditRequestWatcher.pending(msg.toFile())).isTrue();
        EditRequestWatcher.unregister(r);
        assertThat(EditRequestWatcher.pending(msg.toFile())).as("a second request still names it").isTrue();
        EditRequestWatcher.unregister(r);
        assertThat(EditRequestWatcher.pending(msg.toFile())).isFalse();
    }

    @Test
    @DisplayName("the launcher's door resolves on a lane before the EDT opens anything")
    void doorGoesThroughTheLane() throws Exception {
        String option = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/actions/EditRequestOption.java"));
        assertThat(option).contains("shower = EditRequestWatcher::showLater;");
        String watcher = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/actions/EditRequestWatcher.java"));
        String door = watcher.substring(watcher.indexOf("static void showLater("), watcher.indexOf("static void register("));
        assertThat(door.indexOf("register(request);")).as("the files count as asked for before the lane runs")
                .isPositive().isLessThan(door.indexOf("PREPARE.post("));
        assertThat(door).contains("unregister(request);");
        String sweep = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/actions/StaleGitRequestTabs.java"));
        assertThat(sweep).as("the sweep spares a file a request is still resolving")
                .contains("EditRequestWatcher.pending(file)");
    }
}
