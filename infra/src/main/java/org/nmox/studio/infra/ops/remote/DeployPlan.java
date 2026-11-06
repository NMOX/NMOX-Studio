package org.nmox.studio.infra.ops.remote;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Exactly what a deploy runs on the host, as shell lines, decided before
 * anything runs so the confirm dialog can show them and a test can read
 * them. The only values spliced in are a validated slug, two validated
 * ports and a stamp of digits — nothing a project or a host chose reaches
 * a shell unquoted.
 *
 * <p>The shape: the bundle unpacks beside the previous source, swaps in,
 * builds an image tagged with the stamp, replaces the one container by
 * name, and the last step reads back what docker runs. Containers carry
 * {@code nmox.ops.app} so the probe tells a deploy from a neighbour.
 *
 * @param app the app slug ({@code [a-z0-9][a-z0-9-]*}, at most 40 characters)
 * @param hostPort the port published on the host
 * @param containerPort the port the image listens on
 * @param stamp digits only — the image tag
 */
public record DeployPlan(String app, int hostPort, int containerPort, String stamp) {

    public static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");
    /** Where every app lives on the host. */
    public static final String ROOT = "/srv/nmox";
    /** The label the probe reads a deploy back by. */
    public static final String LABEL = "nmox.ops.app";

    public DeployPlan {
        if (app == null || !SLUG.matcher(app).matches()) {
            throw new IllegalArgumentException("not an app slug: " + app);
        }
        if (hostPort < 1 || hostPort > 65535 || containerPort < 1 || containerPort > 65535) {
            throw new IllegalArgumentException("not a port");
        }
        if (stamp == null || stamp.isEmpty() || !stamp.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("not a stamp: " + stamp);
        }
    }

    /** A folder or project name as a slug: lower-cased, runs of other characters as one dash. */
    public static String slugOf(String name) {
        String s = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.isEmpty()) {
            s = "app";
        }
        if (s.length() > 40) {
            s = s.substring(0, 40).replaceAll("-+$", "");
        }
        return s.isEmpty() ? "app" : s;
    }

    public String dir() {
        return ROOT + "/" + app;
    }

    public String image() {
        return "nmox/" + app + ":" + stamp;
    }

    public String container() {
        return "nmox-" + app;
    }

    /** Step 1: receive the bundle on stdin into a fresh folder. */
    public String unpack() {
        return "set -e; mkdir -p " + dir() + "; rm -rf " + dir() + "/src.next; mkdir " + dir() + "/src.next"
                + "; tar xzf - -C " + dir() + "/src.next";
    }

    /** Step 2: the new source becomes current; the previous stays as src.prev. */
    public String swap() {
        return "set -e; cd " + dir() + "; rm -rf src.prev; if [ -d src ]; then mv src src.prev; fi; mv src.next src";
    }

    /** Step 3: build the image. */
    public String build() {
        return "docker build -t " + image() + " -t nmox/" + app + ":latest " + dir() + "/src";
    }

    /** Step 4: replace the container by name. */
    public String run() {
        return "docker rm -f " + container() + " >/dev/null 2>&1 || true; docker run -d --name " + container()
                + " --label " + LABEL + "=" + app + " --restart unless-stopped -p " + hostPort + ":" + containerPort
                + " " + image();
    }

    /** Step 5: read back what runs and whether the port answers. */
    public String verify() {
        return "sleep 2; docker ps --filter 'name=^" + container() + "$' --format '{{json .}}'"
                + "; code=$(curl -s -o /dev/null -m 5 -w '%{http_code}' http://127.0.0.1:" + hostPort
                + "/ 2>/dev/null || echo none); echo http " + hostPort + " $code";
    }

    /** The whole plan as the lines the confirm dialog shows. */
    public List<String> describe() {
        return List.of(unpack(), swap(), build(), run(), verify());
    }
}
