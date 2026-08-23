package org.nmox.studio.ui.actions;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The teaching wiring of New Experiment (v2.36.0), source-gated: the
 * walkthrough must OPEN (a guide on disk nobody sees teaches nobody),
 * the install must be consent-gated by the checkbox and guarded by
 * package.json, and the catalog cross-link must resolve the real
 * action id. Each is a call-site claim a unit test cannot see.
 */
class ExperimentTeachingWiringTest {

    private static String src() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/ui/actions/NewExperimentAction.java"));
    }

    @Test
    @DisplayName("the walkthrough opens in the editor after the aim")
    void guideOpens() throws Exception {
        assertThat(src()).contains("openInEditor(new File(dir, Experiments.GUIDE))");
    }

    @Test
    @DisplayName("the install is checkbox-gated AND package.json-guarded")
    void installGuarded() throws Exception {
        assertThat(src()).contains(
                "if (install && new File(dir, \"package.json\").isFile())");
    }

    @Test
    @DisplayName("the catalog hand-off NEVER also creates an experiment — dispose keeps the OK value")
    void handOffCreatesNothing() throws Exception {
        assertThat(src())
                .as("dispose() leaves descriptor.getValue() at OK; only the flag "
                        + "stops the create path (caught live, v2.36.0)")
                .contains("wentToSpaces[0] = true;")
                .contains("if (wentToSpaces[0] || descriptor.getValue() != DialogDescriptor.OK_OPTION)");
    }

    @Test
    @DisplayName("the catalog cross-link resolves the real learning-space action id")
    void catalogLink() throws Exception {
        assertThat(src()).contains(
                "\"org.nmox.studio.ui.actions.NewLearningSpaceAction\"");
    }

    private static String managerSrc() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/ui/actions/ManageExperimentsAction.java"));
    }

    @Test
    @DisplayName("the empty shelf offers to start an experiment, not a dead-end message")
    void emptyShelfActs() throws Exception {
        assertThat(managerSrc())
                .contains("new NewExperimentAction().actionPerformed(e);")
                .contains("START_ONE");
    }

    @Test
    @DisplayName("the shelf header gets its size OFF the EDT and speaks the summary")
    void shelfHeaderSized() throws Exception {
        assertThat(managerSrc())
                .as("the size walk must ride the experiments lane, never the paint thread")
                .contains("EXPERIMENTS_RP.post")
                .contains("Experiments.shelfSummary(count, total)");
    }

    @Test
    @DisplayName("the manager's Open re-opens the walkthrough when one exists")
    void openReopensGuide() throws Exception {
        assertThat(managerSrc())
                .contains("NewExperimentAction.openGuide(guide)")
                .contains("guide.isFile()");
    }
}
