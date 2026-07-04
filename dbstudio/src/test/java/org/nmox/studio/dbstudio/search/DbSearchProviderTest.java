package org.nmox.studio.dbstudio.search;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;
import org.nmox.studio.dbstudio.search.DbSearchIndex.Hit;
import org.nmox.studio.dbstudio.search.DbSearchIndex.Kind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Quick Search provider's index plumbing: DB Studio publishes its
 * specs plus whatever containers the tree has already fetched, the
 * provider searches exactly that snapshot — no database is ever touched
 * from a search keystroke.
 */
class DbSearchProviderTest {

    private static final ConnectionSpec STAGING = new ConnectionSpec(
            "id-staging", "staging", DbEngine.POSTGRES,
            "pg.example.com", 5432, "app", "app", "");
    private static final ConnectionSpec MONGO = new ConnectionSpec(
            "id-mongo", "docs", DbEngine.MONGODB,
            "mongo.example.com", 27017, "docs", "root", "");

    @AfterEach
    void forgetSnapshot() {
        DbSearchProvider.reset();
    }

    @Test
    @DisplayName("a published snapshot answers connection queries")
    void publishedConnections() {
        DbSearchProvider.publish(List.of(STAGING, MONGO), Map.of());

        List<Hit> hits = DbSearchProvider.currentIndex().matches("staging");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).kind()).isEqualTo(Kind.CONNECTION);
        assertThat(hits.get(0).spec()).isEqualTo(STAGING);
    }

    @Test
    @DisplayName("cached containers surface as table hits carrying their spec")
    void publishedContainers() {
        DbSearchProvider.publish(
                List.of(STAGING, MONGO),
                Map.of("id-mongo", List.of(new TableInfo("", "docs", "invoices", "COLLECTION"))));

        List<Hit> hits = DbSearchProvider.currentIndex().matches("invoices");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).kind()).isEqualTo(Kind.TABLE);
        assertThat(hits.get(0).spec()).isEqualTo(MONGO);
        assertThat(hits.get(0).table().name()).isEqualTo("invoices");
    }

    @Test
    @DisplayName("re-publishing replaces the snapshot entirely")
    void republishReplaces() {
        DbSearchProvider.publish(List.of(STAGING), Map.of());
        DbSearchProvider.publish(List.of(MONGO), Map.of());

        assertThat(DbSearchProvider.currentIndex().matches("staging")).isEmpty();
        assertThat(DbSearchProvider.currentIndex().matches("docs")).isNotEmpty();
    }

    @Test
    @DisplayName("before any publish, the provider still yields a usable (disk-backed) index")
    void unpublishedFallsBack() {
        DbSearchProvider.reset();

        // contents depend on the machine's workspace file; the contract under
        // test is "never null, never throws" — search stays usable pre-open
        assertThat(DbSearchProvider.currentIndex()).isNotNull();
        assertThat(DbSearchProvider.currentIndex().matches("")).isEmpty();
    }
}
