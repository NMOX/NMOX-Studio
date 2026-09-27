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
 *
 * <p>{@link org.nmox.studio.ui.a11y.WindowTabsAccessibility} (3.4) gives
 * the window system's tab containers the tab-list structure their role
 * promises, so a screen reader can navigate into a window's content at all:
 * before it, every container exposed no children and only pointing reached
 * anything inside.
 */
package org.nmox.studio.ui.a11y;
