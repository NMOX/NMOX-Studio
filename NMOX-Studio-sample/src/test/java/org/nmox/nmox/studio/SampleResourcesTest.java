package org.nmox.nmox.studio;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.Manifest;

import org.junit.jupiter.api.Test;

/**
 * Tests for sample module resources.
 */
public class SampleResourcesTest {

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
