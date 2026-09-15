package org.nmox.studio.dbstudio.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bson.Document;

/**
 * Stops a running MongoDB command ON THE SERVER (debt ledger 10).
 *
 * <p>Measured against mongo:7 with the bundled 5.11 sync driver:
 * interrupting the thread blocked in {@code runCommand} does NOT stop a
 * slow command, because the driver's socket read cannot be interrupted.
 * The client just goes on waiting until the server finishes. So a real
 * cancel has to go through the server:
 * {@code currentOp {$ownOps: true, appName: <this backend's name>}} finds
 * the operation, and {@code killOp} ends it. The blocked call then returns
 * the server's "operation was interrupted" error, which the backend reports
 * as a cancel.
 *
 * <p>Each {@link MongoBackend} gives its client a unique application
 * name, so the filter can only match this backend's own operations,
 * never another tool's or another connection's. {@code $ownOps} keeps
 * the lookup within what an unprivileged user may see and kill. The
 * {@code currentOp} call lists itself, so it is filtered out here as
 * well as by name. Every step is best-effort: a failure is logged and
 * the thread interrupt still follows.
 */
final class MongoServerCancel {

    private static final Logger LOG = Logger.getLogger(MongoServerCancel.class.getName());

    private MongoServerCancel() {
    }

    /** The {@code currentOp} command that lists this backend's own operations. */
    static Document currentOpCommand(String appName) {
        return new Document("currentOp", 1).append("$ownOps", true).append("appName", appName);
    }

    /**
     * The opids in a {@code currentOp} reply that belong to
     * {@code appName} and are not themselves cancel plumbing
     * ({@code currentOp}/{@code killOp}).
     */
    static List<Object> opsToKill(Document currentOpReply, String appName) {
        List<Object> ops = new ArrayList<>();
        if (currentOpReply == null || appName == null
                || !(currentOpReply.get("inprog") instanceof List<?> inprog)) {
            return ops;
        }
        for (Object entry : inprog) {
            if (!(entry instanceof Document op) || !appName.equals(op.getString("appName"))) {
                continue;
            }
            Object opid = op.get("opid");
            if (opid == null) {
                continue;
            }
            if (op.get("command") instanceof Document command
                    && (command.containsKey("currentOp") || command.containsKey("killOp"))) {
                continue;
            }
            ops.add(opid);
        }
        return ops;
    }

    /**
     * Finds and kills this backend's running operations through
     * {@code admin} (a transport bound to the {@code admin} database).
     * Returns how many kills the server accepted. Never throws.
     */
    static int kill(MongoCursorPager.Transport admin, String appName) {
        List<Object> ops;
        try {
            ops = opsToKill(admin.run(currentOpCommand(appName)), appName);
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "currentOp failed; the cancel falls back to the interrupt", e);
            return 0;
        }
        int killed = 0;
        for (Object opid : ops) {
            try {
                admin.run(new Document("killOp", 1).append("op", opid));
                killed++;
            } catch (RuntimeException e) {
                LOG.log(Level.FINE, "killOp failed for " + opid, e);
            }
        }
        return killed;
    }
}
