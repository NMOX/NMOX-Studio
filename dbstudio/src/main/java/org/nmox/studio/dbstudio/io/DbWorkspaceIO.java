package org.nmox.studio.dbstudio.io;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

/**
 * Reads and writes the project's database connections as
 * {@code .nmoxdb.json} beside the project — the file is meant to be
 * committed and shared, so BY CONSTRUCTION it never carries a secret:
 * {@link ConnectionSpec} has no password field, and passwords live only
 * in the OS keychain via
 * {@link org.nmox.studio.dbstudio.engine.Passwords} (mirroring the
 * ATMOS rule and {@code .nmoxapi.json}'s policy).
 *
 * <p>The engine takes an explicit directory {@link File} rather than a
 * project object — the UI decides what "the project dir" is, keeping
 * this class free of platform coupling and trivially testable.
 *
 * <p>Loading is tolerant: a missing file, malformed JSON, or an
 * unknown engine from a newer NMOX degrade to "fewer connections",
 * never an exception.
 */
public final class DbWorkspaceIO {

    public static final String FILENAME = ".nmoxdb.json";

    private static final Logger LOG = Logger.getLogger(DbWorkspaceIO.class.getName());

    private DbWorkspaceIO() {
    }

    public static String toJson(List<ConnectionSpec> specs) {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        JSONArray connections = new JSONArray();
        for (ConnectionSpec spec : specs) {
            JSONObject cj = new JSONObject();
            cj.put("id", nz(spec.id()));
            cj.put("name", nz(spec.name()));
            cj.put("engine", spec.engine().name());
            cj.put("host", nz(spec.host()));
            cj.put("port", spec.port());
            cj.put("database", nz(spec.database()));
            cj.put("user", nz(spec.user()));
            cj.put("filePath", nz(spec.filePath()));
            connections.put(cj);
        }
        root.put("connections", connections);
        return root.toString(2);
    }

    /** Parses connections; malformed input yields an empty list, never throws. */
    public static List<ConnectionSpec> fromJson(String json) {
        List<ConnectionSpec> specs = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return specs;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONArray connections = root.optJSONArray("connections");
            if (connections == null) {
                return specs;
            }
            for (int i = 0; i < connections.length(); i++) {
                ConnectionSpec spec = connection(connections.getJSONObject(i));
                if (spec != null) {
                    specs.add(spec);
                }
            }
        } catch (RuntimeException malformed) {
            // the message carries the parse position; the stack adds nothing
            LOG.log(Level.WARNING, "Malformed {0}; starting with no connections ({1})",
                    new Object[]{FILENAME, malformed.getMessage()});
            specs.clear();
        }
        return specs;
    }

    private static ConnectionSpec connection(JSONObject cj) {
        DbEngine engine;
        try {
            engine = DbEngine.valueOf(cj.optString("engine", ""));
        } catch (IllegalArgumentException unknownEngine) {
            // an engine from a newer NMOX: skip this connection, keep the rest
            return null;
        }
        String id = cj.optString("id", "");
        if (id.isBlank()) {
            id = UUID.randomUUID().toString(); // heal a hand-edited file
        }
        return new ConnectionSpec(
                id,
                cj.optString("name", "connection"),
                engine,
                cj.optString("host", ""),
                cj.optInt("port", -1),
                cj.optString("database", ""),
                cj.optString("user", ""),
                cj.optString("filePath", ""));
    }

    /** Writes {@code .nmoxdb.json} into the given project directory. */
    public static void save(File dir, List<ConnectionSpec> specs) throws IOException {
        Files.writeString(new File(dir, FILENAME).toPath(), toJson(specs),
                StandardCharsets.UTF_8);
    }

    /**
     * Loads connections from the given project directory. A missing or
     * unreadable or malformed file loads as an empty list — never throws.
     */
    public static List<ConnectionSpec> load(File dir) {
        File file = new File(dir, FILENAME);
        if (!file.isFile()) {
            return new ArrayList<>();
        }
        try {
            return fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot read " + file, e);
            return new ArrayList<>();
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
