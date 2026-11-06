package org.nmox.studio.infra.ops.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The little a deploy needs to read out of a Dockerfile: which ports the
 * image says it listens on. {@code EXPOSE 3000 8080/tcp} lists both; a
 * Dockerfile with no EXPOSE lists none, and the deploy dialog asks.
 */
public final class Dockerfiles {

    private static final Pattern EXPOSE = Pattern.compile("(?im)^\\s*EXPOSE\\s+(.+)$");

    private Dockerfiles() {
    }

    /** Every port an EXPOSE line names, in order, each once. */
    public static List<Integer> exposedPorts(String dockerfile) {
        List<Integer> out = new ArrayList<>();
        if (dockerfile == null) {
            return out;
        }
        Matcher m = EXPOSE.matcher(dockerfile);
        while (m.find()) {
            for (String token : m.group(1).trim().split("\\s+")) {
                String t = token;
                int comment = t.indexOf('#');
                if (comment == 0) {
                    break;
                }
                int slash = t.indexOf('/');
                if (slash >= 0) {
                    t = t.substring(0, slash);
                }
                if (t.startsWith("$")) {
                    continue; // an ARG the build decides; nothing to read here
                }
                try {
                    int port = Integer.parseInt(t);
                    if (port > 0 && port <= 65535 && !out.contains(port)) {
                        out.add(port);
                    }
                } catch (NumberFormatException notAPort) {
                    // a build-time variable or a typo: not a port
                }
            }
        }
        return out;
    }
}
