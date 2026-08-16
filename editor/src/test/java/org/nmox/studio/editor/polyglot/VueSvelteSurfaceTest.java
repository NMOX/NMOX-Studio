package org.nmox.studio.editor.polyglot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.nmox.studio.editor.grammars.SvelteEditorKit;
import org.nmox.studio.editor.grammars.VueEditorKit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v2.14.0 Vue+Svelte surface, pinned (David: "make sure we're top
 * notch with those"):
 *
 * <ul>
 *   <li>both component mimes have EDITOR KITS — the Angular-top arc's
 *       JS/TS finding one mime over: without a kit whose
 *       getContentType() names the mime, the pane's keymap resolves NO
 *       mime-registered action and every chord is dead;</li>
 *   <li>Vue reaches typing/vocabulary parity with what Svelte got in
 *       v1.207.0 — auto-pairs, directives, the Composition API;</li>
 *   <li>Emmet's ⌥⌘E covers both SFC mimes, chord bound in all five
 *       keymap profiles (the v2.3.0 parity law).</li>
 * </ul>
 */
class VueSvelteSurfaceTest {

    // The platform's registration annotations are SOURCE-retained —
    // reflection sees nothing at runtime — so these gates read the
    // source files, the house wiring-gate idiom.

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/org/nmox/studio/editor/" + path))
                .replace("\r\n", "\n");
    }

    @Test
    @DisplayName("both component mimes have kits registered for EditorKit.class")
    void editorKitsRegistered() throws Exception {
        assertThat(new VueEditorKit().getContentType()).isEqualTo("text/x-vue");
        assertThat(new SvelteEditorKit().getContentType()).isEqualTo("text/x-svelte");
        String vue = source("grammars/VueEditorKit.java");
        assertThat(vue)
                .as("the create() factory carries the EditorKit registration")
                .contains("@MimeRegistration(mimeType = \"text/x-vue\", service = EditorKit.class)");
        String svelte = source("grammars/SvelteEditorKit.java");
        assertThat(svelte)
                .contains("@MimeRegistration(mimeType = \"text/x-svelte\", service = EditorKit.class)");
    }

    @Test
    @DisplayName("both mimes have LOADERS — a kit without a DataObject is never consulted")
    void dataObjectsRegistered() throws Exception {
        // The walk's find (v2.14.0): with no @DataObject.Registration the
        // file opens through DefaultDataObject, whose editor ignores the
        // mime kit entirely — chords stayed dead WITH the kit registered.
        // registerEditor(mime, true) is the binding that makes it real.
        String vue = source("grammars/VueDataObject.java");
        assertThat(vue).contains("@DataObject.Registration(");
        assertThat(vue).contains("mimeType = \"text/x-vue\"");
        assertThat(vue).contains("registerEditor(\"text/x-vue\", true)");
        String svelte = source("grammars/SvelteDataObject.java");
        assertThat(svelte).contains("mimeType = \"text/x-svelte\"");
        assertThat(svelte).contains("registerEditor(\"text/x-svelte\", true)");
    }

    @Test
    @DisplayName("Vue joins the typed/deleted-text interceptors (auto-pair parity)")
    void vueTypingParity() throws Exception {
        assertThat(source("typing/JsTypedTextInterceptor.java"))
                .contains("mimeType = \"text/x-vue\", service = TypedTextInterceptor.Factory.class");
        assertThat(source("typing/JsDeletedTextInterceptor.java"))
                .contains("mimeType = \"text/x-vue\", service = DeletedTextInterceptor.Factory.class");
    }

    @Test
    @DisplayName("the Vue vocabulary: directives, shorthands, Composition API, macros")
    void vueKeywords() {
        Set<String> vue = PolyglotCompletionProvider.KEYWORDS.get("text/x-vue");
        assertThat(vue).as("text/x-vue carries a keyword set").isNotNull();
        assertThat(vue).contains("v-if", "v-for", "v-model", "v-slot", "v-memo");
        assertThat(vue).contains("@click", ":class", ":key");
        assertThat(vue).contains("ref", "computed", "watch", "watchEffect",
                "onMounted", "nextTick");
        assertThat(vue).contains("defineProps", "defineEmits", "defineModel",
                "useTemplateRef");
        assertThat(vue).contains("Teleport", "Suspense", "Transition", "KeepAlive");
    }

    @Test
    @DisplayName("Svelte 5 currency: the dotted rune variants complete too")
    void svelteDottedRunes() {
        Set<String> svelte = PolyglotCompletionProvider.KEYWORDS.get("text/x-svelte");
        assertThat(svelte).contains("$state.raw", "$state.snapshot",
                "$effect.pre", "$effect.tracking", "$effect.root", "$inspect.trace");
    }

    @Test
    @DisplayName("the Vue sigil walk: @click, :class and v-if prefixes are reachable")
    void vuePrefixWalk() {
        assertThat(PolyglotCompletionProvider.prefixAt("<button @cl", 11, "text/x-vue"))
                .as("@ walks into the prefix — else @click can never match")
                .isEqualTo("@cl");
        assertThat(PolyglotCompletionProvider.prefixAt("<div :cla", 9, "text/x-vue"))
                .isEqualTo(":cla");
        assertThat(PolyglotCompletionProvider.prefixAt("<div v-i", 8, "text/x-vue"))
                .as("the hyphen walks — else every directive stops at 'v'")
                .isEqualTo("v-i");
        assertThat(PolyglotCompletionProvider.prefixAt("<div v-i", 8, "text/x-python"))
                .as("other mimes keep the plain walk")
                .isEqualTo("i");
    }

    @Test
    @DisplayName("Emmet reaches both SFC mimes: action registration + all-profile chords")
    void emmetOnComponentMimes() throws Exception {
        String action = source("emmet/ExpandAbbreviationAction.java");
        assertThat(action).contains("mimeType = \"text/x-vue\"");
        assertThat(action).contains("mimeType = \"text/x-svelte\"");

        // CRLF-fold like source(): a Windows checkout otherwise breaks
        // the \n-anchored section cut below (the v1.42.0 lane's lesson)
        String layer = Files.readString(Path.of(
                "src/main/resources/org/nmox/studio/editor/layer.xml"))
                .replace("\r\n", "\n");
        for (String mime : new String[]{"x-vue", "x-svelte"}) {
            String section = layer.substring(layer.indexOf("<folder name=\"" + mime + "\">"));
            section = section.substring(0, section.indexOf("</folder>\n            </folder>") + 10);
            for (String profile : new String[]{"NetBeans", "Eclipse", "Emacs", "Idea", "NetBeans55"}) {
                assertThat(section)
                        .as("the ⌥⌘E chord rides profile %s for %s (v2.3.0 parity law)",
                                profile, mime)
                        .contains("<folder name=\"" + profile + "\">");
            }
            assertThat(section).contains("emmet-keybindings.xml");
        }
    }
}
