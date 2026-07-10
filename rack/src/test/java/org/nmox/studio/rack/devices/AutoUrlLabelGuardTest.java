package org.nmox.studio.rack.devices;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guard that de-fanged the Windows flake in {@link AutoUrlGateTest}:
 * VITALS/BEACON reflect an auto pick on the URL LCD as "auto: &lt;url&gt;",
 * but that write is a cosmetic reflection that is marshalled to the EDT —
 * so it can land AFTER the LCD has been explicitly dialed. The pre-fix code
 * wrote the label unconditionally, so a stale auto value could overwrite a
 * URL the user just typed (or a patch applied). {@code effectiveUrl()}'s
 * mutating read was the smell.
 *
 * <p>{@link AutoUrl#autoLabelOrKeep} is the fix as a pure, re-checkable
 * function: it returns the label only while the LCD is still an auto
 * candidate, and {@code null} (keep the glass) once it holds an explicit
 * dial. Testing the guard as a pure function is what makes the "explicit
 * dial survives a late auto-write" guarantee provable — the integration
 * path can't force a marshalled write to observe the explicit dial through
 * EDT ordering, but the guard's decision is deterministic in isolation.
 */
class AutoUrlLabelGuardTest {

    // BEACON's factory default; VITALS uses a different one — the guard is
    // parameterized on it, so both devices share this behavior.
    private static final String BEACON_DEFAULT = "https://example.com";
    private static final String VITALS_DEFAULT = "http://localhost:5173";
    private static final String AUTO = "http://localhost:4242";

    @Test
    @DisplayName("an explicitly dialed URL is left untouched — the auto label is never applied over it")
    void explicitDialIsKept() {
        // This is the regression: pre-fix, effectiveUrl() wrote
        // "auto: <auto>" unconditionally, clobbering the explicit dial.
        assertThat(AutoUrl.autoLabelOrKeep("https://prod.example.io", BEACON_DEFAULT, AUTO))
                .as("a URL the user typed must survive a late auto-write")
                .isNull();
        assertThat(AutoUrl.autoLabelOrKeep("http://localhost:9000/dashboard", VITALS_DEFAULT, AUTO))
                .isNull();
    }

    @Test
    @DisplayName("the factory default is an auto candidate — it takes the label")
    void factoryDefaultTakesLabel() {
        assertThat(AutoUrl.autoLabelOrKeep(BEACON_DEFAULT, BEACON_DEFAULT, AUTO))
                .isEqualTo("auto: " + AUTO);
        assertThat(AutoUrl.autoLabelOrKeep(VITALS_DEFAULT, VITALS_DEFAULT, AUTO))
                .isEqualTo("auto: " + AUTO);
    }

    @Test
    @DisplayName("a blank LCD is an auto candidate — it takes the label")
    void blankTakesLabel() {
        assertThat(AutoUrl.autoLabelOrKeep("", BEACON_DEFAULT, AUTO))
                .isEqualTo("auto: " + AUTO);
        assertThat(AutoUrl.autoLabelOrKeep("   ", BEACON_DEFAULT, AUTO))
                .as("whitespace is trimmed before the auto check")
                .isEqualTo("auto: " + AUTO);
    }

    @Test
    @DisplayName("a prior auto pick re-labels to the fresh pick — auto stays auto across serves")
    void priorAutoRelabels() {
        assertThat(AutoUrl.autoLabelOrKeep("auto: http://localhost:4242", BEACON_DEFAULT,
                "http://localhost:5001"))
                .as("a stale auto pick re-reads the registry rather than freezing")
                .isEqualTo("auto: http://localhost:5001");
    }

    @Test
    @DisplayName("the OTHER device's default is NOT an auto candidate here — no cross-device relabel")
    void foreignDefaultIsExplicit() {
        // VITALS' default typed into BEACON is a real, explicit URL for BEACON.
        assertThat(AutoUrl.autoLabelOrKeep(VITALS_DEFAULT, BEACON_DEFAULT, AUTO))
                .isNull();
    }
}
