package org.nmox.studio.apiclient.api;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A newer NMOX Studio may add a FIELD, not only an enum value, and bump
 * {@code version}. The parse keeps only what this build knows, so a
 * writable bind would drop that field for everyone on the next save (3.4;
 * found by the hostile review: {@code description} and {@code retries}
 * resaved away).
 */
class NewerVersionReadOnlyTest {

    private static final String V2 = "{\"version\":2,\"collections\":[{\"name\":\"Payments\",\"requests\":[{\"id\":\"r1\","
            + "\"name\":\"List\",\"method\":\"GET\",\"url\":\"/x\",\"authType\":\"NONE\","
            + "\"description\":\"teammate’s notes\",\"retries\":3}]}],\"environments\":[]}";

    @Test
    @DisplayName("a file whose version is above this build's binds read-only")
    void aHigherVersionIsReadOnly(@TempDir File dir) throws Exception {
        Files.writeString(new File(dir, WorkspaceIO.FILENAME).toPath(), V2, StandardCharsets.UTF_8);

        WorkspaceIO.LoadOutcome o = WorkspaceIO.loadGuarded(dir);

        assertThat(o.newerFormat()).as("a newer version's file must not be resaved by this one").isTrue();
        assertThat(o.readOnly()).isTrue();
        assertThat(o.workspace()).as("it is still shown").isNotNull();
        assertThat(o.workspace().collections.get(0).requests.get(0).name).isEqualTo("List");
    }

    @Test
    @DisplayName("this build's own version, and a file with none, stay writable")
    void ownVersionIsWritable(@TempDir File dir) throws Exception {
        File f = new File(dir, WorkspaceIO.FILENAME);
        Files.writeString(f.toPath(), V2.replace("\"version\":2", "\"version\":" + WorkspaceIO.FORMAT_VERSION),
                StandardCharsets.UTF_8);
        assertThat(WorkspaceIO.loadGuarded(dir).readOnly()).isFalse();

        Files.writeString(f.toPath(), V2.replace("\"version\":2,", ""), StandardCharsets.UTF_8);
        assertThat(WorkspaceIO.loadGuarded(dir).readOnly()).isFalse();
    }
}
