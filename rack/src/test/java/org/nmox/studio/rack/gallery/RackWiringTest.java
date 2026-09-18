package org.nmox.studio.rack.gallery;

import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The readable sketch is a pure function of the document and the names it is given. */
class RackWiringTest {

    /** A tiny catalog: three known types, one jack label that differs from its id. */
    private static final RackWiring.Names NAMES = new RackWiring.Names() {
        private final Map<String, String> titles = Map.of("reflex", "REFLEX", "test", "VERITAS", "console", "MONITOR");
        private final Map<String, String> labels = Map.of("reflex.changed", "CHANGED", "test.run", "RUN",
                "test.out", "OUT", "test.fail", "FAIL", "console.in", "IN");

        @Override
        public String title(String typeId) {
            return titles.get(typeId);
        }

        @Override
        public String label(String typeId, String portId) {
            return labels.get(typeId + "." + portId);
        }
    };

    private static JSONObject doc(String devices, String cables) {
        return new JSONObject("{\"devices\":" + devices + ",\"cables\":" + cables + "}");
    }

    private static String cable(int from, String fromPort, int to, String toPort) {
        return "{\"fromDevice\":" + from + ",\"fromPort\":\"" + fromPort + "\",\"toDevice\":" + to
                + ",\"toPort\":\"" + toPort + "\"}";
    }

    @Test
    @DisplayName("one line per source jack, labels not ids, ordered by the source device's place in the rack")
    void readsLikeASentence() {
        JSONObject doc = doc("[{\"type\":\"reflex\"},{\"type\":\"test\"},{\"type\":\"console\"}]",
                "[" + cable(1, "out", 2, "in") + "," + cable(0, "changed", 1, "run") + "]");
        assertThat(RackWiring.sketch(doc, NAMES)).containsExactly(
                "REFLEX CHANGED ▸ VERITAS RUN",
                "VERITAS OUT ▸ MONITOR IN");
        assertThat(RackWiring.titles(doc, NAMES)).containsExactly("REFLEX", "VERITAS", "MONITOR");
        assertThat(RackWiring.cableCount(doc)).isEqualTo(2);
    }

    @Test
    @DisplayName("a jack that fans out is one line, its targets in cable order; one device's jacks keep cable order")
    void fanOutIsOneLine() {
        JSONObject doc = doc("[{\"type\":\"test\"},{\"type\":\"console\"},{\"type\":\"console\"}]",
                "[" + cable(0, "out", 2, "in") + "," + cable(0, "fail", 1, "in") + "," + cable(0, "out", 1, "in") + "]");
        assertThat(RackWiring.sketch(doc, NAMES)).containsExactly(
                "VERITAS OUT ▸ MONITOR·2 IN, MONITOR IN",
                "VERITAS FAIL ▸ MONITOR IN");
    }

    @Test
    @DisplayName("the second device of one title reads ·2, so two lanes never read as one")
    void twinsAreNumbered() {
        JSONObject doc = doc("[{\"type\":\"test\"},{\"type\":\"test\"},{\"type\":\"console\"}]",
                "[" + cable(0, "out", 2, "in") + "," + cable(1, "out", 2, "in") + "]");
        assertThat(RackWiring.sketch(doc, NAMES)).containsExactly(
                "VERITAS OUT ▸ MONITOR IN",
                "VERITAS·2 OUT ▸ MONITOR IN");
        // the title list is the rack as mounted: the same title twice
        assertThat(RackWiring.titles(doc, NAMES)).containsExactly("VERITAS", "VERITAS", "MONITOR");
    }

    @Test
    @DisplayName("a type this install lacks reads as its id and a question mark; an unknown jack reads as its id")
    void unknownsSayWhatTheyAre() {
        JSONObject doc = doc("[{\"type\":\"com.example.uptime\"},{\"type\":\"console\"}]",
                "[" + cable(0, "beat", 1, "in") + "," + cable(0, "beat", 1, "legacy") + "]");
        assertThat(RackWiring.titles(doc, NAMES)).containsExactly("com.example.uptime ?", "MONITOR");
        assertThat(RackWiring.sketch(doc, NAMES)).containsExactly(
                "com.example.uptime ? beat ▸ MONITOR IN, MONITOR legacy");
    }

    @Test
    @DisplayName("a stranger's ids cannot forge a line: control characters are folded, long ids clipped")
    void hostileIdsStayOnTheirLine() {
        JSONObject doc = new JSONObject().put("devices", new JSONArray()
                .put(new JSONObject().put("type", "evil\nREFLEX CHANGED ▸ VERITAS RUN"))
                .put(new JSONObject().put("type", "x".repeat(500))))
                .put("cables", new JSONArray().put(new JSONObject().put("fromDevice", 0)
                        .put("fromPort", "a\r\nb").put("toDevice", 1).put("toPort", "in")));
        List<String> sketch = RackWiring.sketch(doc, NAMES);
        assertThat(sketch).hasSize(1);
        assertThat(sketch.get(0)).doesNotContain("\n").doesNotContain("\r").contains("a  b");
        assertThat(RackWiring.titles(doc, NAMES).get(1)).hasSize(40 + " ?".length());
    }

    @Test
    @DisplayName("malformed parts are left out, never thrown: a non-object slot, a cable past the rack, a port that is a number")
    void malformedPartsAreLeftOut() {
        JSONObject doc = doc("[\"nope\",{\"type\":\"test\"},{\"type\":\"console\"}]",
                "[7," + cable(1, "out", 9, "in") + ",{\"fromDevice\":1,\"fromPort\":5,\"toDevice\":2,\"toPort\":\"in\"},"
                        + cable(1, "out", 2, "in") + "]");
        assertThat(RackWiring.titles(doc, NAMES)).containsExactly("?", "VERITAS", "MONITOR");
        assertThat(RackWiring.sketch(doc, NAMES)).containsExactly("VERITAS OUT ▸ MONITOR IN");
        assertThat(RackWiring.sketch(new JSONObject(), NAMES)).isEmpty();
        assertThat(RackWiring.titles(null, NAMES)).isEmpty();
    }

    @Test
    @DisplayName("the lists are bounded, and the remainder is counted rather than dropped")
    void boundedWithTheRemainderCounted() {
        JSONArray devices = new JSONArray();
        JSONArray cables = new JSONArray();
        for (int i = 0; i < 100; i++) {
            devices.put(new JSONObject().put("type", "test"));
            cables.put(new JSONObject().put("fromDevice", i).put("fromPort", "out")
                    .put("toDevice", (i + 1) % 100).put("toPort", "run"));
        }
        JSONObject doc = new JSONObject().put("devices", devices).put("cables", cables);
        assertThat(RackWiring.titles(doc, NAMES)).hasSize(RackWiring.MAX_DEVICES + 1).last().isEqualTo("+36");
        assertThat(RackWiring.sketch(doc, NAMES)).hasSize(RackWiring.MAX_LINES + 1).last().isEqualTo("+36");
    }

    @Test
    @DisplayName("the catalog names: a real device's title and its jack LABEL (TEMPO's run jack reads START)")
    void catalogNamesReadTheRealCatalog() {
        assertThat(CatalogNames.INSTANCE.title("tempo")).isEqualTo("TEMPO");
        assertThat(CatalogNames.INSTANCE.label("tempo", "run")).isEqualTo("START");
        assertThat(CatalogNames.INSTANCE.label("tempo", "nope")).isNull();
        assertThat(CatalogNames.INSTANCE.title("com.example.nothing")).isNull();
        assertThat(CatalogNames.INSTANCE.label("com.example.nothing", "x")).isNull();
    }
}
