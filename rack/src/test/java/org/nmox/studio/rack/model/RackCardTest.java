package org.nmox.studio.rack.model;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RackCardTest {

    /** One code point, two chars: a control-knobs emoji, built here so the source stays ASCII. */
    private static final String EMOJI = Character.toString(0x1F39B);
    private static final String LINE_SEPARATOR = Character.toString(0x2028);
    private static final String NUL = Character.toString(0);
    private static final String LONE_HIGH_SURROGATE = String.valueOf((char) 0xD83C);

    private static JSONObject docWith(JSONObject header) {
        return new JSONObject().put("version", 1).put(RackShare.SHARED, header);
    }

    @Test
    @DisplayName("a card round-trips through the shared header, and blank fields are never written")
    void roundTrip() {
        RackCard card = new RackCard("Rust watch loop", "REFLEX watches, VERITAS tests.", "",
                List.of("rust"), List.of("cargo"));
        JSONObject header = new JSONObject().put("product", "2.179.0");
        card.writeTo(header);
        assertThat(header.has("author")).as("an untyped author is not written as an empty claim").isFalse();
        assertThat(header.getString("product")).isEqualTo("2.179.0");
        RackCard back = RackCard.of(docWith(header));
        assertThat(back).isEqualTo(card);
        assertThat(back.kinds()).as("kinds are ProjectKind names: upper case").containsExactly("RUST");
        assertThat(back.fits("rust")).isTrue();
        assertThat(back.fits("GO")).isFalse();
        assertThat(back.fits(null)).isFalse();
    }

    @Test
    @DisplayName("a plain Save Patch file, a null document and a header of the wrong type all read as the empty card")
    void absentIsEmpty() {
        assertThat(RackCard.of(null)).isEqualTo(RackCard.EMPTY);
        assertThat(RackCard.of(new JSONObject().put("version", 1))).isEqualTo(RackCard.EMPTY);
        assertThat(RackCard.of(new JSONObject().put(RackShare.SHARED, "yes"))).isEqualTo(RackCard.EMPTY);
        assertThat(RackCard.EMPTY.isBlank()).isTrue();
    }

    @Test
    @DisplayName("a stranger's card is read tolerantly: a number for a name, an object in a list and a string for a list are absent, never an exception")
    void wrongTypesAreAbsent() {
        JSONObject header = new JSONObject().put("name", 42).put("description", new JSONObject())
                .put("kinds", "RUST").put("requires", new JSONArray().put(7).put(new JSONObject()).put("cargo"));
        RackCard card = RackCard.of(docWith(header));
        assertThat(card.name()).isEmpty();
        assertThat(card.description()).isEmpty();
        assertThat(card.kinds()).isEmpty();
        assertThat(card.requires()).containsExactly("cargo");
    }

    @Test
    @DisplayName("a newline in a name cannot forge a line of the manifest: control characters and line separators fold to one space")
    void controlCharactersFold() {
        String forged = "Nice rack" + "\n\n" + "SETTINGS: none" + LINE_SEPARATOR + "really" + "\t" + "fine" + NUL;
        RackCard card = new RackCard(forged, "", "", null, null);
        assertThat(card.name()).isEqualTo("Nice rack SETTINGS: none really fine");
    }

    @Test
    @DisplayName("text is clipped by code points — an emoji at the limit is kept whole or dropped whole — and a clip on the joining space leaves none dangling")
    void clipsByCodePoints() {
        String atLimit = "a".repeat(RackCard.MAX_NAME - 1) + EMOJI + "tail";
        String name = new RackCard(atLimit, "", "", null, null).name();
        assertThat(name.codePointCount(0, name.length())).isEqualTo(RackCard.MAX_NAME);
        assertThat(name).endsWith(EMOJI);
        String overLimit = "a".repeat(RackCard.MAX_NAME) + EMOJI;
        assertThat(new RackCard(overLimit, "", "", null, null).name()).isEqualTo("a".repeat(RackCard.MAX_NAME));
        String spaceAtLimit = "a".repeat(RackCard.MAX_NAME - 1) + " b";
        assertThat(new RackCard(spaceAtLimit, "", "", null, null).name())
                .isEqualTo("a".repeat(RackCard.MAX_NAME - 1));
        assertThat(new RackCard("x" + LONE_HIGH_SURROGATE + "y", "", "", null, null).name())
                .as("a lone surrogate is not text").isEqualTo("x y");
    }

    @Test
    @DisplayName("requires are bare tool names to look up, never a path or a command line")
    void requiresAreBareNames() {
        RackCard card = new RackCard("", "", "", null, List.of("cargo", "docker-compose", "python3.12", "g++",
                "/bin/sh", "../x", "rm -rf", "-flag", ".hidden", "a;b", "cargo"));
        assertThat(card.requires()).containsExactly("cargo", "docker-compose", "python3.12", "g++");
    }

    @Test
    @DisplayName("lists are bounded on the way in: a hostile thousand-entry array costs MAX_LIST entries")
    void listsAreBounded() {
        JSONArray many = new JSONArray();
        for (int i = 0; i < 1000; i++) {
            many.put("tool" + i);
        }
        RackCard card = RackCard.of(docWith(new JSONObject().put("requires", many)));
        assertThat(card.requires()).hasSize(RackCard.MAX_LIST);
    }
}
