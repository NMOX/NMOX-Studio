package org.nmox.studio.rack.gallery;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.GateSources;
import org.nmox.studio.rack.devices.SelfStarting;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackCard;
import org.nmox.studio.rack.model.RackIO;
import org.nmox.studio.rack.model.RackShare;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One authority for "this key belongs in a rack file" (v2.179.2).
 *
 * <p>v2.179.1 took a hand-kept set of self-starting switches out of
 * {@link RackJudge} and left four more beside it: the keys of the top level,
 * the {@code shared} header, a device slot and a cable, each written down here
 * a second time while {@link RackIO}, {@link RackShare} and {@link RackCard}
 * WRITE them. All four were right the day they shipped — which is how the first
 * one went wrong. A field added to {@code RackCard} tomorrow (or a key added to
 * the patch format) would have made this gate refuse every community rack that
 * used it, with "unknown header key", in a file the author of that field would
 * never think to open.
 *
 * <p>These tests hold the derivation the way {@code RackJudgeOneAuthorityTest}
 * holds the other one: the judge names no key, what it permits is exactly what
 * the writers write, and a key nobody has declared is still refused — because
 * refusing a typo is the whole point of the gate.
 */
class RackJudgeFormatKeysTest {

    private static final String FILE = "x.nmoxrack.json";

    /** Every key name this unit moved out of the judge, spelled as a Java literal. */
    private static final List<String> KEYS = List.of(
            RackIO.VERSION, RackIO.DEVICES, RackIO.CABLES, RackIO.TYPE, RackIO.STATE,
            RackIO.FROM_DEVICE, RackIO.FROM_PORT, RackIO.TO_DEVICE, RackIO.TO_PORT,
            RackShare.PRODUCT, RackCard.NAME, RackCard.DESCRIPTION, RackCard.AUTHOR,
            RackCard.KINDS, RackCard.REQUIRES);

    private static List<String> judged(Consumer<JSONObject> breakIt) {
        JSONObject doc = RackJudgeTest.sound();
        breakIt.accept(doc);
        return RackJudge.problems(FILE, doc);
    }

    private static JSONObject header(JSONObject doc) {
        return doc.getJSONObject(RackShare.SHARED);
    }

    private static Set<String> keysOf(JSONObject o) {
        return Set.copyOf(o.keySet());
    }

    @Test
    @DisplayName("the judge keeps NO key of its own: its source spells none of the format's key names, and asks the classes that write them")
    void theJudgeKeepsNoKeyOfItsOwn() throws Exception {
        String src = GateSources.stripComments(Files.readString(Path.of("src", "main", "java", "org", "nmox",
                "studio", "rack", "gallery", "RackJudge.java"), StandardCharsets.UTF_8).replace("\r\n", "\n"));
        for (String key : KEYS) {
            assertThat(src).as("a key spelled in the judge is a second home for the format: \"" + key + "\"")
                    .doesNotContain("\"" + key + "\"");
        }
        for (String authority : List.of("RackShare.TOP_LEVEL_KEYS", "RackShare.HEADER_KEYS",
                "RackIO.DEVICE_KEYS", "RackIO.CABLE_KEYS", "RackCard.isLanguageSibling(")) {
            assertThat(src).as("the judge asks the authority: " + authority).contains(authority);
        }
    }

    @Test
    @DisplayName("what the judge permits is exactly what the writers write — RackIO's patch, RackShare's header, RackCard's fields")
    void thePermittedKeysAreExactlyWhatTheWritersWrite(@TempDir File dir) {
        Rack rack = new Rack();
        JSONObject patch;
        try {
            rack.setProjectDir(dir);
            RackIO.fromJson(rack, RackShare.imported(RackJudgeTest.sound(), null));
            patch = RackIO.toJson(rack);
        } finally {
            rack.shutdown();
        }
        assertThat(keysOf(patch)).as("RackIO.toJson writes exactly its declared top level")
                .isEqualTo(RackIO.TOP_LEVEL_KEYS);
        JSONArray devices = patch.getJSONArray(RackIO.DEVICES);
        assertThat(devices.length()).isGreaterThan(0);
        for (int i = 0; i < devices.length(); i++) {
            assertThat(keysOf(devices.getJSONObject(i))).as("device slot " + i).isEqualTo(RackIO.DEVICE_KEYS);
        }
        JSONArray cables = patch.getJSONArray(RackIO.CABLES);
        assertThat(cables.length()).isGreaterThan(0);
        for (int i = 0; i < cables.length(); i++) {
            assertThat(keysOf(cables.getJSONObject(i))).as("cable " + i).isEqualTo(RackIO.CABLE_KEYS);
        }

        RackCard card = new RackCard("Name", "What it does.", "Someone",
                List.of("NODE"), List.of("node"));
        JSONObject shared = RackShare.export(patch, null, "9.9.9", card);
        assertThat(keysOf(shared)).as("a shared file's top level is the patch's plus the header")
                .isEqualTo(RackShare.TOP_LEVEL_KEYS);
        assertThat(keysOf(shared.getJSONObject(RackShare.SHARED)))
                .as("a full card plus the product version is exactly the permitted header")
                .isEqualTo(RackShare.HEADER_KEYS);
    }

    @Test
    @DisplayName("every field a full RackCard writes is accepted — the gate reads the declaration, so a field gained here needs no edit to the judge")
    void everyFieldACardWritesIsAccepted() {
        JSONObject doc = RackJudgeTest.sound();
        JSONObject header = header(doc);
        for (String key : RackCard.FIELDS) {
            header.remove(key);
        }
        new RackCard("Test on save", "Every save runs the tests.", "A contributor",
                List.of("NODE"), List.of("node")).writeTo(header);

        assertThat(header.keySet()).as("the fixture really exercises every field")
                .containsAll(RackCard.FIELDS);
        assertThat(RackJudge.problems(FILE, doc)).isEmpty();
    }

    @Test
    @DisplayName("a key NO authority declares is still refused, at every level — the gate keeps its strictness, typo and wrong case included")
    void aKeyNoAuthorityDeclaresIsRefused() {
        assertThat(judged(d -> d.put("extra", "hello")))
                .containsExactly(FILE + ": unknown top-level key \"extra\"");
        assertThat(judged(d -> header(d).put("colour", "blue")))
                .containsExactly(FILE + ": unknown header key \"colour\"");
        assertThat(judged(d -> header(d).put("names", new JSONArray().put("x"))))
                .containsExactly(FILE + ": unknown header key \"names\"");
        assertThat(judged(d -> d.getJSONArray(RackIO.DEVICES).getJSONObject(1).put("notes", "x")))
                .containsExactly(FILE + ": devices[1] test: unknown key \"notes\"");
        assertThat(judged(d -> d.getJSONArray(RackIO.CABLES).getJSONObject(0).put("fromport", "changed")))
                .containsExactly(FILE + ": cables[0]: unknown key \"fromport\"");
    }

    @Test
    @DisplayName("a NEWLY DECLARED key is taken with no edit to the judge: what an authority declares, the gate permits — and what none declares, it refuses")
    void aNewlyDeclaredKeyIsTakenWithNoEditToTheJudge() {
        JSONObject doc = RackJudgeTest.sound();
        header(doc).put("licence", "Apache-2.0");
        doc.getJSONArray(RackIO.CABLES).getJSONObject(0).put("colour", "red");

        assertThat(RackJudge.problems(FILE, doc)).as("nothing declares either key today")
                .containsExactly(FILE + ": unknown header key \"licence\"",
                        FILE + ": cables[0]: unknown key \"colour\"");

        RackJudge.Permitted declared = RackJudge.Permitted.declared();
        RackJudge.Permitted wider = new RackJudge.Permitted(declared.topLevel(),
                and(declared.header(), "licence"), declared.device(), and(declared.cable(), "colour"));
        assertThat(RackJudge.problems(FILE, doc, SelfStarting::keysFor, wider))
                .as("declared by the classes that write the file → permitted here, same bytes, same judge")
                .isEmpty();
    }

    private static Set<String> and(Set<String> keys, String more) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>(keys);
        out.add(more);
        return Set.copyOf(out);
    }
}
