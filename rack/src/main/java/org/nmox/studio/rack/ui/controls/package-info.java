/**
 * The widget library the faceplates are built from: Knob, RackButton,
 * ToggleSwitch, Led, LcdDisplay, VuMeter. These are hand-painted Swing
 * components, and two laws live at this level so no device can break
 * them:
 * <ul>
 *   <li><b>Accessibility is structural</b> (v1.41.0): every control
 *       implements the Swing accessibility API — knobs are SLIDERs with
 *       arrow-key support, buttons answer Space/Enter, and a
 *       catalog-wide contract test fails the build on any control
 *       without an accessible name.</li>
 *   <li><b>An LCD that truncates says so</b> (v1.282.0): head kept,
 *       ellipsis appended, full text as the tooltip — a cut message
 *       must never read as a different message.</li>
 * </ul>
 * Colors come from {@code RackStyle}, where GO-green / STOP-red /
 * MUTATE-amber / QUERY-blue are reserved for meaning (the color law).
 */
package org.nmox.studio.rack.ui.controls;
