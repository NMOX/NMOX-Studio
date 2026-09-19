package org.nmox.studio.rack.gallery;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.GateSources;
import org.nmox.studio.rack.devices.DeviceCatalog;
import org.nmox.studio.rack.devices.SelfStarting;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.RackShare;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One authority for "this setting starts something by itself" (v2.179.1).
 *
 * <p>v2.179.0 shipped the fix for a hand-kept set ({@code RackShare}'s
 * {@code armed}/{@code running}, which had missed TAIL's {@code follow}) and,
 * in the same release, a second hand-kept copy of it in {@code RackJudge} —
 * written by a parallel agent that could not yet see the authority it
 * duplicated. Both were correct the day they shipped, which is exactly how the
 * first one went wrong: a device that DECLARED a new self-starting switch would
 * have been switched off by Import and waved through by the community gate.
 * Found by an outside review of the release.
 *
 * <p>These tests hold the two together the only way that lasts: the judge has
 * no list, and what it refuses is derived from the same declarations.
 */
class RackJudgeOneAuthorityTest {

    private static final String FILE = "x.nmoxrack.json";
    private static final String AT_REST = "is on — a community rack arrives at rest";

    private static JSONObject state(JSONObject doc, int device) {
        JSONObject dj = doc.getJSONArray("devices").getJSONObject(device);
        if (!dj.has("state")) {
            dj.put("state", new JSONObject());
        }
        return dj.getJSONObject("state");
    }

    /** Today's declarations, plus one nobody has made yet: VERITAS's coverage switch starts something. */
    private static Function<String, Set<String>> declaring(String typeId, String key) {
        return type -> type.equals(typeId) ? Set.of(key) : SelfStarting.keysFor(type);
    }

    @Test
    @DisplayName("a NEWLY DECLARED self-starting switch cannot ship enabled: the day a device declares it, the community gate refuses a rack that has it on — with no edit to the judge")
    void aNewlyDeclaredSwitchCannotShipEnabled() {
        JSONObject doc = RackJudgeTest.sound();
        state(doc, 1).put("coverage", "true"); // VERITAS (type "test"): a SETTING today

        assertThat(RackJudge.problems(FILE, doc))
                .as("by today's declarations a coverage switch starts nothing, so the rack may ship").isEmpty();

        List<String> problems = RackJudge.problems(FILE, doc, declaring("test", "coverage"));
        assertThat(problems).as("declared self-starting → refused, by name").hasSize(1);
        assertThat(problems.get(0)).startsWith(FILE + ": ").contains("coverage").contains(AT_REST);

        // and the two consumers move TOGETHER: what the gate refuses is what Import switches off
        JSONObject imported = RackShare.imported(doc, null, declaring("test", "coverage"));
        assertThat(imported.getJSONArray("devices").getJSONObject(1).getJSONObject("state").getString("coverage"))
                .as("Import sets the same newly declared switch off").isEqualTo("false");
        assertThat(RackShare.imported(doc, null).getJSONArray("devices").getJSONObject(1)
                .getJSONObject("state").getString("coverage"))
                .as("and leaves it alone while nothing declares it").isEqualTo("true");
    }

    @Test
    @DisplayName("across the whole fleet, DERIVED from what each device declares: the judge refuses exactly the settings Import switches off — every declared switch, and no other switch")
    void theJudgeRefusesExactlyWhatImportSwitchesOff() {
        List<String> declared = new ArrayList<>();
        List<String> disagreements = new ArrayList<>();
        for (DeviceCatalog.Entry e : DeviceCatalog.all()) {
            if (!e.builtIn()) {
                continue;
            }
            RackDevice device = e.create();
            Set<String> selfStarting;
            Set<String> toggles;
            try {
                selfStarting = Set.copyOf(device.selfStartingKeys());
                toggles = Set.copyOf(device.toggleKeys());
            } finally {
                device.dispose();
            }
            for (String key : toggles) {
                JSONObject doc = new JSONObject().put("version", 1)
                        .put("devices", new JSONArray().put(new JSONObject().put("type", e.id())
                                .put("state", new JSONObject().put(key, "true"))))
                        .put("cables", new JSONArray());
                boolean refused = RackJudge.problems(FILE, doc).stream()
                        .anyMatch(p -> p.contains("." + key + " ") && p.contains(AT_REST));
                boolean switchedOff = "false".equals(RackShare.imported(doc, null).getJSONArray("devices")
                        .getJSONObject(0).getJSONObject("state").getString(key));
                boolean isDeclared = selfStarting.contains(key);
                if (isDeclared) {
                    declared.add(e.id() + "." + key);
                }
                if (refused != isDeclared || switchedOff != isDeclared) {
                    disagreements.add(e.id() + "." + key + ": declared=" + isDeclared + " judgeRefuses=" + refused
                            + " importSwitchesOff=" + switchedOff);
                }
            }
        }
        assertThat(declared).as("the fleet declares its self-starting switches (REFLEX, TEMPO, TAIL at least)")
                .contains("reflex.armed", "tempo.running", "tail.follow");
        assertThat(disagreements).as("the declaration, the community gate and Import say the same thing").isEmpty();
    }

    @Test
    @DisplayName("a value that does not READ as on starts nothing — the judge uses the rule a device restores by (Boolean.parseBoolean), not a looser one of its own")
    void onlyAValueThatRestoresAsOnIsRefused() {
        for (String on : List.of("true", "TRUE", "True")) {
            JSONObject doc = RackJudgeTest.sound();
            state(doc, 0).put("armed", on);
            assertThat(RackJudge.problems(FILE, doc)).as(on).anyMatch(p -> p.contains("reflex.armed") && p.contains(AT_REST));
        }
        for (String off : List.of("false", "", "yes", "1", " true ", "on")) {
            JSONObject doc = RackJudgeTest.sound();
            state(doc, 0).put("armed", off);
            assertThat(RackJudge.problems(FILE, doc)).as("'" + off + "' restores as off — nothing to refuse")
                    .noneMatch(p -> p.contains(AT_REST));
            assertThat(RackShare.startsByItself("reflex", "armed", off)).isFalse();
        }
    }

    @Test
    @DisplayName("the judge keeps NO list: its source names no switch, and asks the one decision Import switches off by")
    void theJudgeKeepsNoList() throws Exception {
        String src = GateSources.stripComments(Files.readString(Path.of("src", "main", "java", "org", "nmox",
                "studio", "rack", "gallery", "RackJudge.java"), StandardCharsets.UTF_8).replace("\r\n", "\n"));
        assertThat(src).as("the at-rest rule asks the authority").contains("RackShare.startsByItself(");
        for (String literal : List.of("\"armed\"", "\"running\"", "\"follow\"")) {
            assertThat(src).as("a switch named in the judge is a second home for a device's declaration: " + literal)
                    .doesNotContain(literal);
        }
    }
}
