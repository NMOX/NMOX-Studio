package org.nmox.studio.ui.shots;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The pure half of Save Screenshot (v2.87.0, the developer-evangelist
 * grant): the IDE painted to an image by Swing itself — the v1.109.0
 * DocsShots recipe, now a user gesture. Swing painting straight to a
 * 2x-supersampled image means no OS screen-recording permission, no
 * desktop in the frame, no cropping, and crisp text for a slide, a doc or
 * a post. {@link #paint2x} runs on the EDT (it paints); writing the file
 * is the caller's off-EDT job.
 */
public final class Screenshot {

    /** 2x supersample: crisp text on a retina slide, the DocsShots choice. */
    public static final int SCALE = 2;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    private Screenshot() {
    }

    /** The component painted at {@link #SCALE}; null for a component with no size (never NPE a hidden window). */
    public static BufferedImage paint2x(Component c) {
        int w = c.getWidth();
        int h = c.getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        BufferedImage img = new BufferedImage(w * SCALE, h * SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.scale(SCALE, SCALE);
            c.paint(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    /** A file name that sorts by time and says what it is: {@code nmox-studio-2026-09-06-081530.png}. */
    public static String defaultFileName(LocalDateTime at) {
        return "nmox-studio-" + STAMP.format(at) + ".png";
    }

    /** Longest document name kept in an editor shot's file name (a bounded name for any tab title). */
    static final int NAME_CAP = 80;

    /**
     * An editor shot's file name: the document it shows, then the stamp —
     * {@code App.jsx-2026-09-06-081530.png}. The name is a tab title or a
     * file name, which is external text: anything outside {@code [A-Za-z0-9._-]}
     * becomes a dash (no separators, no shell metacharacters), a blank name
     * reads {@code editor}, and the name is capped at {@link #NAME_CAP}.
     */
    public static String editorFileName(String documentName, LocalDateTime at) {
        String name = documentName == null ? "" : documentName;
        StringBuilder sb = new StringBuilder();
        name.codePoints().limit(NAME_CAP).forEach(cp -> {
            boolean keep = (cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z') || (cp >= '0' && cp <= '9')
                    || cp == '.' || cp == '_' || cp == '-';
            sb.append(keep ? (char) cp : '-');
        });
        String safe = sb.toString();
        while (safe.startsWith("-") || safe.startsWith(".")) {
            safe = safe.substring(1);
        }
        if (safe.isEmpty()) {
            safe = "editor";
        }
        return safe + "-" + STAMP.format(at) + ".png";
    }
}
