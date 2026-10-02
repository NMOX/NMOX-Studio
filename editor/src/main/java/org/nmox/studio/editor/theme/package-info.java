/**
 * The editor's colours where the platform leaves a gap.
 *
 * <p><b>What lives here:</b> {@link org.nmox.studio.editor.theme.ProfileAnnotationColors},
 * which gives annotation types (the debugger's current line, breakpoints,
 * bookmarks) the colours of the colour profile in use.
 *
 * <p><b>The RCP mechanism:</b> an {@code @OnShowing} hook. Profiles are
 * folders under {@code Editors/FontsColors} in the system filesystem; a
 * file there marked {@code nbeditor-settings-ColoringType=annotation}
 * holds one profile's annotation colours. The platform applies those only
 * from its Options dialog, and this product selects its profile through
 * the layer instead.
 *
 * <p><b>Start reading at:</b> the class comment of
 * {@code ProfileAnnotationColors}, then {@code apply}.
 */
package org.nmox.studio.editor.theme;
