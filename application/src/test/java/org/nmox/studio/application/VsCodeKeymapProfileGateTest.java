package org.nmox.studio.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The VS Code keymap profile is what the generator makes of the cluster this
 * build assembled ({@link VsCodeKeymapProfile}): the default profile's
 * bindings with {@code scripts/vscode-keymap/chords.txt} laid over them. The
 * committed layer regions, keybinding files and {@code displaced.txt} must be
 * exactly that output. A platform upgrade that changes the default profile, a
 * product chord added to it, or an edit to the chord table all change the
 * output, and this fails until {@code scripts/generate-vscode-keymap.sh} has
 * been run and its result committed.
 *
 * <p>With {@code -Dnmox.vscode.keymap.write=true} (what the script passes)
 * the test first writes the output over the committed files, so the compare
 * that follows holds; what changed is then for a person to read in
 * {@code git diff} and commit.
 */
class VsCodeKeymapProfileGateTest {

    static final String WRITE = "nmox.vscode.keymap.write";

    @Test
    @DisplayName("the committed VS Code profile is exactly what the generator makes of this cluster and chords.txt")
    void committedProfileIsGenerated() throws Exception {
        if (Boolean.getBoolean(WRITE)) {
            // the script names this test with -Dtest, which also runs it in the test phase,
            // before package has assembled the cluster; there it has nothing to read yet
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    java.nio.file.Files.isDirectory(VsCodeKeymapProfile.CLUSTER.resolve("nmoxstudio")),
                    "write mode, before package: the cluster is not assembled yet");
        }
        VsCodeKeymapProfile.Output generated =
                VsCodeKeymapProfile.generate(VsCodeKeymapProfile.load(), VsCodeChordTable.read());
        if (Boolean.getBoolean(WRITE)) {
            VsCodeKeymapProfile.write(generated);
        }
        VsCodeKeymapProfile.Output committed = VsCodeKeymapProfile.committed();
        String fix = " - run scripts/generate-vscode-keymap.sh and commit what it writes";
        assertThat(committed.globalRegion()).as("ui layer Keymaps/VSCode region" + fix).isEqualTo(generated.globalRegion());
        assertThat(committed.editorsRegion()).as("ui layer Editors region of the VS Code profile" + fix)
                .isEqualTo(generated.editorsRegion());
        assertThat(committed.files().keySet()).as("the keybinding files of the VS Code profile" + fix)
                .isEqualTo(generated.files().keySet());
        for (Map.Entry<String, String> f : generated.files().entrySet()) {
            assertThat(committed.files().get(f.getKey())).as(f.getKey() + fix).isEqualTo(f.getValue());
        }
        assertThat(committed.displaced()).as("scripts/vscode-keymap/displaced.txt" + fix).isEqualTo(generated.displaced());
    }

    @Test
    @DisplayName("every row of chords.txt is unique per OS: no chord is claimed twice in one scope")
    void noChordClaimedTwice() throws Exception {
        List<String> twice = new ArrayList<>();
        List<VsCodeChordTable.Row> rows = VsCodeChordTable.read();
        for (VsCodeChordTable.Os os : VsCodeChordTable.Os.values()) {
            TreeSet<String> seen = new TreeSet<>();
            for (VsCodeChordTable.Row r : rows) {
                List<String> seq = r.chords().get(os);
                if (seq != null && !seen.add(r.scope() + " " + seq)) {
                    twice.add(os + " " + r.scope() + " " + seq + " at " + r);
                }
            }
        }
        assertThat(twice).isEmpty();
        assertThat(rows).as("the table was read").hasSizeGreaterThan(60);
    }
}
