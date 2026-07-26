package org.nmox.studio.rack.projectstudio;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.nmox.studio.core.process.ProcessSupport;

/**
 * The Image Kit's engine: finds a web project's images and presses
 * them — smaller JPEGs by re-encoding (pure Java2D/ImageIO, no tools
 * required), optional downscale to a maximum width, and WebP siblings
 * via the user's own {@code cwebp} when it's on PATH. Pure file work on
 * the caller's thread; the action runs it off the EDT.
 *
 * <p>The laws it inherits: <b>never clobber</b> — an output that
 * already exists is skipped and said so, and originals are never
 * touched; outputs are siblings ({@code photo.min.jpg},
 * {@code photo.webp}). <b>Honest results</b> — a press that doesn't
 * actually save enough (under {@value #KEEP_THRESHOLD_PERCENT}%) is
 * discarded and reported as already-tight rather than shipping a
 * bigger "optimized" file. PNG re-encoding is deliberately absent:
 * ImageIO can't beat a real PNG optimizer, so for PNGs the honest win
 * is the WebP sibling, and the docs say exactly that.
 */
public final class ImagePress {

    /** A press that saves less than this is noise, not an optimization. */
    public static final int KEEP_THRESHOLD_PERCENT = 10;
    /** A workbench pass, not a bulk archiver. */
    public static final int MAX_FILES = 500;

    private static final Set<String> SKIP_DIRS = Set.of(
            "node_modules", ".git", "dist", "build", "coverage", "target",
            "out", ".next", ".nuxt", "vendor", ".svelte-kit");

    private ImagePress() {
    }

    /** A found image: where, and how heavy. */
    public record Candidate(File file, long bytes) {
    }

    /**
     * One press outcome. {@code output} is null when nothing was
     * written; {@code note} always says why in words.
     */
    public record Result(File source, File output, long before, long after,
                         String note) {

        public long saved() {
            return output == null ? 0 : before - after;
        }
    }

    /** Finds .jpg/.jpeg/.png under root, skipping the heavy dirs. */
    public static List<Candidate> scan(File root) {
        List<Candidate> out = new ArrayList<>();
        walk(root, out, 0);
        return out;
    }

    private static void walk(File dir, List<Candidate> out, int depth) {
        if (depth > 12 || out.size() >= MAX_FILES) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File f : children) {
            if (out.size() >= MAX_FILES) {
                return;
            }
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (f.isDirectory()) {
                if (!SKIP_DIRS.contains(name) && !name.startsWith(".")) {
                    walk(f, out, depth + 1);
                }
            } else if ((name.endsWith(".jpg") || name.endsWith(".jpeg")
                    || name.endsWith(".png"))
                    // our own outputs are not candidates — pressing a press
                    // would compound loss on every run
                    && !name.contains(".min.")) {
                out.add(new Candidate(f, f.length()));
            }
        }
    }

    /**
     * Re-encodes a JPEG/PNG as a JPEG at the given quality, optionally
     * downscaled to {@code maxWidth} (0 = no scaling), into a
     * {@code .min.jpg} sibling. Alpha is flattened onto white — JPEG
     * has no transparency, and pretending otherwise produces black
     * boxes.
     */
    public static Result pressJpeg(File src, float quality, int maxWidth) {
        File out = sibling(src, ".min.jpg");
        long before = src.length();
        if (out.exists()) {
            return new Result(src, null, before, out.length(),
                    out.getName() + " already exists — skipped (never clobber).");
        }
        BufferedImage img;
        try {
            img = ImageIO.read(src);
        } catch (IOException ex) {
            return new Result(src, null, before, before,
                    "unreadable (" + ex.getMessage() + ") — skipped.");
        }
        if (img == null) {
            return new Result(src, null, before, before,
                    "not a decodable image — skipped.");
        }
        BufferedImage flat = flatten(img, maxWidth);
        try {
            writeJpeg(flat, quality, out);
        } catch (IOException ex) {
            return new Result(src, null, before, before,
                    "write failed (" + ex.getMessage() + ").");
        }
        long after = out.length();
        boolean resized = maxWidth > 0 && img.getWidth() > maxWidth;
        if (!resized && after > before * (100 - KEEP_THRESHOLD_PERCENT) / 100) {
            // an "optimization" that saves under the threshold is noise;
            // a RESIZED output is kept regardless — smaller pixels were
            // the point
            try {
                Files.deleteIfExists(out.toPath());
            } catch (IOException ignore) {
                // the report below still tells the truth
            }
            return new Result(src, null, before, before,
                    "already tight — re-encoding saved under "
                    + KEEP_THRESHOLD_PERCENT + "%, nothing written.");
        }
        return new Result(src, out, before, after,
                resized ? "resized to " + flat.getWidth() + "px wide" : "re-encoded");
    }

    /**
     * Writes a {@code .webp} sibling using the user's own cwebp — the
     * ToolLocator/Doctor idiom: their tool, our honest report.
     */
    public static Result pressWebp(File src, File cwebp, int quality) {
        File out = sibling(src, ".webp");
        long before = src.length();
        if (out.exists()) {
            return new Result(src, null, before, out.length(),
                    out.getName() + " already exists — skipped (never clobber).");
        }
        ProcessSupport.BoundedResult run;
        try {
            run = ProcessSupport.runBounded(java.util.List.of(
                    cwebp.getAbsolutePath(), "-quiet", "-q", String.valueOf(quality),
                    src.getAbsolutePath(), "-o", out.getAbsolutePath()),
                    src.getParentFile(), java.time.Duration.ofSeconds(30));
        } catch (IOException ex) {
            return new Result(src, null, before, before,
                    "cwebp failed to run (" + ex.getMessage() + ").");
        }
        if (run.exitCode() != 0 || !out.exists()) {
            return new Result(src, null, before, before,
                    "cwebp exit " + run.exitCode() + " — nothing written.");
        }
        return new Result(src, out, before, out.length(), "webp sibling");
    }

    /** The copy-paste offer: serve the WebP where supported, fall back. */
    public static String pictureSnippet(String imageName) {
        String base = imageName.contains(".")
                ? imageName.substring(0, imageName.lastIndexOf('.')) : imageName;
        return "<picture>\n"
                + "  <source srcset=\"" + base + ".webp\" type=\"image/webp\">\n"
                + "  <img src=\"" + imageName + "\" alt=\"\">\n"
                + "</picture>";
    }

    private static File sibling(File src, String suffix) {
        String name = src.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return new File(src.getParentFile(), base + suffix);
    }

    private static BufferedImage flatten(BufferedImage img, int maxWidth) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (maxWidth > 0 && w > maxWidth) {
            h = Math.max(1, (int) Math.round(h * (maxWidth / (double) w)));
            w = maxWidth;
        }
        BufferedImage flat = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = flat.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(img, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return flat;
    }

    private static void writeJpeg(BufferedImage img, float quality, File out)
            throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("no JPEG writer in this JVM");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
    }
}
