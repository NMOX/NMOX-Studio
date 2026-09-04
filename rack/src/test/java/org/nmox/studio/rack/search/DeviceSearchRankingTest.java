package org.nmox.studio.rack.search;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.search.SearchTerms;
import org.nmox.studio.rack.devices.DeviceCatalog;

import static org.assertj.core.api.Assertions.assertThat;

/** Ledger 67: the strongest-intent device lists first; membership is the one matcher's, unchanged. */
class DeviceSearchRankingTest {

    @Test
    @DisplayName("'compose' lists HARBOR (vocabulary: compose) before the composer devices (prefix hits)")
    void composeRanksHarborFirst() {
        List<String> titles = DeviceSearchProvider.ranked("compose").stream().map(DeviceCatalog.Entry::title).toList();
        assertThat(titles).isNotEmpty();
        assertThat(titles.get(0)).isEqualTo("HARBOR");
        assertThat(titles).contains("ARTISAN", "CRATE");
    }

    @Test
    @DisplayName("Ranking never changes membership: every matching device is listed, exact ones first, ties in shelf order")
    void membershipUnchanged() {
        for (String query : List.of("compose", "docker", "test", "postgres", "lint")) {
            List<DeviceCatalog.Entry> ranked = DeviceSearchProvider.ranked(query);
            List<DeviceCatalog.Entry> matching = DeviceCatalog.all().stream()
                    .filter(t -> SearchTerms.matches(query, t.title(), t.description(), t.keywords())).toList();
            assertThat(ranked).as(query).containsExactlyInAnyOrderElementsOf(matching);
            int seenLoose = -1;
            for (int i = 0; i < ranked.size(); i++) {
                DeviceCatalog.Entry t = ranked.get(i);
                int score = SearchTerms.score(query, t.title(), t.description(), t.keywords());
                if (score == SearchTerms.LOOSE && seenLoose < 0) {
                    seenLoose = i;
                }
                if (score == SearchTerms.EXACT && seenLoose >= 0) {
                    throw new AssertionError(query + ": an exact hit listed after a loose one at " + i);
                }
            }
        }
    }
}
