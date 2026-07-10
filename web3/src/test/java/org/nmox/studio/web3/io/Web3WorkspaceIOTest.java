package org.nmox.studio.web3.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.web3.model.DeploymentRecord;
import org.nmox.studio.web3.model.Network;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * .nmoxweb3.json: tolerant parsing in both directions, the
 * 200-deployment cap — and above all THE PIN: a secret network's URL
 * never reaches the file, whatever the in-memory record says.
 */
class Web3WorkspaceIOTest {

    // ---- THE security pin ---------------------------------------------------

    @Test
    @DisplayName("PIN: a secretUrl network serializes with NO url field at all")
    void secretNetworkHasNoUrlField() {
        // belt and braces: even a record that (wrongly) carries a plainUrl
        // alongside secretUrl=true must not leak it into the file
        Network smuggler = new Network("Mainnet (Infura)", 1, true,
                "https://mainnet.infura.io/v3/SUPERSECRETKEY");
        String json = Web3WorkspaceIO.toJson(
                new Web3WorkspaceIO.Workspace(List.of(smuggler), List.of()));

        assertThat(json).doesNotContain("SUPERSECRETKEY");
        assertThat(json).doesNotContain("\"url\"");
        assertThat(json).contains("\"secretUrl\": true");
    }

    @Test
    @DisplayName("PIN: loading a hand-edited file that smuggled a url into a secret network drops it")
    void secretNetworkLoadDropsSmuggledUrl() {
        String handEdited = """
                {"version": 1, "networks": [
                  {"name": "Sneaky", "chainId": 1, "secretUrl": true,
                   "url": "https://mainnet.infura.io/v3/LEAKED"}
                ], "deployments": []}""";
        Web3WorkspaceIO.Workspace loaded = Web3WorkspaceIO.fromJson(handEdited);
        assertThat(loaded.networks()).hasSize(1);
        assertThat(loaded.networks().get(0).secretUrl()).isTrue();
        assertThat(loaded.networks().get(0).plainUrl()).isNull();
    }

    @Test
    @DisplayName("a plain network keeps its url in the file — that's the point of plain")
    void plainNetworkKeepsUrl() {
        Network local = new Network("Local (anvil)", 31337, false, "http://127.0.0.1:8545");
        String json = Web3WorkspaceIO.toJson(
                new Web3WorkspaceIO.Workspace(List.of(local), List.of()));
        assertThat(json).contains("http://127.0.0.1:8545");
    }

    // ---- round trips ---------------------------------------------------------

    @Test
    @DisplayName("networks and deployments round-trip through save and load")
    void roundTrip(@TempDir Path dir) throws IOException {
        Web3WorkspaceIO.Workspace workspace = new Web3WorkspaceIO.Workspace(
                List.of(new Network("Local (anvil)", 31337, false, "http://127.0.0.1:8545"),
                        new Network("Mainnet", 1, true, null)),
                List.of(new DeploymentRecord("Counter", "0xabc123", "Local (anvil)",
                        "0xtxhash", 12, 1_700_000_000_000L)));
        Web3WorkspaceIO.save(dir.toFile(), workspace);

        Web3WorkspaceIO.Workspace loaded = Web3WorkspaceIO.load(dir.toFile());

        assertThat(loaded.networks()).containsExactly(
                new Network("Local (anvil)", 31337, false, "http://127.0.0.1:8545"),
                new Network("Mainnet", 1, true, null));
        assertThat(loaded.deployments()).containsExactly(
                new DeploymentRecord("Counter", "0xabc123", "Local (anvil)",
                        "0xtxhash", 12, 1_700_000_000_000L));
    }

    @Test
    @DisplayName("save swaps atomically: repeated saves leave only the workspace file, no temp siblings")
    void saveLeavesNoTempSiblings(@TempDir Path dir) throws IOException {
        // a torn or leftover temp file would be read by the ArtifactPulse
        // (and classified as a foreign edit) — the directory must stay clean
        Web3WorkspaceIO.Workspace workspace = new Web3WorkspaceIO.Workspace(
                List.of(new Network("Local (anvil)", 31337, false, "http://127.0.0.1:8545")),
                List.of());
        Web3WorkspaceIO.save(dir.toFile(), workspace);
        Web3WorkspaceIO.save(dir.toFile(), workspace);

        try (var files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString()))
                    .containsExactly(Web3WorkspaceIO.FILENAME);
        }
    }

    @Test
    @DisplayName("deployments cap at 200 newest-first on write; the tail is dropped")
    void deploymentCap() {
        List<DeploymentRecord> many = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            many.add(new DeploymentRecord("C" + i, "0x" + i, "net", "0xtx" + i, i, i));
        }
        String json = Web3WorkspaceIO.toJson(
                new Web3WorkspaceIO.Workspace(List.of(), many));
        Web3WorkspaceIO.Workspace loaded = Web3WorkspaceIO.fromJson(json);

        assertThat(loaded.deployments()).hasSize(Web3WorkspaceIO.DEPLOYMENT_CAP);
        assertThat(loaded.deployments().get(0).contractName()).isEqualTo("C0");
        assertThat(loaded.deployments().get(199).contractName()).isEqualTo("C199");
    }

    // ---- tolerance -------------------------------------------------------------

    @Test
    @DisplayName("a missing file loads as the empty workspace")
    void missingFile(@TempDir Path dir) {
        assertThat(Web3WorkspaceIO.load(dir.toFile()))
                .isEqualTo(Web3WorkspaceIO.Workspace.empty());
    }

    @Test
    @DisplayName("malformed JSON loads as the empty workspace, never throws")
    void malformedJson(@TempDir Path dir) throws IOException {
        Files.writeString(new File(dir.toFile(), Web3WorkspaceIO.FILENAME).toPath(),
                "{{{ definitely not json", StandardCharsets.UTF_8);
        assertThat(Web3WorkspaceIO.load(dir.toFile()))
                .isEqualTo(Web3WorkspaceIO.Workspace.empty());
        assertThat(Web3WorkspaceIO.fromJson(null))
                .isEqualTo(Web3WorkspaceIO.Workspace.empty());
        assertThat(Web3WorkspaceIO.fromJson(""))
                .isEqualTo(Web3WorkspaceIO.Workspace.empty());
    }

    @Test
    @DisplayName("a corrupt file is copied to .bak BEFORE the empty fallback — the address book survives")
    void corruptFileIsBackedUpBeforeEmptyFallback(@TempDir Path dir) throws IOException {
        String corrupt = "{{{ the address book was in here";
        Files.writeString(new File(dir.toFile(), Web3WorkspaceIO.FILENAME).toPath(),
                corrupt, StandardCharsets.UTF_8);

        Web3WorkspaceIO.LoadOutcome outcome = Web3WorkspaceIO.loadGuarded(dir.toFile());

        assertThat(outcome.workspace()).isEqualTo(Web3WorkspaceIO.Workspace.empty());
        assertThat(outcome.backup()).isNotNull();
        assertThat(outcome.backup().getName()).isEqualTo(Web3WorkspaceIO.FILENAME + ".bak");
        assertThat(Files.readString(outcome.backup().toPath(), StandardCharsets.UTF_8))
                .as("the backup carries the original bytes").isEqualTo(corrupt);
    }

    @Test
    @DisplayName("guarded load: missing and clean files make no backup")
    void guardedLoadMakesNoBackupWithoutCorruption(@TempDir Path dir) throws IOException {
        Web3WorkspaceIO.LoadOutcome missing = Web3WorkspaceIO.loadGuarded(dir.toFile());
        assertThat(missing.workspace()).isEqualTo(Web3WorkspaceIO.Workspace.empty());
        assertThat(missing.backup()).isNull();
        assertThat(dir.resolve(Web3WorkspaceIO.FILENAME + ".bak")).doesNotExist();

        Web3WorkspaceIO.save(dir.toFile(), new Web3WorkspaceIO.Workspace(
                List.of(new Network("Local", 31337, false, "http://127.0.0.1:8545")),
                List.of()));
        Web3WorkspaceIO.LoadOutcome clean = Web3WorkspaceIO.loadGuarded(dir.toFile());
        assertThat(clean.workspace().networks()).hasSize(1);
        assertThat(clean.backup()).isNull();
        assertThat(dir.resolve(Web3WorkspaceIO.FILENAME + ".bak")).doesNotExist();
    }

    @Test
    @DisplayName("guarded load keeps the secret-URL rule: a smuggled url still loads as null")
    void guardedLoadKeepsSecretUrlRule(@TempDir Path dir) throws IOException {
        Files.writeString(new File(dir.toFile(), Web3WorkspaceIO.FILENAME).toPath(), """
                {"version": 1, "networks": [
                  {"name": "Sneaky", "chainId": 1, "secretUrl": true,
                   "url": "https://mainnet.example/SECRET-KEY"}
                ], "deployments": []}""", StandardCharsets.UTF_8);

        Web3WorkspaceIO.LoadOutcome outcome = Web3WorkspaceIO.loadGuarded(dir.toFile());

        assertThat(outcome.backup()).isNull();
        assertThat(outcome.workspace().networks().get(0).secretUrl()).isTrue();
        assertThat(outcome.workspace().networks().get(0).plainUrl())
                .as("a hand-smuggled url never survives a load").isNull();
    }

    @Test
    @DisplayName("unknown keys and a future version stamp are ignored, state loads anyway")
    void futureFileTolerated() {
        String future = """
                {"version": 99, "shinyNewThing": {"a": 1}, "networks": [
                  {"name": "Local", "chainId": 31337, "secretUrl": false,
                   "url": "http://127.0.0.1:8545", "futureField": true}
                ], "deployments": []}""";
        Web3WorkspaceIO.Workspace loaded = Web3WorkspaceIO.fromJson(future);
        assertThat(loaded.networks()).hasSize(1);
        assertThat(loaded.networks().get(0).chainId()).isEqualTo(31337);
    }

    @Test
    @DisplayName("entries missing their essential field are skipped, the rest survive")
    void essentialFieldsRequired() {
        String partial = """
                {"version": 1,
                 "networks": [{"chainId": 1}, {"name": "Good", "chainId": 5}],
                 "deployments": [{"contractName": "NoAddress"},
                                 {"address": "0xgood", "contractName": "Kept"}]}""";
        Web3WorkspaceIO.Workspace loaded = Web3WorkspaceIO.fromJson(partial);
        assertThat(loaded.networks()).extracting(Network::name).containsExactly("Good");
        assertThat(loaded.deployments()).extracting(DeploymentRecord::contractName)
                .containsExactly("Kept");
    }

    @Test
    @DisplayName("a pre-deployments file (missing keys) loads with empty lists")
    void missingKeys() {
        Web3WorkspaceIO.Workspace loaded = Web3WorkspaceIO.fromJson("{\"version\": 1}");
        assertThat(loaded.networks()).isEmpty();
        assertThat(loaded.deployments()).isEmpty();
    }

    @Test
    @DisplayName("the file is valid JSON with the version stamp")
    void fileShape() {
        String json = Web3WorkspaceIO.toJson(Web3WorkspaceIO.Workspace.empty());
        assertThat(json).contains("\"version\": 1");
        assertThat(Web3WorkspaceIO.fromJson(json))
                .isEqualTo(Web3WorkspaceIO.Workspace.empty());
    }
}
