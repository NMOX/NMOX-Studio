package org.nmox.studio.dbstudio.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The console's language choice per engine family: SQL engines get the
 * SQL mime (ide-cluster kit), document engines get JSON (the editor
 * module's CSL registration), and the placeholder texts are recognized
 * as "nothing runnable" so RUN skips them and mime switches may replace
 * them.
 */
class ConsoleMimesTest {

    @Test
    @DisplayName("SQL engines ride text/x-sql, document engines text/x-json")
    void mimePerKind() {
        assertThat(ConsoleMimes.mimeFor(DbEngine.Kind.SQL)).isEqualTo("text/x-sql");
        assertThat(ConsoleMimes.mimeFor(DbEngine.Kind.DOCUMENT)).isEqualTo("text/x-json");
    }

    @Test
    @DisplayName("every engine's kind resolves to its console mime")
    void everyEngineResolves() {
        assertThat(ConsoleMimes.mimeFor(DbEngine.MYSQL.kind())).isEqualTo("text/x-sql");
        assertThat(ConsoleMimes.mimeFor(DbEngine.MARIADB.kind())).isEqualTo("text/x-sql");
        assertThat(ConsoleMimes.mimeFor(DbEngine.POSTGRES.kind())).isEqualTo("text/x-sql");
        assertThat(ConsoleMimes.mimeFor(DbEngine.SQLITE.kind())).isEqualTo("text/x-sql");
        assertThat(ConsoleMimes.mimeFor(DbEngine.MONGODB.kind())).isEqualTo("text/x-json");
        assertThat(ConsoleMimes.mimeFor(DbEngine.COUCHDB.kind())).isEqualTo("text/x-json");
    }

    @Test
    @DisplayName("placeholders speak each engine's console dialect")
    void placeholders() {
        assertThat(ConsoleMimes.placeholderFor(DbEngine.POSTGRES)).contains("SELECT");
        assertThat(ConsoleMimes.placeholderFor(DbEngine.MONGODB)).contains("\"find\"");
        assertThat(ConsoleMimes.placeholderFor(DbEngine.COUCHDB)).contains("\"selector\"");
    }

    @ParameterizedTest
    @EnumSource(DbEngine.class)
    @DisplayName("every placeholder is recognized as placeholder text")
    void placeholdersRecognized(DbEngine engine) {
        assertThat(ConsoleMimes.isPlaceholderOrBlank(ConsoleMimes.placeholderFor(engine)))
                .isTrue();
        // whitespace around it still counts — the user never edited it
        assertThat(ConsoleMimes.isPlaceholderOrBlank(
                "  " + ConsoleMimes.placeholderFor(engine) + "\n")).isTrue();
    }

    @Test
    @DisplayName("null and blank are placeholder-or-blank; real queries are not")
    void blanksAndRealText() {
        assertThat(ConsoleMimes.isPlaceholderOrBlank(null)).isTrue();
        assertThat(ConsoleMimes.isPlaceholderOrBlank("")).isTrue();
        assertThat(ConsoleMimes.isPlaceholderOrBlank("   \n\t")).isTrue();
        assertThat(ConsoleMimes.isPlaceholderOrBlank("SELECT * FROM users;")).isFalse();
        assertThat(ConsoleMimes.isPlaceholderOrBlank("{\"find\": \"users\"}")).isFalse();
    }
}
