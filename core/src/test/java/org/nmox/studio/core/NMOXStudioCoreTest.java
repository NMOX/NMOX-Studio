package org.nmox.studio.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for NMOXStudioCore module installer.
 */
class NMOXStudioCoreTest {

    private NMOXStudioCore core;

    @BeforeEach
    void setUp() {
        core = new NMOXStudioCore();
    }

    @AfterEach
    void tearDown() {
        // Clear system properties
        System.clearProperty("nmox.studio.version");
        System.clearProperty("nmox.studio.name");
    }

    @Test
    void testGetInstance() {
        assertThat(NMOXStudioCore.getInstance()).isNotNull();
        assertThat(NMOXStudioCore.getInstance()).isSameAs(core);
    }

    @Test
    void testInitialization() {
        core.restored();
        
        assertThat(System.getProperty("nmox.studio.version")).isEqualTo("1.0.0");
        assertThat(core.getVersion()).isEqualTo("1.0.0");
        assertThat(core.getName()).isNotEmpty();
    }

    @Test
    void testValidation() {
        assertThatCode(() -> core.validate()).doesNotThrowAnyException();
    }

    @Test
    void testClosing() {
        boolean result = core.closing();
        assertThat(result).isTrue();
    }

    @Test
    void testGetVersionWithoutInitialization() {
        String version = core.getVersion();
        assertThat(version).isEqualTo("unknown");
    }
}