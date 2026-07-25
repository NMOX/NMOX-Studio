package org.nmox.nmox.studio.branding;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

/**
 * Basic checks for branding resources.
 */
public class BrandingResourcesTest {

    /**
     * The About dialog's image: Splash.loadContent(true) prefers the branded
     * org/netbeans/core/startup/about.png over the splash, so Help ▸ About
     * shows the NMOX Studio logo only while this file ships. It must be a
     * real PNG (the platform loads it by extension-typed reader) and banner
     * shaped, not an icon.
     */
    @Test
    void aboutLogoShipsAsRealPng() throws IOException {
        Path about = Paths.get(
                "src/main/nbm-branding/core/core.jar/org/netbeans/core/startup/about.png");
        assertTrue(Files.exists(about), "About logo should exist: " + about);
        byte[] head = new byte[8];
        try (var in = Files.newInputStream(about)) {
            assertEquals(8, in.read(head), "About logo should not be truncated");
        }
        assertArrayEquals(
                new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'},
                head, "About logo should carry the PNG signature");
        var img = javax.imageio.ImageIO.read(about.toFile());
        assertNotNull(img, "About logo should decode as an image");
        assertTrue(img.getWidth() >= 400 && img.getWidth() <= 800,
                "About logo should be banner-width, was " + img.getWidth());
        assertTrue(img.getHeight() >= 120 && img.getHeight() < img.getWidth(),
                "About logo should be a landscape banner, was "
                        + img.getWidth() + "x" + img.getHeight());
    }

    @Test
    void manifestPointsToBundle() throws IOException {
        Path manifestPath = Paths.get("src/main/nbm/manifest.mf");
        assertTrue(Files.exists(manifestPath), "Manifest file should exist");
        Manifest manifest = new Manifest(Files.newInputStream(manifestPath));
        String bundle = manifest.getMainAttributes().getValue("OpenIDE-Module-Localizing-Bundle");
        assertNotNull(bundle, "Manifest should declare bundle");
        Path bundlePath = Paths.get("src/main/resources").resolve(bundle);
        assertTrue(Files.exists(bundlePath), "Referenced bundle should exist");
    }
}
