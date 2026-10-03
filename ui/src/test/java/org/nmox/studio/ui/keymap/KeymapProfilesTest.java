package org.nmox.studio.ui.keymap;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.ui.keymap.KeymapProfiles.Outcome;
import org.nmox.studio.ui.keymap.StartOrContinueDebuggingAction.Step;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The switch to the VS Code keymap does what Options' Apply does and says
 * what happened; F5 in that profile starts or continues, never both.
 */
class KeymapProfilesTest {

    /** A platform with two keymap managers that record what they were told. */
    private static final class Fake implements KeymapProfiles.Platform {
        boolean registered = true;
        boolean managersTakeIt = true;
        boolean unreachable;
        int managerCount = 2;
        String current = "NetBeans";
        final List<String> told = new ArrayList<>();

        @Override
        public boolean registered(String profile) {
            return registered;
        }

        @Override
        public String current() {
            return current;
        }

        @Override
        public List<KeymapProfiles.Setter> managers() throws ReflectiveOperationException {
            if (unreachable) {
                throw new ClassNotFoundException(KeymapProfiles.MANAGER);
            }
            List<KeymapProfiles.Setter> out = new ArrayList<>();
            for (int i = 0; i < managerCount; i++) {
                out.add(profile -> {
                    told.add(profile);
                    if (managersTakeIt) {
                        current = profile;
                    }
                });
            }
            return out;
        }
    }

    @Test
    @DisplayName("switching tells every keymap manager the profile's id, and reports a switch only when the platform then names it current")
    void switchTellsEveryManager() {
        Fake p = new Fake();
        assertThat(KeymapProfiles.use(KeymapProfiles.VSCODE, p)).isEqualTo(Outcome.SWITCHED);
        assertThat(p.told).containsExactly("VSCode", "VSCode");
    }

    @Test
    @DisplayName("an unregistered profile changes nothing and says so")
    void unregisteredProfileRefuses() {
        Fake p = new Fake();
        p.registered = false;
        assertThat(KeymapProfiles.use(KeymapProfiles.VSCODE, p)).isEqualTo(Outcome.NO_PROFILE);
        assertThat(p.told).isEmpty();
    }

    @Test
    @DisplayName("already in the profile: nothing is told, and the answer is ALREADY")
    void alreadyCurrent() {
        Fake p = new Fake();
        p.current = "VSCode";
        assertThat(KeymapProfiles.use(KeymapProfiles.VSCODE, p)).isEqualTo(Outcome.ALREADY);
        assertThat(p.told).isEmpty();
    }

    @Test
    @DisplayName("managers that cannot be reached, or that do not take the profile, or none at all, are a failure - never a claimed switch")
    void failuresSpeak() {
        Fake unreachable = new Fake();
        unreachable.unreachable = true;
        assertThat(KeymapProfiles.use(KeymapProfiles.VSCODE, unreachable)).isEqualTo(Outcome.FAILED);
        Fake ignored = new Fake();
        ignored.managersTakeIt = false;
        assertThat(KeymapProfiles.use(KeymapProfiles.VSCODE, ignored)).isEqualTo(Outcome.FAILED);
        Fake none = new Fake();
        none.managerCount = 0;
        assertThat(KeymapProfiles.use(KeymapProfiles.VSCODE, none)).isEqualTo(Outcome.FAILED);
    }

    @Test
    @DisplayName("every outcome has its own sentence, and the switch names the way back on each OS")
    void everyOutcomeSpeaks() {
        assertThat(UseVsCodeKeymapAction.message(Outcome.SWITCHED, false)).contains("Tools ▸ Options ▸ Keymap ▸ Profile");
        assertThat(UseVsCodeKeymapAction.message(Outcome.SWITCHED, true)).contains("Settings… ▸ Keymap ▸ Profile");
        assertThat(UseVsCodeKeymapAction.message(Outcome.FAILED, false)).contains("not changed");
        assertThat(UseVsCodeKeymapAction.message(Outcome.FAILED, true)).contains("Settings…");
        assertThat(UseVsCodeKeymapAction.message(Outcome.NO_PROFILE, false)).contains("not installed");
        assertThat(UseVsCodeKeymapAction.message(Outcome.ALREADY, false)).contains("already");
    }

    @Test
    @DisplayName("F5: a paused session continues; a running one is left alone; with none, the project starts under the debugger")
    void f5DecidesOneStep() {
        assertThat(StartOrContinueDebuggingAction.decide(true, true, true)).isEqualTo(Step.CONTINUE);
        assertThat(StartOrContinueDebuggingAction.decide(false, true, true)).isEqualTo(Step.RUNNING);
        assertThat(StartOrContinueDebuggingAction.decide(false, false, true)).isEqualTo(Step.START);
        assertThat(StartOrContinueDebuggingAction.decide(false, false, false)).isEqualTo(Step.NOTHING);
    }
}
