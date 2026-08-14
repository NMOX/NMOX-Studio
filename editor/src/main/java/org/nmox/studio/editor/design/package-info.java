/**
 * The designer's editor tools for stylesheets: inline color swatches
 * (every literal painted as its color via a {@code HighlightsLayer}),
 * the ⌘-click color picker that rewrites the literal in its authored
 * form, CSS custom-property ({@code var(--token)}) completion with
 * swatch icons and ⌘-click to the declaration, and WCAG contrast
 * verdicts.
 *
 * <p>Two z-order lessons are baked in: a highlight layer that loses
 * the merge fails SILENTLY and PARTIALLY (the feature works only on
 * tokens the grammar doesn't color — v1.229.0), and layer anchors
 * must be decompiled, not guessed (v1.234.0). The color math
 * (oklch/oklab/lab/lch conversions) is pure and pinned to published
 * reference values.
 */
package org.nmox.studio.editor.design;
