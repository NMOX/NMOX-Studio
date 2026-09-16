package org.nmox.studio.rack.devices;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.RackDevice;

/**
 * Paints every faceplate to a PNG so a person can look at it.
 *
 * <p>Property-gated like the docs forge: an ordinary build skips it, because
 * a test that writes 53 images outside {@code target/} on every run is a side
 * effect, not a test. Run it when a faceplate changes:
 * {@code mvn test -pl rack -Dtest=FaceplatePaintProbe -Dnmox.faceplates=/tmp/faceplates}
 *
 * <p>It exists because {@code DeviceContractTest} can only prove rectangles do
 * not intersect. Whether a plate READS well is a question only a picture
 * answers — the gate was green the day VERITAS shipped three labels printed
 * on top of one another.
 */
class FaceplatePaintProbe {

    @Test
    void paintEveryFaceplate() throws Exception {
        String dir = System.getProperty("nmox.faceplates");
        Assumptions.assumeTrue(dir != null, "set -Dnmox.faceplates=<dir> to paint the faceplates");
        File out = new File(dir);
        out.mkdirs();
        for (DeviceCatalog.Entry entry : DeviceCatalog.all()) {
            RackDevice device = entry.create();
            int w = Math.max(1, device.getPreferredSize().width);
            int h = Math.max(1, device.getPreferredSize().height);
            device.setSize(w, h);
            device.doLayout();
            BufferedImage img = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(2, 2);
            device.paint(g);
            g.dispose();
            ImageIO.write(img, "png", new File(out, entry.toString() + ".png"));
        }
        System.out.println("faceplates painted into " + out);
    }
}
