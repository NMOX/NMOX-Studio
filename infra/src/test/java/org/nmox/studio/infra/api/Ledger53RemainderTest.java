package org.nmox.studio.infra.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 53 c/d/e: the drift check matches a REAL 404 (not the digits
 * anywhere), cloud-worker model mutations cross to the EDT, and the
 * designer's debounced save binds to the file it was edited against.
 */
class Ledger53RemainderTest {

    /** Reads a source file with line endings normalized — the newline-
     * delimited slicing below breaks on a CRLF (Windows) checkout
     * otherwise (the v1.103.0 SpawnTrustGateTest lesson). */
    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path)).replace("\r\n", "\n");
    }

    @Test
    @DisplayName("Only a real HTTP 404 severs the deploy linkage — impostor 404s don't (53e)")
    void preciseNotFound() {
        assertThat(DigitalOceanClient.deletedInCloud("HTTP 404: resource not found")).isTrue();
        // the old contains(\"404\") matched ALL of these and severed doId:
        assertThat(DigitalOceanClient.deletedInCloud("HTTP 500: upstream returned 404 page")).isFalse();
        assertThat(DigitalOceanClient.deletedInCloud("droplet web-404 unreachable")).isFalse();
        assertThat(DigitalOceanClient.deletedInCloud("HTTP 429: retry after 404 seconds")).isFalse();
        assertThat(DigitalOceanClient.deletedInCloud(null)).isFalse();
    }

    @Test
    @DisplayName("onModel runs the mutation on the EDT and waits for it (53d)")
    void modelMutationsCrossToEdt() {
        AtomicReference<Boolean> onEdt = new AtomicReference<>();
        DigitalOceanClient.onModel(() ->
                onEdt.set(javax.swing.SwingUtilities.isEventDispatchThread()));
        assertThat(onEdt.get())
                .as("the mutation ran, on the EDT, before onModel returned")
                .isTrue();
    }

    @Test
    @DisplayName("Op-in-flight lock (53b): canvas refuses structural edits; TC defers re-aims")
    void opLockGates() throws Exception {
        String canvas = source("src/main/java/org/nmox/studio/infra/ui/FlowCanvas.java");
        // the three structural entry points all check the lock
        int del = canvas.indexOf("private void deleteSelection()");
        assertThat(canvas.substring(del, del + 200)).contains("if (locked)");
        assertThat(canvas).contains("target != null && !locked");
        int drop = canvas.indexOf("public boolean importData(TransferSupport support)");
        assertThat(canvas.substring(drop, drop + 200)).contains("if (locked)");
        // the TC's one op choke point arms the lock and defers re-aims
        String tc = source("src/main/java/org/nmox/studio/infra/InfraDesignerTopComponent.java");
        int run = tc.indexOf("private void runExclusive(");
        String runBody = tc.substring(run, tc.indexOf("\n    }\n", run));
        // a DEPTH, not a flag: the popup destroy path can queue a second op
        // behind a running one, and a boolean's first finally lifted the
        // lock while that op still ran (the v1.126.0 day-review finding)
        assertThat(runBody).contains("opsInFlight++");
        assertThat(runBody).contains("opsInFlight--");
        assertThat(runBody).contains("if (opsInFlight == 0)");
        assertThat(runBody).contains("canvas.setLocked(true)");
        assertThat(runBody).contains("canvas.setLocked(false)");
        assertThat(runBody).contains("pendingReaim");
        int reaim = tc.indexOf("private void onProjectReaimed()");
        assertThat(tc.substring(reaim, reaim + 400)).contains("if (opsInFlight > 0)");
    }

    @Test
    @DisplayName("Designer source gate (53c): re-aim force-saves the OLD bound file before loading")
    void reaimForceSavesBoundFile() throws Exception {
        String src = source("src/main/java/org/nmox/studio/infra/InfraDesignerTopComponent.java");
        // save() must target the bound file, never the live aim
        int saveAt = src.indexOf("private void save()");
        String saveBody = src.substring(saveAt, src.indexOf("\n    }", saveAt));
        assertThat(saveBody).contains("boundDesignFile");
        // the re-aim path stops the debounce and saves BEFORE load
        int reaimAt = src.indexOf("private void onProjectReaimed()");
        assertThat(reaimAt).as("onProjectReaimed exists").isPositive();
        String reaim = src.substring(reaimAt, src.indexOf("\n    }", reaimAt));
        int stop = reaim.indexOf("saveDebounce.stop()");
        int save = reaim.indexOf("save()");
        int load = reaim.indexOf("load()");
        assertThat(stop).isPositive();
        assertThat(save).as("the pending window saves").isGreaterThan(stop);
        assertThat(load).as("...before the new project loads").isGreaterThan(save);
        // and the rack listener routes re-aims through it
        assertThat(src).contains("InfraDesignerTopComponent.this::onProjectReaimed");
    }
}
