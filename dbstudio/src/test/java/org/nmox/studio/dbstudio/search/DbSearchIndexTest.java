package org.nmox.studio.dbstudio.search;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.nmox.studio.dbstudio.model.TableInfo;
import org.nmox.studio.dbstudio.search.DbSearchIndex.Hit;
import org.nmox.studio.dbstudio.search.DbSearchIndex.Kind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Quick Search matcher over connections and known tables:
 * case-insensitive substring, connections always ahead of tables,
 * table hits carrying the spec they live in.
 */
class DbSearchIndexTest {

    private static final ConnectionSpec STAGING = new ConnectionSpec(
            "id-staging", "staging replica", DbEngine.POSTGRES,
            "pg.staging.example.com", 5432, "app", "app", "");
    private static final ConnectionSpec SHOP = new ConnectionSpec(
            "id-shop", "shop primary", DbEngine.MYSQL,
            "mysql.internal", 3306, "shop", "root", "");
    private static final ConnectionSpec CACHE = new ConnectionSpec(
            "id-cache", "local cache", DbEngine.SQLITE, "", -1, "", "", "/tmp/cache.db");

    private static DbSearchIndex index() {
        return new DbSearchIndex(
                List.of(STAGING, SHOP, CACHE),
                Map.of(
                        "id-staging", List.of(
                                new TableInfo("", "public", "users", "TABLE"),
                                new TableInfo("", "public", "orders", "TABLE")),
                        "id-shop", List.of(
                                new TableInfo("shop", "", "orders", "TABLE"),
                                new TableInfo("shop", "", "order_items", "TABLE"))));
    }

    @Test
    @DisplayName("matches a connection by name, case-insensitively")
    void connectionByName() {
        List<Hit> hits = index().matches("STAGING");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).kind()).isEqualTo(Kind.CONNECTION);
        assertThat(hits.get(0).spec()).isEqualTo(STAGING);
        assertThat(hits.get(0).table()).isNull();
        assertThat(hits.get(0).label()).isEqualTo("staging replica (PostgreSQL)");
    }

    @Test
    @DisplayName("matches a connection by engine display name")
    void connectionByEngine() {
        assertThat(index().matches("mysql"))
                .extracting(Hit::spec).containsExactly(SHOP);
        assertThat(index().matches("sqlite"))
                .extracting(Hit::spec).containsExactly(CACHE);
    }

    @Test
    @DisplayName("matches a connection by host fragment")
    void connectionByHost() {
        assertThat(index().matches("pg.staging"))
                .extracting(Hit::spec).containsExactly(STAGING);
    }

    @Test
    @DisplayName("matches tables by name, carrying the owning spec")
    void tableByName() {
        List<Hit> hits = index().matches("order");

        assertThat(hits).allMatch(h -> h.kind() == Kind.TABLE);
        assertThat(hits).extracting(h -> h.table().name())
                .containsExactly("orders", "orders", "order_items");
        assertThat(hits.get(0).spec()).isEqualTo(STAGING);
        assertThat(hits.get(1).spec()).isEqualTo(SHOP);
        assertThat(hits.get(2).spec()).isEqualTo(SHOP);
        assertThat(hits.get(1).label()).isEqualTo("orders — shop primary");
    }

    @Test
    @DisplayName("connection hits always come before table hits")
    void connectionsBeforeTables() {
        // "shop" matches the SHOP connection by name AND nothing table-wise;
        // "user" matches only a table; combine via a needle hitting both kinds:
        // "s" hits all three connections and the users table.
        List<Hit> hits = index().matches("s");

        assertThat(hits.size()).isGreaterThanOrEqualTo(4);
        int firstTable = -1;
        int lastConnection = -1;
        for (int i = 0; i < hits.size(); i++) {
            if (hits.get(i).kind() == Kind.CONNECTION) {
                lastConnection = i;
            } else if (firstTable < 0) {
                firstTable = i;
            }
        }
        assertThat(lastConnection).isLessThan(firstTable);
    }

    @Test
    @DisplayName("a needle matching nothing yields no hits")
    void noMatches() {
        assertThat(index().matches("zzz-nowhere")).isEmpty();
    }

    @Test
    @DisplayName("blank and null queries yield no hits")
    void degenerateQueries() {
        assertThat(index().matches(null)).isEmpty();
        assertThat(index().matches("")).isEmpty();
        assertThat(index().matches("   ")).isEmpty();
    }

    @Test
    @DisplayName("the needle is trimmed before matching")
    void needleTrimmed() {
        assertThat(index().matches("  staging  "))
                .extracting(Hit::spec).containsExactly(STAGING);
    }

    @Test
    @DisplayName("null inputs to the constructor mean an empty, matchable index")
    void nullConstructorInputs() {
        DbSearchIndex empty = new DbSearchIndex(null, null);
        assertThat(empty.matches("anything")).isEmpty();
    }

    @Test
    @DisplayName("tables keyed to an unknown connection id are ignored (no spec to carry)")
    void orphanTablesIgnored() {
        DbSearchIndex orphan = new DbSearchIndex(
                List.of(CACHE),
                Map.of("id-gone", List.of(new TableInfo("", "", "ghosts", "TABLE"))));
        assertThat(orphan.matches("ghosts")).isEmpty();
    }

    @Test
    @DisplayName("connections without table entries in the map still match as connections")
    void connectionWithoutTables() {
        assertThat(index().matches("cache"))
                .extracting(Hit::spec).containsExactly(CACHE);
    }

    @Test
    @DisplayName("a MongoDB collection indexes and matches like any table (TableInfo generalizes)")
    void mongoCollectionHit() {
        ConnectionSpec mongo = new ConnectionSpec("id-mongo", "docs cluster",
                DbEngine.MONGODB, "mongo.internal", 27017, "appdb", "app", "");
        DbSearchIndex index = new DbSearchIndex(
                List.of(mongo),
                Map.of("id-mongo", List.of(
                        new TableInfo("", "appdb", "user_events", "COLLECTION"))));

        List<Hit> hits = index.matches("user_ev");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).kind()).isEqualTo(Kind.TABLE);
        assertThat(hits.get(0).spec()).isEqualTo(mongo);
        assertThat(hits.get(0).table().type()).isEqualTo("COLLECTION");
        assertThat(hits.get(0).label()).isEqualTo("user_events — docs cluster");

        assertThat(index.matches("mongodb")).as("engine display name still matches the connection")
                .extracting(Hit::spec).containsExactly(mongo);
    }
}
