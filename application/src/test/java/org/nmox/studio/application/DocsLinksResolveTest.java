package org.nmox.studio.application;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The docs' links law (3.1.0): every relative link in the live docs
 * lands. A link to a file that is not there is a 404 on GitHub; a link
 * to a heading that was renamed or renumbered lands at the top of a
 * two-thousand-line manual, which reads as "this was the right page"
 * and is worse. The first census found exactly that: the smart-contract
 * guide pointed at {@code #8-wizards--kits} and {@code #7-learning-spaces}
 * after the user guide's chapters were renamed and renumbered.
 *
 * <p>Anchors are computed with GitHub's own rule, applied to every
 * heading: lower-case, keep letters, digits, marks, hyphens and
 * underscores, turn spaces into hyphens, drop the rest, and suffix
 * {@code -1}, {@code -2} to repeats. Explicit {@code id}/{@code name}
 * anchors count too. Code fences are skipped - a link inside an example
 * is an example.
 */
class DocsLinksResolveTest {

    private static final Pattern LINK =
            Pattern.compile("(?<!!)\\]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern FENCE = Pattern.compile("```.*?```", Pattern.DOTALL);
    private static final Pattern HEADING =
            Pattern.compile("^#{1,6}\\s+(.*?)\\s*#*\\s*$", Pattern.MULTILINE);
    private static final Pattern HTML_ID = Pattern.compile("\\b(?:id|name)=\"([^\"]+)\"");
    private static final Pattern INLINE_LINK = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");

    @Test
    @DisplayName("every relative link in the live docs resolves to a file, and every #anchor to a heading")
    void everyLinkLands() throws Exception {
        Path root = Path.of("..").toRealPath();
        List<Path> docs = liveDocs(root);
        Map<Path, Set<String>> anchorCache = new HashMap<>();
        List<String> dead = new ArrayList<>();
        int checked = 0;
        int anchored = 0;
        for (Path doc : docs) {
            String text = FENCE.matcher(Files.readString(doc)).replaceAll("");
            Matcher m = LINK.matcher(text);
            while (m.find()) {
                String target = m.group(1);
                if (target.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*") || target.startsWith("<")) {
                    continue;                 // http:, mailto: and the like
                }
                int hash = target.indexOf('#');
                String file = hash < 0 ? target : target.substring(0, hash);
                String frag = hash < 0 ? null : target.substring(hash + 1);
                Path resolved = file.isEmpty() ? doc
                        : doc.getParent().resolve(decode(file)).normalize();
                checked++;
                if (!Files.exists(resolved)) {
                    dead.add(root.relativize(doc) + " -> " + target + "  (no such file)");
                    continue;
                }
                if (frag != null && resolved.toString().endsWith(".md")) {
                    anchored++;
                    Set<String> anchors = anchorCache.computeIfAbsent(resolved, DocsLinksResolveTest::anchors);
                    if (!anchors.contains(decode(frag))) {
                        dead.add(root.relativize(doc) + " -> " + target + "  (no such heading)");
                    }
                }
            }
        }
        assertThat(dead).as("relative links that land nowhere").isEmpty();
        assertThat(checked).as("the census found the real links").isGreaterThan(500);
        assertThat(anchored).as("the census found the real #anchors").isGreaterThan(50);
    }

    @Test
    @DisplayName("the anchor rule is GitHub's: punctuation dropped, spaces hyphenated, repeats numbered")
    void anchorRuleIsGitHubs() {
        assertThat(slug("8. Wizards and kits")).isEqualTo("8-wizards-and-kits");
        assertThat(slug("Block Studio (⌥⌘5)")).isEqualTo("block-studio-5");
        assertThat(slug("Presenting and sharing (v2.87.0)")).isEqualTo("presenting-and-sharing-v2870");
        assertThat(slug("Expand Abbreviation (⌥⌘E)")).isEqualTo("expand-abbreviation-e");
        assertThat(slug("Wizards & kits")).isEqualTo("wizards--kits");
        assertThat(slug("Установка")).isEqualTo("установка");
    }

    static List<Path> liveDocs(Path root) throws Exception {
        List<Path> docs = new ArrayList<>();
        for (String top : new String[] {"README.md", "CONTRIBUTING.md", "INSTALL.md"}) {
            Path p = root.resolve(top);
            if (Files.isRegularFile(p)) {
                docs.add(p);
            }
        }
        try (Stream<Path> s = Files.walk(root.resolve("docs"))) {
            s.filter(p -> p.toString().endsWith(".md")).sorted().forEach(docs::add);
        }
        return docs;
    }

    static Set<String> anchors(Path doc) {
        String text;
        try {
            text = FENCE.matcher(Files.readString(doc)).replaceAll("");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        Set<String> out = new HashSet<>();
        Map<String, Integer> seen = new HashMap<>();
        Matcher h = HEADING.matcher(text);
        while (h.find()) {
            String s = slug(INLINE_LINK.matcher(h.group(1)).replaceAll("$1"));
            int n = seen.merge(s, 1, Integer::sum) - 1;
            out.add(n == 0 ? s : s + "-" + n);
        }
        Matcher id = HTML_ID.matcher(text);
        while (id.find()) {
            out.add(id.group(1));
        }
        return out;
    }

    static String slug(String heading) {
        String h = heading.strip().toLowerCase(java.util.Locale.ROOT).replaceAll("<[^>]+>", "");
        StringBuilder b = new StringBuilder();
        h.codePoints().forEach(cp -> {
            int type = Character.getType(cp);
            if (cp == ' ') {
                b.append('-');
            } else if (cp == '-' || cp == '_' || Character.isLetterOrDigit(cp)
                    || type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                b.appendCodePoint(cp);
            }
        });
        return b.toString();
    }

    private static String decode(String s) {
        return URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
