package org.nmox.studio.rack.sharing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The pure half of the sharing package: no window, no real home directory. */
class SharingPureTest {

    private static JSONObject rackWith(String... commands) {
        JSONArray devices = new JSONArray();
        for (String c : commands) {
            devices.put(new JSONObject().put("type", "solder").put("state", new JSONObject().put("command", c)));
        }
        return new JSONObject().put("version", 1).put("devices", devices).put("cables", new JSONArray());
    }

    // ---- ShareCards ----

    @Test
    @DisplayName("requires are suggested from the rack's own commands: the first word of a command line, once each, in order")
    void suggestsRequires() {
        assertThat(ShareCards.suggestRequires(rackWith("npm run build", "cargo test", "npm test", "npx vitest run")))
                .containsExactly("npm", "cargo", "npx");
        assertThat(ShareCards.suggestRequires(null)).isEmpty();
        assertThat(ShareCards.suggestRequires(new JSONObject())).isEmpty();
    }

    @Test
    @DisplayName("a path, a URL, a glob and a lone word are not commands and name no tool; env and sudo name the tool after them")
    void notEveryValueIsACommand() {
        assertThat(ShareCards.toolOf("./gradlew build")).isNull();
        assertThat(ShareCards.toolOf("http://localhost:3000 /health")).isNull();
        assertThat(ShareCards.toolOf("**/*.ts src")).isNull();
        assertThat(ShareCards.toolOf("main")).isNull();
        assertThat(ShareCards.toolOf("8080")).isNull();
        assertThat(ShareCards.toolOf("")).isNull();
        assertThat(ShareCards.toolOf(null)).isNull();
        assertThat(ShareCards.toolOf("NPM run build")).as("a tool name is lower case; a shouted word is a label").isNull();
        assertThat(ShareCards.toolOf("env CI=1")).as("env with nothing after the assignment names nothing").isNull();
        assertThat(ShareCards.toolOf("sudo docker compose up")).isEqualTo("docker");
        assertThat(ShareCards.toolOf("  cargo   test ")).isEqualTo("cargo");
    }

    @Test
    @DisplayName("a file stem keeps letters of any script, folds the rest to single hyphens, and is never empty or a dot-file")
    void fileStems() {
        assertThat(ShareCards.fileStem("Rust Watch Loop!")).isEqualTo("rust-watch-loop");
        assertThat(ShareCards.fileStem("  --API / smoke--  ")).isEqualTo("api-smoke");
        assertThat(ShareCards.fileStem("../../etc/passwd")).isEqualTo("etc-passwd");
        assertThat(ShareCards.fileStem(".hidden")).isEqualTo("hidden");
        assertThat(ShareCards.fileStem("")).isEqualTo("rack");
        assertThat(ShareCards.fileStem(null)).isEqualTo("rack");
        assertThat(ShareCards.fileStem("???")).isEqualTo("rack");
        String hindi = new String(Character.toChars(0x091F)) + new String(Character.toChars(0x0947))
                + new String(Character.toChars(0x0938)) + new String(Character.toChars(0x094D))
                + new String(Character.toChars(0x091F));
        assertThat(ShareCards.fileStem(hindi)).as("a Devanagari name keeps its marks (the v2.114.0 word-splitting bug)")
                .isEqualTo(hindi);
        String longName = "a".repeat(200);
        assertThat(ShareCards.fileStem(longName)).hasSize(48);
        // the cap is read ONCE per iteration and a join appends TWO code
        // points, so a name whose 48th kept code point follows a separator
        // came out 49 long — the plain-run case above could never see it
        // because it never joins (v2.184.0)
        String joinsAtTheCap = "a".repeat(47) + " bbb";
        assertThat(ShareCards.fileStem(joinsAtTheCap))
                .as("a hyphen joined at the boundary must not push the stem past its stated 48")
                .isEqualTo("a".repeat(47));
        for (int run = 1; run <= 60; run++) {
            String name = ("ab ".repeat(run)).strip();
            String stem = ShareCards.fileStem(name);
            assertThat(stem.codePointCount(0, stem.length()))
                    .as("at most 48 code points for any join pattern (%s)", name)
                    .isLessThanOrEqualTo(48);
            assertThat(stem).as("and never left with a dangling join").doesNotEndWith("-");
        }
    }

    @Test
    @DisplayName("a typed list splits on commas, semicolons and whitespace")
    void splitsLists() {
        assertThat(ShareCards.splitList("npm, cargo  docker;go")).containsExactly("npm", "cargo", "docker", "go");
        String typed = "npm" + Character.toString(0xFF0C) + "cargo" + Character.toString(0x3001) + "docker"
                + Character.toString(0x060C) + " go" + Character.toString(0xFF1B) + "deno";
        assertThat(ShareCards.splitList(typed))
                .as("the full-width, enumeration and Arabic commas are commas").containsExactly("npm", "cargo", "docker", "go", "deno");
        assertThat(ShareCards.splitList("  ")).isEmpty();
        assertThat(ShareCards.splitList(null)).isEmpty();
    }

    // ---- MyRacks ----

    @Test
    @DisplayName("keeping writes <stem>.nmoxrack.json into the drop-in dir, creating it; a second rack of that name is refused by name and the first is untouched")
    void keepNeverClobbers(@TempDir Path tmp) throws IOException {
        File dir = tmp.resolve("presets.d").toFile();
        File kept = MyRacks.keepIn(dir, rackWith("npm test"), "My Loop");
        assertThat(kept).hasName("my-loop.nmoxrack.json").exists();
        String first = Files.readString(kept.toPath());
        assertThatThrownBy(() -> MyRacks.keepIn(dir, rackWith("cargo test"), "my loop"))
                .isInstanceOf(MyRacks.AlreadyKeptException.class).hasMessage("my-loop.nmoxrack.json");
        assertThat(Files.readString(kept.toPath())).isEqualTo(first);
    }

    @Test
    @DisplayName("removing takes a kept rack and nothing else: not a file outside the dir, not a non-json, not a directory")
    void removeIsConfined(@TempDir Path tmp) throws IOException {
        File dir = Files.createDirectories(tmp.resolve("presets.d")).toFile();
        File kept = MyRacks.keepIn(dir, rackWith("npm test"), "gone");
        File outside = Files.writeString(tmp.resolve("outside.json"), "{}").toFile();
        File notJson = Files.writeString(dir.toPath().resolve("notes.txt"), "x").toFile();
        File sub = Files.createDirectories(dir.toPath().resolve("sub.json")).toFile();
        File nested = Files.writeString(Files.createDirectories(dir.toPath().resolve("deep")).resolve("x.json"), "{}").toFile();

        assertThatThrownBy(() -> MyRacks.removeFrom(dir, outside)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> MyRacks.removeFrom(dir, new File(dir, "../outside.json"))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> MyRacks.removeFrom(dir, notJson)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> MyRacks.removeFrom(dir, sub)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> MyRacks.removeFrom(dir, nested)).as("directly inside, not somewhere below")
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> MyRacks.removeFrom(dir, null)).isInstanceOf(IOException.class);
        assertThat(outside).exists();
        assertThat(nested).exists();

        MyRacks.removeFrom(dir, kept);
        assertThat(kept).doesNotExist();
    }

    @Test
    @DisplayName("a symlink planted in the drop-in dir cannot aim a removal at the file it points to")
    void removeRefusesSymlinks(@TempDir Path tmp) throws IOException {
        File dir = Files.createDirectories(tmp.resolve("presets.d")).toFile();
        Path victim = Files.writeString(tmp.resolve("victim.json"), "{}");
        Path link = dir.toPath().resolve("link.json");
        try {
            Files.createSymbolicLink(link, victim);
        } catch (IOException | UnsupportedOperationException noSymlinks) {
            Assumptions.abort("this platform or account cannot create symlinks");
        }
        assertThatThrownBy(() -> MyRacks.removeFrom(dir, link.toFile())).isInstanceOf(IOException.class);
        assertThat(victim).exists();
    }

    // ---- RackText ----

    @Test
    @DisplayName("a rack pastes back from plain JSON, from a fenced block with or without a language tag, and past a byte-order mark")
    void parsesPastedRacks() throws Exception {
        String json = RackText.render(rackWith("npm test"));
        assertThat(json).endsWith("}\n");
        assertThat(RackText.parse(json).getJSONArray("devices")).hasSize(1);
        assertThat(RackText.parse("```json\n" + json + "```").getJSONArray("devices")).hasSize(1);
        assertThat(RackText.parse("\n  ```\n" + json + "\n```  \n").getJSONArray("devices")).hasSize(1);
        assertThat(RackText.parse(Character.toString(0xFEFF) + json).getJSONArray("devices")).hasSize(1);
    }

    @Test
    @DisplayName("what is not a rack is refused with its reason, never with org.json's exception")
    void refusalsCarryReasons() {
        assertReason("", RackText.NotARackException.Reason.EMPTY);
        assertReason("   \n", RackText.NotARackException.Reason.EMPTY);
        assertReason(null, RackText.NotARackException.Reason.EMPTY);
        assertReason("npm run build", RackText.NotARackException.Reason.NOT_JSON);
        assertReason("[1,2,3]", RackText.NotARackException.Reason.NOT_JSON);
        assertReason("{\"name\":\"a package.json\"}", RackText.NotARackException.Reason.NO_DEVICES);
        assertReason("{\"devices\":\"many\"}", RackText.NotARackException.Reason.NO_DEVICES);
        assertReason("x".repeat(RackText.MAX_CHARS + 1), RackText.NotARackException.Reason.TOO_LARGE);
        assertReason("[".repeat(200_000), RackText.NotARackException.Reason.NOT_JSON);
    }

    private static void assertReason(String pasted, RackText.NotARackException.Reason reason) {
        assertThatThrownBy(() -> RackText.parse(pasted))
                .isInstanceOfSatisfying(RackText.NotARackException.class, e -> assertThat(e.reason()).isEqualTo(reason));
    }
}
