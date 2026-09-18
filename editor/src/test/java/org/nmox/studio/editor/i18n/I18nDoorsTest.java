package org.nmox.studio.editor.i18n;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Tools ▸ Check Translations… door is wired the safe way round
 * (the {@code RackShareDoorsTest} shape, v2.177.0): the run rides a named
 * RequestProcessor and never the EDT, publishes under the literal tool
 * name {@code i18n}, publishes NOTHING on a parse failure (M3 — the
 * slither rule), and every status text rides {@code PlainStatus.text}.
 * {@code I18nCheckTest} proves the checks decide correctly; this is the
 * other half of the v1.321.0 pair — the call sites exist and are wired.
 */
class I18nDoorsTest {

    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/org/nmox/studio/editor/i18n/CheckTranslationsAction.java"))
                .replace("\r\n", "\n");
    }

    @Test
    @DisplayName("the run is posted to a named RequestProcessor from the action, never run inline")
    void runsOffTheEdt() throws Exception {
        String src = source();
        assertThat(src).contains("new RequestProcessor(\"nmox-i18n-check\", 1)");
        int perform = src.indexOf("public void actionPerformed(");
        String body = src.substring(perform, src.indexOf("static void run(", perform));
        assertThat(body).as("the disk reads are posted to the lane").contains("RP.post(() -> run(");
        // the outcome, not the mechanism: the run is called ONCE in the action,
        // and that once is the posted one — an inline run( beside the post
        // satisfied the line above on the 2026-09-17 arc review
        int posted = body.indexOf("RP.post(() -> run(") + "RP.post(() -> ".length();
        assertThat(body.indexOf("run(")).as("the first run( is the posted one").isEqualTo(posted);
        assertThat(body.lastIndexOf("run(")).as("and there is no other").isEqualTo(posted);
        assertThat(body).as("nothing reads the project on the dispatch thread")
                .doesNotContain("I18nCatalogs.detect(").doesNotContain("I18nUsage.scan(");
        assertThat(src).as("a raw thread would fail DaemonThreadGateTest too").doesNotContain("new Thread(");
    }

    @Test
    @DisplayName("Menu/Tools at a position the census found free, always enabled, refuses out loud with no aim")
    void registeredAndRefusing() throws Exception {
        String src = source();
        assertThat(src).contains("@ActionID(category = \"Tools\"");
        assertThat(src).contains("@ActionReference(path = \"Menu/Tools\", position = 92)");
        assertThat(src).contains("Bundle.CheckTranslationsAction_aimFirst()");
    }

    @Test
    @DisplayName("M3: a parse failure publishes NOTHING — the catch block never touches the bus; a run publishes under \"i18n\"")
    void parseFailurePublishesNothing() throws Exception {
        String src = source();
        int run = src.indexOf("static void run(");
        String body = src.substring(run, src.indexOf("static List<DiagnosticsBus.Problem> problems(", run));
        int catchAt = body.indexOf("catch (I18nCatalogs.ParseFailure");
        assertThat(catchAt).as("the parse failure is caught where the run begins").isPositive();
        int catchEnd = body.indexOf("return;", catchAt);
        String catchBlock = body.substring(catchAt, catchEnd);
        assertThat(catchBlock).as("a crash is not an all-clear: the last good batch stays true")
                .doesNotContain("DiagnosticsBus.publish(");
        assertThat(catchBlock).contains("status(Bundle.CheckTranslationsAction_parseError(");
        assertThat(body).as("the one publish site, under the literal tool name")
                .contains("DiagnosticsBus.publish(TOOL, problems(report))");
        assertThat(src).contains("static final String TOOL = \"i18n\";");
        assertThat(body.indexOf("DiagnosticsBus.publish("))
                .as("the publish comes after the parse could have failed")
                .isGreaterThan(catchEnd);
    }

    @Test
    @DisplayName("every status text is guarded: setStatusText takes PlainStatus.text and nothing else")
    void statusTextsAreGuarded() throws Exception {
        String src = source();
        int at = 0;
        int sites = 0;
        while ((at = src.indexOf("setStatusText(", at)) >= 0) {
            sites++;
            assertThat(src.startsWith("PlainStatus.text(", at + "setStatusText(".length()))
                    .as("status text at " + at + " must ride PlainStatus.text").isTrue();
            at += "setStatusText(".length();
        }
        assertThat(sites).isEqualTo(1);
    }
}
