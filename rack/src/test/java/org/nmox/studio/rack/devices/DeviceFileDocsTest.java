package org.nmox.studio.rack.devices;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every device the docs show a reader must be a device the product
 * accepts.
 *
 * <p>A format's documentation is the first thing anyone copies, and a
 * worked example that no longer parses teaches the wrong syntax
 * confidently. So the examples are not prose here: they are fixtures the
 * build reads out of the Markdown and feeds to the real parser.
 *
 * <p>Only whole devices are checked, and the discriminator is
 * {@code "buttons"} rather than {@code "id"} — the reference also shows
 * port and knob fragments, which carry ids of their own. The tutorial
 * additionally shows a deliberately-refused command to demonstrate the
 * shell-line rule, and a gate that demanded it parse would be arguing
 * with the lesson.
 */
class DeviceFileDocsTest {

    private static List<String> deviceExamples(String markdown) {
        List<String> blocks = new ArrayList<>();
        int i = 0;
        while (true) {
            int open = markdown.indexOf("```json", i);
            if (open < 0) {
                return blocks;
            }
            int start = markdown.indexOf('\n', open) + 1;
            int close = markdown.indexOf("```", start);
            if (close < 0) {
                return blocks;
            }
            String block = markdown.substring(start, close).trim();
            if (block.startsWith("{") && block.contains("\"buttons\"")) {
                blocks.add(block);
            }
            i = close + 3;
        }
    }

    private static void checkAll(String doc) throws Exception {
        String markdown = Files.readString(Path.of("..", "docs", doc));
        List<String> examples = deviceExamples(markdown);
        assertThat(examples).as("%s should show at least one whole device", doc).isNotEmpty();
        for (String example : examples) {
            DeviceFile.Result r = DeviceFile.read(example);
            assertThat(r.problem())
                    .as("example in %s must parse:%n%s", doc, example)
                    .isNull();
            // Parsing is not enough: the v2.0.0 walk followed the
            // tutorial verbatim and its example threw at the first
            // mount because the face did not fit its default height.
            // A worked example must MOUNT — build it through the same
            // load path the shelf uses (which auto-sizes units).
            assertThat(UserDevices.fit(r.device()))
                    .as("example in %s must build a mountable face:%n%s", doc, example)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("every device in the reference parses")
    void reference() throws Exception {
        checkAll("device-files.md");
    }

    @Test
    @DisplayName("every device in the tutorial parses")
    void tutorial() throws Exception {
        checkAll("tutorials/your-own-device.md");
    }

    @Test
    @DisplayName("the tutorial's refused example really is refused")
    void refusedExampleIsRefused() throws Exception {
        // The lesson only lands if the product agrees with it: the piped
        // command the tutorial says will be skipped must actually be.
        String markdown = Files.readString(
                Path.of("..", "docs", "tutorials", "your-own-device.md"));
        assertThat(markdown).contains("git ls-files | wc -l");

        DeviceFile.Result r = DeviceFile.read("{\"id\":\"com.example.counter\","
                + "\"title\":\"COUNTER\",\"tagline\":\"t\",\"category\":\"OBSERVE\","
                + "\"usage\":\"COUNT lists the project's files of the dialled KIND.\\n"
                + "Patch OUT into MONITOR to read the list, or DONE onward to chain.\","
                + "\"buttons\":[{\"label\":\"COUNT\",\"role\":\"QUERY\","
                + "\"command\":[\"sh\",\"-c\",\"git ls-files | wc -l\"]}]}");
        assertThat(r.problem()).contains("argv, never a shell line");
    }
}
