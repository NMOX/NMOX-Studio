/**
 * The soft-dependency seams — THE architectural idea to understand
 * before reading any cross-module code in this product.
 *
 * <p>Problem: the API client, DB Studio and friends want to know which
 * project the rack is aimed at, but they must not depend on the rack
 * module (that coupling is what the v1.46.0 surgery removed). Solution:
 * core declares a small facade here (e.g.
 * {@link org.nmox.studio.core.spi.ProjectAim}), the rack registers an
 * implementation with the NetBeans <b>Lookup</b> mechanism
 * ({@code @ServiceProvider}), and consumers ask
 * {@code Lookup.getDefault().lookup(...)} at runtime. If the provider
 * module is absent the lookup returns {@code null} and the feature
 * degrades honestly — no {@code catch (LinkageError)}, ever.
 *
 * <p>The seams: {@code ProjectAim} (the aimed project + change
 * listeners), {@code LiveServings} (dev servers currently running),
 * {@code TrustGate} (workspace-trust checks for modules that spawn
 * project-controlled code), and {@code KvasirAsk} (text-only questions
 * to the AI device). One resident is not a seam but a shared registry:
 * {@code LiveRuns} (the IDE's own running commands, so the toolbar ■ can
 * stop a run spawned by any module — a pure class with no module to
 * belong to, v2.70.0). The frozen third-party Device SPI lives one level
 * down in {@code core.spi.device} and has its own package doc.
 */
package org.nmox.studio.core.spi;
