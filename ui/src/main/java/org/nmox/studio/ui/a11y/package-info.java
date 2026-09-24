/**
 * Accessibility repairs to the platform's own chrome - the parts NMOX did
 * not build but a screen reader still reads. The product's own controls
 * carry their names where they are built (the name laws in
 * {@code DeviceContractTest} and the inputs/collections gates); this
 * package is for what the platform hands us wrong.
 *
 * <p>{@link org.nmox.studio.ui.a11y.ToolbarAccessibleNames} drops the menu
 * mnemonic marker the platform leaves in every toolbar button's
 * accessible name ({@code &New File...}), installed once the main window
 * shows ({@code @OnShowing}) and kept right as buttons are renamed or
 * added.
 */
package org.nmox.studio.ui.a11y;
