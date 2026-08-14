/**
 * Angular-specific editor intelligence, the framework this product
 * bets on: the component ↔ template ↔ styles ↔ spec four-file
 * switcher, selector navigation (click {@code <app-hero>} in a
 * template, land in the component class), routes support, and the
 * ng-generate action. Everything here keys on the Angular CLI's
 * {@code .component.*} naming and {@code angular.json} discovery —
 * locating the workspace root by walking UP for {@code angular.json}
 * itself, because an Angular repo contains inner manifests that fool
 * generic walks (v1.223.0).
 *
 * <p>These are classic small NetBeans pieces: editor popup actions
 * registered per-MIME, {@code HyperlinkProviderExt}s claiming a text
 * span and opening a target, all resolution off the EDT.
 */
package org.nmox.studio.editor.angular;
