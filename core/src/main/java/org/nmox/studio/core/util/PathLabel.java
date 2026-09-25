package org.nmox.studio.core.util;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.JLabel;

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
 */
public class PathLabel extends JLabel {

    /** The most width this label ever asks its container for. */
    public static final int PREFERRED_CAP = 280;

    private static final String ELLIPSIS = "…";

    private String full = "";

    public PathLabel() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                refit();
            }
        });
    }

    /** Shows {@code path} (rendered as text, never markup) and puts all of it on the tooltip. */
    public void setPath(String path) {
        full = path == null ? "" : path;
        setToolTipText(PlainText.plain(full.isEmpty() ? null : full));
        getAccessibleContext().setAccessibleDescription(full);
        refit();
    }

    /** A new font (Presentation Mode, a look-and-feel change) re-measures the cut. */
    @Override
    public void setFont(java.awt.Font font) {
        super.setFont(font);
        if (full != null) {
            refit();
        }
    }

    /** The whole path, however much of it is on screen. */
    public String getPath() {
        return full;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        FontMetrics fm = getFontMetrics(getFont());
        Insets in = getInsets();
        int want = fm.stringWidth(full) + in.left + in.right;
        d.width = Math.min(want, PREFERRED_CAP);
        return d;
    }

    @Override
    public Dimension getMinimumSize() {
        Dimension d = super.getMinimumSize();
        d.width = 0;
        return d;
    }

    private void refit() {
        FontMetrics fm = getFontMetrics(getFont());
        Insets in = getInsets();
        int room = getWidth() - in.left - in.right;
        String shown = room <= 0 ? full : fitMiddle(full, fm::stringWidth, room);
        super.setText(PlainText.plain(shown));
    }

    /**
     * The longest middle-elided form of {@code text} whose width fits
     * {@code room}: a head and a tail joined by an ellipsis, the tail given
     * the larger share because it names the thing, cut on code points so a
     * surrogate pair is never split. The text itself when it fits.
     */
    public static String fitMiddle(String text, java.util.function.ToIntFunction<String> width, int room) {
        if (text == null || text.isEmpty() || width.applyAsInt(text) <= room) {
            return text == null ? "" : text;
        }
        int[] cps = text.codePoints().toArray();
        int lo = 0;
        int hi = cps.length;
        String best = ELLIPSIS;
        while (lo <= hi) {
            int keep = (lo + hi) >>> 1;
            String candidate = join(cps, keep);
            if (width.applyAsInt(candidate) <= room) {
                best = candidate;
                lo = keep + 1;
            } else {
                hi = keep - 1;
            }
        }
        return best;
    }

    private static String join(int[] cps, int keep) {
        int head = keep * 2 / 5;
        int tail = keep - head;
        return new String(cps, 0, head) + ELLIPSIS + new String(cps, cps.length - tail, tail);
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
