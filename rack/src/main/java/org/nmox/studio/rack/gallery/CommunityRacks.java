package org.nmox.studio.rack.gallery;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject;

/**
 * The community racks, installed and on the shelf: the
 * {@code *.nmoxrack.json} files under {@code racks/} ship INSIDE this module
 * and the gallery lists them on every install — no download, no drop-in dir.
 * That directory is the ONE home, and where a contributor adds a rack by
 * pull request.
 *
 * <p>The classpath cannot be listed portably, so {@code racks/index} names
 * the files (the {@code BundledDevices} idiom) and
 * {@code CommunityRacksGateTest} pins the index to the directory's real
 * contents — a rack that forgets its index line fails the build instead of
 * silently not shipping.
 *
 * <p>Every rack passes {@link RackJudge} before it is listed. One that does
 * not is SKIPPED with its reason logged against its file name: the build
 * gate makes that unreachable for a release, but the loader does not trust
 * the gate — a bad file must never cost the user the good ones, and never
 * reaches the caller as an exception.
 */
final class CommunityRacks {

    /** The file-name suffix every community rack carries; the rest is its stem. */
    static final String SUFFIX = ".nmoxrack.json";

    /** The resource directory, relative to this class. */
    static final String DIR = "racks/";

    /** The most index lines read: the shelf is a few dozen racks, never a feed. */
    private static final int MAX_RACKS = 200;

    private static final Logger LOG = Logger.getLogger(CommunityRacks.class.getName());

    /** One judged rack: its text (immutable — every reader parses a copy of its own) and what it holds. */
    record Loaded(String stem, String json, List<String> titles, int cables, List<String> wiring) {
        Loaded {
            titles = List.copyOf(titles);
            wiring = List.copyOf(wiring);
        }
    }

    /** Loaded once; module resources cannot change at runtime. */
    private static volatile List<Loaded> cached;

    private CommunityRacks() {
    }

    static List<Loaded> all() {
        List<Loaded> result = cached;
        if (result == null) {
            result = load(CommunityRacks::resource, CatalogNames.INSTANCE);
            cached = result;
        }
        return result;
    }

    /** The loader over any source of text — {@code read} answers null for a missing file. */
    static List<Loaded> load(Function<String, String> read, RackWiring.Names names) {
        String index = read.apply("index");
        if (index == null) {
            LOG.warning("community rack index missing — no community racks on the shelf");
            return List.of();
        }
        List<Loaded> found = new ArrayList<>();
        Set<String> stems = new HashSet<>();
        for (String name : indexLines(index)) {
            if (found.size() >= MAX_RACKS) {
                LOG.log(Level.WARNING, "community rack index: more than {0} racks, the rest skipped", MAX_RACKS);
                break;
            }
            if (!isRackFileName(name)) {
                LOG.log(Level.WARNING, "community rack {0} skipped: not a plain <stem>{1} file name",
                        new Object[]{RackWiring.plain(name), SUFFIX});
                continue;
            }
            String stem = name.substring(0, name.length() - SUFFIX.length());
            if (!stems.add(stem)) {
                LOG.log(Level.WARNING, "community rack {0} skipped: listed twice", name);
                continue;
            }
            String json = read.apply(name);
            List<String> problems = RackJudge.problemsOfText(name, json);
            if (!problems.isEmpty()) {
                LOG.log(Level.WARNING, "community rack skipped: {0}", String.join("; ", problems));
                continue;
            }
            JSONObject doc = new JSONObject(json);
            found.add(new Loaded(stem, json, RackWiring.titles(doc, names),
                    RackWiring.cableCount(doc), RackWiring.sketch(doc, names)));
        }
        return List.copyOf(found);
    }

    /** The index's file names: one per line, blank lines and {@code #} comments ignored. */
    static List<String> indexLines(String index) {
        List<String> out = new ArrayList<>();
        for (String line : index.split("\n")) {
            String name = line.strip();
            if (!name.isEmpty() && !name.startsWith("#")) {
                out.add(name);
            }
        }
        return out;
    }

    /** A bare file name with the rack suffix — never a path, so the index cannot reach outside {@code racks/}. */
    static boolean isRackFileName(String name) {
        if (!name.endsWith(SUFFIX) || name.length() == SUFFIX.length() || name.startsWith(".")) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '.';
            if (!ok) {
                return false;
            }
        }
        return !name.contains("..");
    }

    /** One resource, read through a cap: one byte past the judge's limit is enough to be refused for size. */
    private static String resource(String name) {
        try (InputStream in = CommunityRacks.class.getResourceAsStream(DIR + name)) {
            return in == null ? null : new String(in.readNBytes(RackJudge.MAX_BYTES + 1), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "community rack {0} unreadable: {1}", new Object[]{name, ex.toString()});
            return null;
        }
    }
}
