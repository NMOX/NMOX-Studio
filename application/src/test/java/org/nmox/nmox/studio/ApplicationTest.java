package org.nmox.nmox.studio;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * Simple smoke tests for the application module.
 *
 * <p>This replaces the old NbModuleSuite based test with a lightweight
 * JUnit 5 test. It simply ensures that dependent modules are available.
 * Additional integration tests can be added here as the application grows.</p>
 */
public class ApplicationTest {

    @Test
    void brandingModulePresent() {
        Path manifest = Paths.get("../branding/src/main/nbm/manifest.mf");
        assertTrue(Files.exists(manifest), "Branding module manifest should exist");
    }
}
