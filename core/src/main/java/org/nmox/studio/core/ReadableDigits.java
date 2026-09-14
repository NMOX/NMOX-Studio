package org.nmox.studio.core;

import java.util.Locale;
import org.nmox.studio.core.util.UiLocale;
import org.openide.modules.OnStart;

/**
 * Moves the JVM's starting locale onto Latin digits when its language would
 * otherwise format numbers in another script (v2.152.0).
 *
 * <p>The launcher's {@code --locale} is a language and country, nothing else,
 * so the numbering keyword cannot travel in the conf; and a first launch on an
 * Arabic desktop reaches the product through the system locale with no conf at
 * all. Both paths meet here, once, before any window is built. A live switch
 * goes through {@link UiLocale#toLocale}, which applies the same rule.
 */
@OnStart
public class ReadableDigits implements Runnable {

    @Override
    public void run() {
        Locale now = Locale.getDefault();
        Locale readable = UiLocale.readableDigits(now);
        if (!readable.equals(now)) {
            Locale.setDefault(readable);
        }
    }
}
