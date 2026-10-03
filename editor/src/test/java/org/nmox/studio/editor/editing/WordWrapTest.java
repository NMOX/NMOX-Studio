package org.nmox.studio.editor.editing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import javax.swing.JTextArea;
import javax.swing.text.JTextComponent;
import javax.swing.text.PlainDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toggle Word Wrap writes the platform's own per-language setting, for the
 * language of the editor in hand and no other, and says what it did.
 */
class WordWrapTest {

    /** Mime preferences as the platform keeps them: a node per mime, inheriting from the all-languages node. */
    private static final class Store {

        final Map<String, Map<String, String>> nodes = new HashMap<>();

        Preferences of(String mime) {
            Map<String, String> own = nodes.computeIfAbsent(mime, m -> new HashMap<>());
            Map<String, String> all = nodes.computeIfAbsent("", m -> new HashMap<>());
            return new AbstractPreferences(null, "") {
                @Override
                protected void putSpi(String key, String value) {
                    own.put(key, value);
                }

                @Override
                protected String getSpi(String key) {
                    return own.containsKey(key) ? own.get(key) : all.get(key);
                }

                @Override
                protected void removeSpi(String key) {
                    own.remove(key);
                }

                @Override
                protected void removeNodeSpi() {
                }

                @Override
                protected String[] keysSpi() {
                    return own.keySet().toArray(String[]::new);
                }

                @Override
                protected String[] childrenNamesSpi() {
                    return new String[0];
                }

                @Override
                protected AbstractPreferences childSpi(String name) {
                    return null;
                }

                @Override
                protected void syncSpi() {
                }

                @Override
                protected void flushSpi() {
                }
            };
        }

        /** What an editor of {@code mime} reads: the document's lazy property, in miniature. */
        Supplier<Object> effective(String mime) {
            return () -> of(mime).get(WordWrap.KEY, "none");
        }
    }

    /** A document whose line-wrap property is computed from the store, as NbEditorDocument's is. */
    @SuppressWarnings("serial")
    private static final class LazyDoc extends PlainDocument {

        int pokes;

        LazyDoc(String mime, Supplier<Object> wrap) {
            // the same shape as BaseDocument's own property map: a get that
            // computes, and a put that keeps the handler and counts the poke
            java.util.Hashtable<Object, Object> lazy = new java.util.Hashtable<>() {
                @Override
                public synchronized Object get(Object key) {
                    return WordWrap.KEY.equals(key) ? wrap.get() : super.get(key);
                }

                @Override
                public synchronized Object put(Object key, Object value) {
                    if (WordWrap.KEY.equals(key)) {
                        pokes++;
                        return null;
                    }
                    return super.put(key, value);
                }
            };
            java.util.Dictionary<Object, Object> old = getDocumentProperties();
            for (java.util.Enumeration<Object> keys = old.keys(); keys.hasMoreElements();) {
                Object key = keys.nextElement();
                lazy.put(key, old.get(key));
            }
            setDocumentProperties(lazy);
            putProperty("mimeType", mime);
        }
    }

    private final Store store = new Store();
    private final List<String> said = new ArrayList<>();
    private Function<String, Preferences> realPrefs;
    private Supplier<List<? extends JTextComponent>> realEditors;
    private Consumer<String> realStatus;

    @BeforeEach
    void seams() {
        realPrefs = ToggleWordWrapAction.prefs;
        realEditors = ToggleWordWrapAction.editors;
        realStatus = ToggleWordWrapAction.status;
        ToggleWordWrapAction.prefs = store::of;
        ToggleWordWrapAction.status = said::add;
    }

    @AfterEach
    void restore() {
        ToggleWordWrapAction.prefs = realPrefs;
        ToggleWordWrapAction.editors = realEditors;
        ToggleWordWrapAction.status = realStatus;
    }

    @Test
    @DisplayName("which values wrap: after words and anywhere do, none and nothing do not")
    void whichValuesWrap() {
        assertThat(WordWrap.isOn("words")).isTrue();
        assertThat(WordWrap.isOn("chars")).isTrue();
        assertThat(WordWrap.isOn("none")).isFalse();
        assertThat(WordWrap.isOn("")).isFalse();
        assertThat(WordWrap.isOn(null)).isFalse();
    }

    @Test
    @DisplayName("the toggle writes the language of the editor in hand, and no other language")
    void togglesTheRightMime() {
        int[] refreshed = {0};
        WordWrap.Result on = WordWrap.toggle("text/x-markdown", store.effective("text/x-markdown"),
                store::of, () -> refreshed[0]++);
        assertThat(on).isEqualTo(new WordWrap.Result("text/x-markdown", true, true));
        assertThat(store.nodes.get("text/x-markdown")).containsEntry(WordWrap.KEY, "words");
        assertThat(store.of("text/typescript").get(WordWrap.KEY, "none")).isEqualTo("none");
        assertThat(store.nodes.get("")).doesNotContainKey(WordWrap.KEY);
        assertThat(refreshed[0]).isEqualTo(1);
    }

    @Test
    @DisplayName("switching back to what the language inherits removes the override instead of writing one")
    void backToInheritedRemovesTheOverride() {
        WordWrap.toggle("text/x-markdown", store.effective("text/x-markdown"), store::of, () -> { });
        WordWrap.Result off = WordWrap.toggle("text/x-markdown", store.effective("text/x-markdown"),
                store::of, () -> { });
        assertThat(off).isEqualTo(new WordWrap.Result("text/x-markdown", false, true));
        assertThat(store.nodes.get("text/x-markdown")).doesNotContainKey(WordWrap.KEY);
    }

    @Test
    @DisplayName("where every language wraps, switching one off is an override, and anywhere-wrap switches off too")
    void offAgainstAGlobalOn() {
        store.of("").put(WordWrap.KEY, "chars");
        WordWrap.Result off = WordWrap.toggle("text/typescript", store.effective("text/typescript"),
                store::of, () -> { });
        assertThat(off.on()).isFalse();
        assertThat(off.took()).isTrue();
        assertThat(store.nodes.get("text/typescript")).containsEntry(WordWrap.KEY, "none");
        assertThat(store.of("text/css").get(WordWrap.KEY, "none")).isEqualTo("chars");
    }

    @Test
    @DisplayName("a file that takes its wrap from elsewhere is reported as unchanged, not as wrapped")
    void aProjectThatOwnsTheSetting() {
        // the editor reads a project's own formatting settings, which the toggle does not write
        WordWrap.Result r = WordWrap.toggle("text/typescript", () -> "none", store::of, () -> { });
        assertThat(r.on()).isTrue();
        assertThat(r.took()).isFalse();
    }

    @Test
    @DisplayName("a press in an editor: the language's setting flips, every open editor is told, and the scope is said")
    void pressInAnEditor() {
        LazyDoc ts = new LazyDoc("text/typescript", store.effective("text/typescript"));
        LazyDoc md = new LazyDoc("text/x-markdown", store.effective("text/x-markdown"));
        JTextArea tsEditor = new JTextArea(ts);
        JTextArea mdEditor = new JTextArea(md);
        JTextArea plain = new JTextArea("not a code editor");
        ToggleWordWrapAction.editors = () -> List.of(tsEditor, mdEditor, plain);

        assertThat(ToggleWordWrapAction.wrapsNow(tsEditor)).isFalse();
        ToggleWordWrapAction.press(tsEditor);

        assertThat(ToggleWordWrapAction.wrapsNow(tsEditor)).isTrue();
        assertThat(ToggleWordWrapAction.wrapsNow(mdEditor)).isFalse();
        assertThat(ts.pokes).isEqualTo(1);
        assertThat(md.pokes).isEqualTo(1);
        // a document nothing reads the setting from is left alone
        assertThat(plain.getDocument().getProperty(WordWrap.KEY)).isNull();
        assertThat(said).containsExactly("Word wrap is on for every typescript editor");

        ToggleWordWrapAction.press(tsEditor);
        assertThat(ToggleWordWrapAction.wrapsNow(tsEditor)).isFalse();
        assertThat(said).last().isEqualTo("Word wrap is off for every typescript editor");
    }

    @Test
    @DisplayName("the chord's kit action is the same press, and an Alt chord arms the typed-echo guard")
    void theKeyActionIsTheSamePress() {
        LazyDoc ts = new LazyDoc("text/typescript", store.effective("text/typescript"));
        JTextArea editor = new JTextArea(ts);
        ToggleWordWrapAction.editors = () -> List.of(editor);
        int before = editor.getKeyListeners().length;
        ToggleWordWrapKeyAction key = new ToggleWordWrapKeyAction();
        key.actionPerformed(new java.awt.event.ActionEvent(editor, java.awt.event.ActionEvent.ACTION_PERFORMED,
                ToggleWordWrapKeyAction.NAME, java.awt.event.ActionEvent.ALT_MASK), editor);
        assertThat(ToggleWordWrapAction.wrapsNow(editor)).isTrue();
        assertThat(editor.getKeyListeners()).hasSize(before + 1);
        assertThat(said).containsExactly("Word wrap is on for every typescript editor");
        key.actionPerformed(null, null);
        assertThat(said).hasSize(1);
    }

    @Test
    @DisplayName("the View-menu row is a checkbox that reads the editor in hand, and with no editor it says so")
    void theMenuRow() {
        ToggleWordWrapAction.editors = List::of;
        ToggleWordWrapAction action = new ToggleWordWrapAction();
        assertThat(action.getValue(javax.swing.Action.NAME)).isEqualTo("Word Wrap");
        javax.swing.JMenuItem item = action.getMenuPresenter();
        assertThat(item).isInstanceOf(javax.swing.JCheckBoxMenuItem.class);
        assertThat(item.getText()).isEqualTo("Word Wrap");
        // the checkbox reports the editor last typed in (in a test run, usually none)
        assertThat(item.isSelected()).isEqualTo(
                ToggleWordWrapAction.wrapsNow(org.netbeans.api.editor.EditorRegistry.lastFocusedComponent()));
        assertThat(item.getToolTipText()).contains("language").contains("saved");
        action.actionPerformed(null);
        assertThat(said).as("a press always says what it did, or why it did nothing").hasSize(1);
    }

    @Test
    @DisplayName("no editor, or a text component that is not a code editor, is refused out loud")
    void noEditorIsRefused() {
        ToggleWordWrapAction.editors = List::of;
        ToggleWordWrapAction.press(null);
        ToggleWordWrapAction.press(new JTextArea("plain"));
        assertThat(said).hasSize(2).allMatch(s -> s.startsWith("Word Wrap needs a code editor"));
        assertThat(store.nodes).isEmpty();
        assertThat(ToggleWordWrapAction.wrapsNow(null)).isFalse();
    }

    @Test
    @DisplayName("an editor whose project owns the setting hears that nothing changed")
    void unchangedIsSaid() {
        LazyDoc owned = new LazyDoc("text/typescript", () -> "none");
        ToggleWordWrapAction.editors = List::of;
        ToggleWordWrapAction.press(new JTextArea(owned));
        assertThat(said).hasSize(1);
        assertThat(said.get(0)).startsWith("Word wrap did not change").contains("the typescript editor settings")
                .doesNotContain("text/typescript");
    }

    @Test
    @DisplayName("the status line names a language by its VS Code id, and by its MIME type only where it has none")
    void theLanguageIsNamed() {
        assertThat(ToggleWordWrapAction.languageName("text/typescript")).isEqualTo("typescript");
        assertThat(ToggleWordWrapAction.languageName("text/x-python")).isEqualTo("python");
        assertThat(ToggleWordWrapAction.languageName("plain")).as("no id: the mime as it is").isEqualTo("plain");
    }
}
