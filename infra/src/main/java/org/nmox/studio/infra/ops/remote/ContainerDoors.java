package org.nmox.studio.infra.ops.remote;

import java.util.regex.Pattern;

/**
 * The remote lines behind a service's two doors — follow its logs, open
 * a shell in it. Pure. The only value spliced in is the container's
 * name, and that name was printed by the HOST ({@code docker ps}), so it
 * is the one string here an attacker's server could choose: it is held
 * to docker's own rule for a name before it comes near a shell, and a
 * name docker itself would refuse is refused here by name.
 */
public final class ContainerDoors {

    /** Docker's rule for a container name ({@code [a-zA-Z0-9][a-zA-Z0-9_.-]+}), with a length docker never exceeds. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");

    /** How much history the log door opens on. */
    public static final int TAIL_LINES = 200;

    private ContainerDoors() {
    }

    /** True for a name that can be spliced into a remote line: docker's own alphabet, nothing a shell reads. */
    public static boolean nameIsSafe(String container) {
        return container != null && NAME.matcher(container).matches();
    }

    private static String checked(String container) {
        if (!nameIsSafe(container)) {
            throw new IllegalArgumentException("not a container name: " + container);
        }
        return container;
    }

    /** Follow the container's output from its last {@value #TAIL_LINES} lines; Ctrl+C ends the follow, not the container. */
    public static String logs(String container) {
        return "docker logs -f --tail " + TAIL_LINES + " " + checked(container);
    }

    /** A shell inside the container: bash when the image has one, else sh. */
    public static String shell(String container) {
        String c = checked(container);
        return "docker exec -it " + c + " sh -c 'command -v bash >/dev/null 2>&1 && exec bash || exec sh'";
    }
}
