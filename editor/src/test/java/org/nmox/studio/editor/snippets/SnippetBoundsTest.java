package org.nmox.studio.editor.snippets;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.editor.snippets.SnippetBody.Refused;
import org.nmox.studio.editor.snippets.SnippetTemplates.CodeTemplateText;
import org.nmox.studio.editor.snippets.VsCodeSnippets.Parsed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * What a hostile {@code .code-snippets} file in a clone can cost: the
 * review's probes, kept as fixtures. Every test that could run away has a
 * clock, so a regression fails here instead of hanging the build.
 */
class SnippetBoundsTest {

    private static final Duration CLOCK = Duration.ofSeconds(10);

    private static SnippetContext ctx() {
        return SnippetContext.at(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC), new SecureRandom());
    }

    /** {@code ${1:$2$2$2$2}${2:$3$3$3$3}…${levels:leaf}}: 236 characters at sixteen levels. */
    private static String fourFold(int levels, String leaf) {
        StringBuilder b = new StringBuilder();
        for (int k = 1; k < levels; k++) {
            b.append("${").append(k).append(':');
            for (int r = 0; r < 4; r++) {
                b.append('$').append(k + 1);
            }
            b.append('}');
        }
        return b.append("${").append(levels).append(':').append(leaf).append('}').toString();
    }

    private static String file(String body) {
        return "{\"boom\":{\"prefix\":\"log\",\"body\":" + org.json.JSONObject.quote(body) + "}}";
    }

    @Test
    @DisplayName("placeholder defaults that name one another four times, sixteen deep, are refused at load in moments")
    void exponentialDefaultsRefusedAtLoad() {
        String body = fourFold(16, "x");
        assertThat(body).hasSize(236);
        Parsed parsed = assertTimeoutPreemptively(CLOCK, () -> VsCodeSnippets.parse(file(body), "team.code-snippets"));
        assertThat(parsed.snippets()).isEmpty();
        assertThat(parsed.notes()).singleElement().asString()
                .contains("\"boom\" is left out because it would insert more than 1000000 characters");
    }

    @Test
    @DisplayName("a default read once and kept reads the same as before: eight deep, every stop written once")
    void eachDefaultIsReadOnce() throws Refused {
        SnippetBody body = SnippetBody.parse(fourFold(8, "x"));
        CodeTemplateText text = assertTimeoutPreemptively(CLOCK, () -> SnippetTemplates.toCodeTemplate(body, ctx()));
        // every stop once as written: 4^7 + 4^6 + … + 1 x's in the plain text
        int expected = 0;
        for (int k = 0; k < 8; k++) {
            expected += 1 << (2 * k);
        }
        assertThat(text.plain()).hasSize(expected).matches("x+");
    }

    @Test
    @DisplayName("defaults that name one another four times but say nothing are each read once, and the snippet is kept")
    void silentDefaultsAreReadOnce() throws Refused {
        // no ring and nothing to insert: no size bound can stop this, only reading each default once
        SnippetBody body = SnippetBody.parse(fourFold(16, ""));
        CodeTemplateText text = assertTimeoutPreemptively(CLOCK,
                () -> SnippetTemplates.toCodeTemplate(body, ctx(), Long.MAX_VALUE / 4));
        assertThat(text.plain()).isEmpty();
    }

    @Test
    @DisplayName("defaults in a ring with nothing to insert are refused by their steps, not read four billion times")
    void ringOfEmptyDefaultsRefused() throws Refused {
        // the leaf names the first stop: every reading is cut short, so none can be kept
        SnippetBody body = SnippetBody.parse(fourFold(16, "$1"));
        assertThatThrownBy(() -> assertTimeoutPreemptively(CLOCK,
                () -> SnippetTemplates.toCodeTemplate(body, ctx(), Long.MAX_VALUE / 4)))
                .isInstanceOf(Refused.class)
                .hasMessageContaining("name one another more than");
        // and on the translation's own clock, which runs out first
        assertThatThrownBy(() -> assertTimeoutPreemptively(CLOCK,
                () -> SnippetTemplates.toCodeTemplate(body, ctx(), 1_000L)))
                .isInstanceOf(Refused.class)
                .hasMessageContaining("its placeholders were not read within");
    }

    @Test
    @DisplayName("a ring reads as it always did: a stop inside its own default is nothing there, from wherever it began")
    void ringsKeepTheirReading() throws Refused {
        CodeTemplateText text = SnippetTemplates.toCodeTemplate(SnippetBody.parse("${1:a$2} ${2:b$1} $2"), ctx());
        assertThat(text.plain()).isEqualTo("ab ba ba");
    }

    @Test
    @DisplayName("a body kept without its trial is still bounded when it is accepted")
    void untriedBodyIsBoundedAtAccept() throws Exception {
        Parsed parsed = VsCodeSnippets.parse(file(fourFold(16, "x")), "team.code-snippets", 0);
        assertThat(parsed.snippets()).hasSize(1);
        SnippetBody body = parsed.snippets().get(0).body();
        assertThatThrownBy(() -> assertTimeoutPreemptively(CLOCK,
                () -> SnippetTemplates.toCodeTemplate(body, ctx())))
                .isInstanceOf(SnippetTemplates.TooLarge.class);
    }

    @Test
    @DisplayName("the caret's line a thousand times is refused at accept by its size, though the trial (an empty line) passed")
    void totalTextIsCapped() throws Exception {
        Parsed parsed = VsCodeSnippets.parse(file("$TM_CURRENT_LINE".repeat(1000)), "t.code-snippets");
        assertThat(parsed.snippets()).hasSize(1);
        SnippetBody body = parsed.snippets().get(0).body();
        SnippetContext line = ctx().withLine("x".repeat(2000), 0, null, null);
        assertThatThrownBy(() -> assertTimeoutPreemptively(CLOCK,
                () -> SnippetTemplates.toCodeTemplate(body, line)))
                .isInstanceOf(SnippetTemplates.TooLarge.class)
                .hasMessage("it would insert more than 1000000 characters");
        // the same body at a short line is an ordinary snippet
        assertThat(SnippetTemplates.toCodeTemplate(body, ctx().withLine("ab", 0, null, null)).plain())
                .hasSize(2000);
    }

    @Test
    @DisplayName("a 112-character body that multiplies the caret's line is refused at accept, not built to 43 million")
    void multipliedLineIsCapped() throws Exception {
        Parsed parsed = VsCodeSnippets.parse(file(fourFold(8, "$TM_CURRENT_LINE")), "t.code-snippets");
        assertThat(parsed.snippets()).as("its trial, on an empty line, passes").hasSize(1);
        SnippetBody body = parsed.snippets().get(0).body();
        SnippetContext line = ctx().withLine("x".repeat(2000), 0, null, null);
        assertThatThrownBy(() -> assertTimeoutPreemptively(CLOCK,
                () -> SnippetTemplates.toCodeTemplate(body, line)))
                .isInstanceOf(SnippetTemplates.TooLarge.class);
    }

    @Test
    @DisplayName("a variable longer than the clipboard's bound refuses the snippet rather than enter it")
    void variableValuesAreCapped() throws Refused {
        String big = "y".repeat(SnippetVariables.MAX_VALUE_CHARS + 1);
        assertThatThrownBy(() -> SnippetTemplates.toCodeTemplate(SnippetBody.parse("[$TM_CURRENT_LINE]"),
                ctx().withLine(big, 0, null, null)))
                .isInstanceOf(Refused.class)
                .hasMessageContaining("$TM_CURRENT_LINE holds " + big.length() + " characters");
        assertThatThrownBy(() -> SnippetTemplates.toCodeTemplate(SnippetBody.parse("[${1:$TM_SELECTED_TEXT}]"),
                ctx().withLine("", 0, null, big)))
                .isInstanceOf(Refused.class)
                .hasMessageContaining("$TM_SELECTED_TEXT holds");
        String atBound = "y".repeat(SnippetVariables.MAX_VALUE_CHARS);
        assertThat(SnippetTemplates.toCodeTemplate(SnippetBody.parse("$TM_SELECTED_TEXT"),
                ctx().withLine("", 0, null, atBound)).plain()).isEqualTo(atBound);
    }

    @Test
    @DisplayName("the status line names a refusal by size; any other refusal points at the log")
    void statusLineNamesTheSize() {
        assertThat(SnippetInsertion.refusal("Boom", new SnippetTemplates.TooLarge()))
                .isEqualTo("Snippet “Boom” was not inserted: it would insert more than 1000000 characters.");
        assertThat(SnippetInsertion.refusal("Boom", new Refused("a reason")))
                .isEqualTo("Snippet “Boom” was not inserted: it cannot be honoured whole in this file."
                        + " The log says why.");
    }

    @Test
    @DisplayName("no repository around the file, no snippets: a package.json and .vscode in a shared folder are not read")
    void noRepositoryNoSnippets(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("package.json"), "{}");
        Files.createDirectories(tmp.resolve(".vscode"));
        Files.writeString(tmp.resolve(".vscode/x.code-snippets"), "{\"planted\":{\"prefix\":\"log\",\"body\":\"PLANTED $1\"}}");
        Path deep = Files.createDirectories(tmp.resolve("sub/deep"));
        File edited = Files.writeString(deep.resolve("a.js"), "").toFile();
        ProjectSnippets.Found found = ProjectSnippets.read(edited);
        assertThat(found.snippets()).isEmpty();
        assertThat(found.workspace()).as("nothing found, and no folder named as the workspace").isNull();
    }

    @Test
    @DisplayName("a folder has at most one read queued: every query while it waits shares it")
    void oneReadPerFolder(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve(".git"));
        File edited = Files.writeString(tmp.resolve("a.js"), "").toFile();
        String key = tmp.toFile().getPath();
        CountDownLatch release = new CountDownLatch(1);
        Future<?> busy = ProjectSnippets.RP.submit(() -> {
            release.await(10, TimeUnit.SECONDS);
            return null;
        });
        try {
            Future<ProjectSnippets.Found> first = ProjectSnippets.pending(key, edited);
            Future<ProjectSnippets.Found> second = ProjectSnippets.pending(key, edited);
            assertThat(second).isSameAs(first);
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            busy.get(10, TimeUnit.SECONDS);
            assertThat(ProjectSnippets.pending(key, edited)).as("once read, the next query reads again")
                    .isNotSameAs(first);
        } finally {
            release.countDown();
            ProjectSnippets.awaitIdle();
        }
    }
}
