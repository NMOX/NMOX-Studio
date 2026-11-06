package org.nmox.studio.infra.ops.remote;

import java.io.InputStream;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * The one way the Hub runs something on a host: a remote shell line over
 * ssh, optional bytes on its stdin, every output line handed back as it
 * arrives, the exit code at the end. The real implementation spawns
 * {@code ssh}; the simulation answers from a script; tests hand in a fake.
 */
public interface RemoteRunner {

    /** What a run came back with. {@code exitCode} is -1 when the leash fired or ssh could not start. */
    record Result(int exitCode, String tail, boolean timedOut) {

        public boolean ok() {
            return exitCode == 0;
        }
    }

    /**
     * @param target where
     * @param remoteCommand one shell line for the remote login shell
     * @param stdin bytes for the remote command's stdin, or null for none
     * @param onLine every line of stdout and stderr, in arrival order, never null
     * @param timeout the whole run's leash
     */
    Result run(SshTarget target, String remoteCommand, InputStream stdin,
            Consumer<String> onLine, Duration timeout);
}
