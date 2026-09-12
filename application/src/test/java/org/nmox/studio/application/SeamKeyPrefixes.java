package org.nmox.studio.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Which key families have their English somewhere other than a bundle.
 *
 * <p>Five catalogue seams (v2.132.0–v2.134.0) look up a key that is allowed
 * to be missing, because the English it falls back to is the RECORD — a
 * device enum that generates {@code docs/devices.md}, a catalogue file a
 * drop-in author wrote, a template enum this codebase reads. Putting an
 * English copy in a base bundle would be the second home v2.131.0 spent a
 * release removing, so these keys exist in the twelve translated bundles
 * and in no base bundle at all.
 *
 * <p>{@code LocaleBundleParityTest} has to know that, or it calls every one
 * of those keys "extra" — which it did, twice, and each time the fix was to
 * hand-add prefixes to a list inside the gate. That list was a second home
 * for the fact, in a file none of the seams' authors would think to open:
 * add a sixth seam and the parity gate fails on keys that are perfectly
 * correct, and the tempting way to quiet it is a base-bundle copy — the
 * exact defect the design exists to prevent.
 *
 * <p>So the fact lives at the seam now, as a {@code KEY_PREFIXES}
 * declaration built from the same string literals the lookups use, and this
 * reads them. A declaration that disagreed with its lookups would need two
 * literals where there is one; a prefix that is simply WRONG is caught by
 * the parity scan it feeds, which reports the real keys as extra.
 */
final class SeamKeyPrefixes {

    private static final Path REPO = Path.of("..");

    private static final List<String> MODULES = List.of(
            "core", "editor", "tools", "rack", "infra", "apiclient",
            "dbstudio", "web3", "project", "ui");

    /** A class that looks up an optional key. */
    private static final Pattern CALLS_OPTIONAL = Pattern.compile("Bundles\\.optional\\s*\\(");

    /** {@code static final List<String> KEY_PREFIXES = List.of(NAME, DESC);} */
    private static final Pattern DECLARATION = Pattern.compile(
            "KEY_PREFIXES\\s*=\\s*List\\.of\\(([^)]*)\\)");

    /** {@code private static final String NAME = "TemplateName_";} */
    private static final Pattern CONSTANT = Pattern.compile(
            "static\\s+final\\s+String\\s+(\\w+)\\s*=\\s*\"([^\"]*)\"");

    private static final Pattern LITERAL = Pattern.compile("\"([^\"]*)\"");

    /**
     * Seams that own no key family of their own, and why.
     *
     * <p>A blessing here is not "skip this file" — it is the claim that the
     * file looks up keys whose English IS in a base bundle, so the parity
     * gate should keep checking them exactly as it checks everything else.
     */
    static final Map<String, String> OWNS_NO_FAMILY = new LinkedHashMap<>();

    static {
        OWNS_NO_FAMILY.put("LocaleRefresher.java",
                "looks up OTHER packages' own CTL_<window id> keys when the language "
                + "switches live (v2.103.0). Those keys have a base bundle like any "
                + "chrome string — this seam owns none of them and exempts none of "
                + "them; it passes null so a window it cannot name is left alone "
                + "rather than blanked.");
    }

    private SeamKeyPrefixes() {
    }

    /** Every shipping file that looks up a key allowed to be missing. */
    static List<Path> callers() {
        List<Path> out = new ArrayList<>();
        for (String module : MODULES) {
            Path src = REPO.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                    if (CALLS_OPTIONAL.matcher(read(p)).find()) {
                        out.add(p);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return out;
    }

    /**
     * The prefixes one seam declares, empty when it declares none.
     *
     * <p>A seam names its families through the SAME constant its lookups
     * build keys from — one literal per family in the whole file — so the
     * declaration cannot drift from the use. That is what makes this read
     * the constant's value rather than expecting a literal inside the list.
     * (The first cut of this reader expected literals, which the design it
     * was written for had deliberately moved into constants; it read every
     * seam as declaring nothing, and said so loudly.) A literal written
     * straight into the list is still honoured, for a seam with one family
     * and no constant to spare.
     */
    static List<String> declaredBy(Path file) {
        String source = read(file);
        Matcher decl = DECLARATION.matcher(source);
        if (!decl.find()) {
            return List.of();
        }
        Map<String, String> constants = new LinkedHashMap<>();
        Matcher c = CONSTANT.matcher(source);
        while (c.find()) {
            constants.put(c.group(1), c.group(2));
        }
        List<String> out = new ArrayList<>();
        for (String entry : decl.group(1).split(",")) {
            String term = entry.trim();
            if (term.isEmpty()) {
                continue;
            }
            Matcher lit = LITERAL.matcher(term);
            if (lit.matches()) {
                out.add(lit.group(1));
            } else if (constants.containsKey(term)) {
                out.add(constants.get(term));
            } else {
                // a term this reader cannot resolve is not silently dropped:
                // an unresolvable name comes back as itself, so it fails the
                // ends-with-a-separator law by name instead of quietly
                // exempting nothing
                out.add(term);
            }
        }
        return out;
    }

    /** Every key family whose English lives at its record rather than a bundle. */
    static Set<String> all() {
        Set<String> out = new LinkedHashSet<>();
        for (Path caller : callers()) {
            out.addAll(declaredBy(caller));
        }
        return out;
    }

    static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
