package org.nmox.studio.editor.vscode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.editor.vscode.ImportVsCodeSettingsAction.Prepared;
import org.nmox.studio.editor.vscode.ImportVsCodeSettingsAction.Problem;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Build;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Change;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Location;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Outcome;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Row;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Target;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gesture's halves: what is found and read off the event thread,
 * what is shown or refused, what the sheet's checkboxes choose, that
 * Cancel writes nothing, and where each value lands.
 */
class ImportVsCodeSettingsActionTest {

    @TempDir
    Path home;

    private List<Location> candidates() {
        return VsCodeUserSettings.candidates("Linux", home.toString(), name -> null);
    }

    private Path write(Build build, String json) throws Exception {
        Location l = candidates().stream().filter(c -> c.build() == build).findFirst().orElseThrow();
        Files.createDirectories(l.file().getParent());
        Files.writeString(l.file(), json);
        return l.file();
    }

    private Prepared prepare(Location choice) {
        return ImportVsCodeSettingsAction.prepare(candidates(), Files::isRegularFile, choice,
                ImportVsCodeSettingsAction.BOUNDED, () -> List.of("Menlo"));
    }

    /** Records what would have been shown. */
    private static final class Shown implements ImportVsCodeSettingsAction.Presenter {
        final List<String> refusals = new ArrayList<>();
        final List<Prepared> sheets = new ArrayList<>();

        @Override
        public void refuse(String message) {
            refusals.add(message);
        }

        @Override
        public void sheet(Prepared prepared) {
            sheets.add(prepared);
        }
    }

    @Test
    @DisplayName("no settings file anywhere: the status line names every place looked in, and no sheet")
    void notFoundSaysWhereItLooked() {
        Prepared p = prepare(null);
        assertThat(p.problem()).isEqualTo(Problem.NOT_FOUND);
        Shown shown = new Shown();
        ImportVsCodeSettingsAction.present(p, shown);
        assertThat(shown.sheets).isEmpty();
        assertThat(shown.refusals).hasSize(1);
        for (Location l : candidates()) {
            assertThat(shown.refusals.get(0)).contains(l.file().toString());
        }
    }

    @Test
    @DisplayName("the first build found is read; the others are offered; a choice reads that one")
    void firstFoundIsReadAndChoiceIsHonoured() throws Exception {
        write(Build.INSIDERS, "{\"editor.tabSize\": 3}");
        write(Build.CODIUM, "{\"editor.tabSize\": 5}");
        Prepared first = prepare(null);
        assertThat(first.problem()).isNull();
        assertThat(first.read().build()).isEqualTo(Build.INSIDERS);
        assertThat(first.found()).extracting(Location::build).containsExactly(Build.INSIDERS, Build.CODIUM);
        assertThat(first.plan().rows().get(0).changes()).contains(new Change(Target.TAB_SIZE, 3));

        Prepared chosen = prepare(first.found().get(1));
        assertThat(chosen.read().build()).isEqualTo(Build.CODIUM);
        assertThat(chosen.plan().rows().get(0).changes()).contains(new Change(Target.TAB_SIZE, 5));

        Shown shown = new Shown();
        ImportVsCodeSettingsAction.present(first, shown);
        assertThat(shown.sheets).containsExactly(first);
        assertThat(ImportVsCodeSettingsSheet.heading(first)).contains("VS Code Insiders").contains(first.read().file()
                .toString());
    }

    @Test
    @DisplayName("the read is bounded: an over-cap file is refused before it is read, by name")
    void overCapIsRefused() throws Exception {
        Path file = write(Build.CODE, "{\"editor.tabSize\": 2, \"pad\": \""
                + "x".repeat((int) VsCodeUserSettings.MAX_BYTES) + "\"}");
        Prepared p = prepare(null);
        assertThat(p.problem()).isEqualTo(Problem.TOO_LARGE);
        assertThat(p.plan()).isNull();
        assertThat(ImportVsCodeSettingsAction.problem(p)).contains(file.toString()).contains("larger");
    }

    @Test
    @DisplayName("a file that is not JSON is unreadable; one with nothing recognised says so; neither shows a sheet")
    void unreadableAndNothing() throws Exception {
        write(Build.CODE, "{\"editor.tabSize\": ");
        assertThat(prepare(null).problem()).isEqualTo(Problem.UNREADABLE);
        write(Build.CODE, "{\"github.token\": \"ghp_x\", \"python.venvPath\": \"/tmp\"}");
        Prepared nothing = prepare(null);
        assertThat(nothing.problem()).isEqualTo(Problem.NOTHING);
        Shown shown = new Shown();
        ImportVsCodeSettingsAction.present(nothing, shown);
        assertThat(shown.sheets).isEmpty();
        assertThat(shown.refusals.get(0)).contains("has a place in NMOX Studio").doesNotContain("github").doesNotContain("ghp_x");
    }

    @Test
    @DisplayName("the font list is asked only when a file was read")
    void fontsAskedOnlyForARead() throws Exception {
        AtomicInteger asked = new AtomicInteger();
        ImportVsCodeSettingsAction.prepare(candidates(), Files::isRegularFile, null, ImportVsCodeSettingsAction.BOUNDED,
                () -> {
                    asked.incrementAndGet();
                    return List.of();
                });
        assertThat(asked).hasValue(0);
    }

    // ------------------------------------------------------------------ the sheet

    private static List<Row> rows() throws Exception {
        return VsCodeUserSettings.plan("""
                {"editor.tabSize": 2, "files.autoSave": "afterDelay", "workbench.colorTheme": "Monokai",
                 "editor.minimap.enabled": false}
                """, List.of()).rows();
    }

    @Test
    @DisplayName("exact rows start checked, near rows unchecked, rows with nothing to do cannot be checked")
    void checkboxesStartAtTheFit() throws Exception {
        ImportVsCodeSettingsSheet.Model m = new ImportVsCodeSettingsSheet.Model(rows());
        assertThat(m.chosen()).extracting(Row::key).containsExactly("editor.tabSize", "editor.minimap.enabled");
        int theme = indexOf(m, "workbench.colorTheme");
        assertThat(m.isCellEditable(theme, 0)).isFalse();
        m.setValueAt(true, theme, 0);
        assertThat(m.getValueAt(theme, 0)).isEqualTo(false);
        int autoSave = indexOf(m, "files.autoSave");
        assertThat(m.isCellEditable(autoSave, 0)).isTrue();
        m.setValueAt(true, autoSave, 0);
        assertThat(m.chosen()).extracting(Row::key).contains("files.autoSave");
    }

    @Test
    @DisplayName("an unchecked row is not applied")
    void uncheckedRowIsNotApplied() throws Exception {
        ImportVsCodeSettingsSheet.Model m = new ImportVsCodeSettingsSheet.Model(rows());
        m.setValueAt(false, indexOf(m, "editor.minimap.enabled"), 0);
        VsCodeUserSettingsTest.Recorder homes = new VsCodeUserSettingsTest.Recorder();
        Object apply = new Object();
        Outcome o = ImportVsCodeSettingsSheet.close(apply, apply, m, homes);
        assertThat(o.applied()).containsExactly("editor.tabSize");
        assertThat(homes.writes).extracting(Change::target).doesNotContain(Target.MINIMAP);
    }

    @Test
    @DisplayName("Cancel, or the close box, writes nothing")
    void cancelWritesNothing() throws Exception {
        ImportVsCodeSettingsSheet.Model m = new ImportVsCodeSettingsSheet.Model(rows());
        VsCodeUserSettingsTest.Recorder homes = new VsCodeUserSettingsTest.Recorder();
        Object apply = new Object();
        assertThat(ImportVsCodeSettingsSheet.close(new Object(), apply, m, homes)).isNull();
        assertThat(ImportVsCodeSettingsSheet.close(null, apply, m, homes)).isNull();
        assertThat(homes.writes).isEmpty();
        assertThat(homes.settled).isEmpty();
    }

    @Test
    @DisplayName("the status line counts what was applied and names what could not be")
    void statusSentence() {
        assertThat(ImportVsCodeSettingsSheet.said(new Outcome(List.of("a", "b"), List.of())))
                .isEqualTo("Applied 2 VS Code settings");
        assertThat(ImportVsCodeSettingsSheet.said(new Outcome(List.of("a"), List.of("files.autoSave"))))
                .isEqualTo("Applied 1 VS Code setting. Not applied: files.autoSave");
        assertThat(ImportVsCodeSettingsSheet.said(new Outcome(List.of(), List.of())))
                .isEqualTo("No VS Code settings were applied");
    }

    private static int indexOf(ImportVsCodeSettingsSheet.Model m, String key) {
        for (int i = 0; i < m.getRowCount(); i++) {
            if (m.row(i).key().equals(key)) {
                return i;
            }
        }
        throw new AssertionError(key);
    }

    // ------------------------------------------------------------------ the homes

    @Test
    @DisplayName("each change lands in the platform's own key, with the platform's own type")
    void homesWriteThePlatformKeys() {
        MemoryPreferences all = new MemoryPreferences();
        MemoryPreferences autosave = new MemoryPreferences();
        List<String> calls = new ArrayList<>();
        ProductHomes homes = new ProductHomes(() -> all, () -> autosave, () -> calls.add("synchronize"),
                () -> calls.add("refresh"), on -> calls.add("minimap " + on), on -> calls.add("sticky " + on),
                on -> calls.add("format " + on));
        homes.write(new Change(Target.TAB_SIZE, 4));
        homes.write(new Change(Target.INDENT, 2));
        homes.write(new Change(Target.EXPAND_TABS, true));
        homes.write(new Change(Target.LINE_WRAP, "words"));
        homes.write(new Change(Target.RULER_WIDTH, 100));
        homes.write(new Change(Target.RULER_VISIBLE, true));
        homes.write(new Change(Target.WHITESPACE_VISIBLE, false));
        homes.write(new Change(Target.TRIM_ON_SAVE, "always"));
        homes.write(new Change(Target.AUTOSAVE_ACTIVE, true));
        homes.write(new Change(Target.AUTOSAVE_MINUTES, 3));
        homes.write(new Change(Target.AUTOSAVE_ON_FOCUS_LOST, false));
        homes.write(new Change(Target.MINIMAP, false));
        homes.write(new Change(Target.STICKY_SCROLL, true));
        homes.write(new Change(Target.FORMAT_ON_SAVE, false));

        assertThat(all.getInt("tab-size", -1)).isEqualTo(4);
        assertThat(all.getInt("indent-shift-width", -1)).isEqualTo(2);
        assertThat(all.getInt("spaces-per-tab", -1)).isEqualTo(2);
        assertThat(all.getBoolean("expand-tabs", false)).isTrue();
        assertThat(all.get("text-line-wrap", null)).isEqualTo("words");
        assertThat(all.getInt("text-limit-width", -1)).isEqualTo(100);
        assertThat(all.getBoolean("text-limit-line-visible", false)).isTrue();
        assertThat(all.getBoolean("non-printable-characters-visible", true)).isFalse();
        assertThat(all.get("on-save-remove-trailing-whitespace", null)).isEqualTo("always");
        assertThat(autosave.getBoolean("autoSaveActive", false)).isTrue();
        assertThat(autosave.getInt("autoSaveInterval", -1)).isEqualTo(3);
        assertThat(autosave.getBoolean("autoSaveOnFocusLost", true)).isFalse();
        assertThat(calls).containsExactly("minimap false", "sticky true", "format false");

        homes.settled(Set.of(Target.MINIMAP));
        assertThat(calls).as("nothing to nudge").hasSize(3);
        homes.settled(Set.of(Target.LINE_WRAP, Target.AUTOSAVE_MINUTES));
        assertThat(calls).endsWith("refresh", "synchronize");
    }

    @Test
    @DisplayName("without the platform autosave module the autosave row is refused, and says which module")
    void autosaveModuleAbsentIsRefused() {
        // the module is not on this test's class path: the product's reach-by-name must refuse, not guess
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ProductHomes.autosaveCall("prefs", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("autosave module");
        ProductHomes homes = new ProductHomes(MemoryPreferences::new, () -> (java.util.prefs.Preferences)
                ProductHomes.autosaveCall("prefs", null), () -> { }, () -> { }, on -> { }, on -> { }, on -> { });
        Outcome o = VsCodeUserSettings.apply(List.of(new Row("files.autoSave", "off", "here",
                VsCodeUserSettings.Fit.EXACT, List.of(new Change(Target.AUTOSAVE_ACTIVE, false)))), homes);
        assertThat(o.failed()).containsExactly("files.autoSave");
        assertThat(o.applied()).isEmpty();
    }
}
