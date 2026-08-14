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
 * {@code AimFollower} gives suite windows the aimed project's node as
 * their ambient selection, which is what makes the platform's Team menu
 * and ^F6 work while, say, the Browser tab is focused.
 */
package org.nmox.studio.rack.service;
