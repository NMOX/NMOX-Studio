package org.nmox.studio.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The illustrated-docs law (v2.32.0): every relative image reference in
 * the live docs resolves to a real file, and every image in
 * docs/images/** is referenced by some live doc — a dead ref renders as
 * a broken box on GitHub, and an orphan image is a shot that rotted out
 * of the story without anyone noticing. Census-floored so an empty
 * parse can never fake green (the count-gate law).
 */
class ImageRefsTest {

    private static final Pattern IMG = Pattern.compile("!\\[[^\\]]*\\]\\(([^)\\s]+)\\)");

    @Test
    @DisplayName("every image ref resolves; every image is referenced")
    void refsAndFilesAgree() throws Exception {
        Path root = Path.of("..").toRealPath();
        List<Path> docs = new ArrayList<>();
        docs.add(root.resolve("README.md"));
        try (Stream<Path> s = Files.walk(root.resolve("docs"))) {
            s.filter(p -> p.toString().endsWith(".md")).forEach(docs::add);
        }
        List<String> deadRefs = new ArrayList<>();
        java.util.Set<Path> referenced = new java.util.HashSet<>();
        int refs = 0;
        for (Path doc : docs) {
            Matcher m = IMG.matcher(Files.readString(doc));
            while (m.find()) {
                String target = m.group(1);
                if (target.startsWith("http")) {
                    continue;             // badges and external links
                }
                refs++;
                Path resolved = doc.getParent().resolve(target).normalize();
                if (Files.isRegularFile(resolved)) {
                    referenced.add(resolved.toRealPath());
                } else {
                    deadRefs.add(doc.getFileName() + " -> " + target);
                }
            }
        }
        assertThat(deadRefs).as("image refs that resolve to no file").isEmpty();
        assertThat(refs).as("the census found the real refs").isGreaterThan(20);

        List<String> orphans = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root.resolve("docs/images"))) {
            s.filter(p -> p.toString().endsWith(".png")
                    || p.toString().endsWith(".gif")).forEach(img -> {
                try {
                    if (!referenced.contains(img.toRealPath())) {
                        orphans.add(root.relativize(img).toString());
                    }
                } catch (java.io.IOException ignore) {
                }
            });
        }
        assertThat(orphans)
                .as("images no live doc references — wire or remove them")
                .isEmpty();
    }
}
