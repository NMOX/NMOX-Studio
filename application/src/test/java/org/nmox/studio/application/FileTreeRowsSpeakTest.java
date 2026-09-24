package org.nmox.studio.application;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every platform row on Project Studio's file tree speaks the reader's
 * language (3.1.0).
 *
 * <p>The tree's right-click is not an editor popup, so the v2.145.0 census
 * never read it. Its rows are the {@code SystemAction}s FileTreePanel lists
 * by hand, and 3.1.0 found four of them (Rename, Open, Properties and the
 * New that replaced a dead Add) painting English in all fourteen translated
 * builds, beside the Copy Path and Reveal rows the same release translated.
 *
 * <p>The population is DERIVED: every {@code org.openide.actions.X.class}
 * the panel's source names. Each needs an entry in {@link #KEYS} saying which
 * bundle key its name comes from, and that entry is checked against the
 * shipped class itself, whose constant pool must carry the key, so a
 * platform upgrade that renames a key fails here rather than shipping an
 * English row. The key must be in popup-rows.txt, whose laws
 * (PopupRowsSpeakTest) hold it overlaid, non-blank and well-formed in every
 * language.
 */
class FileTreeRowsSpeakTest {

    private static final Path SOURCE = Path.of("..", "rack", "src", "main", "java", "org", "nmox",
            "studio", "rack", "projectstudio", "FileTreePanel.java");
    private static final Path CLUSTER = Path.of("target", "nmoxstudio");

    private record Key(String jar, String pkg, String key) {}

    /** The action class, the jar that ships it, and the bundle key its name reads. */
    private static final Map<String, Key> KEYS = Map.ofEntries(
            Map.entry("NewTemplateAction", new Key("org-openide-loaders.jar", "org/openide/loaders", "NewTemplate")),
            Map.entry("FindAction", new Key("org-openide-actions.jar", "org/openide/actions", "Find")),
            Map.entry("CutAction", new Key("org-openide-actions.jar", "org/openide/actions", "Cut")),
            Map.entry("CopyAction", new Key("org-openide-actions.jar", "org/openide/actions", "Copy")),
            Map.entry("PasteAction", new Key("org-openide-actions.jar", "org/openide/actions", "Paste")),
            Map.entry("DeleteAction", new Key("org-openide-actions.jar", "org/openide/actions", "Delete")),
            Map.entry("RenameAction", new Key("org-openide-actions.jar", "org/openide/actions", "Rename")),
            Map.entry("OpenAction", new Key("org-openide-actions.jar", "org/openide/actions", "Open")),
            Map.entry("ToolsAction", new Key("org-openide-actions.jar", "org/openide/actions", "CTL_Tools")),
            Map.entry("PropertiesAction", new Key("org-openide-actions.jar", "org/openide/actions", "Properties")));

    private static Set<String> treeActions() throws IOException {
        String src = GateSources.stripComments(Files.readString(SOURCE, StandardCharsets.UTF_8));
        Matcher m = Pattern.compile("org\\.openide\\.actions\\.(\\w+)\\.class").matcher(src);
        Set<String> out = new LinkedHashSet<>();
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static Set<String> ledgerKeys() throws IOException {
        Set<String> out = new HashSet<>();
        try (InputStream in = FileTreeRowsSpeakTest.class.getResourceAsStream("popup-rows.txt")) {
            assertThat(in).as("the popup ledger resource").isNotNull();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\|", 4);
                out.add(p[0] + "|" + p[1] + "|" + p[2]);
            }
        }
        return out;
    }

    private static Path findJar(String name) throws IOException {
        try (var walk = Files.walk(CLUSTER)) {
            return walk.filter(p -> p.getFileName().toString().equals(name)).findFirst().orElse(null);
        }
    }

    /** Every CONSTANT_Utf8 in a class file: the strings the class can name. */
    static Set<String> utf8Constants(byte[] classFile) throws IOException {
        Set<String> out = new HashSet<>();
        DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(classFile));
        in.readInt();          // magic
        in.readUnsignedShort(); // minor
        in.readUnsignedShort(); // major
        int count = in.readUnsignedShort();
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> out.add(in.readUTF());
                case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipNBytes(4);
                case 5, 6 -> {
                    in.skipNBytes(8);
                    i++;   // a long or double takes two slots
                }
                case 7, 8, 16, 19, 20 -> in.skipNBytes(2);
                case 15 -> in.skipNBytes(3);
                default -> throw new IOException("constant pool tag " + tag + " at " + i);
            }
        }
        return out;
    }

    @Test
    @DisplayName("every platform row the tree lists is known, names its key, and is ledgered")
    void everyTreeRowSpeaks() throws IOException {
        Set<String> actions = treeActions();
        assertThat(actions).as("the source scan found the tree's rows at all").contains("RenameAction");
        Set<String> ledger = ledgerKeys();
        List<String> problems = new ArrayList<>();
        for (String action : actions) {
            Key k = KEYS.get(action);
            if (k == null) {
                problems.add(action + ": FileTreePanel lists it and nothing here says which key its "
                        + "name reads; find it (javap -c its getName), add it to KEYS and to popup-rows.txt, "
                        + "and overlay it in every language");
                continue;
            }
            Path jar = findJar(k.jar());
            assertThat(jar).as(k.jar() + " in the assembled cluster").isNotNull();
            try (JarFile jf = new JarFile(jar.toFile())) {
                ZipEntry e = jf.getEntry("org/openide/actions/" + action + ".class");
                if (e == null) {
                    problems.add(action + ": not in " + k.jar() + " any more");
                    continue;
                }
                byte[] bytes;
                try (InputStream in = jf.getInputStream(e)) {
                    bytes = in.readAllBytes();
                }
                if (!utf8Constants(bytes).contains(k.key())) {
                    problems.add(action + ": the shipped class no longer names \"" + k.key()
                            + "\", so its row reads some other key");
                }
            }
            if (!ledger.contains(k.jar() + "|" + k.pkg() + "|" + k.key())) {
                problems.add(action + ": " + k.key() + " is not in popup-rows.txt, so nothing holds "
                        + "it translated");
            }
        }
        assertThat(problems).as("Project Studio tree rows a reader could meet in English").isEmpty();
    }
}
