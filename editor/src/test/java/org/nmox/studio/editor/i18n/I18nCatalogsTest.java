package org.nmox.studio.editor.i18n;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.editor.i18n.I18nCatalogs.Catalog;
import org.nmox.studio.editor.i18n.I18nCatalogs.Catalogs;
import org.nmox.studio.editor.i18n.I18nCatalogs.Entry;
import org.nmox.studio.editor.i18n.I18nCatalogs.Format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Detection picks the right format in the stated order, the source
 * locale is chosen by the stated rule and says so, keys flatten with
 * the line a click lands on, the caps are honoured and reported, and a
 * hostile XLIFF reads nothing from disk. One fixture project per
 * format, each under its own temp dir.
 */
class I18nCatalogsTest {

    private static void write(Path root, String rel, String text) throws Exception {
        Path f = root.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, text);
    }

    private static Catalog only(Catalogs c, String locale, String ns) {
        for (Catalog cat : c.ofLocale(locale)) {
            if (cat.namespace().equals(ns)) {
                return cat;
            }
        }
        throw new AssertionError("no catalog " + locale + "/" + ns + " in " + c.catalogs());
    }

    // ---- i18next --------------------------------------------------------

    @Test
    @DisplayName("i18next: public/locales/<lng>/<ns>.json, nested keys flattened with lines, namespaces per file")
    void i18nextNested(@TempDir Path root) throws Exception {
        write(root, "package.json", "{\"dependencies\":{\"i18next\":\"^23\",\"react-i18next\":\"^14\"}}");
        write(root, "public/locales/en/common.json", "{\n"
                + "  \"app\": {\n"
                + "    \"title\": \"Hello!\",\n"
                + "    \"greeting\": \"Hi {{name}}\"\n"
                + "  },\n"
                + "  \"items_one\": \"{{count}} item\",\n"
                + "  \"items_other\": \"{{count}} items\",\n"
                + "  \"tags\": [\"a\", \"b\"]\n"
                + "}\n");
        write(root, "public/locales/en/auth.json", "{ \"login\": \"Log in\" }");
        write(root, "public/locales/de/common.json", "{ \"app\": { \"title\": \"Hallo!\" } }");
        Catalogs c = I18nCatalogs.detect(root);
        assertThat(c.format()).isEqualTo(Format.I18NEXT_NESTED);
        assertThat(c.sourceLocale()).isEqualTo("en");
        assertThat(c.sourceRule()).isEqualTo("en present");
        assertThat(c.censusComplete()).isTrue();
        assertThat(c.catalogHome()).isEqualTo("public/locales/");
        Catalog common = only(c, "en", "common");
        assertThat(common.entries().keySet()).containsExactlyInAnyOrder(
                "app.title", "app.greeting", "items_one", "items_other", "tags.0", "tags.1");
        assertThat(common.entries().get("app.title")).isEqualTo(new Entry("Hello!", 3, null));
        assertThat(common.entries().get("app.greeting").line()).isEqualTo(4);
        assertThat(common.entries().get("items_other").line()).isEqualTo(7);
        assertThat(common.entries().get("tags.1").line()).isEqualTo(8);
        assertThat(only(c, "en", "auth").entries()).containsOnlyKeys("login");
        assertThat(only(c, "de", "common").entries().get("app.title").value()).isEqualTo("Hallo!");
        assertThat(c.locales()).containsExactly("de", "en");
    }

    @Test
    @DisplayName("i18next with no en catalog: fallbackLng in the source picks the source locale, and says so")
    void i18nextFallbackLng(@TempDir Path root) throws Exception {
        write(root, "package.json", "{\"dependencies\":{\"i18next\":\"^23\"}}");
        write(root, "src/locales/ja.json", "{ \"a\": \"あ\" }");
        write(root, "src/locales/fr.json", "{ \"a\": \"a\", \"b\": \"b\" }");
        write(root, "src/i18n.ts", "i18n.init({ fallbackLng: 'ja', debug: false });");
        Catalogs c = I18nCatalogs.detect(root);
        assertThat(c.sourceLocale()).isEqualTo("ja");
        assertThat(c.sourceRule()).isEqualTo("i18next fallbackLng");
    }

    @Test
    @DisplayName("no en and no config: the largest catalog is the source, and the rule says so")
    void largestCatalogRule(@TempDir Path root) throws Exception {
        write(root, "package.json", "{\"dependencies\":{\"i18next\":\"^23\"}}");
        write(root, "locales/ja.json", "{ \"a\": \"あ\" }");
        write(root, "locales/fr.json", "{ \"a\": \"a\", \"b\": \"b\" }");
        Catalogs c = I18nCatalogs.detect(root);
        assertThat(c.sourceLocale()).isEqualTo("fr");
        assertThat(c.sourceRule()).isEqualTo("largest catalog");
    }

    // ---- vue-i18n -------------------------------------------------------

    @Test
    @DisplayName("vue-i18n: src/locales/<lng>.json nested; a .yaml catalog is refused honestly on the package.json line")
    void vueNestedAndYamlRefusal(@TempDir Path root) throws Exception {
        write(root, "package.json", "{\n  \"dependencies\": {\n    \"vue\": \"^3\",\n    \"vue-i18n\": \"^9\"\n  }\n}\n");
        write(root, "src/locales/en.json", "{ \"nav\": { \"home\": \"Home\" }, \"cars\": \"car | cars\" }");
        write(root, "src/locales/fr.json", "{ \"nav\": { \"home\": \"Accueil\" } }");
        Catalogs c = I18nCatalogs.detect(root);
        assertThat(c.format()).isEqualTo(Format.VUE_NESTED);
        assertThat(c.yamlRefusal()).isNull();
        assertThat(only(c, "fr", "").entries().get("nav.home").value()).isEqualTo("Accueil");

        Path yaml = Files.createTempDirectory(root, "yaml");
        write(yaml, "package.json", "{\n  \"dependencies\": {\n    \"vue\": \"^3\",\n    \"vue-i18n\": \"^9\"\n  }\n}\n");
        write(yaml, "src/locales/en.yaml", "nav:\n  home: Home\n");
        write(yaml, "src/locales/fr.json", "{ \"nav\": { \"home\": \"Accueil\" } }");
        Catalogs refused = I18nCatalogs.detect(yaml);
        assertThat(refused.format()).isEqualTo(Format.VUE_NESTED);
        assertThat(refused.catalogs()).as("nothing else is guessed").isEmpty();
        assertThat(refused.yamlRefusal()).isEqualTo(yaml.resolve("package.json"));
        assertThat(refused.yamlRefusalLine()).as("the vue-i18n dependency's own line").isEqualTo(4);
    }

    @Test
    @DisplayName("vue-i18n with no en: createI18n's locale option names the source")
    void vueLocaleOption(@TempDir Path root) throws Exception {
        write(root, "package.json", "{\"dependencies\":{\"vue-i18n\":\"^9\"}}");
        write(root, "src/locales/de.json", "{ \"a\": \"A\" }");
        write(root, "src/locales/fr.json", "{ \"a\": \"A\", \"b\": \"B\" }");
        write(root, "src/main.ts", "const i18n = createI18n({\n  legacy: false,\n  locale: 'de',\n});");
        Catalogs c = I18nCatalogs.detect(root);
        assertThat(c.sourceLocale()).isEqualTo("de");
        assertThat(c.sourceRule()).isEqualTo("vue-i18n locale option");
    }

    // ---- Angular --------------------------------------------------------

    private static final String XLF12_SOURCE = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
            + "<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\">\n"
            + "  <file source-language=\"en\" datatype=\"plaintext\" original=\"ng2.template\">\n"
            + "    <body>\n"
            + "      <trans-unit id=\"greeting\" datatype=\"html\">\n"
            + "        <source>Hello <x id=\"INTERPOLATION\" equiv-text=\"{{name}}\"/></source>\n"
            + "      </trans-unit>\n"
            + "      <trans-unit id=\"7a1b\" datatype=\"html\">\n"
            + "        <source>Sign out</source>\n"
            + "      </trans-unit>\n"
            + "      <trans-unit id=\"farewell\" datatype=\"html\">\n"
            + "        <source>Goodbye</source>\n"
            + "      </trans-unit>\n"
            + "    </body>\n"
            + "  </file>\n"
            + "</xliff>\n";

    private static final String XLF12_DE = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
            + "<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\">\n"
            + "  <file source-language=\"en\" target-language=\"de\" datatype=\"plaintext\" original=\"ng2.template\">\n"
            + "    <body>\n"
            + "      <trans-unit id=\"greeting\" datatype=\"html\">\n"
            + "        <source>Hello <x id=\"INTERPOLATION\" equiv-text=\"{{name}}\"/></source>\n"
            + "        <target state=\"translated\">Hallo <x id=\"INTERPOLATION\" equiv-text=\"{{name}}\"/></target>\n"
            + "      </trans-unit>\n"
            + "      <trans-unit id=\"7a1b\" datatype=\"html\">\n"
            + "        <source>Sign out</source>\n"
            + "        <target state=\"new\">Sign out</target>\n"
            + "      </trans-unit>\n"
            + "      <trans-unit id=\"farewell\" datatype=\"html\">\n"
            + "        <source>Goodbye</source>\n"
            + "      </trans-unit>\n"
            + "      <trans-unit id=\"gone\" datatype=\"html\">\n"
            + "        <source>Old</source>\n"
            + "        <target>Alt</target>\n"
            + "      </trans-unit>\n"
            + "    </body>\n"
            + "  </file>\n"
            + "</xliff>\n";

    @Test
    @DisplayName("Angular XLIFF 1.2: the extract is the source, targets carry state, inline x renders its equiv-text")
    void angularXliff12(@TempDir Path root) throws Exception {
        write(root, "angular.json", "{\"projects\":{\"app\":{\"i18n\":{\"sourceLocale\":\"en-US\","
                + "\"locales\":{\"de\":\"src/locale/messages.de.xlf\"}}}}}");
        write(root, "src/locale/messages.xlf", XLF12_SOURCE);
        write(root, "src/locale/messages.de.xlf", XLF12_DE);
        Catalogs c = I18nCatalogs.detect(root);
        assertThat(c.format()).isEqualTo(Format.ANGULAR_XLIFF);
        assertThat(c.sourceLocale()).isEqualTo("en-US");
        assertThat(c.sourceRule()).isEqualTo("angular.json sourceLocale");
        Map<String, Entry> source = only(c, "en-US", "").entries();
        assertThat(source.get("greeting")).isEqualTo(new Entry("Hello {{name}}", 5, null));
        assertThat(source.get("7a1b").line()).isEqualTo(8);
        Map<String, Entry> de = only(c, "de", "").entries();
        assertThat(de.get("greeting").value()).isEqualTo("Hallo {{name}}");
        assertThat(de.get("greeting").untranslated()).isFalse();
        assertThat(de.get("7a1b").state()).isEqualTo("new");
        assertThat(de.get("7a1b").line()).isEqualTo(9);
        assertThat(de.get("farewell").state()).as("an absent target is a state too").isEqualTo("missing target");
        assertThat(de.get("gone").value()).isEqualTo("Alt");
    }

    @Test
    @DisplayName("Angular XLIFF 2.0: unit/segment with state=initial, no angular.json needed beside the extract")
    void angularXliff20(@TempDir Path root) throws Exception {
        write(root, "src/locale/messages.xlf", "<xliff version=\"2.0\" xmlns=\"urn:oasis:names:tc:xliff:document:2.0\" srcLang=\"en\">\n"
                + "  <file id=\"ngi18n\" original=\"ng.template\">\n"
                + "    <unit id=\"title\">\n"
                + "      <segment>\n"
                + "        <source>Welcome</source>\n"
                + "      </segment>\n"
                + "    </unit>\n"
                + "  </file>\n"
                + "</xliff>\n");
        write(root, "src/locale/messages.fr.xlf", "<xliff version=\"2.0\" xmlns=\"urn:oasis:names:tc:xliff:document:2.0\" srcLang=\"en\" trgLang=\"fr\">\n"
                + "  <file id=\"ngi18n\" original=\"ng.template\">\n"
                + "    <unit id=\"title\">\n"
                + "      <segment state=\"initial\">\n"
                + "        <source>Welcome</source>\n"
                + "        <target>Welcome</target>\n"
                + "      </segment>\n"
                + "    </unit>\n"
                + "  </file>\n"
                + "</xliff>\n");
        Catalogs c = I18nCatalogs.detect(root);
        assertThat(c.format()).isEqualTo(Format.ANGULAR_XLIFF);
        assertThat(c.sourceLocale()).isEqualTo("en");
        assertThat(only(c, "en", "").entries().get("title")).isEqualTo(new Entry("Welcome", 3, null));
        Entry fr = only(c, "fr", "").entries().get("title");
        assertThat(fr.state()).isEqualTo("initial");
        assertThat(fr.line()).isEqualTo(3);
    }

    @Test
    @DisplayName("a DOCTYPE with an external entity is refused whole — nothing is read from disk")
    void xxeIsRefused(@TempDir Path root) throws Exception {
        write(root, "src/locale/messages.xlf", "<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE xliff [ <!ENTITY xxe SYSTEM \"file:///etc/passwd\"> ]>\n"
                + "<xliff version=\"1.2\"><file><body>\n"
                + "<trans-unit id=\"a\"><source>&xxe;</source></trans-unit>\n"
                + "</body></file></xliff>\n");
        assertThatThrownBy(() -> I18nCatalogs.detect(root))
                .isInstanceOf(I18nCatalogs.ParseFailure.class)
                .hasMessageContaining("DOCTYPE");
        // and the same bytes through the parser directly: no entity value anywhere
        String text = Files.readString(root.resolve("src/locale/messages.xlf"));
        assertThatThrownBy(() -> I18nCatalogs.parseXliff(text, root.resolve("x.xlf"), true))
                .isInstanceOf(I18nCatalogs.ParseFailure.class)
                .extracting(Throwable::getMessage).asString().doesNotContain("root:");
    }

    // ---- Lingui ---------------------------------------------------------

    @Test
    @DisplayName("Lingui .po: config path with {locale}, multi-line msgstr joined, #~ obsolete skipped, empty msgstr untranslated")
    void linguiPo(@TempDir Path root) throws Exception {
        write(root, "lingui.config.json", "{\"locales\":[\"en\",\"cs\"],\"sourceLocale\":\"en\","
                + "\"catalogs\":[{\"path\":\"<rootDir>/src/locales/{locale}/messages\",\"include\":[\"src\"]}]}");
        String en = "msgid \"\"\nmsgstr \"\"\n\"Language: en\\n\"\n\n"
                + "msgid \"Hello {name}\"\nmsgstr \"Hello {name}\"\n\n"
                + "msgctxt \"button\"\nmsgid \"Save\"\nmsgstr \"Save\"\n\n"
                + "#~ msgid \"Gone\"\n#~ msgstr \"Gone\"\n\n"
                + "msgid \"Long\"\nmsgstr \"\"\n\"part one \"\n\"part two\"\n";
        String cs = "msgid \"\"\nmsgstr \"\"\n\"Language: cs\\n\"\n\n"
                + "msgid \"Hello {name}\"\nmsgstr \"Ahoj {name}\"\n\n"
                + "msgctxt \"button\"\nmsgid \"Save\"\nmsgstr \"\"\n\n"
                + "msgid \"Long\"\nmsgstr \"\"\n\"část jedna \"\n\"část dvě\"\n";
        write(root, "src/locales/en/messages.po", en);
        write(root, "src/locales/cs/messages.po", cs);
        Catalogs c = I18nCatalogs.detect(root);
        assertThat(c.format()).isEqualTo(Format.LINGUI_PO);
        assertThat(c.sourceLocale()).isEqualTo("en");
        Map<String, Entry> source = only(c, "en", "").entries();
        assertThat(source).containsOnlyKeys("Hello {name}", "button|Save", "Long");
        assertThat(source.get("Long").value()).isEqualTo("part one part two");
        assertThat(source.get("Long").line()).as("the msgid's line, past the obsolete block").isEqualTo(15);
        Map<String, Entry> target = only(c, "cs", "").entries();
        assertThat(target.get("Hello {name}").value()).isEqualTo("Ahoj {name}");
        assertThat(target.get("button|Save").state()).isEqualTo("untranslated");
        assertThat(target.get("button|Save").line()).isEqualTo(10);
        assertThat(target.get("Long").value()).isEqualTo("část jedna část dvě");
    }

    @Test
    @DisplayName("Lingui with a JS config: the default layout is assumed and the rule says so")
    void linguiJsConfig(@TempDir Path root) throws Exception {
        write(root, "lingui.config.js", "module.exports = { locales: ['en', 'de'] }");
        write(root, "src/locales/en/messages.po", "msgid \"Hi\"\nmsgstr \"Hi\"\n");
        write(root, "src/locales/de/messages.po", "msgid \"Hi\"\nmsgstr \"Hallo\"\n");
        Catalogs c = I18nCatalogs.detect(root);
        assertThat(c.format()).isEqualTo(Format.LINGUI_PO);
        assertThat(c.sourceRule()).isEqualTo("lingui default layout, en present");
    }

    // ---- Paraglide / inlang ---------------------------------------------

    @Test
    @DisplayName("Paraglide: settings.json's pathPattern and sourceLanguageTag; $schema is never a key")
    void paraglide(@TempDir Path root) throws Exception {
        write(root, "project.inlang/settings.json", "{\"sourceLanguageTag\":\"de\",\"languageTags\":[\"de\",\"fr\"],"
                + "\"modules\":[],\"plugin.inlang.messageFormat\":{\"pathPattern\":\"./messages/{languageTag}.json\"}}");
        write(root, "messages/de.json", "{\n  \"$schema\": \"https://inlang.com/schema/inlang-message-format\",\n"
                + "  \"hello_world\": \"Hallo Welt\"\n}");
        write(root, "messages/fr.json", "{\"$schema\":\"https://inlang.com/schema/inlang-message-format\",\"hello_world\":\"Bonjour\"}");
        Catalogs c = I18nCatalogs.detect(root);
        assertThat(c.format()).isEqualTo(Format.PARAGLIDE_FLAT);
        assertThat(c.sourceLocale()).isEqualTo("de");
        assertThat(c.sourceRule()).isEqualTo("inlang sourceLanguageTag");
        assertThat(only(c, "de", "").entries()).containsOnlyKeys("hello_world");
        assertThat(only(c, "de", "").entries().get("hello_world").line()).isEqualTo(3);
    }

    // ---- react-intl -----------------------------------------------------

    @Test
    @DisplayName("react-intl: flat ids, dots are characters, a {defaultMessage} object reads its message")
    void reactIntlFlat(@TempDir Path root) throws Exception {
        write(root, "package.json", "{\"dependencies\":{\"react-intl\":\"^6\"}}");
        write(root, "src/lang/en.json", "{\"app.title\":\"Hello\",\"app.count\":{\"defaultMessage\":\"{n, plural, one {# item} other {# items}}\",\"description\":\"x\"},\"nested\":{\"not\":\"flattened\"}}");
        write(root, "src/lang/es.json", "{\"app.title\":\"Hola\"}");
        Catalogs c = I18nCatalogs.detect(root);
        assertThat(c.format()).isEqualTo(Format.FLAT_ICU);
        Map<String, Entry> en = only(c, "en", "").entries();
        assertThat(en).containsOnlyKeys("app.title", "app.count");
        assertThat(en.get("app.count").value()).startsWith("{n, plural");
    }

    // ---- svelte-i18n ----------------------------------------------------

    @Test
    @DisplayName("svelte-i18n: src/lib/i18n/locales nested ICU")
    void svelteNested(@TempDir Path root) throws Exception {
        write(root, "package.json", "{\"devDependencies\":{\"svelte-i18n\":\"^4\"}}");
        write(root, "src/lib/i18n/locales/en.json", "{\"page\":{\"title\":\"Hi\"}}");
        Catalogs c = I18nCatalogs.detect(root);
        assertThat(c.format()).isEqualTo(Format.NESTED_ICU);
        assertThat(only(c, "en", "").entries()).containsOnlyKeys("page.title");
    }

    // ---- the Kit --------------------------------------------------------

    @Test
    @DisplayName("the I18n Kit's shape is the last rule: locales/*.json beside an i18n.js applying data-i18n")
    void kitShape(@TempDir Path root) throws Exception {
        write(root, "i18n.js", "document.querySelectorAll('[data-i18n]')");
        write(root, "locales/en.json", "{\"app.title\":\"Site\",\"app.greeting\":\"Hello\"}");
        write(root, "locales/es.json", "{\"app.title\":\"Sitio\",\"app.greeting\":\"Hola\"}");
        Catalogs c = I18nCatalogs.detect(root);
        assertThat(c.format()).isEqualTo(Format.KIT_FLAT);
        assertThat(c.catalogHome()).isEqualTo("locales/");
        assertThat(only(c, "es", "").entries().get("app.greeting").value()).isEqualTo("Hola");

        Path bare = Files.createTempDirectory(root, "bare");
        write(bare, "locales/en.json", "{\"a\":\"b\"}");
        assertThat(I18nCatalogs.detect(bare)).as("a locales/ folder with no i18n.js is not the kit").isNull();
    }

    // ---- refusals and caps ----------------------------------------------

    @Test
    @DisplayName("a malformed catalog is a ParseFailure naming the file — never a half-read report")
    void malformedJsonFails(@TempDir Path root) throws Exception {
        write(root, "package.json", "{\"dependencies\":{\"i18next\":\"^23\"}}");
        write(root, "public/locales/en/common.json", "{ \"a\": \"b\" }");
        // org.json tolerates a trailing comma; an unterminated object it does not
        write(root, "public/locales/de/common.json", "{ \"a\": \"b\"");
        assertThatThrownBy(() -> I18nCatalogs.detect(root))
                .isInstanceOf(I18nCatalogs.ParseFailure.class)
                .satisfies(t -> assertThat(((I18nCatalogs.ParseFailure) t).file())
                        .isEqualTo(root.resolve("public/locales/de/common.json")));
    }

    @Test
    @DisplayName("more catalog files than the cap: the extra are not read and censusComplete is false")
    void catalogCapIsHonest(@TempDir Path root) throws Exception {
        write(root, "package.json", "{\"dependencies\":{\"i18next\":\"^23\"}}");
        for (int i = 0; i < I18nCatalogs.MAX_CATALOGS + 5; i++) {
            // two-letter tags, so each file reads as a locale catalog
            String tag = "" + (char) ('a' + i / 26) + (char) ('a' + i % 26);
            write(root, "locales/" + tag + ".json", "{\"k\":\"v\"}");
        }
        Catalogs c = I18nCatalogs.detect(root);
        assertThat(c.catalogs()).hasSizeLessThanOrEqualTo(I18nCatalogs.MAX_CATALOGS);
        assertThat(c.censusComplete()).isFalse();
    }

    @Test
    @DisplayName("a file over the size cap is a refusal, not a partial read")
    void oversizeCatalogRefuses(@TempDir Path root) throws Exception {
        write(root, "package.json", "{\"dependencies\":{\"i18next\":\"^23\"}}");
        write(root, "locales/en.json", "{\"pad\":\"" + "x".repeat((int) I18nCatalogs.MAX_FILE_BYTES + 10) + "\"}");
        assertThatThrownBy(() -> I18nCatalogs.detect(root))
                .isInstanceOf(I18nCatalogs.ParseFailure.class)
                .hasMessageContaining("KiB");
    }

    @Test
    @DisplayName("detection order: Angular wins over an i18next dependency, nothing at all is null")
    void detectionOrder(@TempDir Path root) throws Exception {
        write(root, "package.json", "{\"dependencies\":{\"i18next\":\"^23\"}}");
        write(root, "public/locales/en/common.json", "{ \"a\": \"b\" }");
        write(root, "src/locale/messages.xlf", XLF12_SOURCE);
        assertThat(I18nCatalogs.detect(root).format()).isEqualTo(Format.ANGULAR_XLIFF);
        Path empty = Files.createTempDirectory(root, "empty");
        write(empty, "package.json", "{\"dependencies\":{\"react\":\"^19\"}}");
        assertThat(I18nCatalogs.detect(empty)).isNull();
    }

    @Test
    @DisplayName("the line scan: nested keys, arrays, escaped quotes in keys, a key the scan cannot place reads 1")
    void jsonKeyLines() {
        String text = "{\n\"a\": {\n  \"b\": \"x\",\n  \"c\\\"q\": \"y\"\n},\n\"arr\": [\n  1,\n  {\"z\": true}\n]\n}";
        Map<String, Integer> lines = I18nCatalogs.JsonKeyLines.scan(text);
        assertThat(lines).containsEntry("a", 2).containsEntry("a.b", 3).containsEntry("a.c\"q", 4)
                .containsEntry("arr", 6).containsEntry("arr.0", 7).containsEntry("arr.1", 8)
                .containsEntry("arr.1.z", 8);
        Map<String, Entry> parsed = assertDoesNotThrowParse("{\"k\":\"v\"}");
        assertThat(parsed.get("k").line()).isEqualTo(1);
    }

    private static Map<String, Entry> assertDoesNotThrowParse(String json) {
        try {
            return I18nCatalogs.parseJson(json, Path.of("x.json"), true);
        } catch (I18nCatalogs.ParseFailure e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("the parse cache follows the file: a rewritten catalog re-parses, an untouched one does not re-read")
    void parseCacheFollowsTheFile(@TempDir Path root) throws Exception {
        Path f = root.resolve("en.json");
        Files.writeString(f, "{\"a\":\"one\"}");
        assertThat(I18nCatalogs.json(f, true).get("a").value()).isEqualTo("one");
        Files.writeString(f, "{\"a\":\"two-longer\"}");
        assertThat(I18nCatalogs.json(f, true).get("a").value()).isEqualTo("two-longer");
    }
}
