package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A chord written the way a Mac writes it reaches the screen only on a Mac.
 *
 * <p>The product's strings name their chords in Mac notation — {@code ⌥⌘7},
 * {@code ⌘-click} — in all fifteen languages. Every walk before 3.5 was
 * taken on a Mac. The first ones taken on Windows and Linux photographed the
 * Welcome telling the reader to press ⌥⌘K on a keyboard with neither key,
 * one row above a search field the platform had correctly labelled
 * {@code Ctrl+Shift+P}.
 *
 * <p>The strings stay as written and {@code Chords.forThisOs} turns each
 * into the reader's own keys where it is shown. This gate holds every
 * consumer to that. The population is derived, twice over:
 * <ul>
 *   <li>every bundle key, in any language, whose shipped value carries one
 *       of ⌘ ⌥ ⇧ ⌃ — each use of such a key in the sources is an argument of
 *       {@code Chords.forThisOs};</li>
 *   <li>every string literal in the sources that carries one, which is a
 *       bundle declaration, an argument of {@code Chords.forThisOs}, or in a
 *       file whose whole text is converted where it leaves.</li>
 * </ul>
 * So a string written tomorrow with a Mac chord in it fails here until
 * somebody decides how a Windows reader will see it.
 *
 * <p>Runs with the packaged-app gates: the bundles are read from the
 * assembled cluster, where {@code @Messages} values have been compiled in.
 */
class MacChordsReachOnlyMacsGateTest {

    private static final Path MODULES = Path.of("target", "nmoxstudio", "nmoxstudio", "modules");
    private static final Path REPO = Path.of("..");
    private static final List<String> SOURCE_MODULES = List.of(
            "core", "editor", "tools", "rack", "project", "ui", "apiclient", "dbstudio", "web3", "infra");

    private static final Pattern MAC_GLYPH = Pattern.compile("[⌘⌥⇧⌃]");
    private static final Pattern STRING = Pattern.compile("\"(?:[^\"\\\\\\n]|\\\\.)*\"");
    private static final String CONVERT = "Chords.forThisOs(";

    /**
     * Files whose literals are parts of ONE text that is converted where it
     * leaves the file — each with the statement that does it, checked.
     */
    private static final Map<String, String> CONVERTED_WHOLE = Map.of(
            "rack/src/main/java/org/nmox/studio/rack/projectstudio/ExperimentGuide.java",
            "return org.nmox.studio.core.util.Chords.forThisOs(b.toString());",
            // the vocabulary itself: its Mac branch is where the glyphs are made
            "core/src/main/java/org/nmox/studio/core/util/Chords.java",
            "public static String forOs(String text, boolean mac)");

    /** Resources that carry Mac chords, and the statement that converts them on the way out. */
    private static final Map<String, List<String>> CONVERTED_RESOURCES = Map.of(
            "rack/src/main/resources/org/nmox/studio/rack/projectstudio/learn-catalog.json",
            List.of("rack/src/main/java/org/nmox/studio/rack/projectstudio/LearningSpace.java",
                    "org.nmox.studio.core.util.Chords.forThisOs(tutorialWithInstall(space))"));

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /** Every bundle key, in any language, whose shipped value names a Mac modifier. */
    static Set<String> chordKeys() throws IOException {
        assertThat(MODULES).as("the assembled cluster's own modules").isDirectory();
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> jars = Files.list(MODULES)) {
            for (Path jarPath : jars.filter(p -> p.getFileName().toString().startsWith("org-nmox-")
                    && p.toString().endsWith(".jar")).toList()) {
                try (JarFile jar = new JarFile(jarPath.toFile())) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry e = entries.nextElement();
                        String name = e.getName();
                        String file = name.substring(name.lastIndexOf('/') + 1);
                        if (!file.startsWith("Bundle") || !file.endsWith(".properties")) {
                            continue;
                        }
                        Properties props = new Properties();
                        // these bundles ship raw UTF-8: read as such (v2.129.0)
                        try (Reader in = new InputStreamReader(jar.getInputStream(e), StandardCharsets.UTF_8)) {
                            props.load(in);
                        }
                        for (String key : props.stringPropertyNames()) {
                            if (MAC_GLYPH.matcher(props.getProperty(key)).find()) {
                                keys.add(key);
                            }
                        }
                    }
                }
            }
        }
        return keys;
    }

    /** True when the text before {@code at} ends, whitespace aside, with the conversion's opening. */
    private static boolean isArgumentOfConvert(String body, int at) {
        int i = at;
        while (i > 0 && Character.isWhitespace(body.charAt(i - 1))) {
            i--;
        }
        return body.substring(0, i).endsWith(CONVERT);
    }

    @Test
    @DisplayName("the cluster carries chord-naming values, so this gate has a population")
    void thePopulationIsReal() throws IOException {
        assertThat(chordKeys()).as("bundle keys whose value names a Mac modifier")
                .hasSizeGreaterThan(15)
                .contains("MainWindow_dbStudio", "GhostText_inserted", "GettingStarted_runGesture");
    }

    @Test
    @DisplayName("every use of a chord-naming bundle key is an argument of Chords.forThisOs")
    void everyChordKeyIsConvertedWhereItIsUsed() throws IOException {
        Set<String> keys = chordKeys();
        List<String> bare = new ArrayList<>();
        int uses = 0;
        for (String module : SOURCE_MODULES) {
            Path src = REPO.resolve(module).resolve("src").resolve("main").resolve("java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(src)) {
                for (Path java : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String body = GateSources.stripComments(read(java));
                    for (String key : keys) {
                        // the generated accessor, and the key handed to NbBundle by name
                        for (String form : new String[] {"Bundle." + key + "(", "\"" + key + "\""}) {
                            int at = -1;
                            while ((at = body.indexOf(form, at + 1)) >= 0) {
                                if (form.startsWith("\"") && !handedToBundle(body, at)) {
                                    continue;
                                }
                                uses++;
                                if (!isArgumentOfConvert(body, at)) {
                                    int line = 1 + (int) body.chars().limit(at).filter(c -> c == '\n').count();
                                    bare.add(module + "/" + java.getFileName() + ":" + line + " " + key);
                                }
                            }
                        }
                    }
                }
            }
        }
        assertThat(uses).as("uses of chord-naming keys found in the sources").isGreaterThan(15);
        assertThat(bare)
                .as("a string that names a Mac chord, shown without Chords.forThisOs(…) around it")
                .isEmpty();
    }

    /** A quoted key counts as a use only where it is an argument of an NbBundle lookup. */
    private static boolean handedToBundle(String body, int at) {
        int from = Math.max(0, at - 120);
        String before = body.substring(from, at);
        int stmt = Math.max(before.lastIndexOf(';'), Math.max(before.lastIndexOf('{'), before.lastIndexOf('}')));
        return before.substring(stmt + 1).contains("getMessage(");
    }

    @Test
    @DisplayName("every string literal that names a Mac chord is a bundle declaration, converted, or in a converted text")
    void everyChordLiteralIsDecided() throws IOException {
        List<String> undecided = new ArrayList<>();
        for (String module : SOURCE_MODULES) {
            Path src = REPO.resolve(module).resolve("src").resolve("main").resolve("java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(src)) {
                for (Path java : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String rel = REPO.relativize(java).toString().replace('\\', '/');
                    if (CONVERTED_WHOLE.containsKey(rel)) {
                        continue;
                    }
                    String body = GateSources.stripComments(read(java));
                    Matcher s = STRING.matcher(body);
                    while (s.find()) {
                        String literal = s.group();
                        if (!MAC_GLYPH.matcher(literal).find()) {
                            continue;
                        }
                        // anything inside @Messages(…) is a bundle entry — its key, a
                        // "# comment", or the rest of a value written across several
                        // literals: the bundle's, judged above
                        if (insideMessages(body, s.start())) {
                            continue;
                        }
                        if (isArgumentOfConvert(body, s.start())) {
                            continue;
                        }
                        int line = 1 + (int) body.chars().limit(s.start()).filter(c -> c == '\n').count();
                        undecided.add(rel + ":" + line + " " + literal);
                    }
                }
            }
        }
        assertThat(undecided)
                .as("a literal naming a Mac chord: wrap it in Chords.forThisOs(…), or move it to a bundle")
                .isEmpty();
    }

    /** Whether {@code at} lies inside the parentheses of an {@code @Messages} annotation. */
    private static boolean insideMessages(String body, int at) {
        int open = -1;
        int from = 0;
        while (true) {
            int found = body.indexOf("Messages(", from);
            if (found < 0 || found > at) {
                break;
            }
            // @Messages(, @NbBundle.Messages( or the fully qualified form:
            // back over the dotted name to the @ that makes it an annotation
            int head = found - 1;
            while (head >= 0 && (Character.isJavaIdentifierPart(body.charAt(head)) || body.charAt(head) == '.')) {
                head--;
            }
            if (head >= 0 && body.charAt(head) == '@') {
                open = found + "Messages(".length();
            }
            from = found + 1;
        }
        if (open < 0) {
            return false;
        }
        // walk from the opening parenthesis to its match, skipping string literals
        int depth = 1;
        for (int i = open; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"') {
                i++;
                while (i < body.length() && body.charAt(i) != '"') {
                    if (body.charAt(i) == '\\') {
                        i++;
                    }
                    i++;
                }
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return at < i;
            }
        }
        return false;
    }

    @Test
    @DisplayName("each text converted as a whole really is, and so is each resource that names chords")
    void theBlessingsAreTrue() throws IOException {
        for (Map.Entry<String, String> e : CONVERTED_WHOLE.entrySet()) {
            assertThat(read(REPO.resolve(e.getKey()))).as(e.getKey()).contains(e.getValue());
        }
        for (Map.Entry<String, List<String>> e : CONVERTED_RESOURCES.entrySet()) {
            assertThat(MAC_GLYPH.matcher(read(REPO.resolve(e.getKey()))).find())
                    .as("%s still names a Mac chord (a blessing nothing needs is stale)", e.getKey()).isTrue();
            assertThat(read(REPO.resolve(e.getValue().get(0)))).as(e.getValue().get(0))
                    .contains(e.getValue().get(1));
        }
        // and no OTHER shipped text resource names one undecided
        List<String> undecided = new ArrayList<>();
        for (String module : SOURCE_MODULES) {
            Path resources = REPO.resolve(module).resolve("src").resolve("main").resolve("resources");
            if (!Files.isDirectory(resources)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(resources)) {
                for (Path p : walk.filter(f -> {
                    String n = f.toString();
                    return n.endsWith(".json") || n.endsWith(".md") || n.endsWith(".html")
                            || n.endsWith(".txt") || n.endsWith(".js");
                }).toList()) {
                    String rel = REPO.relativize(p).toString().replace('\\', '/');
                    if (!CONVERTED_RESOURCES.containsKey(rel) && MAC_GLYPH.matcher(read(p)).find()) {
                        undecided.add(rel);
                    }
                }
            }
        }
        assertThat(undecided).as("shipped text resources naming a Mac chord, with no conversion on record")
                .isEmpty();
    }
}
