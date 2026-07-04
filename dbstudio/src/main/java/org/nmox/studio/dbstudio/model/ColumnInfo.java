package org.nmox.studio.dbstudio.model;

/**
 * One column as reported by {@code DatabaseMetaData.getColumns},
 * cross-referenced with {@code getPrimaryKeys}.
 *
 * @param name       the column name
 * @param typeName   the engine's type name ({@code "INTEGER"},
 *                   {@code "varchar"}, ...) as the driver reports it
 * @param size       column size / precision, engine-defined
 * @param nullable   false only when the driver is certain the column
 *                   rejects NULLs
 * @param primaryKey true when the column is part of the primary key
 */
public record ColumnInfo(String name, String typeName, int size, boolean nullable, boolean primaryKey) {
}
