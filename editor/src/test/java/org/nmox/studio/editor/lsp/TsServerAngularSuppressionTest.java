package org.nmox.studio.editor.lsp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ledger 81, the structural cure: in an Angular workspace ngserver is
 * the ONLY server on {@code text/typescript}, because the platform's
 * rename refactoring collects edit sets from EVERY binding on the mime
 * — its capability predicate ({@code RenameRefactoringPlugin.
 * lambda$prepare$6}, decompiled from the shipped lsp-client jar) is
 * {@code iconst_1; ireturn}: always true, {@code renameProvider} never
 * consulted. Two servers bound means a class-property rename lands
 * twice at the declaration ({@code headingheading}, proven live twice
 * — the second time through a verifiably armed capability-stripping
 * filter, which changed nothing). The only lever the platform leaves
 * is WHICH servers are bound.
 */
class TsServerAngularSuppressionTest {

    private static String src() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/org/nmox/studio/editor/lsp/LanguageServers.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("tsserver yields text/typescript to ngserver in Angular workspaces")
    void tsYieldsInAngularWorkspaces() throws Exception {
        String src = src();
        int ts = src.indexOf("class TypeScriptServer");
        int end = src.indexOf("class JavaScriptTsServer");
        assertThat(ts).isPositive();
        assertThat(end).isGreaterThan(ts);
        String body = src.substring(ts, end);
        assertThat(body)
                .as("the Angular check keys on angular.json above the project")
                .contains("angularRootAbove(projectDir(lookup))");
        assertThat(body)
                .as("an Angular workspace gets NO tsserver — ngserver owns the mime")
                .containsPattern("if \\(angular\\) \\{\\s*\\n\\s*return null;");
    }

    @Test
    @DisplayName("plain JavaScript keeps its server — the suppression is TS-mime only")
    void javascriptUnaffected() throws Exception {
        String src = src();
        int js = src.indexOf("class JavaScriptTsServer");
        assertThat(js).isPositive();
        String before = src.substring(0, js);
        int reg = before.lastIndexOf("@MimeRegistration(mimeType = \"text/javascript\"");
        assertThat(reg)
                .as("text/javascript has its own registration on the unsuppressed class")
                .isPositive();
        String body = src.substring(js, src.indexOf("\n    }", src.indexOf("startServer", js)));
        assertThat(body)
                .as("the JS provider launches unconditionally")
                .doesNotContain("angularRootAbove")
                .contains("launchNpm(lookup, \"typescript-language-server\", \"--stdio\")");
    }

    @Test
    @DisplayName("the TypeScript registration is single-mime — no shared-registration backdoor")
    void tsRegistrationSingleMime() throws Exception {
        String src = src();
        int ts = src.indexOf("class TypeScriptServer");
        String before = src.substring(0, ts);
        int reg = before.lastIndexOf("@MimeRegistration(");
        String regBlock = before.substring(reg, ts);
        assertThat(regBlock)
                .contains("text/typescript")
                .doesNotContain("text/javascript");
    }
}
