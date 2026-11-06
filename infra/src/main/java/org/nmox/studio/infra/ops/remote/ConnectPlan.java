package org.nmox.studio.infra.ops.remote;

/**
 * Which door a click on a host opens. mosh when both ends have it (the
 * roaming, lag-hiding shell the Hub prefers), ssh when either end lacks
 * it, and the reason is said beside the choice so the inspector can name
 * it. Pure.
 */
public enum ConnectPlan {
    MOSH,
    /** ssh because mosh is not installed on this machine. */
    SSH_NO_LOCAL_MOSH,
    /** ssh because the host has no mosh-server (or has not said yet). */
    SSH_NO_REMOTE_MOSH,
    /** ssh because the host runs a system where mosh-server is not expected (its probe said so). */
    SSH_PREFERRED;

    public boolean mosh() {
        return this == MOSH;
    }

    /**
     * @param moshOnPath {@code mosh} resolves on this machine
     * @param remoteMosh the host's probe found {@code mosh-server}; null before the first probe
     */
    public static ConnectPlan choose(boolean moshOnPath, Boolean remoteMosh) {
        if (!moshOnPath) {
            return SSH_NO_LOCAL_MOSH;
        }
        if (remoteMosh == null || !remoteMosh) {
            return SSH_NO_REMOTE_MOSH;
        }
        return MOSH;
    }
}
