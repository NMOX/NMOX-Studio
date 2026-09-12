package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A module names itself the way the platform expects to be told.
 *
 * <p>The NetBeans Platform has one mechanism for a module's own name and
 * descriptions — the strings the Plugin Manager lists, the update dialog
 * quotes ("Will update NMOX Studio Rack…") and About ▸ Details shows:
 * the manifest names an {@code OpenIDE-Module-Localizing-Bundle}, and the
 * platform reads {@code OpenIDE-Module-Name}, {@code -Short-Description},
 * {@code -Long-Description} and {@code -Display-Category} from that bundle
 * through the ordinary locale suffix chain. That is what makes the module
 * list read in the user's language for free.
 *
 * <p>Through v2.139.0 no product module declared one. The nbm plugin
 * fills the gap from the pom — decompiled: {@code if (!isLocalized())
 * conditionallyAddAttribute(Name, project.getName()) …} — so ten modules
 * carried English burned into the manifest, the editor kept twelve
 * translations in a bundle nothing pointed at, and the branding module,
 * which DID declare a bundle with every key commented out, showed its
 * artifact id and {@code <undefined>} in every update dialog since v1.51.0.
 * The parity gate knew and stripped the keys, calling them "a rule only one
 * module could break".
 *
 * <p>This gate holds the idiom from the assembled cluster: every product
 * jar declares the bundle, the bundle and all twelve siblings carry the
 * four keys, and the manifest carries NO burned-in copy beside them — a
 * second home for the name would be the v2.131.0 defect.
 */
class ModuleDescriptorsSpeakTest {

    private static final List<String> LOCALES = List.of(
            "es", "fr", "de", "ru", "uk", "pl", "pt", "id", "tl", "vi", "zh", "hi");

    private static final List<String> KEYS = List.of(
            "OpenIDE-Module-Name", "OpenIDE-Module-Display-Category",
            "OpenIDE-Module-Short-Description", "OpenIDE-Module-Long-Description");

    @Test
    @DisplayName("every product module declares a localizing bundle that speaks all twelve languages")
    void everyModuleNamesItselfThroughItsBundle() throws IOException {
        List<Path> jars = productJars();
        assertThat(jars).as("product jars in the assembled cluster").isNotEmpty();
        List<String> problems = new ArrayList<>();
        for (Path jar : jars) {
            String name = jar.getFileName().toString();
            try (JarFile jf = new JarFile(jar.toFile())) {
                Manifest mf = jf.getManifest();
                String bundle = mf.getMainAttributes().getValue("OpenIDE-Module-Localizing-Bundle");
                if (bundle == null) {
                    problems.add(name + ": no OpenIDE-Module-Localizing-Bundle in the manifest");
                    continue;
                }
                // The source manifest DECLARES OpenIDE-Module, because the nbm
                // plugin's ExamineManifest reads OpenIDE-Module-Localizing-Bundle
                // only from a manifest that names its module (decompiled:
                // processManifest sets netBeansModule from the OpenIDE-Module
                // attribute and skips every other read when it is absent), and
                // without isLocalized() the plugin burns the pom's English name
                // and description in beside the bundle. That declaration is a
                // second home for the code name the pom derives from
                // groupId + artifactId, so the two are held equal here: the
                // plugin names the jar from ITS value, the manifest carries OURS.
                String expectedCode = name.substring(0, name.length() - ".jar".length()).replace('-', '.');
                String declared = mf.getMainAttributes().getValue("OpenIDE-Module");
                if (!expectedCode.equals(declared)) {
                    problems.add(name + ": OpenIDE-Module is " + declared + " but the jar is named for "
                            + expectedCode + " \u2014 the source manifest and the pom disagree about the code name");
                }
                for (String burned : List.of("OpenIDE-Module-Name",
                        "OpenIDE-Module-Short-Description", "OpenIDE-Module-Long-Description")) {
                    if (mf.getMainAttributes().getValue(burned) != null) {
                        problems.add(name + ": " + burned + " burned into the manifest beside the "
                                + "bundle — a second home for the name");
                    }
                }
                String pkg = bundle.substring(0, bundle.lastIndexOf('/') + 1);
                checkBundle(jf, bundle, name + " (English)", problems);
                for (String loc : LOCALES) {
                    checkBundle(jf, pkg + "Bundle_" + loc + ".properties", name + " " + loc, problems);
                }
            }
        }
        assertThat(problems).as("a module the Plugin Manager cannot name in the reader's language")
                .isEmpty();
    }

    @Test
    @DisplayName("the splash title reaches every language through the branding overlay")
    void theSplashSpeaks() throws IOException {
        List<String> missing = new ArrayList<>();
        for (String loc : LOCALES) {
            Path overlay = cluster().resolve("core/locale/core_nmoxstudio_" + loc + ".jar");
            if (!Files.isRegularFile(overlay)) {
                missing.add(loc + ": no core_nmoxstudio_" + loc + ".jar overlay");
                continue;
            }
            try (JarFile jf = new JarFile(overlay.toFile())) {
                ZipEntry e = jf.getEntry("org/netbeans/core/startup/Bundle_nmoxstudio_" + loc + ".properties");
                if (e == null) {
                    missing.add(loc + ": overlay carries no startup bundle");
                    continue;
                }
                Properties p = read(jf, e);
                String title = p.getProperty("LBL_splash_window_title");
                if (title == null || title.isBlank() || title.equals("Starting NMOX Studio")) {
                    missing.add(loc + ": LBL_splash_window_title = " + title);
                }
            }
        }
        assertThat(missing).as("the first sentence a non-English user sees").isEmpty();
    }

    private static void checkBundle(JarFile jf, String entry, String where, List<String> problems)
            throws IOException {
        ZipEntry e = jf.getEntry(entry);
        if (e == null) {
            problems.add(where + ": " + entry + " missing from the jar");
            return;
        }
        Properties p = read(jf, e);
        for (String k : KEYS) {
            String v = p.getProperty(k);
            if (v == null || v.isBlank()) {
                problems.add(where + ": " + k + " absent or blank");
            }
        }
    }

    private static Properties read(JarFile jf, ZipEntry e) throws IOException {
        Properties p = new Properties();
        try (InputStream in = jf.getInputStream(e)) {
            // raw UTF-8 in the jar; Properties.load(InputStream) is ISO-8859-1
            // by contract and would read its own decoding (the v2.129.0 defect)
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    private static Path cluster() {
        return Path.of("target/nmoxstudio/nmoxstudio");
    }

    private static List<Path> productJars() throws IOException {
        Path modules = cluster().resolve("modules");
        if (!Files.isDirectory(modules)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(modules)) {
            return s.filter(p -> p.getFileName().toString().startsWith("org-nmox-NMOX-Studio")
                    && p.getFileName().toString().endsWith(".jar")).sorted().toList();
        }
    }
}
