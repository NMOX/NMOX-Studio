package org.nmox.studio.rack.docker;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Docker Panel refreshes on its FIRST SHOW and never at construction
 * (v2.85.0): the walk found "ENGINE: checking…" over an empty pane for as
 * long as nobody pressed Refresh All, because the refresh ran only from
 * the open-action helper, the verbs and the (off by default) timer. The
 * zero-boot-spawns law (v1.38.0) still holds — a hidden default-open tab
 * is not showing. Source-gated: the window is Swing, the law is a shape.
 */
class DockerPanelFirstShowTest {

    @Test
    @DisplayName("componentShowing refreshes once; the constructor and componentOpened never do")
    void firstShowRefreshes() throws Exception {
        // normalize CRLF: the Windows checkout has native line endings (.gitattributes
        // text=auto), and the "\n    }\n" method-end search needs a newline AFTER the
        // brace — a bare \r would make indexOf return -1 and substring(start, -1) throw
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/rack/docker/DockerPanelTopComponent.java"))
                .replace("\r\n", "\n");
        int showing = src.indexOf("protected void componentShowing()");
        assertThat(showing).isPositive();
        String body = src.substring(showing, src.indexOf("\n    }\n", showing));
        assertThat(body).as("the first show refreshes").contains("refreshAll()").contains("refreshedOnShow");
        int opened = src.indexOf("public void componentOpened()");
        String openedBody = src.substring(opened, src.indexOf("\n    }\n", opened));
        assertThat(openedBody).as("opened-but-hidden spawns nothing (v1.38.0)").doesNotContain("refreshAll()");
        int ctor = src.indexOf("public DockerPanelTopComponent()");
        String ctorBody = src.substring(ctor, src.indexOf("\n    }\n", ctor));
        assertThat(ctorBody).as("construction spawns nothing (v1.38.0)").doesNotContain("refreshAll()");
    }
}
