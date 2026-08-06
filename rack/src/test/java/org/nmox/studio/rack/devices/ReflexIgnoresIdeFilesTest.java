package org.nmox.studio.rack.devices;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REFLEX does not fire for the IDE's own saves (v1.281.0, the Task Rack
 * persona walk). Pressing Save Patch wrote {@code .nmoxrack.json};
 * REFLEX's "code" filter includes {@code json}; the resulting pipeline
 * launch raised a Workspace Trust prompt for a click that touched no
 * source at all. API Studio and DB Studio save the same way on ordinary
 * edits, so an armed REFLEX kept re-firing all session.
 *
 * <p>The filter has to run BEFORE anything observable: the EYE, the
 * CHANGES meter and the CHANGED trigger must all stay quiet, not just
 * the spawn. That ordering is what these gates pin.
 */
class ReflexIgnoresIdeFilesTest {

    private static String source() throws Exception {
        return Files.readString(Path.of("src", "main", "java", "org", "nmox",
                "studio", "rack", "devices", "ReflexDevice.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the change handler drops IDE workspace files before reacting")
    void dropsIdeFilesFirst() throws Exception {
        String src = source();
        int handler = src.indexOf("private void filesChanged(");
        assertThat(handler).isPositive();
        String body = src.substring(handler, src.indexOf("\n    }", handler));

        assertThat(body)
                .as("the studios' own saves are not the user's work")
                .contains("IdeWorkspaceFiles.isOwn(");

        int filter = body.indexOf("IdeWorkspaceFiles.isOwn(");
        int meter = body.indexOf("meter.pulse(");
        int trigger = body.indexOf("emit(\"changed\"");
        int lcd = body.indexOf("lastChangeLcd.setText(");
        assertThat(meter).isPositive();
        assertThat(trigger).isPositive();
        assertThat(lcd).isPositive();
        assertThat(filter)
                .as("filtering after the LCD/meter/trigger would still show and"
                        + " fire a change nobody made")
                .isLessThan(Math.min(lcd, Math.min(meter, trigger)));
    }

    @Test
    @DisplayName("an all-IDE batch returns without emitting anything")
    void emptyAfterFilterReturns() throws Exception {
        String src = source();
        int handler = src.indexOf("private void filesChanged(");
        String body = src.substring(handler, src.indexOf("\n    }", handler));
        assertThat(body)
                .as("a Save Patch on its own must produce NO trigger at all,"
                        + " not a trigger naming the next file in the batch")
                .contains("changed.isEmpty()")
                .contains("return;");
    }
}
