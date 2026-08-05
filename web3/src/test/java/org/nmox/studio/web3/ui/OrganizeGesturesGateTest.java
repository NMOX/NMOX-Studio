package org.nmox.studio.web3.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source gates for the v1.269.0 organize gestures — the sweep's fourth
 * surface (API Studio v1.263.0, DB Studio v1.266.0, Block Studio
 * v1.268.0, now Contract Studio). Add Network… shipped with no inverse,
 * so a typo'd RPC URL lived in the combo forever and a SECRET network's
 * keychain entry outlived every way to drop it; the deployment address
 * book accumulated rows with no forget gesture at all. Both gestures
 * are pure-Swing wiring plain tests can't drive, so the laws are pinned
 * at the source per the {@code StudioSafetyGateTest} idiom:
 * <ul>
 * <li>the built-in LOCAL_ANVIL refuses removal BEFORE any dialog or
 * mutation (a refusal mutates nothing);</li>
 * <li>both confirms carry the v1.98.0 safe default — full
 * {@code NotifyDescriptor} ctor with {@code NO_OPTION} as
 * initialValue, never a {@code Confirmation};</li>
 * <li>removing a secret network deletes its keychain entry, off the
 * EDT (the keyring may block on OS calls);</li>
 * <li>a forgotten deployment row persists its absence and repaints
 * every consumer (table model + tree branch).</li>
 * </ul>
 */
class OrganizeGesturesGateTest {

    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/web3/ui/Web3StudioTopComponent.java"),
                StandardCharsets.UTF_8);
    }

    private static String method(String src, String signature) {
        int m = src.indexOf(signature);
        assertThat(m).as(signature + " exists").isPositive();
        return src.substring(m, src.indexOf("\n    }", m));
    }

    /** The forget-deployment listener body, anchored on its menu item. */
    private static String forgetBlock(String src) {
        int start = src.indexOf("\"Forget deployment\"");
        assertThat(start).as("the Forget deployment menu item exists").isPositive();
        int end = src.indexOf("popup.add(forget)", start);
        assertThat(end).as("the forget item joins the popup").isPositive();
        return src.substring(start, end);
    }

    @Test
    @DisplayName("LOCAL_ANVIL refuses removal before any dialog or mutation")
    void builtInNetworkRefusesFirst() throws Exception {
        String body = method(source(), "private void removeSelectedNetwork()");
        int guard = body.indexOf("LOCAL_ANVIL.equals(network)");
        assertThat(guard)
                .as("the built-in network is guarded by name")
                .isPositive();
        assertThat(guard)
                .as("the guard fires BEFORE the confirm dialog — a refusal"
                        + " never shows a dialog it would have to walk back")
                .isLessThan(body.indexOf("NotifyDescriptor"));
        assertThat(guard)
                .as("the guard fires BEFORE the list mutation")
                .isLessThan(body.indexOf("networks.remove("));
    }

    @Test
    @DisplayName("Both organize confirms default to No (the v1.98.0 idiom)")
    void confirmsDefaultToSafe() throws Exception {
        String src = source();
        for (String body : new String[]{
            method(src, "private void removeSelectedNetwork()"),
            forgetBlock(src)}) {
            assertThat(body)
                    .as("full NotifyDescriptor ctor with NO_OPTION as"
                            + " initialValue — Confirmation hard-codes OK, so"
                            + " a reflexive Enter would destroy")
                    .contains("NotifyDescriptor.NO_OPTION);")
                    .doesNotContain("new NotifyDescriptor.Confirmation(");
            assertThat(body)
                    .as("a declined confirm returns without mutating")
                    .contains("!= NotifyDescriptor.YES_OPTION)");
        }
    }

    @Test
    @DisplayName("Removing a secret network deletes its keychain entry off the EDT")
    void secretRemovalClearsKeychain() throws Exception {
        String body = method(source(), "private void removeSelectedNetwork()");
        assertThat(body)
                .as("the keychain entry must not outlive the network row —"
                        + " RpcSecrets is the only door to the secret")
                .contains("RpcSecrets.delete(");
        assertThat(body)
                .as("the keyring may block on OS calls, so the delete rides"
                        + " the worker, never the EDT")
                .contains("RP.post(");
        assertThat(body.indexOf("RP.post("))
                .as("the delete happens INSIDE the posted task")
                .isLessThan(body.indexOf("RpcSecrets.delete("));
        assertThat(body)
                .as("only secret networks touch the keychain")
                .contains("network.secretUrl()");
    }

    @Test
    @DisplayName("A forgotten deployment persists its absence and repaints every consumer")
    void forgetPersistsAndRepaints() throws Exception {
        String body = forgetBlock(source());
        int remove = body.indexOf("deployments.remove(");
        assertThat(remove).as("the row actually leaves the list").isPositive();
        assertThat(remove)
                .as("the mutation happens only after the confirm")
                .isGreaterThan(body.indexOf("YES_OPTION)"));
        assertThat(body)
                .as("the absence is written to .nmoxweb3.json, and both the"
                        + " table and the search tree branch repaint")
                .contains("saveWorkspace()")
                .contains("deploymentsModel.fireTableDataChanged()")
                .contains("rebuildDeploymentsBranch()");
    }
}
