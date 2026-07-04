package org.nmox.studio.web3.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.ContractArtifact;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The artifact scanner: pure parse seam with canned Foundry/Hardhat
 * JSON (no build tools needed), the walker over @TempDir fixtures, and
 * the garbage-tolerance contract — skip, never throw.
 */
class ArtifactScannerTest {

    /** A trimmed Foundry out/Counter.sol/Counter.json shape. */
    private static final String FOUNDRY_COUNTER = """
            {
              "abi": [
                {"type": "function", "name": "increment", "inputs": [], "outputs": [],
                 "stateMutability": "nonpayable"},
                {"type": "function", "name": "number", "inputs": [],
                 "outputs": [{"name": "", "type": "uint256"}], "stateMutability": "view"},
                {"type": "event", "name": "Incremented",
                 "inputs": [{"name": "by", "type": "address", "indexed": true},
                            {"name": "newValue", "type": "uint256", "indexed": false}]},
                {"type": "constructor", "inputs": [{"name": "start", "type": "uint256"}],
                 "stateMutability": "nonpayable"},
                {"type": "error", "name": "TooBig",
                 "inputs": [{"name": "value", "type": "uint256"}]},
                {"type": "receive", "stateMutability": "payable"}
              ],
              "bytecode": {"object": "0x6080604052", "sourceMap": "irrelevant"},
              "deployedBytecode": {"object": "0x60806040"},
              "methodIdentifiers": {"increment()": "d09de08a"}
            }
            """;

    /** A trimmed Hardhat artifacts/contracts/Token.sol/Token.json shape. */
    private static final String HARDHAT_TOKEN = """
            {
              "_format": "hh-sol-artifact-1",
              "contractName": "Token",
              "sourceName": "contracts/Token.sol",
              "abi": [
                {"type": "function", "name": "totalSupply", "inputs": [],
                 "outputs": [{"name": "", "type": "uint256"}], "stateMutability": "view"}
              ],
              "bytecode": "0x1234",
              "deployedBytecode": "0x5678",
              "linkReferences": {}
            }
            """;

    // ---- the pure parse seam ---------------------------------------------

    @Test
    @DisplayName("a Foundry artifact parses: name from the file, source from the .sol dir")
    void foundryParse() {
        Optional<ContractArtifact> parsed = ArtifactScanner.parse(
                "out/Counter.sol/Counter.json", FOUNDRY_COUNTER);
        assertThat(parsed).isPresent();
        ContractArtifact artifact = parsed.get();
        assertThat(artifact.name()).isEqualTo("Counter");
        assertThat(artifact.sourcePath()).isEqualTo("Counter.sol");
        assertThat(artifact.bytecodeHex()).isEqualTo("0x6080604052");
        assertThat(artifact.deployedBytecodeHex()).isEqualTo("0x60806040");
    }

    @Test
    @DisplayName("the parsed ABI carries functions, events, constructor and errors; receive is skipped")
    void foundryAbiEntries() {
        ContractArtifact artifact = ArtifactScanner.parse(
                "out/Counter.sol/Counter.json", FOUNDRY_COUNTER).orElseThrow();
        assertThat(artifact.functions()).extracting(AbiEntry::name)
                .containsExactly("increment", "number");
        assertThat(artifact.events()).hasSize(1);
        assertThat(artifact.events().get(0).inputs().get(0).indexed()).isTrue();
        assertThat(artifact.constructor()).isPresent();
        assertThat(artifact.errors()).extracting(AbiEntry::name).containsExactly("TooBig");
        assertThat(artifact.abi()).hasSize(5); // receive did not survive
    }

    @Test
    @DisplayName("a Hardhat artifact parses: name and source from its own fields")
    void hardhatParse() {
        ContractArtifact artifact = ArtifactScanner.parse(
                "artifacts/contracts/Token.sol/Token.json", HARDHAT_TOKEN).orElseThrow();
        assertThat(artifact.name()).isEqualTo("Token");
        assertThat(artifact.sourcePath()).isEqualTo("contracts/Token.sol");
        assertThat(artifact.bytecodeHex()).isEqualTo("0x1234");
        assertThat(artifact.deployedBytecodeHex()).isEqualTo("0x5678");
    }

    @Test
    @DisplayName("*.dbg.json siblings are skipped by name")
    void dbgSkipped() {
        assertThat(ArtifactScanner.parse(
                "artifacts/contracts/Token.sol/Token.dbg.json",
                "{\"abi\": [], \"bytecode\": \"0x\", \"contractName\": \"Token\"}"))
                .isEmpty();
    }

    @Test
    @DisplayName("garbage content — not JSON, no abi, wrong shapes — is skipped, never thrown")
    void garbageTolerated() {
        assertThat(ArtifactScanner.parse("out/x.json", "not json at all")).isEmpty();
        assertThat(ArtifactScanner.parse("out/x.json", "")).isEmpty();
        assertThat(ArtifactScanner.parse("out/x.json", "{\"no\": \"abi\"}")).isEmpty();
        assertThat(ArtifactScanner.parse("out/x.json", "[1, 2, 3]")).isEmpty();
        assertThat(ArtifactScanner.parse("out/x.json",
                "{\"abi\": [], \"bytecode\": 42}")).isEmpty();
        assertThat(ArtifactScanner.parse("out/x.txt", FOUNDRY_COUNTER)).isEmpty();
        assertThat(ArtifactScanner.parse(null, FOUNDRY_COUNTER)).isEmpty();
        assertThat(ArtifactScanner.parse("out/x.json", null)).isEmpty();
    }

    @Test
    @DisplayName("a Foundry artifact without a deployedBytecode object still parses (0x)")
    void missingDeployedBytecode() {
        ContractArtifact artifact = ArtifactScanner.parse("out/I.sol/I.json",
                "{\"abi\": [], \"bytecode\": {\"object\": \"0x\"}}").orElseThrow();
        assertThat(artifact.deployedBytecodeHex()).isEqualTo("0x");
    }

    @Test
    @DisplayName("Windows-style path separators still yield the right name and source")
    void windowsPaths() {
        ContractArtifact artifact = ArtifactScanner.parse(
                "out\\Counter.sol\\Counter.json", FOUNDRY_COUNTER).orElseThrow();
        assertThat(artifact.name()).isEqualTo("Counter");
        assertThat(artifact.sourcePath()).isEqualTo("Counter.sol");
    }

    // ---- the walker --------------------------------------------------------

    @Test
    @DisplayName("scan finds Foundry and Hardhat artifacts side by side")
    void scanBothLayouts(@TempDir Path dir) throws IOException {
        write(dir.resolve("out/Counter.sol/Counter.json"), FOUNDRY_COUNTER);
        write(dir.resolve("artifacts/contracts/Token.sol/Token.json"), HARDHAT_TOKEN);
        write(dir.resolve("artifacts/contracts/Token.sol/Token.dbg.json"),
                "{\"_format\": \"hh-sol-dbg-1\", \"buildInfo\": \"../../build-info/x.json\"}");

        List<ContractArtifact> found = ArtifactScanner.scan(dir);

        assertThat(found).extracting(ContractArtifact::name)
                .containsExactlyInAnyOrder("Counter", "Token");
    }

    @Test
    @DisplayName("duplicate contract names dedupe to the newest file by mtime")
    void dedupeNewestWins(@TempDir Path dir) throws IOException {
        Path stale = dir.resolve("out/Counter.sol/Counter.json");
        Path fresh = dir.resolve("artifacts/contracts/Counter.sol/Counter.json");
        write(stale, FOUNDRY_COUNTER);
        write(fresh, HARDHAT_TOKEN.replace("\"Token\"", "\"Counter\""));
        Files.setLastModifiedTime(stale, FileTime.fromMillis(1_000_000));
        Files.setLastModifiedTime(fresh, FileTime.fromMillis(2_000_000));

        List<ContractArtifact> found = ArtifactScanner.scan(dir);

        assertThat(found).hasSize(1);
        // the fresh Hardhat build won: its bytecode, not Foundry's
        assertThat(found.get(0).bytecodeHex()).isEqualTo("0x1234");
    }

    @Test
    @DisplayName("build-info directories are not even read")
    void buildInfoSkipped(@TempDir Path dir) throws IOException {
        write(dir.resolve("out/build-info/huge.json"), "{\"abi\": \"this is not it\"}");
        assertThat(ArtifactScanner.scan(dir)).isEmpty();
    }

    @Test
    @DisplayName("a project without out/ or artifacts/ scans to empty, and null is safe")
    void missingDirs(@TempDir Path dir) {
        assertThat(ArtifactScanner.scan(dir)).isEmpty();
        assertThat(ArtifactScanner.scan(null)).isEmpty();
    }

    @Test
    @DisplayName("garbage files in the tree don't stop the good ones from loading")
    void garbageAmongGood(@TempDir Path dir) throws IOException {
        write(dir.resolve("out/garbage.json"), "{{{{{");
        write(dir.resolve("out/binary.json"), " ");
        write(dir.resolve("out/Counter.sol/Counter.json"), FOUNDRY_COUNTER);

        assertThat(ArtifactScanner.scan(dir))
                .extracting(ContractArtifact::name).containsExactly("Counter");
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
