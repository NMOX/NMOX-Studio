/**
 * The rack as a service to the rest of the IDE.
 * {@link org.nmox.studio.rack.service.RackService} is the singleton
 * that owns the ONE shared rack, aims it at a project, autoloads the
 * per-project patch ({@code .nmoxrack.json}), and coordinates safe
 * project switching (running devices are stopped, with a prompt).
 *
 * <p>This package is also where the rack PUBLISHES itself to the
 * platform: adapters registered with {@code @ServiceProvider} implement
 * the {@code core.spi} facades (ProjectAim, LiveServings, TrustGate) so
 * other modules can follow the aim without depending on the rack —
 * read {@code core.spi}'s package doc first if that sentence is new.
 *
 * <p>{@code core.util.AimFollower} is the consumer side of that
 * arrangement: it gives a suite window the aimed project's node as its
 * ambient selection, which is what makes the platform's Team menu and
 * ^F6 work while, say, the Browser tab is focused. It lived here until
 * v2.186.0, and that address was the whole of tech-debt ledger 72 —
 * the three studios that had dropped their rack dependency could not
 * reach it. It reads {@link org.nmox.studio.core.spi.ProjectAim}, which
 * this package provides, so the wiring is unchanged; only the address
 * moved.
 */
package org.nmox.studio.rack.service;
