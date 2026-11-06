package org.nmox.studio.infra.ops.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.UIManager;
import org.nmox.studio.infra.ops.model.Evidence;
import org.nmox.studio.infra.ops.model.HostStatus;

/**
 * The HUD's one palette and type. Dark ground, cold phosphor for the
 * machine you sit at and its links, warm amber for anything still
 * becoming, green for ready, red for what stopped answering, lavender
 * for services. Every colour means one thing and is used for that
 * thing only; the evidence of an edge is carried by its stroke, not a
 * hue, so a colour-blind reader keeps the distinction.
 */
public final class HudStyle {

    public static final Color GROUND = new Color(0x0B, 0x10, 0x16);
    public static final Color GRID = new Color(0x15, 0x1D, 0x27);
    public static final Color GRID_STRONG = new Color(0x1C, 0x27, 0x34);
    public static final Color TEXT = new Color(0xD8, 0xE2, 0xEB);
    public static final Color MUTED = new Color(0x7F, 0x8E, 0xA3);
    public static final Color PHOSPHOR = new Color(0x5E, 0xE2, 0xFF);
    public static final Color PHOSPHOR_DIM = new Color(0x2A, 0x6C, 0x7C);
    public static final Color READY = new Color(0x7C, 0xF2, 0x9A);
    public static final Color BECOMING = new Color(0xF2, 0xC1, 0x4E);
    public static final Color TROUBLE = new Color(0xFF, 0x6B, 0x6B);
    public static final Color SERVICE = new Color(0xB8, 0xA6, 0xFF);
    public static final Color SELECTION = new Color(0xFF, 0xFF, 0xFF);
    public static final Color BANNER = new Color(0xF2, 0xC1, 0x4E);
    public static final Color BANNER_TEXT = new Color(0x1A, 0x14, 0x05);

    private HudStyle() {
    }

    public static Color statusColor(HostStatus s) {
        return switch (s) {
            case READY -> READY;
            case UNREACHABLE, GONE -> TROUBLE;
            default -> BECOMING;
        };
    }

    public static Color edgeColor(Evidence e) {
        return switch (e) {
            case OBSERVED -> PHOSPHOR;
            case INFERRED -> MUTED;
            case DECLARED -> SERVICE;
        };
    }

    /** The dash pattern that carries an edge's evidence: solid, dashed, dotted. */
    public static float[] edgeDash(Evidence e) {
        return switch (e) {
            case OBSERVED -> null;
            case INFERRED -> new float[]{7f, 5f};
            case DECLARED -> new float[]{2f, 4f};
        };
    }

    /** The UI's own font family at a size, so the HUD reads like the rest of the window. */
    public static Font ui(float size, boolean bold) {
        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        return base.deriveFont(bold ? Font.BOLD : Font.PLAIN, size);
    }

    /** Monospace for readings, so digits line up as they change. */
    public static Font mono(float size) {
        Font base = UIManager.getFont("TextArea.font");
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        return (base != null && base.getFamily().toLowerCase(java.util.Locale.ROOT).contains("mono") ? base : mono)
                .deriveFont(size);
    }
}
