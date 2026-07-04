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

    private static final Logger LOG = Logger.getLogger(Web3WorkspaceIO.class.getName());

    /**
     * Everything {@code .nmoxweb3.json} holds. Lists are defensively
     * copied; deployments are kept newest-first by the callers (the
     * address book appends at the front).
     */
    public record Workspace(List<Network> networks, List<DeploymentRecord> deployments) {

        public Workspace {
            networks = List.copyOf(networks);
            deployments = List.copyOf(deployments);
        }

        /** A workspace with nothing in it. */
        public static Workspace empty() {
            return new Workspace(List.of(), List.of());
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
            JSONObject root = new JSONObject(json);
            return new Workspace(
                    networks(root.optJSONArray("networks")),
                    cappedDeployments(deployments(root.optJSONArray("deployments"))));
        } catch (RuntimeException malformed) {
            LOG.log(Level.WARNING, "Malformed {0}; starting with an empty workspace ({1})",
                    new Object[]{FILENAME, malformed.getMessage()});
            return Workspace.empty();
        }
    }

    /** Writes the workspace as {@code .nmoxweb3.json} into the directory. */
    public static void save(File dir, Workspace workspace) throws IOException {
        Files.writeString(new File(dir, FILENAME).toPath(), toJson(workspace),
                StandardCharsets.UTF_8);
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

    // ---- internals -------------------------------------------------------

    private static List<Network> networks(JSONArray array) {
        List<Network> out = new ArrayList<>();
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

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
