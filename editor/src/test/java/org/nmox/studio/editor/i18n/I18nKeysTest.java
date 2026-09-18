package org.nmox.studio.editor.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every lookup shape in the brief's table for {@code keyPrefix} and
 * {@code keySpanAt}, and the refusals: {@code fetch('/api')} is a URL,
 * a {@code t(} with no quote yet offers nothing, a template with
 * {@code ${} is dynamic, and a string that merely mentions a key is
 * prose.
 */
class I18nKeysTest {

    @Test
    @DisplayName("keyPrefix: the call shapes — t, $t, i18n.t, this.$tc, $_, _, $format — with every quote")
    void callShapes() {
        assertThat(I18nKeys.keyPrefix("t('app.ti")).isEqualTo("app.ti");
        assertThat(I18nKeys.keyPrefix("$t(\"nav.")).isEqualTo("nav.");
        assertThat(I18nKeys.keyPrefix("i18n.t(`")).isEqualTo("");
        assertThat(I18nKeys.keyPrefix("this.$tc('items")).isEqualTo("items");
        assertThat(I18nKeys.keyPrefix("$_('page.title")).isEqualTo("page.title");
        assertThat(I18nKeys.keyPrefix("_('hi")).isEqualTo("hi");
        assertThat(I18nKeys.keyPrefix("$format( 'x")).isEqualTo("x");
        assertThat(I18nKeys.keyPrefix("t('common:tit")).as("a namespaced key keeps its colon").isEqualTo("common:tit");
        assertThat(I18nKeys.keyPrefix("t('kebab-ca")).isEqualTo("kebab-ca");
    }

    @Test
    @DisplayName("keyPrefix: attributes and components — data-i18n, i18nKey, keypath, v-t, FormattedMessage id, formatMessage/defineMessages id")
    void attributeShapes() {
        assertThat(I18nKeys.keyPrefix("<h1 data-i18n=\"app.")).isEqualTo("app.");
        assertThat(I18nKeys.keyPrefix("<Trans i18nKey='trans.")).isEqualTo("trans.");
        assertThat(I18nKeys.keyPrefix("<i18n-t keypath=\"")).isEqualTo("");
        assertThat(I18nKeys.keyPrefix("<p v-t=\"'vue.")).isEqualTo("vue.");
        assertThat(I18nKeys.keyPrefix("<FormattedMessage id=\"fm.")).isEqualTo("fm.");
        assertThat(I18nKeys.keyPrefix("<FormattedMessage\n  defaultMessage=\"x\"\n  id=\"fm.")).isEqualTo("fm.");
        assertThat(I18nKeys.keyPrefix("intl.formatMessage({ id: 'fm.")).isEqualTo("fm.");
        assertThat(I18nKeys.keyPrefix("defineMessages({ a: { id: \"dm.")).isEqualTo("dm.");
    }

    @Test
    @DisplayName("keyPrefix: Paraglide's m.<identifier> has no quote at all")
    void paraglideShape() {
        assertThat(I18nKeys.keyPrefix("const x = m.hello_wo")).isEqualTo("hello_wo");
        assertThat(I18nKeys.keyPrefix("{m.")).isEqualTo("");
        assertThat(I18nKeys.keyPrefix("t('m.x")).as("a quoted key that starts with m. is a key").isEqualTo("m.x");
        assertThat(I18nKeys.keyPrefix("foo.m.x")).as("someone else's m").isNull();
    }

    @Test
    @DisplayName("keyPrefix refusals: a URL, a bare paren, a template with ${}, an id outside its descriptor, prose")
    void refusals() {
        assertThat(I18nKeys.keyPrefix("fetch('/api")).isNull();
        assertThat(I18nKeys.keyPrefix("fetch('app.")).isNull();
        assertThat(I18nKeys.keyPrefix("t(")).isNull();
        assertThat(I18nKeys.keyPrefix("t(app.")).isNull();
        assertThat(I18nKeys.keyPrefix("t(`errors.${")).isNull();
        assertThat(I18nKeys.keyPrefix("t(`errors.${code}`")).isNull();
        assertThat(I18nKeys.keyPrefix("const other = { id: 'not.")).isNull();
        assertThat(I18nKeys.keyPrefix("intl.formatMessage({ id: 'a' }); const y = { id: 'not.")).isNull();
        assertThat(I18nKeys.keyPrefix("<div id=\"not.")).isNull();
        assertThat(I18nKeys.keyPrefix("<FormattedMessage id=\"a\"/> <div id=\"not.")).isNull();
        assertThat(I18nKeys.keyPrefix("split('app.")).isNull();
        assertThat(I18nKeys.keyPrefix("format('app.")).as("format is not $format").isNull();
        assertThat(I18nKeys.keyPrefix("myt('app.")).as("myt is not t").isNull();
        assertThat(I18nKeys.keyPrefix("width='")).as("width is not an attribute of ours").isNull();
        assertThat(I18nKeys.keyPrefix("")).isNull();
        assertThat(I18nKeys.keyPrefix("app.title")).isNull();
    }

    @Test
    @DisplayName("keySpanAt: the whole key inside a completed lookup, in any position of the token")
    void spanInsideLookups() {
        String js = "const s = t('app.title'); x = $t(\"nav.home\", 2); y = t(`common:cart`);";
        int a = js.indexOf("app.title");
        assertThat(I18nKeys.keySpanAt(js, a)).containsExactly(a, a + 9);
        assertThat(I18nKeys.keySpanAt(js, a + 4)).containsExactly(a, a + 9);
        assertThat(I18nKeys.keySpanAt(js, a + 9)).containsExactly(a, a + 9);
        int n = js.indexOf("nav.home");
        assertThat(I18nKeys.keySpanAt(js, n + 1)).containsExactly(n, n + 8);
        int c = js.indexOf("common:cart");
        assertThat(I18nKeys.keySpanAt(js, c + 3)).containsExactly(c, c + 11);
        String html = "<h1 data-i18n=\"app.greeting\">Hi</h1>";
        int g = html.indexOf("app.greeting");
        assertThat(I18nKeys.keySpanAt(html, g + 2)).containsExactly(g, g + 12);
        String pg = "el.textContent = m.hello_world();";
        int p = pg.indexOf("hello_world");
        assertThat(I18nKeys.keySpanAt(pg, p + 3)).as("the sigil is not part of the key").containsExactly(p, p + 11);
    }

    @Test
    @DisplayName("keySpanAt refusals: prose, a URL, a template, an unterminated string, out of range")
    void spanRefusals() {
        String js = "log('app.title'); fetch('/api/x'); t(`e.${c}`); t('unterminated";
        assertThat(I18nKeys.keySpanAt(js, js.indexOf("app.title") + 2)).isNull();
        assertThat(I18nKeys.keySpanAt(js, js.indexOf("api") + 1)).isNull();
        assertThat(I18nKeys.keySpanAt(js, js.indexOf("e.$") + 1)).isNull();
        assertThat(I18nKeys.keySpanAt(js, js.indexOf("unterminated") + 3)).isNull();
        assertThat(I18nKeys.keySpanAt(js, -1)).isNull();
        assertThat(I18nKeys.keySpanAt(js, js.length() + 1)).isNull();
        assertThat(I18nKeys.keySpanAt("t('')", 3)).as("an empty key is no key").isNull();
    }

    @Test
    @DisplayName("split: ns:key becomes its two halves; a bare key has no namespace; a trailing colon is not a namespace")
    void split() {
        assertThat(I18nKeys.split("common:title")).containsExactly("common", "title");
        assertThat(I18nKeys.split("title")).containsExactly(null, "title");
        assertThat(I18nKeys.split("title:")).containsExactly(null, "title:");
        assertThat(I18nKeys.split(":x")).containsExactly(null, ":x");
    }
}
