package org.nmox.studio.core.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The IDE's own workspace files are recognizable by name (v1.281.0).
 *
 * <p>The predicate is a naming CONVENTION rather than a hand-kept list,
 * so a studio shipped next year is covered on its first day. The second
 * test is what keeps that true: it reads every workspace filename the
 * product actually writes out of the sources and fails the build if one
 * stops matching — the enumeration-beats-recollection idiom.
 */
class IdeWorkspaceFilesTest {

    /** Modules that persist a per-project workspace file. */
    private static final List<String> MODULES =
            List.of("rack", "apiclient", "dbstudio", "web3", "infra");

    @Test
    @DisplayName("the studios' workspace files are ours; the user's files are not")
    void recognisesOwnFiles() {
        assertThat(IdeWorkspaceFiles.isOwn(".nmoxrack.json")).isTrue();
        assertThat(IdeWorkspaceFiles.isOwn(".nmoxapi.json")).isTrue();

        assertThat(IdeWorkspaceFiles.isOwn("package.json"))
                .as("the user's own manifest is source — a save there IS work")
                .isFalse();
        assertThat(IdeWorkspaceFiles.isOwn("nmoxrack.json"))
                .as("without the leading dot it is a file someone wrote by hand")
                .isFalse();
        assertThat(IdeWorkspaceFiles.isOwn(".nmoxrack.json.bak"))
                .as("a .bak sibling is not the live workspace file")
                .isFalse();
        assertThat(IdeWorkspaceFiles.isOwn(null)).isFalse();
    }

    @Test
    @DisplayName("every workspace filename in the product matches the convention")
    void allShippedWorkspaceFilesAreCovered() throws IOException {
        Pattern literal = Pattern.compile("\"(\\.[A-Za-z0-9_.-]*\\.json)\"");
        Set<String> found = new TreeSet<>();
        for (String module : MODULES) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    Matcher m = literal.matcher(
                            Files.readString(p, StandardCharsets.UTF_8));
                    while (m.find()) {
                        String name = m.group(1);
                        if (name.startsWith(".nmox")) {
                            found.add(name);
                        }
                    }
                }
            }
        }
        assertThat(found)
                .as("the scan must actually find the studios' files — an empty"
                        + " result would make this gate vacuously green")
                .hasSizeGreaterThanOrEqualTo(4);
        assertThat(found).allSatisfy(name -> assertThat(IdeWorkspaceFiles.isOwn(name))
                .as("%s is written by the IDE but would be seen as user source", name)
                .isTrue());
    }
}
