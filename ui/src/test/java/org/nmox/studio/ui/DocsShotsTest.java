package org.nmox.studio.ui;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The screenshot forge's contract: every shot it takes belongs to a real
 * tutorial (no orphan images, no drift when tutorials are renamed), and
 * without the property the @OnStart hook is a single property read — the
 * zero-boot-cost law.
 */
class DocsShotsTest {

    @Test
    @DisplayName("Every shot maps to an existing tutorial page")
    void shotsMatchTutorials() {
        File tutorials = new File("../docs/tutorials");
        assertThat(tutorials).isDirectory();
        java.util.List<String> all = new java.util.ArrayList<>(DocsShots.SHOTS.values());
        all.addAll(DocsShots.DIALOG_SHOTS.values());
        for (String image : all) {
            String page = image.replace(".png", ".md");
            assertThat(new File(tutorials, page))
                    .as("shot %s illustrates a real tutorial", image)
                    .isFile();
        }
        assertThat(all)
                .as("one image per shot, no duplicates across tabs and dialogs")
                .doesNotHaveDuplicates();
        // forge v2's reason to exist: the two dialog-only tutorials
        assertThat(DocsShots.DIALOG_SHOTS.values())
                .contains("learning-spaces.png", "wizards-and-kits.png", "agent-port.png");
    }

    @Test
    @DisplayName("a shot is painted at 2x for the docs, and at the scale a walk names")
    void theScaleIsTwoUnlessNamed() {
        assertThat(DocsShots.Session.shotScale(null)).as("the docs' default").isEqualTo(2);
        assertThat(DocsShots.Session.shotScale("1")).as("a walk on a 1x screen").isEqualTo(1);
        assertThat(DocsShots.Session.shotScale(" 3 ")).isEqualTo(3);
        assertThat(DocsShots.Session.shotScale("0")).as("nothing paints at zero").isEqualTo(2);
        assertThat(DocsShots.Session.shotScale("40")).as("nor at a size that exhausts the heap").isEqualTo(2);
        assertThat(DocsShots.Session.shotScale("two")).isEqualTo(2);
    }

    @Test
    @DisplayName("Without the property, the boot hook does nothing")
    void gateHoldsWithoutProperty() {
        // the zero-boot-cost law: no property → return before any window
        // system touch. In this bare unit-test JVM a WindowManager call would
        // be observable (the dummy implementation logs/throws on some paths);
        // the real assertion is simply that run() is a no-op that cannot fail.
        System.clearProperty("nmox.shots.dir");
        new DocsShots().run(); // must not throw, must not require a window system
    }

    @Test
    @DisplayName("the staged phase is off unless its property is set")
    void stagedPhaseIsOptIn() {
        System.clearProperty("nmox.shots.staged");
        assertThat(DocsShots.Session.staged()).isFalse();
        System.setProperty("nmox.shots.staged", "1");
        try {
            assertThat(DocsShots.Session.staged()).isTrue();
        } finally {
            System.clearProperty("nmox.shots.staged");
        }
    }

    @Test
    @DisplayName("a KVASIR faceplate showing a refusal or its idle hint is never photographed as a diagnosis")
    void kvasirAnsweredRefusesRefusals() {
        assertThat(DocsShots.Session.kvasirAnswered(null)).isFalse();
        assertThat(DocsShots.Session.kvasirAnswered("  ")).isFalse();
        assertThat(DocsShots.Session.kvasirAnswered("NO API KEY \u2014 PRESS KEY\u2026 TO SET ONE")).isFalse();
        assertThat(DocsShots.Session.kvasirAnswered("AUTO-EXPLAIN NEEDS CONSENT \u2014 PRESS EXPLAIN ONCE")).isFalse();
        assertThat(DocsShots.Session.kvasirAnswered("NOTHING TO EXPLAIN \u2014 NO FAILED RUN")).isFalse();
        assertThat(DocsShots.Session.kvasirAnswered("READY \u2014 LAST RUN FAILED, PRESS EXPLAIN")).isFalse();
        assertThat(DocsShots.Session.kvasirAnswered("EXPLAIN NEEDS YOUR OK \u2014 PRESS AGAIN")).isFalse();
        assertThat(DocsShots.Session.kvasirAnswered("CONSULTING KVASIR\u2026")).isFalse();
        assertThat(DocsShots.Session.kvasirAnswered("OFFLINE \u2014 COULD NOT REACH KVASIR")).isFalse();
        assertThat(DocsShots.Session.kvasirAnswered("# \u05d0\u05d1\u05d7\u05e0\u05d4\n\u05d4\u05d1\u05d3\u05d9\u05e7\u05d4 \u05e0\u05db\u05e9\u05dc\u05d4")).isTrue();
    }

    @Test
    @DisplayName("walk-only dialog specs parse, skip malformed entries, and carry a tab index")
    void walkDialogSpecs() {
        assertThat(DocsShots.walkDialogs(null)).isEmpty();
        assertThat(DocsShots.walkDialogs("Help/a.B=about.png, System/c.D=plugins.png#tab=3, junk, =x, nocat=y.png"))
                .containsExactly(
                        java.util.Map.entry("Help/a.B", "about.png"),
                        java.util.Map.entry("System/c.D", "plugins.png#tab=3"));
        assertThat(DocsShots.tabIndex("plugins.png#tab=3")).isEqualTo(3);
        assertThat(DocsShots.tabIndex("about.png")).isEqualTo(-1);
        assertThat(DocsShots.tabIndex("x.png#tab=no")).isEqualTo(-1);
        assertThat(DocsShots.shotFile("plugins.png#tab=3")).isEqualTo("plugins.png");
        assertThat(DocsShots.shotFile("about.png")).isEqualTo("about.png");
        assertThat(DocsShots.tabIndex("p.png#tab=3#find=NMOX")).isEqualTo(3);
        assertThat(DocsShots.findText("p.png#tab=3#find=NMOX")).isEqualTo("NMOX");
        assertThat(DocsShots.findText("p.png#tab=3")).isNull();
        assertThat(DocsShots.shotFile("p.png#tab=3#find=NMOX")).isEqualTo("p.png");
    }

    @Test
    @DisplayName("the forge closes a dialog by its close box; a bare dispose() accepts it")
    void dialogsAreClosedNotAccepted() throws java.io.IOException {
        // A NotifyDescriptor starts out holding its initial value, OK for a
        // dialog with an OK button, and only a button or WINDOW_CLOSING
        // changes that. The forge disposed its dialogs for 230 releases and
        // so accepted every one it photographed (3.5.1). The executed proof
        // is scripts/platform-walk.sh, which fails when a photographed dialog
        // left something behind; this holds the shape between walks. Nothing
        // here can open a dialog: the suite runs headless.
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src", "main", "java", "org", "nmox", "studio", "ui", "DocsShots.java"),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
        String code = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\b(\\w+)\\.dispose\\(\\)").matcher(code);
        java.util.List<String> disposed = new java.util.ArrayList<>();
        while (m.find()) {
            disposed.add(m.group(1));
        }
        assertThat(disposed).as("what the forge disposes: a Graphics, and one window after its close box")
                .containsExactlyInAnyOrder("g", "w");
        int closing = code.indexOf("WindowEvent.WINDOW_CLOSING");
        int dispose = code.indexOf("w.dispose()");
        assertThat(closing).as("the close box is sent").isPositive();
        assertThat(dispose).as("and the window is disposed only after it, as a fallback")
                .isGreaterThan(closing);
        String closeAll = code.substring(code.indexOf("private static void closeAllDialogs()"));
        closeAll = closeAll.substring(0, closeAll.indexOf("static void closeAsItsCloseBoxDoes"));
        assertThat(closeAll).as("every dialog goes through the close box")
                .contains("closeAsItsCloseBoxDoes(d)");
    }
}
