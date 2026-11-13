package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A learning space is somebody's FIRST project in a framework, and its
 * pins live inside a JSON string no bot reads: Dependabot keeps the
 * repository current around them. On 2026-11-13 the eight Vite-based
 * spaces were found on Vite 5 with Svelte 4 and React 18 while the
 * project templates had moved to Vite 8 that morning (ledger 140), and
 * the SvelteKit space had never been able to start at all.
 *
 * The law: every package a Vite-based learning space shares with one of
 * the Vite project templates rides the SAME MAJOR the template writes.
 * Both sides are derived. The template's lines come from the generator's
 * real output (every {@link ProjectTemplates} whose package.json names
 * {@code vite}), and the spaces are every catalog entry whose
 * package.json names {@code vite}, so a new Vite space is held on the
 * commit that adds it and a template bump fails here until the spaces
 * follow (AngularSpacePinParityTest's shape, widened from one framework
 * to the Vite family).
 *
 * A space that cannot follow says so in {@link #EXEMPT}: a slug, a
 * package and a reason a person can disagree with. An exemption that no
 * longer exempts anything fails too, so the list cannot outlive its
 * reasons.
 */
class ViteSpacePinParityTest {

    /** "slug package" → why this space cannot ride the template's major. */
    private static final Map<String, String> EXEMPT = Map.of(
            "qwik vite",
            "@builder.io/qwik 1.20.4, the newest stable Qwik, declares the peer"
            + " vite >=5 <8; its successor @qwik.dev/core is 2.0.0-rc.5, a release"
            + " candidate (measured 2026-11-13). The space rides Vite 7, the newest"
            + " major Qwik 1 accepts, until a stable Qwik takes Vite 8.");

    private static final Pattern MAJOR = Pattern.compile("(\\d+)");

    @Test
    @DisplayName("a Vite-based learning space rides the majors the Vite project templates write")
    void spacesRideTheTemplateMajors(@TempDir Path tmp) throws Exception {
        Map<String, String> template = templateLines(tmp);
        assertThat(template).as("the generator writes at least one Vite template")
                .containsKey("vite");

        Map<String, JSONObject> spaces = viteSpaces();
        assertThat(spaces).as("the catalog carries Vite-based spaces").isNotEmpty();

        List<String> behind = new ArrayList<>();
        List<String> usedExemptions = new ArrayList<>();
        for (Map.Entry<String, JSONObject> space : spaces.entrySet()) {
            for (Map.Entry<String, String> pin : pins(space.getValue()).entrySet()) {
                String wanted = template.get(pin.getKey());
                if (wanted == null || major(wanted).equals(major(pin.getValue()))) {
                    continue;
                }
                String key = space.getKey() + " " + pin.getKey();
                if (EXEMPT.containsKey(key)) {
                    usedExemptions.add(key);
                } else {
                    behind.add(key + " is " + pin.getValue()
                            + " while the templates write " + wanted);
                }
            }
        }
        assertThat(behind)
                .as("learning spaces whose pins left the templates' majors (move the space,"
                        + " prove it by npm install + its driver, or add an EXEMPT reason)")
                .isEmpty();
        assertThat(usedExemptions)
                .as("every exemption still exempts something; delete the ones that do not")
                .containsExactlyInAnyOrderElementsOf(EXEMPT.keySet());
    }

    /** package → range, from every project template whose package.json names vite. */
    private static Map<String, String> templateLines(Path tmp) throws Exception {
        Map<String, String> lines = new TreeMap<>();
        for (ProjectTemplates t : ProjectTemplates.values()) {
            File dir = tmp.resolve(t.name()).toFile();
            t.generate(dir, "parity");
            Path pkgFile = dir.toPath().resolve("package.json");
            if (!Files.isRegularFile(pkgFile)) {
                continue;
            }
            Map<String, String> pins = pins(new JSONObject(
                    Files.readString(pkgFile, StandardCharsets.UTF_8)));
            if (!pins.containsKey("vite")) {
                continue;
            }
            for (Map.Entry<String, String> pin : pins.entrySet()) {
                String earlier = lines.put(pin.getKey(), pin.getValue());
                assertThat(earlier == null || major(earlier).equals(major(pin.getValue())))
                        .as("the Vite templates agree with each other on " + pin.getKey())
                        .isTrue();
            }
        }
        return lines;
    }

    /** slug → package.json, for every catalog space whose package.json names vite. */
    private static Map<String, JSONObject> viteSpaces() throws Exception {
        JSONObject cat;
        try (var in = ProjectTemplates.class.getResourceAsStream("learn-catalog.json")) {
            cat = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        Map<String, JSONObject> out = new LinkedHashMap<>();
        JSONArray spaces = cat.getJSONArray("spaces");
        for (int i = 0; i < spaces.length(); i++) {
            JSONObject space = spaces.getJSONObject(i);
            JSONArray files = space.getJSONArray("files");
            for (int j = 0; j < files.length(); j++) {
                JSONObject f = files.getJSONObject(j);
                if (!"package.json".equals(f.getString("path"))) {
                    continue;
                }
                JSONObject pkg = new JSONObject(f.getString("content"));
                if (pins(pkg).containsKey("vite")) {
                    out.put(space.getString("slug"), pkg);
                }
            }
        }
        return out;
    }

    private static Map<String, String> pins(JSONObject pkg) {
        Map<String, String> out = new TreeMap<>();
        for (String section : List.of("dependencies", "devDependencies")) {
            JSONObject deps = pkg.optJSONObject(section);
            if (deps == null) {
                continue;
            }
            for (String name : deps.keySet()) {
                out.put(name, deps.getString(name));
            }
        }
        return out;
    }

    private static String major(String range) {
        Matcher m = MAJOR.matcher(range);
        assertThat(m.find()).as("a range with a number in it: " + range).isTrue();
        return m.group(1);
    }
}
