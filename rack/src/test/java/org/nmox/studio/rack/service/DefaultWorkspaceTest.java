package org.nmox.studio.rack.service;

import java.io.File;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fresh-launch default aim: {@code ~/NMOX} rather than $HOME, so the
 * IDE only ever scans one initially-empty folder and never touches a
 * macOS TCC-protected directory (~/Desktop, ~/Downloads, the Photos
 * library) on the EDT during startup.
 *
 * <p>These tests exercise the PURE path computation and the aim-priority
 * rule; none of them creates ~/NMOX in the real home directory (the
 * create-if-missing side effect is guarded to the platform run and never
 * fires here).
 */
class DefaultWorkspaceTest {

    @Test
    @DisplayName("defaultWorkspaceDir(home) is <home>/NMOX for any home")
    void computesWorkspaceUnderHome() {
        assertThat(RackService.defaultWorkspaceDir("/Users/ada"))
                .isEqualTo(new File("/Users/ada", "NMOX"));
        assertThat(RackService.defaultWorkspaceDir("/home/turing"))
                .isEqualTo(new File("/home/turing/NMOX"));
    }

    @Test
    @DisplayName("the pure computation never creates the directory")
    void pureComputationHasNoSideEffect(@org.junit.jupiter.api.io.TempDir File fakeHome) {
        File workspace = RackService.defaultWorkspaceDir(fakeHome.getAbsolutePath());
        assertThat(workspace).doesNotExist();
        assertThat(workspace.getName()).isEqualTo("NMOX");
    }

    @Test
    @DisplayName("a fresh rack, unaimed, is never pointed at bare $HOME")
    void freshRackNeverAimsAtHome() {
        // getRack() runs the startup seam (followOpenProjects throws in a plain
        // unit test and is caught, then aimAtDefaultWorkspace runs). netbeans.user
        // is unset here, so nothing is created; the aim is either ~/NMOX (if that
        // dir happens to exist on the dev box) or the rack's own ~/NMOX default.
        RackService service = new RackService();
        File aim = service.getRack().getProjectDir();
        File home = new File(System.getProperty("user.home"));
        assertThat(aim).as("must never scan bare $HOME").isNotEqualTo(home);
        assertThat(aim.getName()).as("aim is the NMOX workspace").isEqualTo("NMOX");
        service.getRack().shutdown();
    }

    @Test
    @DisplayName("an explicit aim is NOT overridden by the default-workspace seam")
    void explicitAimSurvivesDefaultSeam(@org.junit.jupiter.api.io.TempDir File project) {
        RackService service = new RackService();
        // openProject triggers getRack() init (which runs the default seam) and
        // then explicitly aims — the explicit aim must win.
        service.openProject(project);
        assertThat(service.isAimed()).isTrue();
        assertThat(service.getRack().getProjectDir()).isEqualTo(project);
        service.getRack().shutdown();
    }

    @Test
    @DisplayName("a passive open-project aim survives the default-workspace seam")
    void passiveProjectAimSurvivesDefaultSeam(@org.junit.jupiter.api.io.TempDir File project) {
        // This is the regression for the followOpenProjects() ordering: an open
        // project is aimed PASSIVELY (which never flips `aimed`), so the seam
        // must not fall back to ~/NMOX and clobber it. openProjectPassively is
        // the very first call, so it drives getRack() init and the seam runs
        // inside it, before this passive aim lands — later passive wins.
        RackService service = new RackService();
        service.openProjectPassively(project);
        assertThat(service.isAimed()).as("passive claims no intent").isFalse();
        assertThat(service.getRack().getProjectDir())
                .as("passive project aim, not ~/NMOX").isEqualTo(project);
        service.getRack().shutdown();
    }

    @Test
    @DisplayName("constructing a RackService in a plain unit JVM never creates the real ~/NMOX")
    void realHomeUntouched() {
        // The create-if-missing side effect (ensureWorkspace) is gated on the
        // platform being present (netbeans.user set). A plain unit JVM has it
        // unset, so a fresh ~/NMOX must never be conjured here. If the developer
        // genuinely already has ~/NMOX, skip rather than fail their real setup.
        org.junit.jupiter.api.Assumptions.assumeTrue(
                System.getProperty("netbeans.user") == null,
                "inside the platform the create-if-missing side effect is expected");
        File realWorkspace = RackService.defaultWorkspaceDir(System.getProperty("user.home"));
        org.junit.jupiter.api.Assumptions.assumeFalse(realWorkspace.exists(),
                "developer already has a real ~/NMOX; cannot prove non-creation");

        // exercise the full startup seam
        RackService service = new RackService();
        service.getRack();

        assertThat(realWorkspace)
                .as("a plain unit test must not create ~/NMOX in the real home")
                .doesNotExist();
        service.getRack().shutdown();
    }
}
