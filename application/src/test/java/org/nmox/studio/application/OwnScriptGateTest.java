package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In a script that shares no letters with English, an English word is a
 * decision — so write it down.
 *
 * <p>v2.137.0 showed why "is this value translated?" cannot be gated key by
 * key: 226 of the product's 2,848 keys hold a value identical to their
 * English somewhere, and almost all are honest, because German really does
 * write {@code Name:} and {@code Message} really is French. Vocabulary
 * coincidence is the noise, and it is enormous.
 *
 * <p>Four of the twelve languages have none of that noise available.
 * Russian, Ukrainian, Chinese and Hindi share no alphabet with English, so a
 * Latin word sitting in one of their bundles is never a coincidence: it is
 * either a deliberate technical term that stays in Latin — a language name,
 * a protocol, a product — or it is a key nobody got to. Both are fine. What
 * is not fine is not knowing which.
 *
 * <p>Two filters make the signal clean, and both were measured before this
 * gate was written. Without the second, Russian alone reports 93 rows, 70 of
 * them the names of programming languages, which must never be translated.
 *
 * <ol>
 *   <li>The value is PROSE: a plain word or short phrase, no placeholder, no
 *       markup, not an ALL-CAPS machine token.</li>
 *   <li>At least nine of the twelve languages DID translate it. That is the
 *       evidence — the same shape as {@code HalfTranslatedRowGateTest}'s
 *       sibling description, one surface over: a word nine translators
 *       rendered is a word, not a name.</li>
 * </ol>
 *
 * <p>With both, the whole product reports a handful, and every one of them
 * is a real question with a real answer.
 */
class OwnScriptGateTest {

    private static final List<String> LOCALES = List.of(
            "es", "fr", "de", "ru", "uk", "pl", "pt", "id", "tl", "vi", "zh", "hi");

    /** The four whose readers share no alphabet with English. */
    private static final List<String> OWN_SCRIPT = List.of("ru", "uk", "zh", "hi");

    /**
     * How many of the twelve must have translated a value before it counts
     * as a word rather than a name.
     *
     * <p>Eight, and the number was measured rather than picked. At nine the
     * product reports a handful and every one is an ordinary word. At eight
     * it reports six more, all of them ordinary too — {@code Load Balancer},
     * {@code Motion}, {@code Assertion}, {@code Tests}. At seven and below
     * the cloud PRODUCT names arrive — {@code HZ Server}, {@code Reserved
     * IP}, {@code Spaces Bucket} — which half the languages kept on purpose,
     * and a gate that argued with them would be arguing about names.
     */
    private static final int TRANSLATED_ELSEWHERE = 8;

    private static final Pattern PROSE =
            Pattern.compile("[A-Za-z][A-Za-z /\\-]{2,26}:?");

    /** Latin words that STAY Latin for these readers, and why. */
    private static final Map<String, String> STAYS_LATIN = new LinkedHashMap<>();

    static {
        STAYS_LATIN.put("zh OpenIDE-Module-Name", "the module's display name is the product name plus the studio's own name, and the only word in it is the acronym: `NMOX Studio API` names the module that carries API Studio, whose studio name the glossary keeps as `API \u5de5\u4f5c\u5ba4` / `API \u0938\u094d\u091f\u0942\u0921\u093f\u092f\u094b` \u2014 Latin `API` inside the translated name (v2.98.0, David: translate the studio names, keep the protocol names). A module name of `NMOX Studio` followed by an acronym is therefore wholly a name in every script, and nine other languages writing it identically is the rule holding, not a key nobody reached.");
        STAYS_LATIN.put("hi OpenIDE-Module-Name", "the module's display name is the product name plus the studio's own name, and the only word in it is the acronym: `NMOX Studio API` names the module that carries API Studio, whose studio name the glossary keeps as `API \u5de5\u4f5c\u5ba4` / `API \u0938\u094d\u091f\u0942\u0921\u093f\u092f\u094b` \u2014 Latin `API` inside the translated name (v2.98.0, David: translate the studio names, keep the protocol names). A module name of `NMOX Studio` followed by an acronym is therefore wholly a name in every script, and nine other languages writing it identically is the rule holding, not a key nobody reached.");
        STAYS_LATIN.put("ru DockerPanelTopComponent_colActive",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("zh DockerPanelTopComponent_colActive",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("ru DockerPanelTopComponent_colCategory",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("zh DockerPanelTopComponent_colCategory",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("ru DockerPanelTopComponent_colCount",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("zh DockerPanelTopComponent_colCount",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("ru DockerPanelTopComponent_colCreated",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("zh DockerPanelTopComponent_colCreated",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("ru DockerPanelTopComponent_colName",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("zh DockerPanelTopComponent_colName",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("ru DockerPanelTopComponent_colReclaimable",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("zh DockerPanelTopComponent_colReclaimable",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("ru DockerPanelTopComponent_colReference",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("zh DockerPanelTopComponent_colReference",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("ru DockerPanelTopComponent_colScope",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("zh DockerPanelTopComponent_colScope",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("ru DockerPanelTopComponent_colSize",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("zh DockerPanelTopComponent_colSize",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("ru DockerPanelTopComponent_reclaim",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("zh DockerPanelTopComponent_reclaim",
                "these are the column headings `docker ps` and `docker system df` print, shown in a panel whose rows are that command\u2019s own output; the Russian and Chinese panels keep the header and the row in one vocabulary, which is what a reader matching the panel against their terminal needs.");
        STAYS_LATIN.put("ru NodeKind_hzNetwork",
                "one member of Hetzner's product family, which the Russian palette keeps "
                + "whole: HZ Server, HZ Volume, HZ Firewall, HZ Floating IP and HZ Load "
                + "Balancer all stay Latin beside it. Translating only this one would "
                + "leave the palette half in each language, the v2.132.0 call about the "
                + "Infra palette's own COMPUTE header.");
        STAYS_LATIN.put("uk NodeKind_hzNetwork",
                "the same family decision as its Russian sibling above: the Ukrainian "
                + "palette keeps the whole HZ family as Hetzner's product names, and one "
                + "translated member would be the leftover, not the loanword.");
        STAYS_LATIN.put("ru AskKvasirAction_title",
                "the Russian bundle treats \"Ask KVASIR\" as the FEATURE's name and "
                + "uses it that way throughout — in the menu item, in both consent "
                + "dialogs and in the refusal that fires with no selection. Translating "
                + "the title alone would give one door two names, the v2.118.0 defect; "
                + "translating all of them is a call for a Russian reader to make.");
        STAYS_LATIN.put("ru EditWithKvasirAction_title",
                "the same decision as its sibling above: \"Edit with KVASIR\" names the "
                + "feature everywhere else in this bundle, including the sentence that "
                + "explains what it rewrites, so the title follows the menu.");
        STAYS_LATIN.put("hi CssFuturesCompletionProvider_atRule",
                "\"at-rule\" is the CSS specification's own name for the @media / "
                + "@supports family, shown in a completion popup beside the literal "
                + "@ the user is typing. French, Indonesian and Filipino kept it for "
                + "the same reason: it names a construct in the language on screen.");
    }

    @Test
    @DisplayName("a word nine languages translated is not left in English for a reader of another script")
    void everyEnglishWordInAnotherScriptIsADecision() throws IOException {
        List<Path> jars = productJars();
        assertThat(jars).as("product jars in the assembled cluster — an empty scan "
                + "would find every script perfectly served").isNotEmpty();

        int wordsWeighed = 0;
        List<String> undecided = new ArrayList<>();
        for (Path jar : jars) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                Map<String, Properties> base = new TreeMap<>();
                Map<String, Map<String, Properties>> byLocale = new TreeMap<>();
                for (ZipEntry e : zip.stream().toList()) {
                    String n = e.getName();
                    if (!n.endsWith(".properties") || !n.contains("/Bundle")) {
                        continue;
                    }
                    String pkg = n.substring(0, n.lastIndexOf('/'));
                    String file = n.substring(n.lastIndexOf('/') + 1);
                    if (file.equals("Bundle.properties")) {
                        base.put(pkg, read(zip, e));
                    } else {
                        for (String loc : LOCALES) {
                            if (file.equals("Bundle_" + loc + ".properties")) {
                                byLocale.computeIfAbsent(pkg, k -> new TreeMap<>())
                                        .put(loc, read(zip, e));
                            }
                        }
                    }
                }
                for (Map.Entry<String, Properties> pkg : base.entrySet()) {
                    Properties en = pkg.getValue();
                    Map<String, Properties> locales =
                            byLocale.getOrDefault(pkg.getKey(), Map.of());
                    if (locales.size() < LOCALES.size()) {
                        continue;
                    }
                    for (String key : en.stringPropertyNames()) {
                        String english = en.getProperty(key);
                        if (!isProse(english)) {
                            continue;
                        }
                        Set<String> keptEnglish = new LinkedHashSet<>();
                        int translated = 0;
                        for (Map.Entry<String, Properties> loc : locales.entrySet()) {
                            String v = loc.getValue().getProperty(key);
                            if (v == null) {
                                continue;
                            }
                            if (v.equals(english)) {
                                keptEnglish.add(loc.getKey());
                            } else {
                                translated++;
                            }
                        }
                        if (translated < TRANSLATED_ELSEWHERE) {
                            continue;
                        }
                        wordsWeighed++;
                        for (String loc : OWN_SCRIPT) {
                            if (!keptEnglish.contains(loc)) {
                                continue;
                            }
                            if (STAYS_LATIN.containsKey(loc + " " + key)) {
                                continue;
                            }
                            undecided.add(loc + " " + key + " = \"" + english
                                    + "\" (translated by " + translated + " of "
                                    + LOCALES.size() + ")");
                        }
                    }
                }
            }
        }
        assertThat(wordsWeighed).as("words this gate weighed — a run that weighs none "
                + "proves nothing about any script").isPositive();
        assertThat(undecided).as("a Latin word in a script that shares no letters with "
                + "English, where nine other languages found a word for it. Translate "
                + "it, or record in STAYS_LATIN why this one stays Latin for that "
                + "reader").isEmpty();
    }

    @Test
    @DisplayName("a word blessed as staying Latin gives a reason, and names a locale we ship")
    void everyBlessingIsADecision() {
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, String> e : STAYS_LATIN.entrySet()) {
            if (e.getValue().length() < 60) {
                stale.add(e.getKey() + ": a reason this short decides nothing");
            }
            String locale = e.getKey().split(" ", 2)[0];
            if (!OWN_SCRIPT.contains(locale)) {
                stale.add(e.getKey() + ": blessed for a locale this gate never weighs");
            }
        }
        assertThat(stale).as("a blessing nobody can check has stopped being a decision")
                .isEmpty();
    }

    private static boolean isProse(String v) {
        String t = v == null ? "" : v.trim();
        if (t.isEmpty() || t.contains("{") || t.contains("\\") || t.contains("<")) {
            return false;
        }
        // NOT filtered: an ALL-CAPS value. The first cut of this gate dropped
        // them as machine tokens, and the Hindi walk photographed three that
        // were not — the rack's DEVICE SHELF header, its RACK EMPTY placard
        // and the Infra Designer's red DEPLOY button, all reading English in
        // a Devanagari window. A filter that removes noise removes signal
        // with it, and only the picture says which. The ten that ARE machine
        // tokens are written down below instead, where a person can disagree.
        return PROSE.matcher(t).matches();
    }

    private static Properties read(ZipFile zip, ZipEntry e) throws IOException {
        Properties p = new Properties();
        try (InputStream in = zip.getInputStream(e)) {
            // raw UTF-8 in the jar; Properties.load(InputStream) is ISO-8859-1
            // by contract and would measure its own decoding (the v2.129.0 defect)
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    private static List<Path> productJars() throws IOException {
        Path modules = Path.of("target/nmoxstudio/nmoxstudio/modules");
        if (!Files.isDirectory(modules)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(modules)) {
            return s.filter(p -> p.getFileName().toString().startsWith("org-nmox-NMOX-Studio")
                    && p.getFileName().toString().endsWith(".jar")).sorted().toList();
        }
    }
}
