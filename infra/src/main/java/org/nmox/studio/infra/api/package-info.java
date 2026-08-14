/**
 * The Infra Designer's cloud clients (DigitalOcean, Hetzner,
 * Cloudflare) and deploy planner. Each client is a thin typed wrapper
 * over the provider's REST API through the shared capped-read HTTP
 * plumbing; tokens live in the OS keyring via {@code CloudTokens}.
 *
 * <p>Patterns to notice: destructive operations (Destroy, Deploy)
 * confirm with the SAFE button as the default (v1.98.0 — Swing's
 * {@code NotifyDescriptor} defaults Enter to OK unless you use the
 * full constructor); live cloud operations lock the canvas with a
 * DEPTH counter, not a boolean (v1.126.0 — two overlapping ops taught
 * that lesson); and "deleted in cloud" is believed only on a literal
 * HTTP 404, never a substring match (v1.120.0).
 */
package org.nmox.studio.infra.api;
