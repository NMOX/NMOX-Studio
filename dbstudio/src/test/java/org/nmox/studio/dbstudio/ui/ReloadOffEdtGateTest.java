package org.nmox.studio.dbstudio.ui;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source gate for ledger 54 M5: the workspace reload's file read (and the
 * save-lane drain it runs behind) and the .env offer's read both ride RP —
 * never the EDT. DbStudioTopComponent is a JaCoCo-excluded pure-Swing
 * window, so the law is pinned structurally: the I/O calls must appear
 * INSIDE an {@code RP.post} body, after it opens.
 */
class ReloadOffEdtGateTest {

    private static String method(String src, String signature) {
        int at = src.indexOf(signature);
        assertThat(at).as("%s exists", signature).isPositive();
        // slice to the start of the next method-level comment or signature;
        // generous window — ordering assertions do the real work
        return src.substring(at, Math.min(src.length(), at + 2_500));
    }

    @Test
    @DisplayName("reloadWorkspace: flush + read + stamp all sit inside RP.post, apply is seq-guarded")
    void reloadReadsOffEdt() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/dbstudio/ui/DbStudioTopComponent.java"));
        String body = method(src, "private void reloadWorkspace()");
        int post = body.indexOf("RP.post(");
        int flush = body.indexOf("SAVES.flush(");
        int read = body.indexOf("loadWorkspaceGuarded(");
        int stamp = body.indexOf("Stamp.of(");
        int seq = body.indexOf("seq != reloadSeq");
        assertThat(post).as("the reload posts to RP").isPositive();
        assertThat(flush).as("the save-lane drain rides RP, not the EDT").isGreaterThan(post);
        assertThat(read).as("the file read rides RP").isGreaterThan(post);
        assertThat(stamp).as("the own-write stamp stats the file off-EDT").isGreaterThan(post);
        assertThat(seq).as("a newer reload supersedes a stale read").isGreaterThan(read);
    }

    @Test
    @DisplayName("offerEnvConnection: the .env stat + read ride RP; the guard stays on the EDT")
    void envOfferReadsOffEdt() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/dbstudio/ui/DbStudioTopComponent.java"));
        String body = method(src, "private void offerEnvConnection()");
        int guard = body.indexOf("envOfferedProjects.add(");
        int post = body.indexOf("RP.post(");
        int read = body.indexOf("Files.readString(");
        assertThat(guard).as("the once-per-project guard exists").isPositive();
        assertThat(post).as("the offer posts to RP").isGreaterThan(guard);
        assertThat(read).as("the .env read rides RP").isGreaterThan(post);
    }
}
