package org.nmox.studio.rack.gallery;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.gallery.RackGallery.Entry;
import org.nmox.studio.rack.gallery.RackGallery.Source;
import org.nmox.studio.rack.projectstudio.RackPresets;
import org.openide.util.NbBundle;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documentation that cannot lie, the {@code DeviceDocsTest} way:
 * docs/racks.md is generated from the gallery catalog itself — every
 * community rack, preset and starter with its description, what it fits, what
 * it requires, its devices and its wiring sketch. This test fails when the
 * committed file drifts from the catalog; regenerate with
 *
 *   mvn -pl rack test -Dtest=RackGalleryDocsTest -Dnmox.docs.write=true
 *
 * <p>The document is ENGLISH on every lane whatever the machine speaks: a
 * community rack is read with {@code RackCard.of(doc, "")} (through the
 * entry's patch), and a preset's or starter's words come from the base bundle
 * by {@link Locale#ROOT} — not from {@code RackPresets}' fields, which were
 * read once in whatever locale first touched the enum.
 */
class RackGalleryDocsTest {

    @Test
    @DisplayName("docs/racks.md matches the rack gallery exactly")
    void rackReferenceIsCurrent(@TempDir Path noDropIns) throws Exception {
        String generated = generate(noDropIns.toFile());
        File doc = new File("../docs/racks.md");
        if (Boolean.getBoolean("nmox.docs.write")) {
            Files.writeString(doc.toPath(), generated);
            return;
        }
        assertThat(doc).as("docs/racks.md missing — regenerate with -Dnmox.docs.write=true").exists();
        // a Windows checkout materializes the file with CRLF; the generator's \n
        // content is the canonical form, so fold line endings before comparing
        assertThat(Files.readString(doc.toPath()).replace("\r\n", "\n"))
                .as("docs/racks.md is stale — regenerate with -Dnmox.docs.write=true")
                .isEqualTo(generated);
    }

    @Test
    @DisplayName("the English the document prints is the English the shelf shows: the bundle keys derived here are the ones RackPresets and RackGallery read")
    void theDerivedKeysAreTheRealOnes(@TempDir Path noDropIns) {
        if (!Locale.getDefault().getLanguage().isEmpty() && !"en".equals(Locale.getDefault().getLanguage())) {
            return; // on a translated machine the shelf is translated; the document still is not
        }
        for (Entry e : RackGallery.entries(null, noDropIns.toFile())) {
            assertThat(englishName(e)).as(e.id()).isEqualTo(e.card().name());
            assertThat(englishDescription(e)).as(e.id()).isEqualTo(e.card().description());
        }
    }

    static String generate(File noDropIns) {
        StringBuilder md = new StringBuilder();
        md.append("# The Rack Gallery\n\n");
        md.append("Every rack the product hands out, straight from the catalog — this file is\n");
        md.append("generated from `RackGallery` by `RackGalleryDocsTest` and CI fails if it\n");
        md.append("drifts. Do not edit by hand; regenerate with:\n\n");
        md.append("```\nmvn -pl rack test -Dtest=RackGalleryDocsTest -Dnmox.docs.write=true\n```\n\n");
        md.append("A rack file is a saved rack: the devices in order, every knob and setting, and\n");
        md.append("the patch cables between their jacks, as JSON (`.nmoxrack.json` — what Save\n");
        md.append("Patch writes beside a project). A file meant to travel also carries a `shared`\n");
        md.append("header that says what it is: a `name`, a `description`, the project `kinds` it\n");
        md.append("fits and the tools it `requires` on the PATH. Mounting a rack runs nothing —\n");
        md.append("every rack here arrives at rest, and each device still asks Workspace Trust\n");
        md.append("before its first command.\n\n");
        md.append("## Contribute a rack\n\n");
        md.append("The community racks live in one directory, and a pull request is how one joins:\n\n");
        md.append("1. Build the rack in the IDE and use Share… to write it, or write the JSON by\n");
        md.append("   hand. Name it `<stem>.nmoxrack.json` (lower-case letters, digits, hyphens).\n");
        md.append("2. Put it in `rack/src/main/resources/org/nmox/studio/rack/gallery/racks/` and\n");
        md.append("   add its file name as a line of `index` in the same directory.\n");
        md.append("3. Give the `shared` header a `name` (60 characters at most), a `description`\n");
        md.append("   that says what the rack DOES and when you would want it (400 at most),\n");
        md.append("   `kinds` and `requires`. English only; translations follow as\n");
        md.append("   `name.<lang>` / `description.<lang>` siblings.\n");
        md.append("4. Run the gate: `mvn -pl rack -am test -Dtest=CommunityRacksGateTest`. It fails\n");
        md.append("   by file name with the reason: only built-in devices, every cable really\n");
        md.append("   mounts, nothing switched on that would start by itself, no absolute or home path, no address but\n");
        md.append("   localhost, bare tool names, real project kinds.\n");
        md.append("5. Regenerate this file (the command above) and open the pull request.\n");

        List<Entry> entries = RackGallery.entries(null, noDropIns);
        section(md, "Community racks", entries, Source.COMMUNITY);
        section(md, "Presets", entries, Source.PRESET);
        section(md, "Starter racks", entries, Source.STARTER);
        return md.toString();
    }

    private static void section(StringBuilder md, String heading, List<Entry> entries, Source source) {
        md.append("\n## ").append(heading).append("\n");
        if (source == Source.STARTER) {
            md.append("\nWhat a project with no saved rack starts with, decided by what the project is.\n");
        }
        for (Entry e : entries) {
            if (e.source() != source) {
                continue;
            }
            md.append("\n### ").append(englishName(e)).append("\n\n");
            md.append("> ").append(englishDescription(e)).append("\n\n");
            if (source == Source.COMMUNITY) {
                md.append("- **File:** `").append(e.id().substring("community:".length()))
                        .append(CommunityRacks.SUFFIX).append("`\n");
                md.append("- **Fits:** ").append(e.card().kinds().isEmpty() ? "any project"
                        : String.join(", ", e.card().kinds())).append("\n");
                if (!e.card().requires().isEmpty()) {
                    md.append("- **Requires:** `").append(String.join("`, `", e.card().requires())).append("`\n");
                }
            }
            md.append("- **Devices:** ").append(String.join(", ", e.deviceTitles())).append("\n");
            md.append("- **Wiring:**\n");
            for (String line : e.wiring()) {
                md.append("  - `").append(line).append("`\n");
            }
        }
    }

    // ---- the English record, whatever the machine speaks ----

    static String englishName(Entry e) {
        return english(e, "Name", org.nmox.studio.rack.model.RackCard.of(e.patch(), "").name());
    }

    static String englishDescription(Entry e) {
        return english(e, "Description", org.nmox.studio.rack.model.RackCard.of(e.patch(), "").description());
    }

    private static String english(Entry e, String field, String fromFile) {
        String name = e.id().substring(e.id().indexOf(':') + 1);
        return switch (e.source()) {
            case PRESET -> root(RackPresets.class).getString("RackPresets_" + camel(name) + field);
            case STARTER -> root(RackGallery.class).getString("RackGallery_starter"
                    + Character.toUpperCase(name.charAt(0)) + name.substring(1) + field);
            default -> fromFile;
        };
    }

    private static ResourceBundle root(Class<?> beside) {
        return NbBundle.getBundle(beside.getPackageName() + ".Bundle", Locale.ROOT, beside.getClassLoader());
    }

    /** {@code MULTI_CHAIN_BENCH} → {@code multiChainBench}: how RackPresets spells its bundle keys. */
    static String camel(String constant) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : constant.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                sb.append(upper ? c : Character.toLowerCase(c));
                upper = false;
            }
        }
        return sb.toString();
    }
}
