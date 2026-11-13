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
            ContractKit.Chain.CLARITY, new String[]{"clarity-stacks", "@stacks/clarinet-sdk"},
            ContractKit.Chain.AIKEN, new String[]{"cardano-aiken", "aiken-lang/stdlib"},
            ContractKit.Chain.TACT, new String[]{"ton-tact", "@tact-lang/compiler"});

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

    /** kit chain → catalog slug, for every chain the catalog also teaches. */
    private static final Map<ContractKit.Chain, String> SAME_STARTER = Map.of(
            ContractKit.Chain.SOROBAN, "stellar-soroban",
            ContractKit.Chain.SOLANA, "solana-native",
            ContractKit.Chain.COSMWASM, "cosmwasm",
            ContractKit.Chain.INK, "ink-polkadot",
            ContractKit.Chain.BITCOIN, "bitcoin-miniscript",
            ContractKit.Chain.CLARITY, "clarity-stacks",
            ContractKit.Chain.AIKEN, "cardano-aiken",
            ContractKit.Chain.TACT, "ton-tact",
            ContractKit.Chain.CAIRO, "cairo-starknet",
            ContractKit.Chain.MOVE, "move-sui");

    /** The files that carry pins. Clarinet.toml is here for its
     *  {@code clarity_version} and {@code epoch}. */
    private static final java.util.List<String> MANIFESTS = java.util.List.of(
            "Cargo.toml", "package.json", "aiken.toml", "Scarb.toml",
            "Move.toml", "Clarinet.toml");

    /**
     * The law above binds ONE pin per chain, and the two homes carry more
     * than one: the 2026-11 refresh moved vitest, jest, @ton/sandbox, the
     * Aiken compiler and five more, and none of those was bound - either
     * home could have been moved alone with this class green. A manifest of
     * the kit and of the catalog is the same file except for what the kit
     * templates into it (the project's own name, and the description), so
     * the whole manifest is compared and every pin, edition and feature
     * list in it is bound at once, including one added tomorrow.
     */
    @Test
    @DisplayName("Kit and catalog manifests are the same file except for the project's own name")
    void everyManifestLineAgrees() throws Exception {
        int compared = 0;
        for (var entry : SAME_STARTER.entrySet()) {
            ContractKit.Chain chain = entry.getKey();
            String slug = entry.getValue();
            File dir = Files.createDirectories(root.resolve("m-" + chain.name())).toFile();
            ContractKit.scaffold(dir, chain, "Vault");
            int found = 0;
            for (String manifest : MANIFESTS) {
                String catalog = catalogFile(slug, manifest);
                File kit = new File(dir, manifest);
                assertThat(kit.isFile())
                        .as("%s: %s exists in the kit exactly when the catalog space has it",
                                chain, manifest)
                        .isEqualTo(catalog != null);
                if (catalog == null) {
                    continue;
                }
                found++;
                assertThat(withoutOwnName(Files.readString(kit.toPath())))
                        .as("%s %s: the kit's and the catalog's (%s) are one starter in two"
                                + " homes - every pin moves in both", chain, manifest, slug)
                        .isEqualTo(withoutOwnName(catalog));
            }
            assertThat(found).as("%s has a manifest to compare", chain).isPositive();
            compared += found;
        }
        assertThat(compared).as("manifests compared across all chains").isGreaterThanOrEqualTo(11);
    }

    /** A manifest with the lines that name the project itself removed: the
     *  first {@code name} line, a {@code description}, and (Clarinet.toml)
     *  the contract's own table header and path. Nothing that carries a
     *  version, an edition or a feature is dropped. */
    private static String withoutOwnName(String manifest) {
        StringBuilder out = new StringBuilder();
        boolean nameDropped = false;
        for (String line : manifest.replace("\r\n", "\n").split("\n")) {
            String t = line.strip();
            boolean isName = t.startsWith("name =") || t.startsWith("\"name\":");
            if (isName && !nameDropped) {
                nameDropped = true;
                continue;
            }
            if (t.startsWith("description =") || t.startsWith("[contracts.")
                    || t.startsWith("path = \"contracts/")) {
                continue;
            }
            out.append(line.stripTrailing()).append('\n');
        }
        return out.toString().strip();
    }

    /** One sample file of a catalog space, or null when it has none by that path. */
    private static String catalogFile(String slug, String path) {
        for (LearningCatalog.Space s : LearningCatalog.all()) {
            if (s.slug().equals(slug)) {
                for (LearningCatalog.SampleFile f : s.files()) {
                    if (f.path().equals(path)) {
                        return f.content();
                    }
                }
                return null;
            }
        }
        throw new AssertionError("no catalog space: " + slug);
    }

    /** The first version number pinned to {@code crate}, tolerating the
     *  cargo forms ({@code crate = "X"}, {@code crate = { version = "X" }}),
     *  the npm form ({@code "crate": "^X"}), and the TOML dependency-table
     *  form ({@code name = "crate"} followed by {@code version = "X"} —
     *  aiken.toml's shape). */
    private static String pinIn(String manifests, String crate) {
        Matcher m = Pattern.compile(
                "\"?" + Pattern.quote(crate)
                + "\"?\\s*[=:]\\s*(?:\\{[^}]*version\\s*=\\s*)?\"([^\"]+)\""
                + "|name\\s*=\\s*\"" + Pattern.quote(crate)
                + "\"\\s*\\n\\s*version\\s*=\\s*\"([^\"]+)\"")
                .matcher(manifests);
        assertThat(m.find()).as("a pin for %s exists", crate).isTrue();
        return m.group(1) != null ? m.group(1) : m.group(2);
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
