package org.nmox.studio.dbstudio.io;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.PersonalState;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two 3.4 review findings for {@code .nmoxdb.json}: a file from a newer
 * version that adds a FIELD was resaved without it, and the personal SQL
 * history could be written larger than the cap it is read back with.
 */
class DbVersionAndHistoryCapTest {

    private static final String V2 = "{\"version\":2,\"connections\":[{\"id\":\"c1\",\"name\":\"local\","
            + "\"engine\":\"SQLITE\",\"filePath\":\"app.db\",\"readOnlyReplica\":true}],\"saved\":[]}";

    @Test
    @DisplayName("a file whose version is above this build's binds read-only")
    void higherVersionIsReadOnly(@TempDir File dir) throws Exception {
        Files.writeString(new File(dir, DbWorkspaceIO.FILENAME).toPath(), V2, StandardCharsets.UTF_8);

        DbWorkspaceIO.LoadOutcome o = DbWorkspaceIO.loadWorkspaceGuarded(dir);

        assertThat(o.newerFormat()).isTrue();
        assertThat(o.readOnly()).isTrue();
        assertThat(o.workspace().connections()).as("still shown").hasSize(1);
    }

    @Test
    @DisplayName("this build's own version, and a file with none, stay writable")
    void ownVersionIsWritable(@TempDir File dir) throws Exception {
        File f = new File(dir, DbWorkspaceIO.FILENAME);
        Files.writeString(f.toPath(), V2.replace("\"version\":2", "\"version\":" + DbWorkspaceIO.FORMAT_VERSION),
                StandardCharsets.UTF_8);
        assertThat(DbWorkspaceIO.loadWorkspaceGuarded(dir).readOnly()).isFalse();
        Files.writeString(f.toPath(), V2.replace("\"version\":2,", ""), StandardCharsets.UTF_8);
        assertThat(DbWorkspaceIO.loadWorkspaceGuarded(dir).readOnly()).isFalse();
    }

    @Test
    @DisplayName("a history of pasted large queries is written small enough to read back, newest kept")
    void personalHistoryFitsItsReadCap() {
        List<DbWorkspaceIO.HistoryEntry> history = new ArrayList<>();
        for (int i = DbWorkspaceIO.HISTORY_CAP - 1; i >= 0; i--) {
            history.add(new DbWorkspaceIO.HistoryEntry("SELECT '" + "x".repeat(40_000) + "' -- " + i, "SQLite", i));
        }

        String doc = DbWorkspaceIO.personalJson(history);

        assertThat(PersonalState.fits(doc)).isTrue();
        List<DbWorkspaceIO.HistoryEntry> back = DbWorkspaceIO.personalHistory(doc);
        assertThat(back).hasSizeGreaterThan(10).hasSizeLessThan(DbWorkspaceIO.HISTORY_CAP);
        assertThat(back.get(0).at()).as("the newest run is the one kept").isEqualTo(DbWorkspaceIO.HISTORY_CAP - 1);
    }
}
