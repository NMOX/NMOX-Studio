package org.nmox.studio.dbstudio.model;

/**
 * One table or view as reported by {@code DatabaseMetaData.getTables}.
 * Catalog and schema are normalized to {@code ""} when the engine has
 * no such concept (SQLite reports neither).
 *
 * @param catalog the table's catalog, or {@code ""}
 * @param schema  the table's schema, or {@code ""}
 * @param name    the table name
 * @param type    {@code "TABLE"} or {@code "VIEW"} (the JDBC table type)
 */
public record TableInfo(String catalog, String schema, String name, String type) {

    /** True when this entry is a view rather than a base table. */
    public boolean isView() {
        return "VIEW".equalsIgnoreCase(type);
    }
}
