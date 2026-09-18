package org.nmox.studio.rack.devices;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.RackIO;
import org.nmox.studio.rack.model.RackShare;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every persisted switch in the built-in fleet is either a SETTING (restoring
 * it on changes what the next press does) or SELF-STARTING (restoring it on
 * starts a timer, a watcher or a poll) — and somebody decided which.
 *
 * <p>v2.176.0 promised that an imported rack arrives at rest and kept the
 * self-starting keys as a hand list inside {@code RackShare}: {@code armed},
 * {@code running}. TAIL's {@code follow} — which starts a one-second poll of a
 * path the SENDER chose — was not in it, and nothing could have noticed. So the
 * POPULATION here is derived: every catalog built-in is instantiated and asked
 * for its {@code toggleKeys()}. The ledger below is hand-kept on purpose — a
 * verdict with a reason is a decision, and a decision cannot be derived — but
 * every claim in it is checked: an unclassified pair, a stale row, and a row
 * that disagrees with what the device itself declares all fail BY NAME, and
 * every SELF_STARTING row must carry a probe that watches the thing start.
 */
class SelfStartingLedgerTest {

    enum Verdict {
        SELF_STARTING, SETTING
    }

    private record Row(Verdict verdict, String reason) {
    }

    private static final Map<String, Row> LEDGER = new LinkedHashMap<>();

    private static void row(String typeId, String key, Verdict verdict, String reason) {
        LEDGER.put(typeId + "." + key, new Row(verdict, reason));
    }

    static {
        row("reflex", "armed", Verdict.SELF_STARTING,
                "restartWatcher() runs from the switch's change listener and applyState, and starts a FileWatcher over the project that fires CHANGED into whatever is cabled.");
        row("tempo", "running", Verdict.SELF_STARTING,
                "syncTimer() runs from the switch's change listener and applyState, and starts the Swing timer that fires TICK and BAR triggers.");
        row("tail", "follow", Verdict.SELF_STARTING,
                "sync() runs from the switch's change listener and applyState, and starts a one-second poll that opens and reads the saved path.");
        row("terminal", "follow", Verdict.SETTING,
                "PHOSPHOR's FOLLOW only decides whether the scrollback autoscrolls when a line arrives; it has no change listener and reads nothing.");
        row("env", "ci", Verdict.SETTING,
                "ATMOS's CI sets an environment variable that the NEXT launched command reads and emits a DATA line; no process, timer or watcher starts.");
        row("test", "coverage", Verdict.SETTING,
                "VERITAS reads COVER only inside buildCommand, when RUN is pressed; the switch has no change listener.");
        row("lint", "fix", Verdict.SETTING,
                "PURITY reads FIX only inside buildCommand, when LINT is pressed; the switch has no change listener.");
        row("format", "write", Verdict.SETTING,
                "GLOSS reads WRITE/CHECK only inside buildCommand, when FORMAT is pressed; the switch has no change listener.");
        row("typecheck", "strict", Verdict.SETTING,
                "TYPEGUARD reads STRICT only inside buildCommand, when CHECK is pressed; the switch has no change listener.");
        row("typecheck", "watch", Verdict.SETTING,
                "TYPEGUARD's WATCH adds --watch to the NEXT tsc command and changes how a RUNNING check is read; with no change listener it cannot start one.");
        row("build", "prod", Verdict.SETTING,
                "FORGE reads PROD/DEV only inside buildCommand, when BUILD is pressed; the switch has no change listener.");
        row("build", "watch", Verdict.SETTING,
                "FORGE's WATCH adds the watch flag to the NEXT build and changes how a RUNNING build's lines are read; with no change listener it cannot start one.");
        row("angular", "prod", Verdict.SETTING,
                "HALO reads BUILD AS only inside its build command, when the action is pressed; the switch has no change listener.");
    }

    /** How to see each self-starting thing actually running — the smallest seam each device offers. */
    private static final Map<String, Predicate<RackDevice>> STARTED = Map.of(
            "reflex.armed", d -> ((ReflexDevice) d).isWatching(),
            "tempo.running", d -> ((TempoDevice) d).isClockRunning(),
            "tail.follow", d -> ((TailDevice) d).isPolling());

    /** typeId.key for every switch a built-in persists, read off a fresh instance of each. */
    private static Set<String> population() {
        Set<String> pairs = new TreeSet<>();
        for (DeviceCatalog.Entry e : DeviceCatalog.all()) {
            if (!e.builtIn()) {
                continue;
            }
            RackDevice d = e.create();
            for (String key : d.toggleKeys()) {
                pairs.add(e.id() + "." + key);
            }
        }
        return pairs;
    }

    @Test
    @DisplayName("every (built-in device, switch key) pair is classified, and no ledger row outlives its switch")
    void everySwitchIsClassified() {
        Set<String> population = population();
        assertThat(population).as("the derivation itself works: the fleet persists switches").hasSizeGreaterThan(5);
        List<String> problems = new ArrayList<>();
        for (String pair : population) {
            if (!LEDGER.containsKey(pair)) {
                problems.add("UNCLASSIFIED " + pair + " — read the device: does restoring this switch ON start a timer, "
                        + "watcher, poll or process (SELF_STARTING, register it with paramSelfStarting) or only change "
                        + "what the next press does (SETTING)?");
            }
        }
        for (String pair : LEDGER.keySet()) {
            if (!population.contains(pair)) {
                problems.add("STALE " + pair + " — no built-in persists this switch any more; delete the row");
            }
        }
        assertThat(problems).as("the self-starting ledger").isEmpty();
    }

    @Test
    @DisplayName("the ledger and the devices agree: SELF_STARTING exactly where the device declared paramSelfStarting")
    void ledgerAgreesWithTheDeclarations() {
        List<String> problems = new ArrayList<>();
        for (DeviceCatalog.Entry e : DeviceCatalog.all()) {
            if (!e.builtIn()) {
                continue;
            }
            RackDevice d = e.create();
            assertThat(d.toggleKeys()).as(e.id() + ": a self-starting key is a switch key").containsAll(d.selfStartingKeys());
            for (String key : d.toggleKeys()) {
                Row row = LEDGER.get(e.id() + "." + key);
                if (row == null) {
                    continue; // everySwitchIsClassified names it
                }
                boolean declared = d.selfStartingKeys().contains(key);
                if (declared != (row.verdict() == Verdict.SELF_STARTING)) {
                    problems.add(e.id() + "." + key + " — the ledger says " + row.verdict() + " but the device "
                            + (declared ? "registers it with paramSelfStarting" : "registers it with plain param")
                            + "; RackShare.imported follows the DEVICE, so one of the two is wrong");
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("every verdict carries a reason a person could disagree with")
    void everyVerdictHasAReason() {
        LEDGER.forEach((pair, row) -> assertThat(row.reason()).as(pair).hasSizeGreaterThan(60).endsWith("."));
    }

    @Test
    @DisplayName("each SELF_STARTING switch really starts something when a patch restores it on — watched through the device's own seam")
    void selfStartingSwitchesReallyStart(@TempDir Path project) throws Exception {
        for (Map.Entry<String, Row> e : LEDGER.entrySet()) {
            if (e.getValue().verdict() != Verdict.SELF_STARTING) {
                continue;
            }
            String pair = e.getKey();
            assertThat(STARTED).as("a SELF_STARTING row needs a probe that watches it start: " + pair).containsKey(pair);
            String typeId = pair.substring(0, pair.indexOf('.'));
            String key = pair.substring(pair.indexOf('.') + 1);
            Rack rack = new Rack();
            try {
                rack.setProjectDir(project.toFile());
                RackDevice device = DeviceCatalog.byId(typeId).orElseThrow().create();
                rack.addDevice(device);
                flushEdt();
                assertThat(STARTED.get(pair).test(device)).as(pair + " is at rest on a fresh mount").isFalse();
                device.applyState(Map.of(key, "true"));
                flushEdt();
                assertThat(STARTED.get(pair).test(device))
                        .as(pair + " starts by being restored — which is why a shared rack must not restore it on")
                        .isTrue();
            } finally {
                flushEdt();
                rack.shutdown();
            }
        }
    }

    @Test
    @DisplayName("a rack that arrives through RackShare.imported mounts with every self-starting switch off and nothing running; the same patch loaded raw starts all three")
    void importedRackArrivesAtRest(@TempDir Path project) throws Exception {
        JSONArray devices = new JSONArray();
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, Row> e : LEDGER.entrySet()) {
            if (e.getValue().verdict() == Verdict.SELF_STARTING) {
                String pair = e.getKey();
                pairs.add(pair);
                devices.put(new JSONObject().put("type", pair.substring(0, pair.indexOf('.')))
                        .put("state", new JSONObject().put(pair.substring(pair.indexOf('.') + 1), "true")));
            }
        }
        JSONObject patch = new JSONObject().put("version", 1).put("devices", devices).put("cables", new JSONArray());
        JSONObject shared = RackShare.export(patch, Path.of(System.getProperty("user.home")), "test");

        assertThat(startedAfterLoading(patch, project, pairs))
                .as("control: the raw patch DOES start them, so the next assertion can fail").containsExactlyElementsOf(pairs);
        assertThat(startedAfterLoading(RackShare.imported(shared, project), project, pairs))
                .as("imported: nothing starts until the receiver presses it").isEmpty();
    }

    @Test
    @DisplayName("the manifest's at-rest count is every SELF_STARTING device saved on — the number the receiver reads agrees with what the mount does")
    void manifestCountsThem() {
        JSONArray devices = new JSONArray();
        int expected = 0;
        for (Map.Entry<String, Row> e : LEDGER.entrySet()) {
            String pair = e.getKey();
            devices.put(new JSONObject().put("type", pair.substring(0, pair.indexOf('.')))
                    .put("state", new JSONObject().put(pair.substring(pair.indexOf('.') + 1), "true")));
            if (e.getValue().verdict() == Verdict.SELF_STARTING) {
                expected++;
            }
        }
        JSONObject shared = new JSONObject().put(RackShare.SHARED, new JSONObject()).put("devices", devices);
        assertThat(RackShare.inspect(shared, id -> true).atRest())
                .as("settings saved on are not arrivals at rest; self-starting switches are").isEqualTo(expected);

        JSONArray mounted = RackShare.imported(shared, Path.of(System.getProperty("user.home"))).getJSONArray("devices");
        int i = 0;
        for (Map.Entry<String, Row> e : LEDGER.entrySet()) {
            String key = e.getKey().substring(e.getKey().indexOf('.') + 1);
            assertThat(mounted.getJSONObject(i++).getJSONObject("state").getString(key))
                    .as(e.getKey() + ": a SETTING travels as the sender left it, a SELF_STARTING switch arrives off")
                    .isEqualTo(e.getValue().verdict() == Verdict.SETTING ? "true" : "false");
        }
    }

    private static List<String> startedAfterLoading(JSONObject doc, Path project, List<String> pairs) throws Exception {
        Rack rack = new Rack();
        try {
            rack.setProjectDir(project.toFile());
            RackIO.fromJson(rack, doc);
            flushEdt();
            List<String> started = new ArrayList<>();
            List<RackDevice> mounted = rack.getDevices();
            for (int i = 0; i < pairs.size(); i++) {
                if (STARTED.get(pairs.get(i)).test(mounted.get(i))) {
                    started.add(pairs.get(i));
                }
            }
            return started;
        } finally {
            flushEdt();
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("the installed resolver: a built-in answers what it declared, everything else answers EVERY_SWITCH")
    void resolverAnswers() {
        assertThat(SelfStarting.keysFor("tail")).containsExactly("follow");
        assertThat(SelfStarting.keysFor("reflex")).containsExactly("armed");
        assertThat(SelfStarting.keysFor("tempo")).containsExactly("running");
        assertThat(SelfStarting.keysFor("terminal")).as("PHOSPHOR's follow is a setting").isEmpty();
        assertThat(SelfStarting.keysFor("oracle")).as("a legacy id resolves to its device").isEmpty();
        assertThat(SelfStarting.keysFor("org.nmox.fixture.echo"))
                .as("an installed extension is never instantiated to be asked").isSameAs(RackShare.EVERY_SWITCH);
        assertThat(SelfStarting.keysFor("com.example.not.installed")).isSameAs(RackShare.EVERY_SWITCH);
        assertThat(SelfStarting.keysFor(null)).isSameAs(RackShare.EVERY_SWITCH);
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
