package org.nmox.studio.infra.ops.remote;

import java.util.ArrayList;
import java.util.List;

/**
 * Going back to the image that ran before. A deploy tags every build
 * {@code nmox/<app>:<stamp>} and never removes the earlier ones, so the
 * host itself holds the history; a rollback reads the tags the host has,
 * picks the newest one OLDER than what runs now, and starts it under the
 * same name and ports — the last two steps of a deploy with an older
 * stamp, nothing new for a shell to read.
 *
 * <p>Pure: the remote line that lists the tags, the parse of its answer,
 * the choice. What the host prints is a host's word and is treated as
 * one: only all-digit tags count, anything else on a line is ignored.
 */
public final class Rollback {

    /**
     * How many lines of the host's answer a rollback will hold. One app's
     * tags are a few dozen lines; a host that prints without end is a
     * stranger's stream, and it is refused by name instead of collected
     * (the probe's rule, {@link HostProbe#MAX_LINES}).
     */
    public static final int MAX_TAG_LINES = 2_000;

    private Rollback() {
    }

    /** The remote line listing every tag the host holds for an app's image. */
    public static String imagesCommand(String app) {
        if (app == null || !DeployPlan.SLUG.matcher(app).matches()) {
            throw new IllegalArgumentException("not an app slug: " + app);
        }
        return "docker images nmox/" + app + " --format '{{.Tag}}'";
    }

    /** The stamp of an image this product built ({@code nmox/<app>:<digits>}), or null for anything else. */
    public static String stampOf(String image, String app) {
        String prefix = "nmox/" + app + ":";
        if (image == null || app == null || !image.startsWith(prefix)) {
            return null;
        }
        String tag = image.substring(prefix.length());
        return isStamp(tag) ? tag : null;
    }

    private static boolean isStamp(String tag) {
        return !tag.isEmpty() && tag.length() <= 20 && tag.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    /** The stamps among the lines a host printed, each once, newest first. */
    public static List<String> stamps(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String raw : lines) {
            String tag = raw == null ? "" : raw.strip();
            if (isStamp(tag) && !out.contains(tag)) {
                out.add(tag);
            }
        }
        out.sort((a, b) -> compare(b, a));
        return out;
    }

    /** Stamps are numbers too long for a long: the longer is the later, then digit by digit. */
    static int compare(String a, String b) {
        String x = a.replaceFirst("^0+(?=.)", "");
        String y = b.replaceFirst("^0+(?=.)", "");
        return x.length() != y.length() ? Integer.compare(x.length(), y.length()) : x.compareTo(y);
    }

    /** The newest stamp older than {@code current}; null when there is none (or nothing of ours runs now). */
    public static String previous(String current, List<String> stamps) {
        if (current == null) {
            return null;
        }
        for (String s : stamps(stamps)) {
            if (compare(s, current) < 0) {
                return s;
            }
        }
        return null;
    }
}
