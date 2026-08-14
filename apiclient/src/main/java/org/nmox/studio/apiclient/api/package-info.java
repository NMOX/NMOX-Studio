/**
 * API Studio's engine room — everything that is NOT the window: the
 * request/collection model, the send engine (bounded response reads,
 * real cancel), {@code {{var}}} environments, the importers
 * (curl, .http, OpenAPI, Postman, HAR, Insomnia) and the .http
 * exporter, security-header grading, and workspace IO for
 * {@code .nmoxapi.json}.
 *
 * <p>The law that shapes half these classes: <b>secrets are
 * keychain-only</b>. Auth tokens never serialize into the committable
 * workspace file — {@code ApiSecrets} stores them under the request's
 * stable id via the OS keyring, importers LIFT captured credentials
 * out of recordings, and the exporter deliberately omits auth. When
 * reading an importer, check what it refuses as carefully as what it
 * accepts; honest refusal over silent guessing is the house style.
 */
package org.nmox.studio.apiclient.api;
