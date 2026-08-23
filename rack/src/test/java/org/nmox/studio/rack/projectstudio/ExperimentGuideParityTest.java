package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The walkthrough's one rot-prone claim, pinned: "open {@code this
 * file} and change it" must name a file the template REALLY writes.
 * Each template is generated for real and the guide's named edit file
 * asserted against the output — a template that renames its main file
 * fails this test, not the learner (v2.36.0). The structural claims
 * (run gesture, lifecycle verbs, the learning-space count) are pinned
 * here too so the text can't drift from the product's menus.
 */
class ExperimentGuideParityTest {

    @ParameterizedTest
    @EnumSource(ProjectTemplates.class)
    @DisplayName("the guide's edit-this-file claim names a file the template writes")
    void editFileExistsInGeneratedOutput(ProjectTemplates t, @TempDir Path work) throws Exception {
        File dir = new File(work.toFile(), "probe-experiment");
        t.generate(dir, dir.getName());
        String editFile = ExperimentGuide.editFile(t, dir.getName());
        assertThat(new File(dir, editFile))
                .as("%s's walkthrough says to edit %s — the template must write it", t, editFile)
                .isFile();
        assertThat(ExperimentGuide.walkthrough(t, dir.getName()))
                .as("the walkthrough names its own edit file")
                .contains("`" + editFile + "`");
    }

    @ParameterizedTest
    @EnumSource(ProjectTemplates.class)
    @DisplayName("every walkthrough carries the run gesture, the lifecycle, and the catalog pointer")
    void structuralClaimsPresent(ProjectTemplates t) {
        String md = ExperimentGuide.walkthrough(t, "probe");
        assertThat(md).contains("**F6**");
        assertThat(md)
                .as("the lifecycle names the real menu items")
                .contains("File ▸ Experiments…")
                .contains("Promote")
                .contains("Discard");
        assertThat(md)
                .as("the catalog pointer names the real gesture and count")
                .contains("File ▸ New Learning Space…")
                .contains("92 guided tutorials");
        assertThat(md).contains(t.getDisplayName());
    }
}
