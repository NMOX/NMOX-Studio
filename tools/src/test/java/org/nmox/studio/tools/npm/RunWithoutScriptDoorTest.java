package org.nmox.studio.tools.npm;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.spi.project.ActionProvider;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Run on a Node project with no dev, start or serve script (3.1.0): the ▶
 * was greyed with no word about why. It stays pressable; the press says
 * why and shows the scripts the project has, and spawns nothing.
 */
class RunWithoutScriptDoorTest {

    private static final ProjectState NO_OP_STATE = new ProjectState() {
        @Override public void markModified() { }
        @Override public void notifyDeleted() { }
    };

    private static WebProjectActionProvider providerFor(Path dir) {
        return new WebProjectActionProvider(new WebProject(FileUtil.toFileObject(dir.toFile()), NO_OP_STATE));
    }

    @Test
    @DisplayName("a Node project with only a build script keeps Run pressable")
    void runStaysPressable(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("package.json"), "{\"name\":\"x\",\"scripts\":{\"build\":\"tsc\"}}");
        WebProjectActionProvider p = providerFor(tmp);
        assertThat(p.isActionEnabled(ActionProvider.COMMAND_RUN, Lookup.EMPTY)).isTrue();
        assertThat(p.isActionEnabled(ActionProvider.COMMAND_BUILD, Lookup.EMPTY)).isTrue();
        assertThat(p.isActionEnabled(ActionProvider.COMMAND_TEST, Lookup.EMPTY))
                .as("Test has no script either, and stays as it was").isFalse();
    }

    @Test
    @DisplayName("pressing it spawns nothing and says why")
    void pressSpeaksAndSpawnsNothing(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("package.json"), "{\"name\":\"x\",\"scripts\":{\"build\":\"tsc\"}}");
        java.util.List<String> said = new java.util.ArrayList<>();
        java.util.function.Consumer<String> prior = WebProjectActionProvider.noRunScriptStatus;
        WebProjectActionProvider.noRunScriptStatus = said::add;
        try {
            providerFor(tmp).invokeAction(ActionProvider.COMMAND_RUN, Lookup.EMPTY);
        } finally {
            WebProjectActionProvider.noRunScriptStatus = prior;
        }
        assertThat(said).singleElement().asString()
                .contains("dev").contains("start").contains("serve");
    }

    @Test
    @DisplayName("a project that has a dev script runs it as before")
    void devScriptStillRuns(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("package.json"), "{\"name\":\"x\",\"scripts\":{\"dev\":\"vite\"}}");
        assertThat(providerFor(tmp).isActionEnabled(ActionProvider.COMMAND_RUN, Lookup.EMPTY)).isTrue();
        assertThat(WebProjectCommands.commandFor(tmp.toFile(),
                org.nmox.studio.rack.devices.ProjectInspector.ProjectKind.NODE, ActionProvider.COMMAND_RUN))
                .isNotNull();
    }
}
