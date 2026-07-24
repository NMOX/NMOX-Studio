package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Contract Kit's laws: every chain scaffolds a manifest + contract +
 * test + notes, the contract name lands in each dialect's casing, a
 * re-run is a no-op, and an existing differing file is never clobbered
 * (the .suggested sibling law). The templates themselves are the
 * v1.130–v1.137 arc's live-proven starters.
 */
class ContractKitTest {

    @TempDir
    Path root;

    @ParameterizedTest
    @EnumSource(ContractKit.Chain.class)
    @DisplayName("Every chain scaffolds manifest + contract + test + notes, name templated")
    void scaffoldsEveryChain(ContractKit.Chain chain) throws Exception {
        File dir = Files.createDirectories(root.resolve(chain.name().toLowerCase())).toFile();
        List<ContractKit.Outcome> outcomes =
                ContractKit.scaffold(dir, chain, "SkyVault");

        assertThat(outcomes).as("%s writes files", chain).isNotEmpty();
        assertThat(outcomes).allMatch(o -> o.status().equals("written"));
        assertThat(new File(dir, "CONTRACT-NOTES.md")).isFile();

        // the name lands in the dialect's own casing somewhere real
        String all = concatenated(dir);
        switch (chain) {
            case FOUNDRY -> {
                assertThat(new File(dir, "src/SkyVault.sol")).isFile();
                assertThat(all).contains("contract SkyVault");
            }
            case SOROBAN -> assertThat(all)
                    .contains("name = \"sky_vault\"").contains("pub struct SkyVault");
            case SOLANA, COSMWASM -> assertThat(all).contains("name = \"sky_vault\"");
            case INK -> assertThat(all)
                    .contains("name = \"sky_vault\"").contains("mod sky_vault")
                    .contains("pub struct SkyVault");
            case CAIRO -> assertThat(all).contains("name = \"sky_vault\"");
            case MOVE -> assertThat(all)
                    .contains("name = \"sky_vault\"").contains("module sky_vault::counter");
        }
    }

    @ParameterizedTest
    @EnumSource(ContractKit.Chain.class)
    @DisplayName("A re-run is a perfect no-op — idempotent by law")
    void rerunIsNoOp(ContractKit.Chain chain) throws Exception {
        File dir = Files.createDirectories(root.resolve("re-" + chain.name())).toFile();
        ContractKit.scaffold(dir, chain, "SkyVault");
        List<ContractKit.Outcome> second = ContractKit.scaffold(dir, chain, "SkyVault");
        assertThat(second)
                .as("%s second run changes nothing", chain)
                .allMatch(o -> !o.changed())
                .allMatch(o -> o.status().equals("already exists, untouched"));
    }

    @Test
    @DisplayName("An existing differing file is kept; the proposal lands as .suggested")
    void neverClobbers() throws Exception {
        File dir = Files.createDirectories(root.resolve("clobber")).toFile();
        Files.writeString(dir.toPath().resolve("Scarb.toml"), "# my hand-tuned manifest\n");
        List<ContractKit.Outcome> outcomes =
                ContractKit.scaffold(dir, ContractKit.Chain.CAIRO, "SkyVault");

        assertThat(Files.readString(dir.toPath().resolve("Scarb.toml")))
                .as("the user's file is untouched")
                .isEqualTo("# my hand-tuned manifest\n");
        assertThat(new File(dir, "Scarb.toml.suggested")).isFile();
        assertThat(outcomes).anyMatch(o -> o.path().equals("Scarb.toml.suggested"));
    }

    @Test
    @DisplayName("Names are identifiers; casing helpers do what the dialects need")
    void validationAndCasing() {
        assertThat(ContractKit.validate("SkyVault")).isNull();
        assertThat(ContractKit.validate("sky_vault9")).isNull();
        assertThat(ContractKit.validate("")).isNotNull();
        assertThat(ContractKit.validate("9lives")).isNotNull();
        assertThat(ContractKit.validate("my-token")).isNotNull();
        assertThat(ContractKit.pascal("sky_vault")).isEqualTo("SkyVault");
        assertThat(ContractKit.snake("SkyVault")).isEqualTo("sky_vault");
        assertThat(ContractKit.snake("sky_vault")).isEqualTo("sky_vault");
    }

    private static String concatenated(File dir) throws Exception {
        StringBuilder all = new StringBuilder();
        try (var walk = Files.walk(dir.toPath())) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                all.append(Files.readString(p)).append('\n');
            }
        }
        return all.toString();
    }
}
