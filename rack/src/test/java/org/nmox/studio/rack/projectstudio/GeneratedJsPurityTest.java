package org.nmox.studio.rack.projectstudio;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cross-language sweep leak, gated at the OTHER emitters
 * (v2.38.4, the evening review after v2.38.3's find): the v2.37.5
 * Turkish-I sweep rewrote {@code .toLowerCase()} inside DevTools'
 * injected-JS string and killed the DOM pane — a mechanical sweep
 * cannot tell Java from JavaScript-in-a-Java-string, so every
 * generator that EMITS JavaScript gets the same purity check
 * {@code InjectedJsPurityTest} gave the DevTools scripts. A Java-ism
 * in emitted JS is always a leak: the browser has no
 * {@code java.util.Locale}.
 */
class GeneratedJsPurityTest {

    private static final String[] JAVA_ISMS = {
        "java.util.", "java.lang.", "Locale.ROOT", "StandardCharsets",
    };

    private static void assertPure(String what, String js) {
        for (String ism : JAVA_ISMS) {
            assertThat(js)
                    .as("%s emits JavaScript — \"%s\" in it is a cross-language sweep leak", what, ism)
                    .doesNotContain(ism);
        }
    }

    @Test
    @DisplayName("the I18n Kit's helper is pure JS")
    void i18nHelper() {
        assertPure("I18nKit.helper()", I18nKit.helper());
    }

    @Test
    @DisplayName("the PWA Kit's service worker is pure JS, both strategies")
    void pwaServiceWorker() {
        for (PwaKit.Strategy s : PwaKit.Strategy.values()) {
            assertPure("PwaKit.serviceWorker(" + s + ")",
                    PwaKit.serviceWorker(List.of("icon-192.png"), s));
        }
    }

    @Test
    @DisplayName("Block Studio's generated component is pure JS")
    void blockCodegen() {
        var doc = new org.nmox.studio.rack.blockstudio.BlockDoc();
        doc.root().setParam("tag", "probe-widget");
        var state = doc.create(org.nmox.studio.rack.blockstudio.BlockKind.STATE);
        state.setParam("name", "count");
        state.setParam("initial", "0");
        doc.insert(doc.root(), state, 0);
        var result = org.nmox.studio.rack.blockstudio.BlockCodegen.generate(doc);
        assertPure("BlockCodegen.generate", result.code());
    }
}
