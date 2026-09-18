package org.nmox.studio.editor.i18n;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.editor.i18n.I18nUsage.Ref;
import org.nmox.studio.editor.i18n.I18nUsage.Usage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every lookup shape the reader knows is a reference; a dynamic lookup
 * is recorded as dynamic and never as a key; comments are blanked and
 * strings are not; namespaces scope by prefix and by
 * {@code useTranslation}; the walk reports whether it read everything.
 */
class I18nUsageTest {

    // The two scans below were quadratic in the first cut (the 2026-09-17 arc
    // review, hostile-input lens): 10,000 unclosed defineMessages( cost 4.8 s
    // and 30,000 keys on one newline-free line 1.4 s — per FILE, in a census
    // of up to 400. Fixed, each runs in milliseconds; the two-second leash is
    // a 100x margin over the fix and a fraction of the mutant's own cost on
    // the inputs below, so a slow runner cannot make it lie in either direction.
    private static final java.time.Duration LEASH = java.time.Duration.ofSeconds(2);

    @Test
    @DisplayName("10,000 unclosed defineMessages( in one source cost one pass, not one rescan to the end per call")
    void unclosedMessageDescriptorsAreLinear() {
        String src = "defineMessages(".repeat(10_000);
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(LEASH, () -> I18nUsage.fromSource(src));
        int[] close = I18nUsage.closers("a(b(c)d)e()(");
        assertThat(close[1]).isEqualTo(7);
        assertThat(close[3]).isEqualTo(5);
        assertThat(close[9]).isEqualTo(10);
        assertThat(close[11]).as("never closed").isEqualTo(-1);
    }

    @Test
    @DisplayName("60,000 keys on one line without a newline cost one scan of each key, not one walk back to the file's start per key")
    void manyKeysOnOneLineAreLinear() {
        String src = "t('k').".repeat(60_000);
        Usage u = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(LEASH, () -> I18nUsage.fromSource(src));
        assertThat(u.refs()).containsExactly(new Ref(null, "k"));
        assertThat(I18nUsage.fromSource("t('a\nb')").refs()).as("a quote closed on another line is still not a key").isEmpty();
    }

    @Test
    @DisplayName("the 40-char cut on a dynamic argument never splits a surrogate pair (the v1.149.0 cap class)")
    void dynamicArgumentCutKeepsPairsWhole() {
        String src = "t(x" + "a".repeat(36) + "😀😀😀)";
        Usage u = I18nUsage.fromSource(src);
        assertThat(u.dynamic()).hasSize(1);
        String d = u.dynamic().get(0);
        assertThat(Character.isHighSurrogate(d.charAt(d.length() - 1))).as("no lone high surrogate at the cut").isFalse();
        assertThat(d).startsWith("x" + "a".repeat(36) + "😀");
    }

    @Test
    @DisplayName("call shapes: t, $t, i18n.t, $tc, $_, _, $format, single/double/backtick quotes")
    void callShapes() {
        Usage u = I18nUsage.fromSource("t('a.one'); $t(\"b\"); i18n.t(`c`); this.$tc('d', 2); "
                + "$_('e'); _('f'); $format('g'); i18next.t('h', { count: 2 });");
        assertThat(u.refs()).extracting(Ref::key).containsExactlyInAnyOrder("a.one", "b", "c", "d", "e", "f", "g", "h");
        assertThat(u.refs()).allMatch(r -> r.namespace() == null);
        assertThat(u.dynamic()).isEmpty();
    }

    @Test
    @DisplayName("attributes and components: i18nKey, data-i18n, keypath, v-t, <FormattedMessage id>, formatMessage({id}), defineMessages, m.key(")
    void attributeAndComponentShapes() {
        Usage u = I18nUsage.fromSource("<Trans i18nKey=\"trans.key\"/>\n"
                + "<h1 data-i18n=\"app.title\"></h1>\n"
                + "<i18n-t keypath='vue.path'>\n"
                + "<p v-t=\"'vue.directive'\"></p>\n"
                + "<FormattedMessage id=\"fm.id\" defaultMessage=\"x\"/>\n"
                + "intl.formatMessage({ id: 'fm.call', defaultMessage: 'y' })\n"
                + "const msgs = defineMessages({ a: { id: 'dm.a' }, b: { id: \"dm.b\" } });\n"
                + "const other = { id: 'not.a.key' };\n"
                + "m.hello_world(); m.greeting({ name })");
        assertThat(u.refs()).extracting(Ref::key).containsExactlyInAnyOrder(
                "trans.key", "app.title", "vue.path", "vue.directive", "fm.id", "fm.call",
                "dm.a", "dm.b", "hello_world", "greeting");
    }

    @Test
    @DisplayName("dynamic lookups are dynamic, never keys: a template with ${}, a concatenation, a variable")
    void dynamicLookups() {
        Usage u = I18nUsage.fromSource("t(`errors.${code}`); t(prefix + '.x'); t(variable); t('plain'); t(`plain2`)");
        assertThat(u.refs()).extracting(Ref::key).containsExactlyInAnyOrder("plain", "plain2");
        assertThat(u.dynamic()).containsExactly("errors.${…}", "prefix + '.x'", "variable");
    }

    @Test
    @DisplayName("comments are blanked, strings are not — the key is inside a string; fetch() is not a lookup")
    void commentsBlankedStringsKept() {
        Usage u = I18nUsage.fromSource("// t('in.comment')\n/* t('in.block') */\n<!-- t('in.html') -->\n"
                + "const s = 'see t(\"in.string\")'; fetch('/api/t'); t('real');");
        assertThat(u.refs()).extracting(Ref::key).containsExactlyInAnyOrder("in.string", "real");
    }

    @Test
    @DisplayName("namespaces: ns:key scopes by prefix, useTranslation('ns') scopes the file's bare keys, elsewhere bare keys match any")
    void namespaceScoping() {
        Usage scoped = I18nUsage.fromSource("const { t } = useTranslation('auth'); t('login'); t('common:title');");
        assertThat(scoped.refs()).containsExactlyInAnyOrder(new Ref("auth", "login"), new Ref("common", "title"));
        assertThat(scoped.uses("auth", "login")).isTrue();
        assertThat(scoped.uses("common", "login")).isFalse();
        assertThat(scoped.uses("common", "title")).isTrue();
        Usage array = I18nUsage.fromSource("useTranslation(['a', \"b\"]); t('k');");
        assertThat(array.refs()).containsExactlyInAnyOrder(new Ref("a", "k"), new Ref("b", "k"));
        Usage bare = I18nUsage.fromSource("t('k');");
        assertThat(bare.uses("anything", "k")).isTrue();
        assertThat(bare.uses("", "k")).isTrue();
        assertThat(bare.uses(null, "other")).isFalse();
    }

    @Test
    @DisplayName("the walk reads the source family through the bounded census and merges dynamic prefixes once")
    void walkMergesFiles(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("src/pages"));
        Files.createDirectories(root.resolve("node_modules/lib"));
        Files.writeString(root.resolve("src/App.jsx"), "t('a'); t(`e.${x}`)");
        Files.writeString(root.resolve("src/pages/Home.vue"), "<template>{{ $t('b') }}</template>");
        Files.writeString(root.resolve("src/index.html"), "<p data-i18n=\"c\"></p> t(`e.${y}`)");
        Files.writeString(root.resolve("src/notes.md"), "t('never-read')");
        Files.writeString(root.resolve("node_modules/lib/x.js"), "t('never-read-either')");
        Usage u = I18nUsage.scan(root);
        assertThat(u.refs()).extracting(Ref::key).containsExactlyInAnyOrder("a", "b", "c");
        assertThat(u.dynamic()).containsExactly("e.${…}");
        assertThat(u.complete()).isTrue();
    }

    @Test
    @DisplayName("more source files than the cap: the census says it is partial")
    void walkCapIsHonest(@TempDir Path root) throws Exception {
        for (int i = 0; i < I18nUsage.MAX_SOURCE_FILES + 1; i++) {
            Files.writeString(root.resolve("f" + i + ".js"), "t('k')");
        }
        assertThat(I18nUsage.scan(root).complete()).isFalse();
    }
}
