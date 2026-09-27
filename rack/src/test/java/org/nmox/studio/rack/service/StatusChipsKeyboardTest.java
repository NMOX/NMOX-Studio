package org.nmox.studio.rack.service;

import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.accessibility.AccessibleRole;
import javax.swing.JLabel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.core.util.KeyboardAccess;
import org.nmox.studio.rack.service.ServingRegistry.Kind;
import org.nmox.studio.rack.service.ServingRegistry.Serving;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The status line's chips without a mouse or a screen (3.4, question 3):
 * each is a pressable button to a screen reader, named in words rather than
 * by its glyphs, and the two git verbs that lived only on the chip have a
 * Team-menu door a keyboard reaches.
 */
class StatusChipsKeyboardTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("the git chip is named in words: branch, changed, ahead, behind — each clause only when the label shows it")
    void gitChipSpokenName() throws Exception {
        Path gitDir = dir.resolve("proj").resolve(".git");
        Files.createDirectories(gitDir);
        Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/main\n", StandardCharsets.UTF_8);
        GitChip chip = new GitChip();
        chip.aim(dir.resolve("proj").toFile());
        assertThat(chip.spokenName()).isEqualTo("Git: branch main");

        chip.porcelain("# branch.oid abc\n# branch.head main\n# branch.ab +2 -1\n"
                + "1 .M N... 100644 100644 100644 a a pom.xml\n"
                + "1 .M N... 100644 100644 100644 a a app.js\n");
        assertThat(chip.label()).isEqualTo("⎇ main ±2 ↑2 ↓1");
        assertThat(chip.spokenName()).isEqualTo("Git: branch main, 2 changed, 2 ahead, 1 behind");

        chip.porcelain("# branch.ab +0 -3\n");
        assertThat(chip.spokenName()).isEqualTo("Git: branch main, 0 changed, 3 behind");

        GitChip none = new GitChip();
        none.aim(dir.toFile());
        assertThat(none.spokenName()).as("no repository, no chip, no name").isNull();
    }

    @Test
    @DisplayName("the git chip is a pressable button: Enter, Space and Shift+F10 are bound, a mouse press keeps focus where it was")
    void gitChipIsPressable() {
        Component strip = new GitStatusLine().getStatusLineElement();
        JLabel chip = firstLabel((Container) strip);
        assertThat(chip).isInstanceOf(KeyboardAccess.Chip.class);
        assertThat(chip.getAccessibleContext().getAccessibleRole()).isEqualTo(AccessibleRole.PUSH_BUTTON);
        assertThat(chip.isRequestFocusEnabled()).isFalse();
        // hidden (nothing aimed), the press is a no-op — but the keys reach it
        assertThat(KeyboardAccess.perform(chip, KeyboardAccess.ENTER)).isTrue();
        assertThat(KeyboardAccess.perform(chip, KeyboardAccess.SPACE)).isTrue();
        assertThat(KeyboardAccess.perform(chip, KeyboardAccess.SHIFT_F10)).isTrue();
    }

    @Test
    @DisplayName("the serving and agent chips are pressable buttons named in words")
    void servingAndAgentChips() {
        Container strip = (Container) new RackStatusLine().getStatusLineElement();
        long chips = java.util.Arrays.stream(strip.getComponents())
                .filter(c -> c instanceof KeyboardAccess.Chip).count();
        assertThat(chips).as("serving + agent").isEqualTo(2);

        assertThat(RackStatusLine.spokenServing(List.of())).isNull();
        assertThat(RackStatusLine.spokenServing(List.of(
                new Serving("a", "Vite", "http://localhost:5173", Kind.WEB, new File("/p")),
                new Serving("b", "api", "http://localhost:3000", Kind.WEB, new File("/p")))))
                .isEqualTo("Serving: Vite — http://localhost:5173, api — http://localhost:3000")
                .doesNotContain("⇄");
        assertThat(RackStatusLine.spokenAgent(null)).isNull();
        assertThat(RackStatusLine.spokenAgent(new int[]{57511, 0})).isEqualTo("Agent Port listening on port 57511");
    }

    @Test
    @DisplayName("Pull Requests… and Draft Commit Message with KVASIR… are Team-menu rows, running the chip's own gated path")
    void teamMenuRows() throws Exception {
        String layer = Files.readString(Path.of("target", "classes", "META-INF", "generated-layer.xml"),
                StandardCharsets.UTF_8);
        for (String id : new String[]{"org-nmox-studio-rack-service-PullRequestsAction",
            "org-nmox-studio-rack-service-DraftCommitMessageAction"}) {
            int at = layer.indexOf("<file name=\"" + id + ".shadow\">");
            assertThat(at).as(id + " shadowed into a menu").isPositive();
            int folder = layer.lastIndexOf("<folder name=\"Versioning\">", at);
            int menu = layer.lastIndexOf("<folder name=\"Menu\">", at);
            assertThat(folder).as(id + " sits in Menu/Versioning").isGreaterThan(menu).isPositive();
        }
        for (String action : new String[]{"PullRequestsAction", "DraftCommitMessageAction"}) {
            String src = Files.readString(Path.of("src/main/java/org/nmox/studio/rack/service/" + action + ".java"),
                    StandardCharsets.UTF_8);
            assertThat(src).as(action + " reuses the chip's gated path").contains("GitStatusLine.fromTeamMenu(");
        }
        // a Windows checkout has CRLF line ends: the body search below is in LF
        String line = Files.readString(Path.of("src/main/java/org/nmox/studio/rack/service/GitStatusLine.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        int from = line.indexOf("static void fromTeamMenu(");
        String body = line.substring(from, line.indexOf("\n    }\n", from));
        assertThat(body).as("the boot guard precedes the chip's verbs")
                .contains("if (!strip.chip.mayRunProcess())")
                .contains("Bundle.GitStatusLine_noRepository()");
        assertThat(body.indexOf("mayRunProcess")).isLessThan(body.indexOf("strip::showPullRequests"));
    }

    private static JLabel firstLabel(Container c) {
        for (Component child : c.getComponents()) {
            if (child instanceof JLabel l) {
                return l;
            }
        }
        throw new AssertionError("no label in " + c);
    }
}
