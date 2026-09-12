package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.util.UiLocale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three catalogues an enum holds are read in the reader's language.
 *
 * <p>{@code CatalogueProseLedgerTest} says WHICH enums carry words; this
 * says those words are translated. The population is derived twice over —
 * the constants from the shipping SOURCE, the values from the ASSEMBLED
 * cluster's bundles — so a template, a Block Studio piece or a contract
 * chain added tomorrow fails the build until it speaks.
 *
 * <p>Prose and names are held to different laws, because they are different
 * things. A description is always prose and always carries a key. A NAME is
 * prose only sometimes: "TypeScript Library" ends in a word, "Vite + React"
 * and "Cairo (Starknet)" do not, and a name a person types or searches for
 * must survive translation intact. So a name carries a key when somebody
 * wrote one — and then it must carry one in EVERY language, because a row
 * translated in eight languages and not in four reads as a bug in the four.
 *
 * <p>Bound to {@code packaged-app-gates}.
 */
class CatalogueEnumsSpeakTest {

    private static final Path MODULES = Path.of("target", "nmoxstudio", "nmoxstudio", "modules");

    private static final Path REPO = Path.of("..");

    /** A constant name at the head of an enum row. */
    private static final Pattern CONSTANT = Pattern.compile("(?m)^\\s{4,}([A-Z][A-Z_0-9]*)\\(\"");

    /** enum source → the bundle package that renders it. */
    private static final String BLOCK_SRC =
            "rack/src/main/java/org/nmox/studio/rack/blockstudio/BlockKind.java";
    private static final String BLOCK_BUNDLE = "org/nmox/studio/rack/blockstudio/Bundle_";
    private static final String TEMPLATE_SRC =
            "rack/src/main/java/org/nmox/studio/rack/projectstudio/ProjectTemplates.java";
    private static final String TEMPLATE_BUNDLE = "org/nmox/studio/rack/projectstudio/Bundle_";
    private static final String CHAIN_SRC =
            "rack/src/main/java/org/nmox/studio/rack/projectstudio/ContractKit.java";
    private static final String CHAIN_BUNDLE = "org/nmox/studio/ui/actions/Bundle_";

    @Test
    @DisplayName("every Block Studio piece is named in every language")
    void everyPieceSpeaks() throws IOException {
        assertComplete(constants(BLOCK_SRC), BLOCK_BUNDLE, "BlockKind_",
                "a palette of English pieces under a translated heading");
    }

    @Test
    @DisplayName("every New Project template describes itself in every language")
    void everyTemplateSpeaks() throws IOException {
        assertComplete(constants(TEMPLATE_SRC), TEMPLATE_BUNDLE, "TemplateDesc_",
                "the most-used catalogue in the product, in English");
    }

    @Test
    @DisplayName("a translated name or chain label is translated in all twelve languages")
    void namesAreAllOrNothing() throws IOException {
        List<String> half = new ArrayList<>();
        half.addAll(allOrNothing(constants(TEMPLATE_SRC), TEMPLATE_BUNDLE, "TemplateName_"));
        half.addAll(allOrNothing(constants(CHAIN_SRC), CHAIN_BUNDLE, "Chain_"));
        assertThat(half).as("a row translated in some languages and not others reads "
                + "as a bug in the ones it skipped").isEmpty();
    }

    @Test
    @DisplayName("no key is written for a row that no longer exists")
    void noKeyIsDead() throws IOException {
        record Family(String prefix, String bundle, List<String> live) { }
        List<Family> families = List.of(
                new Family("BlockKind_", BLOCK_BUNDLE, constants(BLOCK_SRC)),
                new Family("TemplateName_", TEMPLATE_BUNDLE, constants(TEMPLATE_SRC)),
                new Family("TemplateDesc_", TEMPLATE_BUNDLE, constants(TEMPLATE_SRC)),
                new Family("Chain_", CHAIN_BUNDLE, constants(CHAIN_SRC)));

        List<String> dead = new ArrayList<>();
        for (Family f : families) {
            for (String lang : languages()) {
                Properties p = bundle(f.bundle() + lang + ".properties");
                for (String key : p.stringPropertyNames()) {
                    if (key.startsWith(f.prefix())
                            && !f.live().contains(key.substring(f.prefix().length()))) {
                        dead.add(lang + ": " + key);
                    }
                }
            }
        }
        assertThat(dead).as("a key nobody reads usually means the row it was written "
                + "for is spelled differently now and reads English").isEmpty();
    }

    @Test
    @DisplayName("every translated value is prose, not a copy of the English")
    void everyValueRenders() throws IOException {
        List<String> broken = new ArrayList<>();
        int measured = 0;
        Map<String, String> bundles = new LinkedHashMap<>();
        bundles.put("BlockKind_", BLOCK_BUNDLE);
        bundles.put("TemplateName_", TEMPLATE_BUNDLE);
        bundles.put("TemplateDesc_", TEMPLATE_BUNDLE);
        bundles.put("Chain_", CHAIN_BUNDLE);

        for (Map.Entry<String, String> family : bundles.entrySet()) {
            for (String lang : languages()) {
                Properties p = bundle(family.getValue() + lang + ".properties");
                for (String key : p.stringPropertyNames()) {
                    if (!key.startsWith(family.getKey())) {
                        continue;
                    }
                    String value = p.getProperty(key);
                    measured++;
                    String where = lang + "/" + key;
                    if (value.isBlank()) {
                        broken.add(where + ": blank");
                    }
                    if (!value.equals(value.strip())) {
                        broken.add(where + ": edge whitespace");
                    }
                    if (value.contains("â") || value.indexOf('�') >= 0) {
                        broken.add(where + ": mojibake");
                    }
                }
            }
        }
        assertThat(measured).as("the gate should measure every row in every language")
                .isGreaterThan(350);
        assertThat(broken).as("values a reader would see as damage").isEmpty();
    }

    /** Every constant must carry {@code prefix + name} in every language. */
    private static void assertComplete(List<String> constants, String bundlePath,
            String prefix, String whatItWouldLookLike) throws IOException {
        assertThat(constants).as("rows in the catalogue").hasSizeGreaterThan(9);
        List<String> silent = new ArrayList<>();
        for (String lang : languages()) {
            Properties p = bundle(bundlePath + lang + ".properties");
            for (String c : constants) {
                if (p.getProperty(prefix + c) == null) {
                    silent.add(lang + ": " + c);
                }
            }
        }
        assertThat(silent).as(whatItWouldLookLike).isEmpty();
    }

    /** Rows keyed in some languages but not all. */
    private static List<String> allOrNothing(List<String> constants, String bundlePath,
            String prefix) throws IOException {
        List<String> languages = languages();
        List<String> half = new ArrayList<>();
        for (String c : constants) {
            TreeSet<String> have = new TreeSet<>();
            for (String lang : languages) {
                if (bundle(bundlePath + lang + ".properties").getProperty(prefix + c) != null) {
                    have.add(lang);
                }
            }
            if (!have.isEmpty() && have.size() != languages.size()) {
                half.add(prefix + c + ": " + have);
            }
        }
        return half;
    }

    private static List<String> constants(String source) throws IOException {
        String text = Files.readString(REPO.resolve(source), StandardCharsets.UTF_8);
        Matcher m = CONSTANT.matcher(text);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static List<String> languages() {
        return UiLocale.SUPPORTED.stream()
                .filter(c -> !c.isSystem() && !"en".equals(c.code()))
                .map(UiLocale.Choice::code)
                .toList();
    }

    /** A bundle out of whichever shipped module jar carries it. */
    private static Properties bundle(String entry) throws IOException {
        assertThat(MODULES).as("the assembled cluster's modules").isDirectory();
        try (Stream<Path> jars = Files.list(MODULES)) {
            for (Path jarPath : jars.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (JarFile jar = new JarFile(jarPath.toFile())) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry e = entries.nextElement();
                        if (!e.getName().equals(entry)) {
                            continue;
                        }
                        Properties props = new Properties();
                        try (InputStream in = jar.getInputStream(e)) {
                            // UTF-8: Properties.load(InputStream) is ISO-8859-1
                            // by contract and these bundles ship raw UTF-8 (v2.129.0)
                            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                        }
                        return props;
                    }
                }
            }
        }
        throw new AssertionError("no shipped " + entry + " — a catalogue lost a language");
    }
}
