package org.nmox.studio.rack.devices;

import java.io.File;
import org.nmox.studio.rack.service.ServingRegistry;

/**
 * URL auto-resolution for the quality gates (VITALS, BEACON): when the
 * URL LCD is blank, still the factory default, or showing a previous
 * auto-pick, RUN aims at whatever WEB server the {@link ServingRegistry}
 * says is live for the aimed project — the LCD then reads
 * "auto: &lt;url&gt;" so the pick is visible. An explicitly dialed URL
 * always wins; the auto text itself stays an auto candidate, so the next
 * RUN re-reads the registry instead of freezing on a stale pick.
 */
final class AutoUrl {

    static final String AUTO_PREFIX = "auto: ";

    private AutoUrl() {
    }

    /** True when the dialed text is open to auto-resolution. */
    static boolean isAuto(String dialed, String factoryDefault) {
        return dialed.isEmpty() || dialed.equals(factoryDefault)
                || dialed.startsWith(AUTO_PREFIX);
    }

    /**
     * The label the URL LCD should show for an auto pick, or {@code null}
     * to leave the glass untouched. Pure and re-checkable: showing the
     * "auto: &lt;url&gt;" label is only ever a cosmetic reflection of the
     * pick, so it must be gated on the LCD STILL being an auto candidate.
     * The device labels through this guard on the EDT, so a value that was
     * an auto candidate when RUN read it but has since been explicitly
     * dialed (the user typed a URL, a patch applied one) is left alone —
     * a deferred label write can never clobber an explicit dial.
     */
    static String autoLabelOrKeep(String currentLcdText, String factoryDefault, String auto) {
        return isAuto(currentLcdText.trim(), factoryDefault) ? AUTO_PREFIX + auto : null;
    }

    /** The first live WEB serving for this project, or null. */
    static String firstWebServing(File projectDir) {
        for (ServingRegistry.Serving s : ServingRegistry.getDefault().snapshot()) {
            if (s.kind() == ServingRegistry.Kind.WEB && s.projectDir().equals(projectDir)) {
                return s.url();
            }
        }
        return null;
    }

    /**
     * What an auto candidate falls back to when nothing is serving: a
     * stale "auto: x" strips to its last pick, anything else (blank, the
     * factory default) stays as dialed — exactly the pre-auto behavior.
     */
    static String fallback(String dialed) {
        return dialed.startsWith(AUTO_PREFIX)
                ? dialed.substring(AUTO_PREFIX.length()) : dialed;
    }
}
