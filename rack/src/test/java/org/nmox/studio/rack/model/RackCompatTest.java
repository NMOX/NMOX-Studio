package org.nmox.studio.rack.model;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a rack file loses on this install is found BEFORE it mounts and said
 * by name. The fixtures use jacks with a recorded history: TEMPO's
 * {@code halt} was renamed {@code stop} (an alias — not a loss), STELLAR's
 * {@code enable} was removed (a loss), both on 2026-09-17.
 */
class RackCompatTest {

    private static JSONObject device(String type, JSONObject state) {
        return new JSONObject().put("type", type).put("state", state);
    }

    private static JSONObject cable(int from, String fromPort, int to, String toPort) {
        return new JSONObject().put("fromDevice", from).put("fromPort", fromPort)
                .put("toDevice", to).put("toPort", toPort);
    }

    private static JSONObject rack(JSONArray devices, JSONArray cables) {
        return new JSONObject().put("version", 1).put("devices", devices).put("cables", cables);
    }

    @Test
    @DisplayName("a rack whose every device, jack and setting exists here carries whole — including a cable spelled with a renamed jack's old id")
    void carriesWhole() {
        JSONObject doc = rack(
                new JSONArray().put(device("dev-server", new JSONObject())).put(device("tempo", new JSONObject())),
                new JSONArray().put(cable(0, "ready", 1, "halt")));
        RackCompat.Report report = RackCompat.check(doc, "2.179.0");
        assertThat(report.lostCables()).as("halt is tempo.stop spelled the old way").isEmpty();
        assertThat(report.lostSettings()).isEmpty();
        assertThat(report.carriesWhole()).isTrue();
        assertThat(report.formatTooNew()).isFalse();
    }

    @Test
    @DisplayName("a cable into a jack this install does not have is lost BY NAME, and the cables beside it are not")
    void lostCableIsNamed() {
        JSONObject doc = rack(
                new JSONArray().put(device("dev-server", new JSONObject())).put(device("tempo", new JSONObject()))
                        .put(device("stellar", new JSONObject())),
                new JSONArray().put(cable(0, "ready", 1, "stop")).put(cable(0, "running", 2, "enable")));
        RackCompat.Report report = RackCompat.check(doc, "2.179.0");
        assertThat(report.lostCables()).hasSize(1);
        assertThat(report.lostCables().get(0)).contains("running").contains("enable");
        assertThat(report.carriesWhole()).isFalse();
    }

    @Test
    @DisplayName("a cable naming a device slot the file does not have is lost by its number, never an exception")
    void danglingCableIndex() {
        JSONObject doc = rack(new JSONArray().put(device("tempo", new JSONObject())),
                new JSONArray().put(cable(0, "tick", 7, "run")).put("not a cable"));
        RackCompat.Report report = RackCompat.check(doc, "2.179.0");
        assertThat(report.lostCables()).containsExactly("#1", "#2");
    }

    @Test
    @DisplayName("a setting the device here does not have is lost by name; a placeholder for a device this install lacks loses nothing — it keeps every setting for the day the device arrives")
    void lostSettingIsNamed() {
        JSONObject doc = rack(
                new JSONArray().put(device("tempo", new JSONObject().put("warpFactor", "9")))
                        .put(device("com.example.uptime", new JSONObject().put("anything", "kept"))),
                new JSONArray());
        RackCompat.Report report = RackCompat.check(doc, "2.179.0");
        assertThat(report.lostSettings()).hasSize(1);
        assertThat(report.lostSettings().get(0)).endsWith("warpFactor");
    }

    @Test
    @DisplayName("a file in a newer FORMAT is not dry-run at all: nothing in it can be trusted to mean what it meant")
    void newerFormatIsNotProbed() {
        JSONObject doc = rack(new JSONArray().put("a device shape from the future"), new JSONArray()).put("version", 2);
        RackCompat.Report report = RackCompat.check(doc, "2.179.0");
        assertThat(report.formatTooNew()).isTrue();
        assertThat(report.format()).isEqualTo(2);
        assertThat(report.carriesWhole()).isFalse();
    }

    @Test
    @DisplayName("made with a newer product is said; an older or equal one, an absent one and a dev build's absent version are not")
    void madeWithNewer() {
        JSONObject doc = rack(new JSONArray(), new JSONArray())
                .put(RackShare.SHARED, new JSONObject().put("product", "2.200.0"));
        assertThat(RackCompat.check(doc, "2.179.0").madeWithNewer()).isTrue();
        assertThat(RackCompat.check(doc, "2.200.0").madeWithNewer()).isFalse();
        assertThat(RackCompat.check(doc, "3.0.0").madeWithNewer()).isFalse();
        assertThat(RackCompat.check(doc, null).madeWithNewer()).as("a dev build is never 'older'").isFalse();
        assertThat(RackCompat.check(doc, " ").madeWithNewer()).isFalse();
        assertThat(RackCompat.check(rack(new JSONArray(), new JSONArray()), "2.179.0").madeWith()).isEmpty();
        JSONObject hostile = rack(new JSONArray(), new JSONArray())
                .put(RackShare.SHARED, new JSONObject().put("product", 42));
        assertThat(RackCompat.check(hostile, "2.179.0").madeWith()).as("a number is not a version string").isEmpty();
    }

    @Test
    @DisplayName("the probe arms nothing: every switch in the dry-run copy is off, whatever key it is saved under")
    void probeArmsNothing() {
        JSONObject doc = rack(new JSONArray().put(device("tail",
                new JSONObject().put("follow", "true").put("path", "app.log").put("anyFutureSwitch", "TRUE"))),
                new JSONArray());
        JSONObject atRest = RackCompat.atRest(doc);
        JSONObject state = atRest.getJSONArray("devices").getJSONObject(0).getJSONObject("state");
        assertThat(state.getString("follow")).isEqualTo("false");
        assertThat(state.getString("anyFutureSwitch")).isEqualTo("false");
        assertThat(state.getString("path")).isEqualTo("app.log");
        assertThat(doc.getJSONArray("devices").getJSONObject(0).getJSONObject("state").getString("follow"))
                .as("the caller's document is untouched").isEqualTo("true");
    }
}
