package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.jar.JarFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dialog and wizard chrome speaks the user's language.
 *
 * <p>Every dialog in the product paints its buttons and its title from
 * bundles no line of our source can reach, so a fully translated build still
 * read {@code Cancel} and {@code OK} beside our own {@code Abbrechen} — found
 * by photographing the Standards Kit wizard in German.
 *
 * <p>THE WALK'S LESSON, and why there are two overlays here: the Dialogs
 * API's own {@code CTL_OK} / {@code CTL_CANCEL} are NOT what a
 * {@code DialogDescriptor} paints. Overlaying them alone changed nothing, and
 * the second photograph said so. The buttons come from NbPresenter's
 * {@code *_OPTION_CAPTION} keys one module over, in core-windows. The Dialogs
 * bundle still owns the wizard's Next / Back / Finish and the message titles,
 * so both are overlaid — the one the walk named, and the one that looked
 * obvious.
 *
 * <p>Nothing a compiler sees holds these honest: a key could be renamed by a
 * platform upgrade, a language could be missed, or a {@code choice} pattern
 * could be malformed and throw at paint time in the one locale nobody on this
 * team reads. The gate derives its key population from the ASSEMBLED
 * cluster's own bundles, which is why it runs bound to the
 * {@code packaged-app-gates} execution — a gate reading {@code target/} in
 * the test phase passes on a stale cluster.
 */
class DialogChromeOverlayGateTest {

    private static final Path BRANDING =
            Path.of("..", "branding", "src", "main", "nbm-branding", "modules");

    private static final Path PLATFORM_MODULES =
            Path.of("target", "nmoxstudio", "platform", "modules");

    private static final List<String> LOCALES =
            List.of("es", "fr", "de", "ru", "uk", "pl", "pt", "id", "tl", "vi", "zh", "hi");

    /**
     * One overlay: the platform jar it covers, the package inside it, and the
     * keys whose English word has no plausible coincidence in these twelve
     * languages — so a value still equal to the platform's is untranslated.
     */
    private record Overlay(String jar, String pkg, List<String> mustDiffer) {

        Path source(String locale) {
            return BRANDING.resolve(jar).resolve(pkg).resolve("Bundle_" + locale + ".properties");
        }
    }

    private static final List<Overlay> OVERLAYS = List.of(
            // the wizard's own navigation and the message-dialog titles
            new Overlay("org-openide-dialogs.jar", "org/openide",
                    List.of("CTL_CANCEL", "CTL_CLOSE", "CTL_FINISH", "NTF_ErrorTitle")),
            // the buttons NbPresenter actually paints — the walk's find
            new Overlay("org-netbeans-core-windows.jar", "org/netbeans/core/windows/services",
                    List.of("CancelButton", "CloseButton", "CANCEL_OPTION_CAPTION",
                            "CLOSED_OPTION_CAPTION", "HELP_OPTION_CAPTION")),
            // v2.141.0: the Plugin Manager's chrome (tabs, columns, buttons,
            // detail labels) — photographed English in a Hindi build
            new Overlay("org-netbeans-modules-autoupdate-ui.jar", "org/netbeans/modules/autoupdate/ui",
                    List.of("PluginManagerUI_UnitTab_Installed_Title", "UnitTab_bClose_Text",
                            "UnitTab_OperationName_Text_INSTALLED", "UnitDetails_Plugin_Description",
                            "InstalledTableModel_Columns_Name")),
            // v2.141.0: the Plugins menu row and the update-found balloon
            new Overlay("org-netbeans-modules-autoupdate-ui.jar", "org/netbeans/modules/autoupdate/ui/actions",
                    // not Panel_Name: six of the twelve borrow "Plugins" as is
                    List.of("PluginManager_CloseButton_Name",
                            "AutoupdateCheckScheduler_UpdateFound_Hint")),
            // v2.141.0: the About dialog — our own paragraph and the platform's
            // Product Version / Java / System / User directory labels
            new Overlay("org-netbeans-core.jar", "org/netbeans/core/ui",
                    List.of("LBL_Close", "LBL_description", "updates_not_found", "LBL_Copyright")));

    /**
     * Values that legitimately read the same as English, each blessed by
     * reason — because "equals the platform's value" is evidence of an
     * untranslated key, not proof of one.
     */
    private static final List<String> COINCIDES_WITH_ENGLISH = List.of(
            // "error" is the Spanish word, not the English one left behind
            "es/NTF_ErrorTitle",
            // Filipino borrows "error"; "kamalian" means a moral fault
            "tl/NTF_ErrorTitle",
            // "Name" is the German word for name (v2.141.0, the gate's own find)
            "de/InstalledTableModel_Columns_Name");

    @Test
    @DisplayName("every key we overlay still exists in the platform's own bundle")
    void theKeysAreRealKeys() throws IOException {
        List<String> ghosts = new ArrayList<>();
        for (Overlay o : OVERLAYS) {
            Properties platform = platformBundle(o);
            assertThat(platform.stringPropertyNames())
                    .as("%s should carry %s", o.jar(), o.pkg()).isNotEmpty();
            for (String key : load(o.source("de")).stringPropertyNames()) {
                if (platform.getProperty(key) == null) {
                    ghosts.add(o.jar() + ": " + key);
                }
            }
        }
        assertThat(ghosts).as("overlay keys the platform no longer defines — these paint nothing").isEmpty();
    }

    @Test
    @DisplayName("every overlay covers all twelve languages, with the same keys, in their own words")
    void everyLanguageIsCovered() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Overlay o : OVERLAYS) {
            Properties platform = platformBundle(o);
            List<String> reference = null;
            for (String locale : LOCALES) {
                Path p = o.source(locale);
                if (!Files.isRegularFile(p)) {
                    wrong.add(o.jar() + " [" + locale + "]: missing");
                    continue;
                }
                Properties props = load(p);
                List<String> keys = new ArrayList<>(props.stringPropertyNames());
                keys.sort(String::compareTo);
                if (reference == null) {
                    reference = keys;
                } else if (!reference.equals(keys)) {
                    wrong.add(o.jar() + " [" + locale + "]: keys " + keys + " != " + reference);
                }
                for (String key : o.mustDiffer()) {
                    String ours = props.getProperty(key);
                    if (ours != null && ours.equals(platform.getProperty(key))
                            && !COINCIDES_WITH_ENGLISH.contains(locale + "/" + key)) {
                        wrong.add(o.jar() + " [" + locale + "]: " + key
                                + " is still the platform's English value");
                    }
                }
            }
            assertThat(reference).as("%s should declare keys", o.jar()).isNotNull().isNotEmpty();
        }
        assertThat(wrong).as("dialog overlays that do not cover every language in its own words").isEmpty();
    }

    @Test
    @DisplayName("every value renders — choice branches included — and none opens a MessageFormat quote")
    void everyValueRenders() throws IOException {
        List<String> broken = new ArrayList<>();
        int rendered = 0;
        for (Overlay o : OVERLAYS) {
            for (String locale : LOCALES) {
                Path p = o.source(locale);
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                Properties props = load(p);
                for (String key : props.stringPropertyNames()) {
                    String value = props.getProperty(key);
                    // a bare ASCII apostrophe opens a MessageFormat quote and
                    // eats the rest of the message (the v2.98.0 rule)
                    if (value.contains("'")) {
                        broken.add(key + " [" + locale + "]: bare ASCII apostrophe");
                    }
                    if (!value.contains("{0")) {
                        continue;
                    }
                    // -1 and 0 are the platform's own no-neighbouring-step branches
                    for (int n : new int[] {-1, 0, 1, 2, 3, 5, 11}) {
                        try {
                            // the About panel's LBL_description takes eleven
                            // arguments; every slot gets a value so an
                            // unresolved placeholder means a typo, not a
                            // short argument list
                            Object[] args = new Object[12];
                            java.util.Arrays.fill(args, "demo");
                            args[0] = n;
                            String out = new MessageFormat(value, Locale.of(locale)).format(args);
                            rendered++;
                            if (out.contains("{") || out.contains("}")) {
                                broken.add(key + " [" + locale + "] n=" + n
                                        + ": unresolved placeholder in " + out);
                            }
                        } catch (RuntimeException malformed) {
                            broken.add(key + " [" + locale + "] n=" + n + ": " + malformed);
                        }
                    }
                }
            }
        }
        assertThat(broken).as("dialog overlay values that would throw or misrender at paint time").isEmpty();
        assertThat(rendered).as("the argument-carrying values should actually have been exercised")
                .isGreaterThan(400);
    }

    @Test
    @DisplayName("a declared mnemonic letter really occurs in the button it belongs to")
    void mnemonicsPointAtTheirOwnLabel() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Overlay o : OVERLAYS) {
            for (String locale : LOCALES) {
                Path p = o.source(locale);
                if (!Files.isRegularFile(p)) {
                    // everyLanguageIsCovered owns the missing-file verdict;
                    // this one states its own finding instead of throwing
                    wrong.add(o.jar() + " [" + locale + "]: no overlay at all");
                    continue;
                }
                Properties props = load(p);
                for (String key : props.stringPropertyNames()) {
                    if (!key.endsWith("_Mnemonic")) {
                        continue;
                    }
                    String mnemonic = props.getProperty(key);
                    String label = props.getProperty(key.substring(0, key.length() - "_Mnemonic".length()));
                    if (label == null) {
                        wrong.add(locale + ": " + key + " marks a button with no label");
                    } else if (label.toUpperCase(Locale.ROOT)
                            .indexOf(mnemonic.toUpperCase(Locale.ROOT)) < 0) {
                        // the platform marks the accelerated character with &
                        // and falls back to the _Mnemonic key; a letter the
                        // label does not contain accelerates nothing
                        wrong.add(locale + ": mnemonic '" + mnemonic + "' is not in \"" + label + "\"");
                    }
                }
            }
        }
        assertThat(wrong).as("mnemonics that name a letter their own button does not contain").isEmpty();
    }

    private static Properties platformBundle(Overlay o) throws IOException {
        Path jarPath = PLATFORM_MODULES.resolve(o.jar());
        assertThat(jarPath).as("the assembled cluster's %s", o.jar()).exists();
        Properties props = new Properties();
        try (JarFile jar = new JarFile(jarPath.toFile());
                InputStream in = jar.getInputStream(jar.getEntry(o.pkg() + "/Bundle.properties"))) {
            props.load(in);
        }
        return props;
    }

    private static Properties load(Path p) throws IOException {
        Properties props = new Properties();
        // .properties are ISO-8859-1 with \\uXXXX escapes, which is what the
        // platform's own loader expects and what these files are written as
        props.load(new StringReader(Files.readString(p, StandardCharsets.UTF_8)));
        return props;
    }
}
