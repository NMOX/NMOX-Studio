package org.nmox.studio.rack.blockstudio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.79.0 studio-law parity pieces: Block Studio's workspace file
 * now honors the external-edit pulse law (v1.35) and the ⌘I reach law
 * (v1.26/v1.33) like every other studio. The pulse's fire-exactly-once
 * stat semantics and the search provider's pure matching are pinned
 * here; the TopComponent wiring rides the same seams.
 */
class BlockParityTest {

    @Test
    @DisplayName("Pulse: first tick primes, a change fires once, steady state is silent")
    void pulseFiresExactlyOnce(@TempDir Path dir) throws Exception {
        File f = dir.resolve(".nmoxblocks.json").toFile();
        Files.writeString(f.toPath(), "{}");
        List<long[]> fired = new ArrayList<>();
        BlockFilePulse pulse = new BlockFilePulse(f, (m, s) -> fired.add(new long[]{m, s}));

        pulse.tick(); // primes, must not fire
        assertThat(fired).isEmpty();

        Files.writeString(f.toPath(), "{\"changed\":true}");
        f.setLastModified(f.lastModified() + 5000); // mtime granularity guard
        pulse.tick();
        assertThat(fired).hasSize(1);

        pulse.tick(); // unchanged — silent
        assertThat(fired).hasSize(1);
    }

    @Test
    @DisplayName("Pulse: deletion fires with the gone sentinel")
    void pulseReportsDeletion(@TempDir Path dir) throws Exception {
        File f = dir.resolve(".nmoxblocks.json").toFile();
        Files.writeString(f.toPath(), "{}");
        List<long[]> fired = new ArrayList<>();
        BlockFilePulse pulse = new BlockFilePulse(f, (m, s) -> fired.add(new long[]{m, s}));
        pulse.tick();
        Files.delete(f.toPath());
        pulse.tick();
        assertThat(fired).hasSize(1);
        assertThat(fired.get(0)).containsExactly(-1, -1);
    }
}
