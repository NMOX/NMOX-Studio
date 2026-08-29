package org.nmox.studio.web3.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.core.util.AtomicFiles;
import org.nmox.studio.web3.model.DeploymentRecord;
import org.nmox.studio.web3.model.Network;

/**
 * Reads and writes the project's Contract Studio state as
 * {@code .nmoxweb3.json} beside the project — meant to be committed and
 * shared, so BY CONSTRUCTION it never carries a secret: a network with
 * {@link Network#secretUrl()} gets <b>no {@code url} field at all</b>,
 * even if its in-memory record happens to carry one (belt and braces,
 * test-pinned); its URL lives only in the OS keyring via
 * {@link RpcSecrets}. The mirror of {@code .nmoxdb.json}'s policy.
 *
 * <p>Loading is tolerant in both directions (the DbWorkspaceIO idiom):
 * a missing file, malformed JSON, unknown keys from a newer NMOX, or a
 * version stamp from the future all degrade to "less state", never an
 * exception. Deployments are capped at {@value #DEPLOYMENT_CAP},
 * newest-first, on both write and load.
 */
public final class Web3WorkspaceIO {

    public static final String FILENAME = ".nmoxweb3.json";

    /** How many deployment records the file keeps — the newest 200. */
    public static final int DEPLOYMENT_CAP = 200;

    /** Imported-ABI cap — a workspace file is a checked-in file. */
    public static final int IMPORTED_CAP = 100;

    private static final Logger LOG = Logger.getLogger(Web3WorkspaceIO.class.getName());

    /**
     * Everything {@code .nmoxweb3.json} holds. Lists are defensively
     * copied; deployments are kept newest-first by the callers (the
     * address book appends at the front).
     */
    public record Workspace(List<Network> networks, List<DeploymentRecord> deployments,
            List<org.nmox.studio.web3.model.ImportedContract> imported) {

        public Workspace {
            networks = List.copyOf(networks);
            deployments = List.copyOf(deployments);
            imported = List.copyOf(imported);
        }

        /** A workspace with nothing in it. */
        public static Workspace empty() {
            return new Workspace(List.of(), List.of(), List.of());
        }
    }

    private Web3WorkspaceIO() {
    }

    /**
     * Serializes the workspace. THE PIN: a secret network's entry has
     * {@code secretUrl: true} and no {@code url} key — whatever its
     * {@link Network#plainUrl()} says.
     */
    public static String toJson(Workspace workspace) {
        JSONObject root = new JSONObject();
        root.put("version", 1);

        JSONArray networks = new JSONArray();
        for (Network network : workspace.networks()) {
            JSONObject nj = new JSONObject();
            nj.put("name", nz(network.name()));
            nj.put("chainId", network.chainId());
            nj.put("secretUrl", network.secretUrl());
            if (!network.secretUrl()) {
                nj.put("url", nz(network.plainUrl()));
            }
            networks.put(nj);
        }
        root.put("networks", networks);

        JSONArray deployments = new JSONArray();
        for (DeploymentRecord record : cappedDeployments(workspace.deployments())) {
            JSONObject dj = new JSONObject();
            dj.put("contractName", nz(record.contractName()));
            dj.put("address", nz(record.address()));
            dj.put("networkName", nz(record.networkName()));
            dj.put("txHash", nz(record.txHash()));
            dj.put("blockNumber", record.blockNumber());
            dj.put("at", record.timestampMillis());
            deployments.put(dj);
        }
        root.put("deployments", deployments);

        JSONArray imported = new JSONArray();
        for (org.nmox.studio.web3.model.ImportedContract contract
                : cappedImported(workspace.imported())) {
            JSONObject ij = new JSONObject();
            ij.put("name", nz(contract.name()));
            ij.put("abi", nz(contract.abiJson()));
            ij.put("address", nz(contract.address()));
            imported.put(ij);
        }
        root.put("imported", imported);

        return root.toString(2);
    }

    /**
     * Parses the workspace. Malformed JSON yields
     * {@link Workspace#empty()}; missing keys yield empty lists; unknown
     * keys and unknown version stamps are ignored; entries missing their
     * essential field (a network's name, a deployment's address) are
     * skipped, keeping the rest. A secret network loads with
     * {@code plainUrl == null} even if some hand-edited file smuggled a
     * {@code url} in.
     */
    public static Workspace fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Workspace.empty();
        }
        try {
            return parse(new JSONObject(json));
        } catch (RuntimeException malformed) {
            LOG.log(Level.WARNING, "Malformed {0}; starting with an empty workspace ({1})",
                    new Object[]{FILENAME, malformed.getMessage()});
            return Workspace.empty();
        }
    }

    /** Writes the workspace as {@code .nmoxweb3.json} into the directory. */
    public static void save(File dir, Workspace workspace) throws IOException {
        // atomic rename, never truncate-then-write: the ArtifactPulse (and
        // any foreign reader) must never observe a torn .nmoxweb3.json
        AtomicFiles.writeString(new File(dir, FILENAME).toPath(), toJson(workspace));
    }

    /**
     * Loads the workspace from the given project directory. A missing,
     * unreadable or malformed file loads as {@link Workspace#empty()} —
     * never throws.
     */
    public static Workspace load(File dir) {
        File file = new File(dir, FILENAME);
        if (!file.isFile()) {
            return Workspace.empty();
        }
        try {
            return fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot read " + file, e);
            return Workspace.empty();
        }
    }

    /**
     * A guarded load: the {@code workspace} is never null (empty on any
     * failure, like {@link #load}); {@code backup} is non-null when the
     * file EXISTED but failed to parse and was copied aside.
     */
    public record LoadOutcome(Workspace workspace, File backup) {
    }

    /**
     * Loads like {@link #load}, but guards the user's file against the
     * corrupt-load → empty-model → save-clobbers-original sequence —
     * sharpest here, where the deployment address book lives: when
     * {@code .nmoxweb3.json} exists and fails to parse, the unreadable
     * original is copied to {@code .nmoxweb3.json.bak} BEFORE the empty
     * fallback is returned, so the studio's next save can never destroy
     * the only copy. Missing/unreadable files make no backup. Never
     * throws. (Serialization policy untouched: secret networks still
     * never carry a {@code url} — see {@link #toJson}.)
     */
    public static LoadOutcome loadGuarded(File dir) {
        File file = new File(dir, FILENAME);
        if (!file.isFile()) {
            return new LoadOutcome(Workspace.empty(), null);
        }
        String json;
        try {
            json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot read " + file, e);
            return new LoadOutcome(Workspace.empty(), null);
        }
        if (json.isBlank()) {
            return new LoadOutcome(Workspace.empty(), null); // nothing to lose
        }
        try {
            return new LoadOutcome(parse(new JSONObject(json)), null);
        } catch (RuntimeException malformed) {
            LOG.log(Level.WARNING, "Malformed {0}; keeping a .bak and starting empty ({1})",
                    new Object[]{FILENAME, malformed.getMessage()});
            return new LoadOutcome(Workspace.empty(), backupCorrupt(file));
        }
    }

    /** Copies the corrupt file to {@code <name>.bak}; null when even that fails. */
    private static File backupCorrupt(File file) {
        File backup = new File(file.getParentFile(), file.getName() + ".bak");
        try {
            Files.copy(file.toPath(), backup.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return backup;
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Could not back up corrupt " + file, e);
            return null;
        }
    }

    // ---- internals -------------------------------------------------------

    private static List<Network> networks(JSONArray array) {
        List<Network> out = new ArrayList<>();
        java.util.Set<String> seenNames = new java.util.HashSet<>();
        if (array == null) {
            return out;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject nj = array.optJSONObject(i);
            if (nj == null) {
                continue;
            }
            String name = nj.optString("name", "");
            if (name.isBlank()) {
                continue; // the name is the identity — nothing to file it under
            }
            // the name IS the identity (RpcSecrets keys the keychain by
            // it), and a keep-both git merge can duplicate it — then
            // removing either network deletes the secret URL both
            // resolve. First occurrence keeps the name and the stored
            // secret; later duplicates get a suffixed name — visible,
            // honest, re-enterable (the v2.9.0 parse-time-heal law).
            for (int n = 2; !seenNames.add(name); n++) {
                name = nj.optString("name", "") + "-" + n;
            }
            boolean secret = nj.optBoolean("secretUrl", false);
            out.add(new Network(
                    name,
                    nj.optInt("chainId", 0),
                    secret,
                    secret ? null : nj.optString("url", "")));
        }
        return out;
    }

    private static List<DeploymentRecord> deployments(JSONArray array) {
        List<DeploymentRecord> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject dj = array.optJSONObject(i);
            if (dj == null) {
                continue;
            }
            String address = dj.optString("address", "");
            if (address.isBlank()) {
                continue; // an address book line without an address points nowhere
            }
            out.add(new DeploymentRecord(
                    dj.optString("contractName", ""),
                    address,
                    dj.optString("networkName", ""),
                    dj.optString("txHash", ""),
                    dj.optLong("blockNumber", 0L),
                    dj.optLong("at", 0L)));
        }
        return out;
    }

    private static List<DeploymentRecord> cappedDeployments(List<DeploymentRecord> deployments) {
        return deployments.size() <= DEPLOYMENT_CAP
                ? deployments : deployments.subList(0, DEPLOYMENT_CAP);
    }

    /**
     * THE one construction site for a parsed workspace (v2.45.0 review
     * find): fromJson and loadGuarded once built Workspaces separately,
     * and the second site silently dropped the imported list because a
     * compatibility constructor defaulted it — a compile error was
     * masked into a data hole. Every field a Workspace carries is read
     * HERE or nowhere.
     */
    private static Workspace parse(JSONObject root) {
        return new Workspace(
                networks(root.optJSONArray("networks")),
                cappedDeployments(deployments(root.optJSONArray("deployments"))),
                cappedImported(imported(root.optJSONArray("imported"))));
    }

    /**
     * Imported contracts (v2.45.0). PARSE-TIME HEAL (the v2.36.2 law):
     * the studio keys sessions by contract name, so a keep-both merge
     * that duplicates a name would make gestures ambiguous — the FIRST
     * occurrence keeps the name, later duplicates are dropped with a
     * log line. Entries without a name or ABI are skipped.
     */
    private static List<org.nmox.studio.web3.model.ImportedContract> imported(
            JSONArray array) {
        List<org.nmox.studio.web3.model.ImportedContract> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        if (array == null) {
            return out;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            String name = entry.optString("name", "");
            String abi = entry.optString("abi", "");
            if (name.isBlank() || abi.isBlank()) {
                continue;
            }
            if (!seen.add(name)) {
                LOG.log(Level.WARNING,
                        "Duplicate imported contract \"{0}\" in {1} — keeping the first",
                        new Object[]{name, FILENAME});
                continue;
            }
            try {
                out.add(new org.nmox.studio.web3.model.ImportedContract(
                        name, abi, entry.optString("address", "")));
            } catch (IllegalArgumentException oversize) {
                // a hand-edited monster entry skips alone — the rest of
                // the workspace loads (v2.47.1, the skip-with-log family)
                LOG.log(Level.WARNING, "Skipping imported \"{0}\": {1}",
                        new Object[]{name, oversize.getMessage()});
            }
        }
        return out;
    }

    private static List<org.nmox.studio.web3.model.ImportedContract> cappedImported(
            List<org.nmox.studio.web3.model.ImportedContract> list) {
        return list.size() <= IMPORTED_CAP ? list : list.subList(0, IMPORTED_CAP);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
