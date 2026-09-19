package org.nmox.studio.rack.projectstudio;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 111's structural half, rack side. The behavioural walks next
 * door prove the refusals; this proves there is still only ONE rule.
 *
 * <p>The defect the ledger named was not a wrong answer — it was four
 * implementations of one decision, three of whose javadocs called
 * themselves the canonical one. A fifth copy would be the same defect
 * again, and no behavioural test can see a copy that merely agrees
 * today, so the population is named here and checked against source.
 *
 * <p>The shape the gate hunts is the RESOLVE shape specifically:
 * building a child out of a caller-supplied relative
 * ({@code new File(root, rel)}) and then judging it with
 * {@code getCanonicalFile()} or {@code normalize()}. Deliberately out
 * of scope: {@code LearningSpace.promote}'s check that an EXISTING
 * directory sits under the spaces root. That answers a different
 * question — is this file mine? — not "which file does this string
 * name inside this root?", and folding it in would change a
 * destructive move's refusals without a walk to back it (the ledger's
 * own reason for holding this family back).
 */
class ContainmentSingleHomeTest {

    private static final String[][] POPULATION = {
        {"projectstudio", "LearningSpace.java"},
        {"projectstudio", "Checkpoints.java"},
        {"projectstudio", "CheckDisclosure.java"},
        {"docker", "DockerRecipes.java"},
    };

    private static String source(String... parts) throws Exception {
        // CRLF checkouts (the windows lane) — normalize before asserting
        return Files.readString(Path.of("src", "main", "java", "org", "nmox",
                "studio", "rack", String.join("/", parts)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    /** Source with every whitespace run flattened, so a line break
     *  inside a call cannot hide it from a literal match. */
    private static String flat(String... parts) throws Exception {
        return source(parts).replaceAll("\\s+", " ");
    }

    @Test
    @DisplayName("every rack containment call names core.util.Containment")
    void oneHome() throws Exception {
        assertThat(flat("projectstudio", "LearningSpace.java"))
                .as("a drop-in catalog's sample files ride the ONE guard")
                .contains("Containment.resolve(dir, f.path())");
        assertThat(flat("projectstudio", "Checkpoints.java"))
                .as("a checkpoint's file path rides the ONE guard")
                .contains("Containment .resolve(spaceDir, c.filePath())");
        assertThat(flat("projectstudio", "CheckDisclosure.java"))
                .as("what an outward AI flow reads rides the ONE guard")
                .contains("Containment .resolve(spaceDir, c.filePath())");
        assertThat(flat("docker", "DockerRecipes.java"))
                .as("the Dockerize WRITER rides the ONE guard")
                .contains("Containment.resolvePath(dir, name)");
    }

    @Test
    @DisplayName("and none of them re-rolls the decision")
    void noSecondRule() throws Exception {
        for (String[] file : POPULATION) {
            for (String line : source(file).split("\n")) {
                boolean resolvesAChild = line.contains("new File(")
                        && (line.contains("getCanonicalFile()")
                        || line.contains("getCanonicalPath()")
                        || line.contains(".normalize()"));
                assertThat(resolvesAChild)
                        .as(file[1] + " must not carry a second containment rule —"
                                + " one decision, one home (ledger 111): " + line.trim())
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("the refusal SENTENCES stay with their surfaces — the guard decides, they speak")
    void refusalsStayLocal() throws Exception {
        assertThat(source("docker", "DockerRecipes.java"))
                .as("the writer's own words, not the guard's")
                .contains("Refusing to write outside the project: ");
        assertThat(source("projectstudio", "LearningSpace.java"))
                .as("a skipped sample file must SAY so — a silent skip reads"
                        + " like a catalog that never declared it")
                .contains("resolves outside");
    }
}
