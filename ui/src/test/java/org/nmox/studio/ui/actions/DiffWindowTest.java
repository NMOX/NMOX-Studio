package org.nmox.studio.ui.actions;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The window {@code nmox -d} and git's difftool open (3.2.0), opened for
 * real on the platform's diff controller: its name, the empty side git calls
 * /dev/null, and the binary verdict the controller does not give.
 */
class DiffWindowTest {

    @TempDir
    Path tmp;

    private static DiffWindow open(java.io.File left, java.io.File right) throws Exception {
        DiffWindow[] w = new DiffWindow[1];
        Exception[] failed = new Exception[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                w[0] = DiffWindow.open(left, right);
            } catch (Exception ex) {
                failed[0] = ex;
            }
        });
        if (failed[0] != null) {
            throw failed[0];
        }
        return w[0];
    }

    private static String bar(DiffWindow w) throws Exception {
        String[] text = new String[1];
        SwingUtilities.invokeAndWait(() -> {
            w.showWhereForTest();
            text[0] = w.whereTextForTest();
        });
        return text[0];
    }

    @Test
    @DisplayName("two text files open side by side, named left ↔ right, never persisted")
    void textPair() throws Exception {
        Path a = Files.writeString(tmp.resolve("a.txt"), "one\ntwo\n");
        Path b = Files.writeString(tmp.resolve("b.txt"), "one\n2\n");
        DiffWindow w = open(a.toFile(), b.toFile());
        assertThat(w.getName()).isEqualTo("a.txt ↔ b.txt");
        assertThat(w.getPersistenceType()).isEqualTo(org.openide.windows.TopComponent.PERSISTENCE_NEVER);
        assertThat(w.getToolTipText()).contains(a.toString()).contains(b.toString());
        SwingUtilities.invokeAndWait(w::close);
    }

    @Test
    @DisplayName("git's /dev/null side is an empty pane named 'no file'")
    void emptySide() throws Exception {
        Path b = Files.writeString(tmp.resolve("new.txt"), "fresh\n");
        DiffWindow w = open(null, b.toFile());
        assertThat(w.getName()).isEqualTo("no file ↔ new.txt");
        SwingUtilities.invokeAndWait(w::close);
    }

    @Test
    @DisplayName("two binaries one byte apart read 'Binary files that differ'; identical ones never do")
    void binaryVerdict() throws Exception {
        Path l = Files.write(tmp.resolve("l.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 1, 2});
        Path r = Files.write(tmp.resolve("r.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 1, 3});
        Path same = Files.write(tmp.resolve("same.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 1, 2});
        DiffWindow differ = open(l.toFile(), r.toFile());
        DiffWindow.awaitBytesForTest();
        assertThat(bar(differ)).isEqualTo("Binary files that differ");
        DiffWindow alike = open(l.toFile(), same.toFile());
        DiffWindow.awaitBytesForTest();
        assertThat(bar(alike)).isNotEqualTo("Binary files that differ");
        SwingUtilities.invokeAndWait(() -> {
            differ.close();
            alike.close();
        });
    }
}
