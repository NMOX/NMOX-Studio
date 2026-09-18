package org.nmox.studio.rack.gallery;

import java.util.List;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One test per rule of {@link RackJudge}, and each breaks a rack that is
 * otherwise sound in exactly ONE way — so the single problem that comes back
 * is that rule's, and a rule deleted from the judge has a test that names it
 * (an input two rules refuse proves neither).
 */
class RackJudgeTest {

    /** REFLEX -> VERITAS -> MONITOR: a small rack that breaks no rule. */
    static JSONObject sound() {
        return new JSONObject("""
                {
                  "version": 1,
                  "shared": {
                    "product": "2.179.0",
                    "name": "Test on save",
                    "description": "Every save runs the tests.",
                    "kinds": ["NODE"],
                    "requires": ["node"]
                  },
                  "devices": [
                    {"type": "reflex", "state": {"armed": "false", "glob": "js"}},
                    {"type": "test"},
                    {"type": "console"},
                    {"type": "tail", "state": {"path": "logs/app.log", "follow": "false"}}
                  ],
                  "cables": [
                    {"fromDevice": 0, "fromPort": "changed", "toDevice": 1, "toPort": "run"},
                    {"fromDevice": 1, "fromPort": "out", "toDevice": 2, "toPort": "in"},
                    {"fromDevice": 3, "fromPort": "out", "toDevice": 2, "toPort": "in"}
                  ]
                }
                """);
    }

    private static List<String> judged(Consumer<JSONObject> breakIt) {
        JSONObject doc = sound();
        breakIt.accept(doc);
        return RackJudge.problems("x.nmoxrack.json", doc);
    }

    private static void assertOnlyProblem(List<String> problems, String saying) {
        assertThat(problems).as("exactly the one rule under test refuses").hasSize(1);
        assertThat(problems.get(0)).startsWith("x.nmoxrack.json: ").contains(saying);
    }

    private static JSONObject header(JSONObject doc) {
        return doc.getJSONObject("shared");
    }

    private static JSONObject state(JSONObject doc, int device) {
        return doc.getJSONArray("devices").getJSONObject(device).getJSONObject("state");
    }

    @Test
    @DisplayName("the sound rack is sound — every other test here breaks exactly one thing about it")
    void soundRackHasNoProblems() {
        assertThat(RackJudge.problems("x.nmoxrack.json", sound())).isEmpty();
    }

    @Test
    @DisplayName("a blank name is refused")
    void blankName() {
        assertOnlyProblem(judged(d -> header(d).put("name", "  \n ")), "name is blank");
    }

    @Test
    @DisplayName("a blank description is refused")
    void blankDescription() {
        assertOnlyProblem(judged(d -> header(d).remove("description")), "description is blank");
    }

    @Test
    @DisplayName("a name RackCard would clip is refused rather than shipped clipped")
    void overlongName() {
        assertOnlyProblem(judged(d -> header(d).put("name", "n".repeat(61))), "name is over 60");
    }

    @Test
    @DisplayName("a description RackCard would clip is refused")
    void overlongDescription() {
        assertOnlyProblem(judged(d -> header(d).put("description", "d".repeat(401))), "description is over 400");
    }

    @Test
    @DisplayName("no shared header: a plain Save Patch file says nothing about itself")
    void noHeader() {
        assertOnlyProblem(judged(d -> d.remove("shared")), "no \"shared\" header");
    }

    @Test
    @DisplayName("a header key the reader ignores is refused; a name.<lang> sibling is not")
    void unknownHeaderKey() {
        assertOnlyProblem(judged(d -> header(d).put("licence", "MIT")), "unknown header key \"licence\"");
        assertThat(judged(d -> header(d).put("name.de", "Test beim Speichern").put("description.pt", "x"))).isEmpty();
    }

    @Test
    @DisplayName("anything outside version/shared/devices/cables at the top is refused")
    void unknownTopLevelKey() {
        assertOnlyProblem(judged(d -> d.put("notes", "hello")), "unknown top-level key \"notes\"");
    }

    @Test
    @DisplayName("version must be 1")
    void wrongVersion() {
        assertOnlyProblem(judged(d -> d.put("version", 2)), "version must be 1");
    }

    @Test
    @DisplayName("a plugin device is refused: a community rack depends on nothing the product does not ship")
    void pluginDevice() {
        assertOnlyProblem(judged(d -> d.getJSONArray("devices").getJSONObject(3)
                .put("type", "com.nmox.examples.probe").remove("state")), "is not a built-in device");
    }

    @Test
    @DisplayName("a retired device id is refused by its current name")
    void retiredDeviceId() {
        JSONObject doc = sound();
        doc.getJSONArray("devices").put(new JSONObject().put("type", "oracle"));
        doc.getJSONArray("cables").put(new JSONObject().put("fromDevice", 1).put("fromPort", "fail")
                .put("toDevice", 4).put("toPort", "explain"));
        assertOnlyProblem(RackJudge.problems("x.nmoxrack.json", doc), "retired id — use \"kvasir\"");
    }

    @Test
    @DisplayName("a cable naming a jack the device does not have is refused BY NAME — the rack would mount one cable short with nothing said")
    void cableToAMissingJack() {
        List<String> problems = judged(d -> d.getJSONArray("cables").getJSONObject(0).put("toPort", "go"));
        assertOnlyProblem(problems, "cables[0] reflex.changed -> test.go does not mount");
        assertThat(problems.get(0)).contains("VERITAS has no jack \"go\"");
    }

    @Test
    @DisplayName("a cable between jacks of different signal types is refused with the types")
    void cableOfMismatchedTypes() {
        assertOnlyProblem(judged(d -> d.getJSONArray("cables").getJSONObject(0).put("toDevice", 2).put("toPort", "in")),
                "TRIGGER cannot feed DATA");
    }

    @Test
    @DisplayName("a cable written backwards (IN jack first) is refused: the file would read backwards in every sketch")
    void cableWrittenBackwards() {
        assertOnlyProblem(judged(d -> d.getJSONArray("cables").getJSONObject(0)
                .put("fromDevice", 1).put("fromPort", "run").put("toDevice", 0).put("toPort", "changed")),
                "from an OUT jack to an IN jack");
    }

    @Test
    @DisplayName("a cable pointing past the last device is refused (RackIO would skip it silently)")
    void cableOutOfRange() {
        assertOnlyProblem(judged(d -> d.getJSONArray("cables").getJSONObject(0).put("toDevice", 9)),
                "cables[0] points at a device the rack does not have");
    }

    @Test
    @DisplayName("a repeated cable is refused")
    void repeatedCable() {
        assertOnlyProblem(judged(d -> d.getJSONArray("cables")
                .put(new JSONObject(d.getJSONArray("cables").getJSONObject(1).toString()))), "cables[3] repeats");
    }

    @Test
    @DisplayName("a rack with no cables is not a rack worth sharing")
    void noCables() {
        assertOnlyProblem(judged(d -> d.put("cables", new JSONArray())), "no cables");
    }

    @Test
    @DisplayName("an armed REFLEX is refused: a community rack arrives at rest")
    void armedReflex() {
        assertOnlyProblem(judged(d -> state(d, 0).put("armed", "true")), "reflex.armed is on");
    }

    @Test
    @DisplayName("a running flag is refused")
    void runningClock() {
        JSONObject doc = sound();
        doc.getJSONArray("devices").put(new JSONObject().put("type", "tempo")
                .put("state", new JSONObject().put("running", "true")));
        doc.getJSONArray("cables").put(new JSONObject().put("fromDevice", 4).put("fromPort", "tick")
                .put("toDevice", 1).put("toPort", "run"));
        assertOnlyProblem(RackJudge.problems("x.nmoxrack.json", doc), "tempo.running is on");
    }

    @Test
    @DisplayName("a TAIL saved following is refused — and PHOSPHOR's follow, a view setting, is not")
    void followingTail() {
        assertOnlyProblem(judged(d -> state(d, 3).put("follow", "true")), "tail.follow is on");
        JSONObject doc = sound();
        doc.getJSONArray("devices").put(new JSONObject().put("type", "terminal")
                .put("state", new JSONObject().put("follow", "true")));
        doc.getJSONArray("cables").put(new JSONObject().put("fromDevice", 1).put("fromPort", "out")
                .put("toDevice", 4).put("toPort", "in"));
        assertThat(RackJudge.problems("x.nmoxrack.json", doc)).isEmpty();
    }

    @Test
    @DisplayName("an absolute path in a setting is refused — POSIX, and a Windows drive")
    void absolutePath() {
        assertOnlyProblem(judged(d -> state(d, 3).put("path", "/var/log/app.log")), "tail.path holds an absolute path");
        assertOnlyProblem(judged(d -> state(d, 3).put("path", "cat C:\\logs\\app.log")), "holds an absolute path");
    }

    @Test
    @DisplayName("a ~ path is refused")
    void tildePath() {
        assertOnlyProblem(judged(d -> state(d, 3).put("path", "~dave")), "holds a ~ path");
    }

    @Test
    @DisplayName("a home-looking path is refused even when it is not absolute")
    void homeLookingPath() {
        assertOnlyProblem(judged(d -> state(d, 3).put("path", "../../Users/david/app.log")), "names a home directory");
    }

    @Test
    @DisplayName("an address that is not this machine is refused; localhost is not")
    void foreignAddress() {
        JSONObject doc = sound();
        doc.getJSONArray("devices").put(new JSONObject().put("type", "http")
                .put("state", new JSONObject().put("url", "https://api.example.org/health")));
        doc.getJSONArray("cables").put(new JSONObject().put("fromDevice", 1).put("fromPort", "ok")
                .put("toDevice", 4).put("toPort", "send"));
        assertOnlyProblem(RackJudge.problems("x.nmoxrack.json", doc), "addresses api.example.org");
        doc.getJSONArray("devices").getJSONObject(4).getJSONObject("state").put("url", "http://localhost:3000/health");
        assertThat(RackJudge.problems("x.nmoxrack.json", doc)).isEmpty();
    }

    @Test
    @DisplayName("a requires entry RackCard would drop (a path, a command line) is refused, not dropped")
    void requiresMustBeBareTools() {
        assertOnlyProblem(judged(d -> header(d).put("requires", new JSONArray().put("node").put("./bin/tool"))),
                "requires must be bare tool names");
    }

    @Test
    @DisplayName("a kind that is not a ProjectKind name is refused")
    void unknownKind() {
        assertOnlyProblem(judged(d -> header(d).put("kinds", new JSONArray().put("NODE").put("COBOLT"))),
                "kinds[1] is not a ProjectKind name: COBOLT");
    }

    @Test
    @DisplayName("a setting that is not a string is refused — RackIO could not load it")
    void nonStringSetting() {
        assertOnlyProblem(judged(d -> state(d, 0).put("filter", 1)), "reflex.filter must be a string");
    }

    @Test
    @DisplayName("a devices slot that is not a device is refused by its index, never thrown")
    void nonObjectDevice() {
        List<String> problems = judged(d -> d.getJSONArray("devices").put("not a device"));
        assertOnlyProblem(problems, "devices[4] is not a device object");
    }

    @Test
    @DisplayName("over 64 KiB, and not JSON at all, are refused as text")
    void sizeAndParse() {
        String padded = sound().put("cables", new JSONArray()).toString() + " ".repeat(RackJudge.MAX_BYTES);
        assertThat(RackJudge.problemsOfText("big.nmoxrack.json", padded)).containsExactly("big.nmoxrack.json: over 64 KiB");
        assertThat(RackJudge.problemsOfText("bad.nmoxrack.json", "{ nope")).hasSize(1)
                .first().asString().startsWith("bad.nmoxrack.json: not a JSON object");
        assertThat(RackJudge.problemsOfText("gone.nmoxrack.json", null)).containsExactly("gone.nmoxrack.json: missing");
        assertThat(RackJudge.problemsOfText("ok.nmoxrack.json", sound().toString(2))).isEmpty();
    }

    @Test
    @DisplayName("one run names every problem, so a contributor fixes a rack in one push")
    void everyProblemInOneRun() {
        List<String> problems = judged(d -> {
            header(d).put("name", "");
            state(d, 0).put("armed", "true");
            d.getJSONArray("cables").getJSONObject(0).put("toPort", "go");
        });
        assertThat(problems).hasSize(3);
    }
}
