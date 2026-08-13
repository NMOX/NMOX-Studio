package org.nmox.studio.rack.devices;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.device.DeviceCategory;
import org.nmox.studio.core.spi.device.DeviceFace;
import org.nmox.studio.core.spi.device.PortSpec;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The judge on a device file. Every refusal here is a device that would
 * otherwise reach a user's shelf carrying a control whose label lies, or
 * a command they did not mean to run.
 */
class DeviceFileTest {

    /** The shelf law wants two lines and more than 60 characters. */
    private static final String USAGE =
            "GO runs the build for the dialled mode and lights FAIL on a bad exit.\n"
            + "Patch DONE onward to chain another device after it.";

    private static String json(String body) {
        return "{\"id\":\"com.example.build\",\"title\":\"BUILD\",\"tagline\":\"builds it\","
                + "\"category\":\"AUTOMATE\",\"usage\":\"" + USAGE.replace("\n", "\\n") + "\","
                + body + "}";
    }

    private static final String ONE_BUTTON =
            "\"buttons\":[{\"label\":\"GO\",\"role\":\"GO\",\"command\":[\"npm\",\"test\"]}]";

    @Test
    @DisplayName("a well-formed file becomes a device")
    void happyPath() {
        DeviceFile.Result r = DeviceFile.read(json(
                "\"accent\":\"#4FC3F7\",\"units\":2,"
                + "\"ports\":[{\"id\":\"done\",\"label\":\"DONE\",\"direction\":\"OUT\","
                + "\"signal\":\"TRIGGER\"}],"
                + "\"knobs\":[{\"key\":\"mode\",\"label\":\"MODE\","
                + "\"options\":[\"dev\",\"prod\"]}],"
                + "\"buttons\":[{\"label\":\"GO\",\"role\":\"GO\","
                + "\"command\":[\"npm\",\"run\",\"{{mode}}\"],\"emit\":\"done\"}]"));

        assertThat(r.problem()).isNull();
        DeviceFile d = r.device();
        assertThat(d.id()).isEqualTo("com.example.build");
        assertThat(d.category()).isEqualTo(DeviceCategory.AUTOMATE);
        assertThat(d.units()).isEqualTo(2);
        assertThat(d.accent()).isEqualTo(java.awt.Color.decode("#4FC3F7"));
        assertThat(d.ports()).singleElement()
                .extracting(PortSpec::direction, PortSpec::signal)
                .containsExactly(PortSpec.Direction.OUT, PortSpec.Signal.TRIGGER);
        assertThat(d.knobs()).singleElement().extracting(DeviceFile.Knob::options)
                .isEqualTo(List.of("dev", "prod"));
        assertThat(d.buttons()).singleElement()
                .extracting(DeviceFile.Button::role, DeviceFile.Button::emit)
                .containsExactly(DeviceFace.ButtonRole.GO, "done");
    }

    @Test
    @DisplayName("knob selections substitute into the command")
    void substitution() {
        List<String> argv = DeviceFile.substitute(
                List.of("npm", "run", "{{mode}}", "--target={{mode}}"),
                Map.of("mode", "prod")::get);
        assertThat(argv).containsExactly("npm", "run", "prod", "--target=prod");
    }

    @Test
    @DisplayName("a shell line is refused — a command is argv, not a script")
    void refusesShellMetacharacters() {
        for (String hostile : List.of("rm -rf / ; echo", "a | b", "x && y",
                "$(whoami)", "`id`", "out > file")) {
            DeviceFile.Result r = DeviceFile.read(json(
                    "\"buttons\":[{\"label\":\"GO\",\"role\":\"GO\","
                    + "\"command\":[\"sh\",\"" + hostile.replace("\"", "\\\"") + "\"]}]"));
            assertThat(r.problem()).as("hostile token %s", hostile).isNotNull();
        }
    }

    @Test
    @DisplayName("a tool named by path is refused — the tool comes from PATH")
    void refusesToolPath() {
        assertThat(DeviceFile.read(json(
                "\"buttons\":[{\"label\":\"GO\",\"role\":\"GO\","
                + "\"command\":[\"./evil.sh\"]}]")).problem())
                .contains("bare name found on PATH");
        assertThat(DeviceFile.read(json(
                "\"buttons\":[{\"label\":\"GO\",\"role\":\"GO\","
                + "\"command\":[\"/tmp/evil\"]}]")).problem())
                .contains("bare name found on PATH");
    }

    @Test
    @DisplayName("an unknown {{variable}} is refused, never passed through literally")
    void refusesUnknownVariable() {
        DeviceFile.Result r = DeviceFile.read(json(
                "\"buttons\":[{\"label\":\"GO\",\"role\":\"GO\","
                + "\"command\":[\"npm\",\"run\",\"{{nope}}\"]}]"));
        assertThat(r.problem()).contains("{{nope}}").contains("no knob declares");
    }

    @Test
    @DisplayName("a knob cannot build the tool name — the tool must be readable before it runs")
    void refusesVariableTool() {
        DeviceFile.Result r = DeviceFile.read(json(
                "\"knobs\":[{\"key\":\"tool\",\"label\":\"TOOL\",\"options\":[\"npm\",\"rm\"]}],"
                + "\"buttons\":[{\"label\":\"GO\",\"role\":\"GO\","
                + "\"command\":[\"{{tool}}\",\"test\"]}]"));
        assertThat(r.problem()).contains("literal");
    }

    @Test
    @DisplayName("emit and trigger must name declared trigger ports")
    void refusesUndeclaredPorts() {
        assertThat(DeviceFile.read(json(
                "\"buttons\":[{\"label\":\"GO\",\"role\":\"GO\",\"command\":[\"npm\"],"
                + "\"emit\":\"ghost\"}]")).problem())
                .contains("not a declared OUT TRIGGER");
        assertThat(DeviceFile.read(json(
                "\"buttons\":[{\"label\":\"GO\",\"role\":\"GO\",\"command\":[\"npm\"],"
                + "\"trigger\":\"ghost\"}]")).problem())
                .contains("not a declared IN TRIGGER");
    }

    @Test
    @DisplayName("the shelf law is enforced on usage, and the colour law on roles")
    void refusesLawBreakers() {
        String shortUsage = "{\"id\":\"com.example.a\",\"title\":\"A\",\"tagline\":\"t\","
                + "\"category\":\"AUTOMATE\",\"usage\":\"too short\"," + ONE_BUTTON + "}";
        assertThat(DeviceFile.read(shortUsage).problem()).contains("shelf law");

        assertThat(DeviceFile.read(json(
                "\"buttons\":[{\"label\":\"GO\",\"role\":\"PURPLE\",\"command\":[\"npm\"]}]"))
                .problem()).contains("colour law");

        assertThat(DeviceFile.read(json(
                "\"buttons\":[{\"label\":\"\",\"role\":\"GO\",\"command\":[\"npm\"]}]"))
                .problem()).contains("accessibility law");
    }

    @Test
    @DisplayName("an un-dotted id is refused — those belong to the built-in fleet")
    void refusesUndottedId() {
        String body = "{\"id\":\"BUILD\",\"title\":\"BUILD\",\"tagline\":\"t\","
                + "\"category\":\"AUTOMATE\",\"usage\":\"" + USAGE.replace("\n", "\\n") + "\","
                + ONE_BUTTON + "}";
        assertThat(DeviceFile.read(body).problem()).contains("reverse-DNS");
    }

    @Test
    @DisplayName("a device with no button is refused — that is a label, not a device")
    void refusesButtonless() {
        assertThat(DeviceFile.read(json("\"knobs\":[]")).problem())
                .contains("at least one button");
    }

    @Test
    @DisplayName("a STOP button stops the run and must not carry a command of its own")
    void stopButton() {
        assertThat(DeviceFile.read(json(
                "\"buttons\":[{\"label\":\"GO\",\"role\":\"GO\",\"command\":[\"npm\"]},"
                + "{\"label\":\"STOP\",\"role\":\"STOP\"}]")).problem()).isNull();
        assertThat(DeviceFile.read(json(
                "\"buttons\":[{\"label\":\"STOP\",\"role\":\"STOP\",\"command\":[\"kill\"]}]"))
                .problem()).contains("must not declare one");
    }

    @Test
    @DisplayName("malformed JSON is refused with the parser's own reason")
    void refusesMalformedJson() {
        assertThat(DeviceFile.read("{not json").problem()).startsWith("not valid JSON");
    }
}
