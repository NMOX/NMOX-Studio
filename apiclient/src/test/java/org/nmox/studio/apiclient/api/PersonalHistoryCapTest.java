package org.nmox.studio.apiclient.api;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.apiclient.model.ApiModel.Request;
import org.nmox.studio.apiclient.model.ApiModel.Workspace;
import org.nmox.studio.apiclient.model.SendHistory;
import org.nmox.studio.core.util.PersonalState;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The personal document this studio writes must fit the cap it reads it
 * back with (3.4; the hostile review: fifty 24 KB sends made 1.2 MB, the
 * next load read nothing, and the next save made the loss permanent).
 */
class PersonalHistoryCapTest {

    @AfterEach
    void restore() {
        PersonalState.setBaseForTest(null);
    }

    private static Request upload(String body) {
        Request r = new Request();
        r.name = "Upload fixture";
        r.method = "POST";
        r.url = "{{base}}/import";
        r.body = body;
        return r;
    }

    @Test
    @DisplayName("fifty large sends write a history that reads back, newest first")
    void historyWrittenIsHistoryReadBack(@TempDir Path base, @TempDir File project) throws Exception {
        PersonalState.setBaseForTest(base);
        Workspace w = new Workspace();
        w.activeEnvironment = "Staging";
        for (int i = 0; i < SendHistory.CAP; i++) {
            SendHistory.record(w.history, SendHistory.of(i, upload("{\"rows\":\"" + "x".repeat(24_000) + "\"}"), 200, 5));
        }
        String doc = WorkspaceIO.personalJson(w);
        PersonalState.write(project, WorkspaceIO.PERSONAL_STUDIO, doc);

        String back = PersonalState.read(project, WorkspaceIO.PERSONAL_STUDIO);
        assertThat(back).as("the document this studio just wrote must be readable by it").isNotNull();
        Workspace reread = new Workspace();
        WorkspaceIO.applyPersonal(reread, back);
        assertThat(reread.activeEnvironment).isEqualTo("Staging");
        assertThat(reread.history).as("as many rows as fit are kept").hasSizeGreaterThan(10)
                .hasSizeLessThan(SendHistory.CAP);
        assertThat(reread.history.get(0).timestamp).as("the NEWEST row is the one kept")
                .isEqualTo(SendHistory.CAP - 1);
    }

    @Test
    @DisplayName("a small history is written whole")
    void smallHistoryIsWhole() {
        Workspace w = new Workspace();
        for (int i = 0; i < SendHistory.CAP; i++) {
            SendHistory.record(w.history, SendHistory.of(i, upload("{}"), 200, 5));
        }
        Workspace reread = new Workspace();
        WorkspaceIO.applyPersonal(reread, WorkspaceIO.personalJson(w));
        assertThat(reread.history).hasSize(SendHistory.CAP);
    }
}
