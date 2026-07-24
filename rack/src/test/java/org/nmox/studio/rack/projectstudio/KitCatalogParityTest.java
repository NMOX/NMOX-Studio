package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The v1.141.0 debt sprint's drift gate: the Contract Kit's templates and
 * the learning-catalog spaces carry the SAME live-proven starters, which
 * means the same dependency pins live in two places with nothing tying
 * them together. Pin rot (the soroban-sdk "23" lesson) must hit ONE loud
 * gate, not surface as a user's broken scaffold months later. This test
 * scaffolds each kit chain and asserts its pin equals the corresponding
 * catalog space's pin.
 */
class KitCatalogParityTest {

    @TempDir
    Path root;

    /** kit chain → (catalog slug, the pinned dependency's crate name). */
    private static final Map<ContractKit.Chain, String[]> PAIRS = Map.of(
            ContractKit.Chain.SOROBAN, new String[]{"stellar-soroban", "soroban-sdk"},
            ContractKit.Chain.SOLANA, new String[]{"solana-native", "solana-program"},
            ContractKit.Chain.COSMWASM, new String[]{"cosmwasm", "cosmwasm-std"},
            ContractKit.Chain.INK, new String[]{"ink-polkadot", "ink"},
            ContractKit.Chain.BITCOIN, new String[]{"bitcoin-miniscript", "miniscript"},
            ContractKit.Chain.CLARITY, new String[]{"clarity-stacks", "@stacks/clarinet-sdk"});

    @Test
    @DisplayName("Every shared dependency pin matches between kit template and catalog space")
    void kitAndCatalogPinsAgree() throws Exception {
        for (var entry : PAIRS.entrySet()) {
            ContractKit.Chain chain = entry.getKey();
            String slug = entry.getValue()[0];
            String crate = entry.getValue()[1];

            File dir = Files.createDirectories(root.resolve(chain.name())).toFile();
            ContractKit.scaffold(dir, chain, "Vault");
            String kitPin = pinIn(readAll(dir), crate);
            String catalogPin = pinIn(catalogFiles(slug), crate);

            assertThat(kitPin)
                    .as("%s: kit pins %s, catalog pins %s — the same starter, "
                            + "two homes: keep them in lockstep", crate, kitPin, catalogPin)
                    .isEqualTo(catalogPin);
        }
    }

    /** The first version number pinned to {@code crate}, tolerating the
     *  cargo forms ({@code crate = "X"}, {@code crate = { version = "X" }})
     *  and the npm form ({@code "crate": "^X"}). */
    private static String pinIn(String manifests, String crate) {
        Matcher m = Pattern.compile(
                "\"?" + Pattern.quote(crate)
                + "\"?\\s*[=:]\\s*(?:\\{[^}]*version\\s*=\\s*)?\"([^\"]+)\"")
                .matcher(manifests);
        assertThat(m.find()).as("a pin for %s exists", crate).isTrue();
        return m.group(1);
    }

    private static String readAll(File dir) throws Exception {
        StringBuilder all = new StringBuilder();
        try (var walk = Files.walk(dir.toPath())) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                all.append(Files.readString(p)).append('\n');
            }
        }
        return all.toString();
    }

    /** The concatenated file bodies of a learning-catalog space. */
    private static String catalogFiles(String slug) {
        for (LearningCatalog.Space s : LearningCatalog.all()) {
            if (s.slug().equals(slug)) {
                StringBuilder all = new StringBuilder();
                s.files().forEach(f -> all.append(f.content()).append('\n'));
                return all.toString();
            }
        }
        throw new AssertionError("no catalog space: " + slug);
    }
}
