package org.nmox.studio.ui.gettingstarted;

import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GettingStartedTest {

    @Test
    @DisplayName("Five steps with stable keys, counted and phrased as 'n of 5'")
    void arithmetic() {
        assertThat(GettingStarted.STEPS).extracting(GettingStarted.Step::key)
                .containsExactly("project", "run", "serve", "oracle", "learn");
        assertThat(GettingStarted.progress(Set.of())).isEqualTo("0 of 5");
        assertThat(GettingStarted.progress(Set.of("project", "learn"))).isEqualTo("2 of 5");
        // an unknown key never counts
        assertThat(GettingStarted.done(Set.of("project", "bogus"))).isEqualTo(1);
    }

    @Test
    @DisplayName("next() is the first untouched step in order; all five done means no next")
    void next() {
        assertThat(GettingStarted.next(Set.of()).key()).isEqualTo("project");
        assertThat(GettingStarted.next(Set.of("project", "serve")).key()).isEqualTo("run");
        assertThat(GettingStarted.next(Set.of("project", "run", "serve", "oracle", "learn"))).isNull();
        assertThat(GettingStarted.allDone(Set.of("project", "run", "serve", "oracle", "learn"))).isTrue();
    }

    @Test
    @DisplayName("The column shows only while something is left AND the user has not hidden it")
    void visibility() {
        assertThat(GettingStarted.visible(Set.of(), false)).isTrue();
        assertThat(GettingStarted.visible(Set.of(), true)).isFalse();
        assertThat(GettingStarted.visible(Set.of("project", "run", "serve", "oracle", "learn"), false)).isFalse();
    }

    @Test
    @DisplayName("Every step is a door: its target's action id exists in the generated layer, or it names a window or a guide anchor (v2.69.9 — the walk found the rows inert)")
    void everyStepOpensSomething() throws Exception {
        String layer = Files.readString(Path.of("target/classes/META-INF/generated-layer.xml"));
        for (GettingStarted.Step s : GettingStarted.STEPS) {
            GettingStarted.Target t = s.target();
            assertThat(t).as("step %s has a target", s.key()).isNotNull();
            switch (t.kind()) {
                case ACTION -> assertThat(layer)
                        .as("step %s: the action %s/%s must be registered — a dead id is a silent dud", s.key(), t.category(), t.id())
                        .contains("Actions/" + t.category() + "/" + t.id().replace('.', '-') + ".instance");
                case WINDOW -> assertThat(t.id()).as("step %s names a TopComponent", s.key()).endsWith("TopComponent");
                case GUIDE -> assertThat(t.id()).as("step %s names a guide anchor", s.key()).startsWith("#");
            }
        }
    }

    @Test
    @DisplayName("The Welcome builds every step row as a link, never a label (source law)")
    void rowsAreLinks() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/MainWindow.java"));
        int start = src.indexOf("private void paintGettingStarted(");
        int end = src.indexOf("gettingStarted.revalidate();", start);
        String body = src.substring(start, end);
        assertThat(body).as("rows ride stepLink — a JLabel row is the v2.66.0 dud").contains("stepLink(").doesNotContain("new JLabel(");
    }

    @Test
    @DisplayName("The checklist is FIRST STEPS (START already names the launchpad verbs), and the serve tick is listener-driven while the Welcome shows (v2.69.11)")
    void firstStepsAndTheServeListener() throws Exception {
        String src = Files.readAllLines(Path.of("src/main/java/org/nmox/studio/ui/MainWindow.java"))
                .stream().filter(l -> !l.strip().startsWith("//") && !l.strip().startsWith("*")).collect(java.util.stream.Collectors.joining("\n"));
        assertThat(src).contains("\"FIRST STEPS\"").doesNotContain("\"GETTING STARTED\"");
        assertThat(src).as("a server goes live while the user looks at the editor: the Welcome listens for its whole OPEN life")
                .contains("live.addListener(servingsListener)").contains("liveClosed.removeListener(servingsListener)")
                .contains("GettingStartedSignals.serverAppeared()");
        int opened = src.indexOf("public void componentOpened()");
        int attach = src.indexOf("live.addListener(servingsListener)");
        int earlyReturn = src.indexOf("return;", opened);
        assertThat(attach).as("attached in componentOpened").isGreaterThan(opened);
        assertThat(attach).as("… BEFORE the welcomeShown early return, or every later start never listens").isLessThan(earlyReturn);
        assertThat(src.indexOf("public void componentClosed()")).as("detached in componentClosed")
                .isLessThan(src.indexOf("liveClosed.removeListener(servingsListener)"));
    }
}
