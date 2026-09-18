package org.nmox.studio.rack.gallery;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.util.UiLocale;
import org.nmox.studio.rack.model.RackCard;
import org.nmox.studio.rack.model.RackShare;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The racks that ship with the product speak the reader's language (v2.179.0).
 * A community rack is a FILE, so its translations ride the file —
 * {@code name.<lang>} / {@code description.<lang>} beside the English, the
 * learning catalogue's mechanism (v2.133.0) — and this holds them complete.
 *
 * <p>Both populations are derived: the racks from the directory, the languages
 * from {@link UiLocale#SUPPORTED}. A rack added tomorrow, or a sixteenth
 * language, fails here by name until it is described — and a contributor's
 * English-only pull request fails with a message that says what to add.
 */
class CommunityRacksSpeakTest {

    private static final Path RACKS = Path.of("src", "main", "resources", "org", "nmox", "studio", "rack",
            "gallery", "racks");

    private static List<String> languages() {
        List<String> out = new ArrayList<>();
        for (UiLocale.Choice c : UiLocale.SUPPORTED) {
            if (!c.code().isEmpty() && !"en".equals(c.code())) {
                out.add(c.code());
            }
        }
        return out;
    }

    @Test
    @DisplayName("every community rack carries its name and description in every shipped language, within the card's limits, and the description is a translation rather than a copy")
    void everyRackSpeaksEveryLanguage() throws Exception {
        List<String> languages = languages();
        assertThat(languages).as("UiLocale should list the shipped languages").hasSizeGreaterThan(10);
        List<Path> files;
        try (Stream<Path> s = Files.list(RACKS)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".nmoxrack.json")).sorted().toList();
        }
        assertThat(files).as("the gallery should ship racks").hasSizeGreaterThanOrEqualTo(8);
        List<String> missing = new ArrayList<>();
        for (Path file : files) {
            JSONObject doc = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
            JSONObject header = doc.getJSONObject(RackShare.SHARED);
            String english = header.getString("description");
            for (String lang : languages) {
                String where = file.getFileName() + " [" + lang + "]";
                String name = header.optString("name." + lang, "");
                String description = header.optString("description." + lang, "");
                if (name.isBlank()) {
                    missing.add(where + ": add \"name." + lang + "\"");
                } else if (name.codePointCount(0, name.length()) > RackCard.MAX_NAME) {
                    missing.add(where + ": name." + lang + " is over " + RackCard.MAX_NAME + " characters — the card clips it");
                }
                if (description.isBlank()) {
                    missing.add(where + ": add \"description." + lang + "\"");
                } else if (description.codePointCount(0, description.length()) > RackCard.MAX_DESCRIPTION) {
                    missing.add(where + ": description." + lang + " is over " + RackCard.MAX_DESCRIPTION
                            + " characters — the card clips it");
                } else if (description.equals(english)) {
                    missing.add(where + ": description." + lang + " is the English sentence");
                }
                // what a reader of that language actually meets is what RackCard hands the gallery
                RackCard card = RackCard.of(doc, lang);
                if (!description.isBlank() && !card.description().equals(RackCard.of(
                        new JSONObject().put(RackShare.SHARED, new JSONObject().put("description", description)), "")
                        .description())) {
                    missing.add(where + ": the gallery does not show description." + lang + " as written");
                }
            }
        }
        assertThat(missing).as("a rack that ships with the product speaks every shipped language").isEmpty();
    }
}
