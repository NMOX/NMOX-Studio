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
    /** ssh because the host's probe found no mosh-server. */
    SSH_NO_REMOTE_MOSH,
    /** ssh because the host has not answered a probe yet, so nobody knows whether it has mosh-server. */
    SSH_REMOTE_UNKNOWN;

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
        if (remoteMosh == null) {
            // not "it has none": a host that never answered was said to lack what nobody had asked it for
            return SSH_REMOTE_UNKNOWN;
        }
        if (!remoteMosh) {
            return SSH_NO_REMOTE_MOSH;
        }
        return MOSH;
    }
}
