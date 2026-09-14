package org.nmox.studio.application;

import java.util.List;
import org.nmox.studio.core.util.UiLocale;

/**
 * The translated languages the product ships, derived from the one list the
 * Options dialog offers.
 *
 * <p>Until v2.151.0 eleven gates each spelled the twelve translated languages
 * out by hand. Adding Hebrew meant finding and editing all eleven, and a gate
 * nobody remembered would have gone on checking twelve languages while the
 * product shipped fourteen, green the whole time. The population of a gate is
 * a fact the product already states, so the gates read it from there
 * (v2.131.0: the defect is the second home, not the disagreement).
 */
final class ShippedLocales {

    /** Every language with translated bundles: {@code UiLocale.SUPPORTED} less System and English. */
    static final List<String> TRANSLATED = UiLocale.SUPPORTED.stream()
            .map(UiLocale.Choice::code)
            .filter(code -> !code.isEmpty() && !code.equals("en"))
            .toList();

    private ShippedLocales() {
    }
}
