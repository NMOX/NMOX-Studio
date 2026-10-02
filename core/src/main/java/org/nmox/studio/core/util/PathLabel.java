package org.nmox.studio.core.util;

/**
 * A label that shows a filesystem path and never decides how wide its
 * window is (3.1.0).
 *
 * <p>A plain {@code JLabel} asks for the width of its whole text, and a
 * docked pane on a fresh layout grows to its content's preferred width:
 * aiming a project under a long path pushed the Workbench, and then Project
 * Studio, across half the window. This label asks for at most
 * {@link #PREFERRED_CAP} pixels, shrinks to nothing when squeezed, and
 * shows as much of the path as fits, eliding the MIDDLE - a path's ends
 * are what tell you where it is, the project's own name last of all. The
 * whole path is always on the tooltip and the accessible description.
 *
 * <p>The fitting itself is {@link FitLabel}'s (3.5.2), which also cuts
 * sentences; this is the path-shaped one, with the path's own helpers.
 */
public class PathLabel extends FitLabel {

    /** The most width this label ever asks its container for. */
    public static final int PREFERRED_CAP = 280;

    public PathLabel() {
        super(Cut.MIDDLE, PREFERRED_CAP, false);
    }

    /** Shows {@code path} (rendered as text, never markup) and puts all of it on the tooltip. */
    public void setPath(String path) {
        setFull(path);
    }

    /** The whole path, however much of it is on screen. */
    public String getPath() {
        return getFull();
    }

    /** A path's tooltip is the whole path whether or not it is cut: it is what a person copies from. */
    @Override
    protected String tooltipFor(String whole, boolean isCut) {
        return whole;
    }

    /**
     * {@code file} relative to {@code root}, in the platform's separators,
     * as VS Code's Copy Relative Path writes it ({@code .} for the root
     * itself); the absolute path when there is no root or the file is
     * outside it, because a relative path that climbs out names somewhere
     * else. One rule for the file tree's row and the editor's (3.2.0).
     */
    public static String relative(java.io.File root, java.io.File file) {
        if (root == null) {
            return file.getAbsolutePath();
        }
        java.nio.file.Path r = root.toPath().toAbsolutePath().normalize();
        java.nio.file.Path f = file.toPath().toAbsolutePath().normalize();
        if (!f.startsWith(r)) {
            return file.getAbsolutePath();
        }
        String rel = r.relativize(f).toString();
        return rel.isEmpty() ? "." : rel;
    }
}
