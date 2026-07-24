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

    /** kit chain → (catalog slug, the pinned dependency's manifest line prefix). */
    private static final Map<ContractKit.Chain, String[]> PAIRS = Map.of(
            ContractKit.Chain.SOROBAN, new String[]{"stellar-soroban", "soroban-sdk"},
            ContractKit.Chain.SOLANA, new String[]{"solana-native", "solana-program"},
            ContractKit.Chain.COSMWASM, new String[]{"cosmwasm", "cosmwasm-std"},
            ContractKit.Chain.INK, new String[]{"ink-polkadot", "ink"},
            ContractKit.Chain.BITCOIN, new String[]{"bitcoin-miniscript", "miniscript"});

    @Test
    @DisplayName("Every shared dependency pin matches between kit template and catalog space")
