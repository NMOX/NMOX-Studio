package org.nmox.studio.ui.shortcuts;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sheet lists what the keymap honors — the profile's Keymaps folder
 * AND the global Shortcuts folder where the Welcome's doors live
 * (v2.85.0: ⇧⌘E / ⇧⌘N / ⇧⌘L were missing from a sheet that said
 * "every NMOX shortcut"); a chord bound in both lists once, the
 * Keymaps way, because that is what the keypress does.
 */
class KeyboardShortcutsActionTest {

    private static FileObject shadow(FileObject folder, String chord, String original) throws Exception {
        FileObject f = folder.createData(chord, "shadow");
        f.setAttribute("originalFile", original);
        return f;
    }

    @Test
    @DisplayName("Shortcuts/ doors join the sheet; a chord in both folders lists once, the Keymaps way")
    void shortcutsFolderJoinsTheSheet() throws Exception {
        FileSystem fs = FileUtil.createMemoryFileSystem();
        FileObject keymaps = FileUtil.createFolder(fs.getRoot(), "Keymaps/NetBeans");
        FileObject shortcuts = FileUtil.createFolder(fs.getRoot(), "Shortcuts");
        shadow(keymaps, "DA-4", "Actions/Window/org-nmox-studio-ui-actions-OpenBrowserAction.instance");
        shadow(keymaps, "DS-X", "Actions/File/org-nmox-studio-ui-actions-KeymapsWins.instance");
        shadow(shortcuts, "DS-E", "Actions/File/org-nmox-studio-ui-actions-NewExperimentAction.instance");
        shadow(shortcuts, "DS-X", "Actions/File/org-nmox-studio-ui-actions-ShortcutsLoses.instance");
        shadow(shortcuts, "D-Q", "Actions/System/org-netbeans-core-actions-Platform.instance");

        List<ShortcutSheet.Row> rows = KeyboardShortcutsAction.rows(keymaps, shortcuts, true);

        assertThat(rows).extracting(ShortcutSheet.Row::chord)
                .as("the door from Shortcuts/ is listed; the platform's own row is not")
                .contains("⇧⌘E", "⌥⌘4").doesNotContain("⌘Q");
        assertThat(rows).filteredOn(r -> r.chord().equals("⇧⌘X"))
                .as("a chord bound in both folders lists once")
                .hasSize(1)
                .allMatch(r -> r.action().contains("KeymapsWins"), "the Keymaps binding is what the keypress does");
        assertThat(KeyboardShortcutsAction.rows(keymaps, null, true))
                .as("no Shortcuts folder at all still lists the profile").hasSize(2);
    }
}
