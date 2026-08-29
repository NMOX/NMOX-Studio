package org.nmox.studio.web3.io;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.web3.engine.ArtifactScanner;
import org.nmox.studio.web3.model.ContractArtifact;
import org.nmox.studio.web3.model.ImportedContract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Imported contracts in the workspace file (v2.45.0): verbatim
 * round trip, the duplicate-name parse-time heal (first occurrence
 * keeps the name), the cap, pre-v2.45.0 files loading clean, and the
 * one-parser law — an import becomes an artifact through the exact
 * parser built artifacts use, with malformed ABI refused with a
 * reason.
 */
class ImportedContractsIoTest {

    private static final String ERC20_ABI = """
            [{"type":"function","name":"balanceOf","stateMutability":"view",
              "inputs":[{"name":"owner","type":"address"}],
              "outputs":[{"name":"","type":"uint256"}]},
             {"type":"event","name":"Transfer","inputs":[
              {"name":"from","type":"address","indexed":true},
              {"name":"to","type":"address","indexed":true},
              {"name":"value","type":"uint256","indexed":false}]}]""";

    @Test
    @DisplayName("imported contracts round-trip verbatim; old files load with none")
    void roundTrip() {
        Web3WorkspaceIO.Workspace ws = new Web3WorkspaceIO.Workspace(
                List.of(), List.of(),
                List.of(new ImportedContract("USDC", ERC20_ABI,
                        "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")));
        Web3WorkspaceIO.Workspace back =
                Web3WorkspaceIO.fromJson(Web3WorkspaceIO.toJson(ws));
        assertThat(back.imported()).hasSize(1);
        assertThat(back.imported().get(0).name()).isEqualTo("USDC");
        assertThat(back.imported().get(0).abiJson()).isEqualTo(ERC20_ABI);
        assertThat(back.imported().get(0).address())
                .isEqualTo("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48");

        // a pre-v2.45.0 file has no "imported" key — loads empty, never throws
        Web3WorkspaceIO.Workspace old = Web3WorkspaceIO.fromJson(
                "{\"version\":1,\"networks\":[],\"deployments\":[]}");
        assertThat(old.imported()).isEmpty();
    }

    @Test
    @DisplayName("duplicate names heal at parse: the first occurrence keeps the name")
    void duplicateHeal() {
        String json = Web3WorkspaceIO.toJson(new Web3WorkspaceIO.Workspace(
                List.of(), List.of(), List.of(
                        new ImportedContract("Token", "[1]", "0x01"),
                        new ImportedContract("Other", "[2]", "0x02"))));
        // simulate a keep-both merge: duplicate the first entry with a new address
        String merged = json.replace("\"imported\": [",
                "\"imported\": [{\"name\":\"Token\",\"abi\":\"[9]\",\"address\":\"0x99\"},");
        Web3WorkspaceIO.Workspace back = Web3WorkspaceIO.fromJson(merged);
        assertThat(back.imported()).hasSize(2);
        assertThat(back.imported().get(0).abiJson())
                .as("first occurrence keeps the name").isEqualTo("[9]");
        assertThat(back.imported().get(1).name()).isEqualTo("Other");
    }

    @Test
    @DisplayName("the cap holds on load")
    void cap() {
        List<ImportedContract> many = new ArrayList<>();
        for (int i = 0; i < Web3WorkspaceIO.IMPORTED_CAP + 20; i++) {
            many.add(new ImportedContract("c" + i, "[]", ""));
        }
        Web3WorkspaceIO.Workspace back = Web3WorkspaceIO.fromJson(
                Web3WorkspaceIO.toJson(new Web3WorkspaceIO.Workspace(
                        List.of(), List.of(), many)));
        assertThat(back.imported()).hasSize(Web3WorkspaceIO.IMPORTED_CAP);
    }

    @Test
    @DisplayName("loadGuarded carries imported too — the two-construction-sites bug stays dead")
    void loadGuardedCarriesImported(@org.junit.jupiter.api.io.TempDir java.io.File dir)
            throws Exception {
        // the v2.45.0 walk find: fromJson parsed imported while loadGuarded
        // built its Workspace at a second site that silently dropped it (a
        // compat constructor masked the compile error) — the studio's boot
        // path uses loadGuarded, so imports vanished on every relaunch
        Web3WorkspaceIO.save(dir, new Web3WorkspaceIO.Workspace(
                List.of(), List.of(),
                List.of(new ImportedContract("USDC", ERC20_ABI, "0xA0"))));
        Web3WorkspaceIO.LoadOutcome outcome = Web3WorkspaceIO.loadGuarded(dir);
        assertThat(outcome.backup()).isNull();
        assertThat(outcome.workspace().imported())
                .as("the BOOT path must carry imports").hasSize(1);
        assertThat(outcome.workspace().imported().get(0).name()).isEqualTo("USDC");
    }

    @Test
    @DisplayName("the ABI cap holds at construction, and an oversize file entry skips alone")
    void abiCap() {
        String big = "x".repeat(ImportedContract.ABI_CAP_CHARS + 1);
        assertThatThrownBy(() -> new ImportedContract("Huge", big, ""))
                .hasMessageContaining("cap");
        // hand-edit a monster entry into an otherwise-good file: the good
        // entry loads, the monster skips (never kills the whole workspace)
        String json = Web3WorkspaceIO.toJson(new Web3WorkspaceIO.Workspace(
                List.of(), List.of(),
                List.of(new ImportedContract("Good", "[]", ""))));
        String merged = json.replace("\"imported\": [",
                "\"imported\": [{\"name\":\"Huge\",\"abi\":\""
                        + big + "\",\"address\":\"\"},");
        Web3WorkspaceIO.Workspace back = Web3WorkspaceIO.fromJson(merged);
        assertThat(back.imported()).hasSize(1);
        assertThat(back.imported().get(0).name()).isEqualTo("Good");
    }

    @Test
    @DisplayName("one parser, one truth: an import becomes a full artifact; junk is refused with a reason")
    void oneParser() {
        ContractArtifact artifact = ArtifactScanner.fromImported(
                new ImportedContract("USDC", ERC20_ABI, ""));
        assertThat(artifact.name()).isEqualTo("USDC");
        assertThat(artifact.abi()).hasSize(2);
        assertThat(artifact.functions()).hasSize(1);
        assertThat(artifact.events()).hasSize(1);
        assertThat(artifact.bytecodeHex()).as("imports carry no bytecode").isEqualTo("0x");

        assertThatThrownBy(() -> ArtifactScanner.fromImported(
                new ImportedContract("Bad", "{not an array", "")))
                .isInstanceOf(RuntimeException.class);
    }
}
