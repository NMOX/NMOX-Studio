package org.nmox.studio.dbstudio.ui;

import org.nmox.studio.dbstudio.model.DbEngine;

/**
 * Which editor MIME and starter text the DB Studio console gets, per
 * engine. SQL engines ride {@code text/x-sql} — the ide cluster ships
 * {@code org.netbeans.modules.db.sql.editor}, whose kit highlights it.
 * Document engines get {@code text/x-json}: the editor module registers
 * that MIME as a first-class CSL language backed by the bundled TextMate
 * JSON grammar ({@code JsonLanguage}/{@code JsonGrammar}), so MongoDB
 * command documents and CouchDB Mango queries highlight as JSON.
 *
 * <p>Pure and static so the choice is unit-testable without a window
 * system; {@code DbStudioTopComponent} applies the result to its
 * console {@code JEditorPane}.
 */
final class ConsoleMimes {

    /** SQL console MIME, served by the ide cluster's SQL editor kit. */
    static final String SQL_MIME = "text/x-sql";

    /** Document console MIME, served by the editor module's JSON CSL language. */
    static final String JSON_MIME = "text/x-json";

    private ConsoleMimes() {
    }

    /** The console MIME for an engine family. */
    static String mimeFor(DbEngine.Kind kind) {
        return kind == DbEngine.Kind.SQL ? SQL_MIME : JSON_MIME;
    }

    /** The starter text shown in an empty console aimed at {@code engine}. */
    static String placeholderFor(DbEngine engine) {
        return switch (engine) {
            case MYSQL, MARIADB, POSTGRES, SQLITE -> "SELECT …;";
            case MONGODB -> "{\"find\": \"collection\", \"filter\": {}}";
            case COUCHDB -> "{\"selector\": {}}";
        };
    }

    /**
     * True when the console holds nothing runnable: null, blank, or
     * exactly one of the per-engine placeholders. RUN skips such text
     * and the mime switch may overwrite it with the new engine's
     * placeholder without losing user work.
     */
    static boolean isPlaceholderOrBlank(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String trimmed = text.trim();
        for (DbEngine engine : DbEngine.values()) {
            if (placeholderFor(engine).equals(trimmed)) {
                return true;
            }
        }
        return false;
    }
}
