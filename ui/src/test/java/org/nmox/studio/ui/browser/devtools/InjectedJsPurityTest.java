package org.nmox.studio.ui.browser.devtools;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Injected JavaScript is JavaScript (v2.37.6's find): the v2.37.5
 * Turkish-I sweep mechanically rewrote {@code .toLowerCase()} to
 * {@code .toLowerCase(java.util.Locale.ROOT)} INSIDE
 * {@link DevScripts#DOM_SNAPSHOT}'s string — JS that runs in the
 * page, where {@code java} is an undefined variable — and the whole
 * DevTools DOM pane died with "ReferenceError: Can't find variable:
 * java", invisibly to every Java-side test. This gate reflects over
 * EVERY public String constant of DevScripts and refuses Java-isms
 * that can only mean a sweep or refactor leaked across the language
 * boundary; a new script constant is covered the day it is added.
 */
class InjectedJsPurityTest {

    @Test
    @DisplayName("no DevScripts constant carries a Java-ism into the page")
    void injectedScriptsArePureJavaScript() throws Exception {
        List<String> offenders = new ArrayList<>();
        int checked = 0;
        for (Field f : DevScripts.class.getDeclaredFields()) {
            if (f.getType() != String.class || !Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            f.setAccessible(true);
            String js = (String) f.get(null);
            checked++;
            for (String javaism : new String[] {
                "java.util.", "java.lang.", "Locale.ROOT", "StandardCharsets", }) {
                if (js.contains(javaism)) {
                    offenders.add(f.getName() + " contains \"" + javaism + "\"");
                }
            }
        }
        assertThat(checked).as("the reflection sweep found the script constants").isGreaterThan(3);
        assertThat(offenders)
                .as("injected JS must be pure JS — a Java identifier in a script "
                    + "string is a cross-language sweep leak")
                .isEmpty();
    }
}
