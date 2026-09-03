package org.nmox.studio.core.util;

import java.util.function.Supplier;

/**
 * The product version the running install carries, read the one way a
 * MODULE can actually see it. The branded "currentVersion" lives in the
 * platform's startup bundle (org.netbeans.core.startup.Bundle, branded
 * by the branding module); a module's own classloader cannot load that
 * bundle — core.startup is not a dependency of anything here — so every
 * {@code ResourceBundle.getBundle(...)} read of it from a module threw
 * MissingResourceException and fell back to null. Measured v2.67.0: the
 * Welcome footer read plain "NMOX Studio" in every shipped build, What's
 * New never recorded a version, Report a Problem said "dev build", and
 * UpdateCheck's daily notifier had skipped as "dev" since v1.25.0.
 *
 * <p>The honest reads, in order: the {@code netbeans.productversion}
 * system property when the launcher sets it, else the branded bundle
 * through the SYSTEM classloader (the one the platform hands out via
 * Lookup — it sees every module and the branding overlay), else null.
 * Dev builds carry "NMOX Studio 1.0" and stay dev by {@link Versions#isStamped}.
 */
public final class ProductVersion {

    /** The branded startup bundle and its key — spelled here and nowhere else. */
    static final String STARTUP_BUNDLE = "org.netbeans.core.startup.Bundle";
    static final String CURRENT_VERSION = "currentVersion";

    private ProductVersion() {
    }

    /** The full branded string ("NMOX Studio 2.67.0"), or null when unbranded. */
    public static String current() {
        return current(() -> System.getProperty("netbeans.productversion"), ProductVersionBundle::read);
    }

    /** The version number alone ("2.67.0", or "1.0" on a dev build), or null. */
    public static String number() {
        return Versions.extract(current());
    }

    /** Whether this is a stamped release build. */
    public static boolean stamped() {
        return Versions.isStamped(number());
    }

    /** The seam: property first, then the bundle, blanks treated as absent. */
    static String current(Supplier<String> property, Supplier<String> bundle) {
        String p = safe(property);
        if (p != null && !p.isBlank()) {
            return p.strip();
        }
        String b = safe(bundle);
        return b == null || b.isBlank() ? null : b.strip();
    }

    private static String safe(Supplier<String> s) {
        try {
            return s.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

}
