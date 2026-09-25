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
 * {@code prepare}, so the EDT turn that opens and tracks does no disk work,
 * and a file that went missing between the launcher and the IDE refuses the
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
    }

    @Test
    @DisplayName("the launcher's door resolves on a lane before the EDT opens anything")
    void doorGoesThroughTheLane() throws Exception {
        String option = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/actions/EditRequestOption.java"));
        assertThat(option).contains("shower = EditRequestWatcher::showLater;");
    }
}
