package org.nmox.studio.rack.service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 3.4, "when something goes wrong": on a full 4 MB volume the session
 * snapshot went from 37 bytes to 0 — a plain writeString truncates before
 * it writes — the error was swallowed, and the next launch's resume offer
 * swallowed the parse failure too, so a crash silently lost the one thing
 * the file exists to keep. The write is atomic and both failures speak.
 */
class SessionSnapshotWriteTest {

    @TempDir
    Path dir;

    private final List<LogRecord> warnings = new CopyOnWriteArrayList<>();
    private final Handler tap = new Handler() {
        @Override
        public void publish(LogRecord r) {
            if (r.getLevel().intValue() >= Level.WARNING.intValue()) {
                warnings.add(r);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @BeforeEach
    void listen() {
        Logger.getLogger(RackService.class.getName()).addHandler(tap);
    }

    @AfterEach
    void stopListening() {
        Logger.getLogger(RackService.class.getName()).removeHandler(tap);
    }

    @Test
    @DisplayName("A snapshot is written whole and a removal removes it")
    void writesAndRemoves() throws Exception {
        File file = dir.resolve("sessions/p.json").toFile();
        RackService.writeSnapshot(file, "{\"x\":1}");
        assertThat(Files.readString(file.toPath(), StandardCharsets.UTF_8)).isEqualTo("{\"x\":1}");
        RackService.writeSnapshot(file, null);
        assertThat(file).doesNotExist();
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("A snapshot that cannot be written says so at WARNING, and the last good one survives")
    void failedWriteSpeaksAndKeepsTheOldOne() throws Exception {
        Path sessions = dir.resolve("sessions");
        Files.createDirectories(sessions);
        File file = sessions.resolve("p.json").toFile();
        Files.writeString(file.toPath(), "{\"good\":true}");
        // the directory refuses new files: an atomic write cannot even make its temp.
        // Windows has no read-only directory (setWritable answers false there), so
        // the case cannot be made on that lane
        RackService.snapshotFailing = false;
        org.junit.jupiter.api.Assumptions.assumeTrue(sessions.toFile().setWritable(false),
                "this file system cannot make a directory read-only");
        try {
            RackService.writeSnapshot(file, "{\"good\":false}");
            // rewritten every few seconds while anything runs: a failing disk
            // is said once, not with a stack trace every five seconds
            RackService.writeSnapshot(file, "{\"good\":false}");
            RackService.writeSnapshot(file, "{\"good\":false}");
        } finally {
            sessions.toFile().setWritable(true);
        }
        assertThat(Files.readString(file.toPath())).isEqualTo("{\"good\":true}");
        assertThat(warnings).as("the failure is not swallowed, and said once per streak").hasSize(1);
        assertThat(warnings.get(0).getMessage()).contains("session snapshot").contains("crash-resume");
        RackService.writeSnapshot(file, "{\"good\":true}");
        assertThat(sessions.toFile().setWritable(false)).isTrue();
        try {
            RackService.writeSnapshot(file, "{\"good\":false}");
        } finally {
            sessions.toFile().setWritable(true);
        }
        assertThat(warnings).as("a success ends the streak; the next failure speaks again").hasSize(2);
    }

    @Test
    @DisplayName("An empty snapshot (the full-disk leftover) is logged, not silently dropped with its offer")
    void emptySnapshotSpeaks() throws Exception {
        File file = dir.resolve("p.json").toFile();
        Files.writeString(file.toPath(), "");
        assertThat(RackService.readSnapshot(file)).isNull();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).getMessage()).contains("session snapshot");
    }

    @Test
    @DisplayName("Gate: the snapshot is written through AtomicFiles, never a truncating writeString")
    void writeIsAtomic() throws Exception {
        // a Windows checkout has CRLF line ends: the body search below is in LF
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/rack/service/RackService.java"))
                .replace("\r\n", "\n");
        int start = src.indexOf("static void writeSnapshot(");
        String body = src.substring(start, src.indexOf("\n    }\n", start));
        assertThat(body).contains("AtomicFiles.writeString(").doesNotContain("nio.file.Files.writeString(");
    }
}
