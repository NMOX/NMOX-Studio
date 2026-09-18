package org.nmox.studio.rack.gallery;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.RackCard;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gate a community rack passes to ship. Its population is DERIVED, twice
 * over: from the resource directory as it sits on disk and from the
 * {@code index} the runtime reads — a classpath cannot be listed, so a rack
 * file with no index line would build green and never reach a user, and an
 * index line with no file would be a skip in a log nobody reads. Either fails
 * here BY NAME, as does any {@link RackJudge} problem (file + problem), so a
 * contributor's pull request says exactly what to fix.
 */
class CommunityRacksGateTest {

    /** The ONE home of the community racks, as the rack module sees it from its base directory. */
    static final File DIR = new File("src/main/resources/org/nmox/studio/rack/gallery/racks");

    @Test
    @DisplayName("every rack file on disk is in the index, and every index line is a file on disk")
    void theIndexIsTheDirectory() throws IOException {
        assertThat(DIR).as("the community rack directory").isDirectory();
        assertThat(onDisk()).as("rack files on disk vs lines of racks/index — add the missing line, or the missing file")
                .containsExactlyInAnyOrderElementsOf(indexed());
        assertThat(indexed()).as("an index line listed twice").doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("every community rack passes the judge: named, built-in devices only, mounts with every cable, at rest, nothing from its author's machine")
    void everyRackPassesTheJudge() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String name : onDisk()) {
            problems.addAll(RackJudge.problemsOfText(name, read(name)));
        }
        assertThat(problems).as("community rack problems (file: problem)").isEmpty();
    }

    @Test
    @DisplayName("there is a shelf worth opening: at least eight racks, every name and every stem its own")
    void theShelfIsWorthOpening() throws IOException {
        Set<String> names = new HashSet<>();
        Set<String> stems = new HashSet<>();
        for (String file : onDisk()) {
            assertThat(CommunityRacks.isRackFileName(file)).as(file + " is a plain lower-case <stem>" + CommunityRacks.SUFFIX)
                    .isTrue();
            String stem = file.substring(0, file.length() - CommunityRacks.SUFFIX.length());
            assertThat(stems.add(stem)).as("stem " + stem + " used twice").isTrue();
            String name = RackCard.of(new JSONObject(read(file)), "").name();
            assertThat(names.add(name.toLowerCase(java.util.Locale.ROOT))).as(file + ": the name \"" + name + "\" is another rack's")
                    .isTrue();
        }
        assertThat(stems).as("community racks").hasSizeGreaterThanOrEqualTo(8);
    }

    @Test
    @DisplayName("the runtime loader lists exactly the racks on disk, in index order — what the gate judged is what a user is shown")
    void theLoaderShowsWhatTheGateJudged() throws IOException {
        List<String> loaded = new ArrayList<>();
        for (CommunityRacks.Loaded rack : CommunityRacks.all()) {
            loaded.add(rack.stem() + CommunityRacks.SUFFIX);
            assertThat(rack.titles()).as(rack.stem() + " device titles").isNotEmpty().noneMatch(t -> t.endsWith("?"));
            assertThat(rack.wiring()).as(rack.stem() + " wiring sketch").isNotEmpty();
        }
        assertThat(loaded).as("racks the classpath loader lists").containsExactlyElementsOf(indexed());
    }

    static Set<String> onDisk() {
        Set<String> names = new TreeSet<>();
        String[] listed = DIR.list((dir, name) -> !name.equals("index"));
        if (listed != null) {
            names.addAll(List.of(listed));
        }
        return names;
    }

    static List<String> indexed() throws IOException {
        return CommunityRacks.indexLines(read("index"));
    }

    static String read(String name) throws IOException {
        return Files.readString(new File(DIR, name).toPath(), StandardCharsets.UTF_8);
    }
}
