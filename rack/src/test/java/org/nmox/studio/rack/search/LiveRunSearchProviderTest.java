package org.nmox.studio.rack.search;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** ⌘I reaches every running command (v2.73.0): the words that find it, and the registration beside the servers. */
class LiveRunSearchProviderTest {

    @Test
    @DisplayName("the run's own words and the controlled vocabulary find it; strangers do not")
    void matching() {
        assertThat(LiveRunSearchProvider.matches("dev", "npm run dev — shop")).isTrue();
        assertThat(LiveRunSearchProvider.matches("shop", "npm run dev — shop")).isTrue();
        assertThat(LiveRunSearchProvider.matches("stop", "Run — api")).as("the vocabulary").isTrue();
        assertThat(LiveRunSearchProvider.matches("running", "Focused test: adds")).isTrue();
        assertThat(LiveRunSearchProvider.matches("postgres", "npm run dev — shop")).isFalse();
    }

    @Test
    @DisplayName("registered in QuickSearch beside the servers, with its folder named")
    void registered() throws Exception {
        String layer = Files.readString(Path.of("src/main/resources/org/nmox/studio/rack/layer.xml"));
        assertThat(layer).contains("<folder name=\"LiveRuns\">")
                .contains("org-nmox-studio-rack-search-LiveRunSearchProvider.instance");
        assertThat(Files.readString(Path.of("src/main/resources/org/nmox/studio/rack/search/Bundle.properties")))
                .contains("QuickSearch/LiveRuns=");
    }
}
