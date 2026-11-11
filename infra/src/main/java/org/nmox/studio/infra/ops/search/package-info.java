/**
 * The Ops Hub's reach from Quick Search (⌘I): servers and the services
 * on them, by name or address.
 *
 * <p><b>RCP mechanism:</b> a {@code SearchProvider} registered as an
 * instance file under the root {@code QuickSearch} folder in the infra
 * module's {@code layer.xml}, with a position, and a localizing bundle
 * that names the category. The bundle here is hand-written for that
 * reason, so this package's keys live in it and no class in it uses
 * {@code @Messages} (a generated bundle and a hand one collide).
 *
 * <p><b>Reading order:</b> {@link org.nmox.studio.infra.ops.search.OpsSearchProvider}
 * is the whole of it.
 */
package org.nmox.studio.infra.ops.search;
