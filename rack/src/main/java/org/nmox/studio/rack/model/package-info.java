/**
 * The rack's pure model: {@link org.nmox.studio.rack.model.Rack} holds
 * {@link org.nmox.studio.rack.model.RackDevice}s, devices expose typed
 * {@link org.nmox.studio.rack.model.Port}s, and a
 * {@link org.nmox.studio.rack.model.Cable} routes a signal from an OUT
 * jack to an IN jack. No Swing in the contracts — the UI in
 * {@code rack.ui} renders this model, and the signal router lives on
 * the model so pipelines behave identically in tests and on screen.
 *
 * <p>Reading order for the run-a-pipeline story: a device's GO fires →
 * {@code RackDevice.exec} spawns via the engine package → EXIT status
 * emits on the device's OK/FAIL out-jacks → {@code Rack}'s router
 * follows every cable and delivers to the wired in-jacks. Structural
 * edits (add/remove/rewire) are undoable; loading a patch or preset is
 * a deliberate undo BOUNDARY (v1.50.0) so ⌘Z cannot peel a fresh load
 * apart.
 */
package org.nmox.studio.rack.model;
