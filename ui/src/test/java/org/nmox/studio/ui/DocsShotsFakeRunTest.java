package org.nmox.studio.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The forge can paint the RUNNING section (v2.75.0): the fake-run spec parses, and the script asks for it. */
class DocsShotsFakeRunTest {

    @Test
    @DisplayName("label|url and a bare label parse; the script passes a fake run for the workbench shot")
    void specAndScript() throws Exception {
        assertThat(DocsShots.Session.fakeRunParts("Run — meridian|http://localhost:3000/"))
                .containsExactly("Run — meridian", "http://localhost:3000/");
        assertThat(DocsShots.Session.fakeRunParts("Focused test: adds")).containsExactly("Focused test: adds", null);
        String script = Files.readString(Path.of("../scripts/docs-shots.sh"));
        assertThat(script).contains("-J-Dnmox.shots.fakerun=");
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/ui/DocsShots.java"));
        assertThat(src.indexOf("seedFakeRun();")).as("seeded before the first tab is painted")
                .isPositive().isLessThan(src.indexOf("void next() {"));
    }
}
