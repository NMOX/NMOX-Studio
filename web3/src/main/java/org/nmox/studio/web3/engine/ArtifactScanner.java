package org.nmox.studio.web3.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.web3.model.AbiEntry;
import org.nmox.studio.web3.model.AbiParam;
import org.nmox.studio.web3.model.ContractArtifact;

/**
 * Finds compiled contracts in a project: Foundry's
 * {@code out/<Source>.sol/<Name>.json}
 * ({@code {abi, bytecode:{object}, deployedBytecode:{object}}}) and
 * Hardhat's {@code artifacts/contracts/**&#47;<Name>.json}
 * ({@code {contractName, abi, bytecode:"0x…"}}, with {@code *.dbg.json}
 * siblings that carry no ABI and are skipped). The format is
 * auto-detected from the fields, never from the path.
 *
 * <p>Garbage tolerance is the contract: a file that isn't JSON, isn't
 * an artifact, or is half-written gets skipped silently — the scanner
 * never throws. Two artifacts with the same contract name dedupe to the
 * newest by file modification time (a Foundry and a Hardhat build of
 * the same contract, or a stale copy).
 *
 * <p>{@link #parse(String, String)} is the pure seam — path string and
 * content in, artifact out — so tests need no real build output;
 * {@link #scan(Path)} is the thin directory walker over it.
 */
public final class ArtifactScanner {

    private static final Logger LOG = Logger.getLogger(ArtifactScanner.class.getName());

    /** Walk depth cap — Hardhat nests by source path, Foundry is flat. */
    private static final int MAX_DEPTH = 12;

    private ArtifactScanner() {
    }

    /**
     * Parses one artifact file. The path is only used for the contract
     * name (Foundry names the file after the contract), the
     * {@code .sol} parent directory (Foundry's source hint), and the
     * {@code .dbg.json} skip — content decides everything else.
     * Anything unparseable is an empty Optional, never an exception.
     */
    public static Optional<ContractArtifact> parse(String path, String content) {
        if (path == null || content == null) {
            return Optional.empty();
        }
        String normalized = path.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".json")
                || filename.toLowerCase(Locale.ROOT).endsWith(".dbg.json")) {
            return Optional.empty();
        }
        try {
            JSONObject root = new JSONObject(content);
            JSONArray abiJson = root.optJSONArray("abi");
            if (abiJson == null) {
                return Optional.empty();
            }
            List<AbiEntry> abi = parseAbi(abiJson);

            Object bytecode = root.opt("bytecode");
            if (bytecode instanceof JSONObject foundryBytecode) {
                // Foundry: bytecode/deployedBytecode are {object: "0x…"}
                String name = filename.substring(0, filename.length() - ".json".length());
                return Optional.of(new ContractArtifact(
                        name,
                        foundrySourcePath(normalized),
                        abi,
                        foundryBytecode.optString("object", "0x"),
                        objectOf(root.optJSONObject("deployedBytecode"))));
            }
            if (bytecode instanceof String hardhatBytecode
                    && root.has("contractName")) {
                // Hardhat: flat hex strings plus contractName/sourceName
                return Optional.of(new ContractArtifact(
                        root.optString("contractName"),
                        root.optString("sourceName", ""),
                        abi,
                        hardhatBytecode,
                        root.optString("deployedBytecode", "0x")));
            }
            return Optional.empty();
        } catch (RuntimeException garbage) {
            // not an artifact; the scanner's contract is to move on
            return Optional.empty();
        }
    }

    /**
     * Scans a project directory: Foundry's {@code out/} and Hardhat's
     * {@code artifacts/contracts/}, whichever exist. Never throws;
     * unreadable files and directories are skipped. Duplicate contract
     * names keep the newest file by modification time.
     */
    public static List<ContractArtifact> scan(Path projectDir) {
        Map<String, Found> byName = new LinkedHashMap<>();
        if (projectDir != null) {
            scanRoot(projectDir.resolve("out"), byName);
            scanRoot(projectDir.resolve("artifacts").resolve("contracts"), byName);
        }
        List<ContractArtifact> out = new ArrayList<>(byName.size());
        for (Found found : byName.values()) {
            out.add(found.artifact);
        }
        return out;
    }

    // ---- internals -----------------------------------------------------

    private record Found(ContractArtifact artifact, long mtime) {
    }

    private static void scanRoot(Path root, Map<String, Found> byName) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root, MAX_DEPTH)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("build-info"))
                    .forEach(p -> collect(p, byName));
        } catch (IOException | RuntimeException walkFailed) {
            LOG.log(Level.FINE, "Artifact scan of " + root + " stopped early", walkFailed);
        }
    }

    private static void collect(Path file, Map<String, Found> byName) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".json") || name.endsWith(".dbg.json")) {
            return;
        }
        String content;
        long mtime;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
            mtime = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException | RuntimeException unreadable) {
            return; // half-written or binary-masquerading file: skip
        }
        parse(file.toString(), content).ifPresent(artifact -> {
            Found previous = byName.get(artifact.name());
            if (previous == null || mtime >= previous.mtime()) {
                byName.put(artifact.name(), new Found(artifact, mtime));
            }
        });
    }

    /** {@code out/Counter.sol/Counter.json} → {@code Counter.sol}; else {@code ""}. */
    private static String foundrySourcePath(String normalizedPath) {
        int lastSlash = normalizedPath.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "";
        }
        String parentPath = normalizedPath.substring(0, lastSlash);
        String parent = parentPath.substring(parentPath.lastIndexOf('/') + 1);
        return parent.toLowerCase(Locale.ROOT).endsWith(".sol") ? parent : "";
    }

    private static String objectOf(JSONObject bytecodeObject) {
        return bytecodeObject == null ? "0x" : bytecodeObject.optString("object", "0x");
    }

    private static List<AbiEntry> parseAbi(JSONArray abiJson) {
        List<AbiEntry> abi = new ArrayList<>();
        for (int i = 0; i < abiJson.length(); i++) {
            JSONObject entry = abiJson.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            switch (entry.optString("type", "")) {
                case "function" -> abi.add(AbiEntry.function(
                        entry.optString("name", ""),
                        params(entry.optJSONArray("inputs")),
                        params(entry.optJSONArray("outputs")),
                        entry.optString("stateMutability", "nonpayable")));
                case "event" -> abi.add(AbiEntry.event(
                        entry.optString("name", ""),
                        params(entry.optJSONArray("inputs"))));
                case "constructor" -> abi.add(AbiEntry.constructor(
                        params(entry.optJSONArray("inputs")),
                        entry.optString("stateMutability", "nonpayable")));
                case "error" -> abi.add(AbiEntry.error(
                        entry.optString("name", ""),
                        params(entry.optJSONArray("inputs"))));
                default -> {
                    // fallback/receive carry no callable surface; skip
                }
            }
        }
        return abi;
    }

    private static List<AbiParam> params(JSONArray array) {
        List<AbiParam> out = new ArrayList<>();
        if (array == null) {
            return out;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject param = array.optJSONObject(i);
            if (param == null) {
                continue;
            }
            out.add(new AbiParam(
                    param.optString("name", ""),
                    param.optString("type", ""),
                    param.optBoolean("indexed", false)));
        }
        return out;
    }
}
