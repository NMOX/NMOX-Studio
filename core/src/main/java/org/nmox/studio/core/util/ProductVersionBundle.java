package org.nmox.studio.core.util;

import java.util.Locale;
import java.util.MissingResourceException;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * The platform half of {@link ProductVersion}: the branded startup bundle
 * read through the SYSTEM classloader (the one the platform hands out via
 * Lookup — it sees every module and the branding overlay), which a
 * module's own loader cannot do. Excluded from coverage at the root pom
 * with that reason: it needs a running platform; the ordering and
 * absence rules it feeds are the measured seam in ProductVersion.
 */
final class ProductVersionBundle {

    private ProductVersionBundle() {
    }

    /** The branded "currentVersion", or null when the bundle cannot be seen. */
    static String read() {
        ClassLoader system = Lookup.getDefault().lookup(ClassLoader.class);
        ClassLoader loader = system != null ? system : ProductVersionBundle.class.getClassLoader();
        try {
            return NbBundle.getBundle(ProductVersion.STARTUP_BUNDLE, Locale.getDefault(), loader)
                    .getString(ProductVersion.CURRENT_VERSION);
        } catch (MissingResourceException missing) {
            return null;
        }
    }
}
