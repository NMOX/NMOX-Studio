package org.nmox.studio.editor.format;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Format on a JavaScript or TypeScript document: the project's Prettier
 * when the project configures it, a sentence when it does not, and
 * nothing at all for a region.
 */
class PrettierReformatTest {

    @TempDir
    Path project;

    private final Consumer<String> realStatus = PrettierReformat.statusSink;
    private final Consumer<Runnable> realLane = PrettierReformat.lane;
    private final Consumer<Runnable> realEdt = PrettierReformat.onEventThread;
    private final Supplier<PrettierFormatter> realFormatter = PrettierReformat.formatter;

    private final List<String> said = new ArrayList<>();
    private final List<List<String>> ran = new ArrayList<>();

    @BeforeEach
    void seams() throws Exception {
        Files.createDirectories(project.resolve(".git"));
        // a Prettier that resolves (the Runner seam means the stub never runs), in a trusted project
        Path bin = Files.createDirectories(project.resolve("node_modules/.bin"));
        Path prettier = bin.resolve("prettier");
        Files.writeString(prettier, "#!/bin/sh\ncat\n");
        assertThat(prettier.toFile().setExecutable(true)).isTrue();
        Files.writeString(bin.resolve("prettier.cmd"), "@echo off\r\n");
        org.nmox.studio.rack.service.WorkspaceTrust.trust(project.toFile());
        PrettierReformat.statusSink = said::add;
        PrettierReformat.lane = Runnable::run;
        PrettierReformat.onEventThread = Runnable::run;
        PrettierReformat.formatter = () -> new PrettierFormatter((command, dir, stdin) -> {
            ran.add(command);
            return new PrettierFormatter.Result(0, stdin.replace("      return", "  return"));
        });
    }

    @AfterEach
    void restore() {
        PrettierReformat.statusSink = realStatus;
        PrettierReformat.lane = realLane;
        PrettierReformat.onEventThread = realEdt;
        PrettierReformat.formatter = realFormatter;
    }

    private static Document doc(String text) throws Exception {
        Document doc = new PlainDocument();
        doc.insertString(0, text, null);
        return doc;
    }

    @Test
    @DisplayName("only a reformat of the WHOLE document is the Format gesture; a region is a template or a paste")
    void wholeDocumentsOnly() {
        assertThat(PrettierReformat.wholeDocument(0, 120, 120)).isTrue();
        assertThat(PrettierReformat.wholeDocument(0, 121, 120)).as("the platform may pass the end past the text").isTrue();
        assertThat(PrettierReformat.wholeDocument(0, 119, 120)).isFalse();
        assertThat(PrettierReformat.wholeDocument(3, 120, 120)).isFalse();
        assertThat(PrettierReformat.wholeDocument(40, 60, 120)).isFalse();
        assertThat(PrettierReformat.wholeDocument(0, 0, 0)).as("an empty document is whole").isTrue();
    }

    @Test
    @DisplayName("a project that configures Prettier is formatted by it, with the same edit and the same sentence as Format with Prettier")
    void theProjectsPrettierFormats() throws Exception {
        Files.writeString(project.resolve(".prettierrc"), "{}");
        File file = project.resolve("shop.js").toFile();
        Document doc = doc("function f() {\n      return 1;\n}\n");
        PrettierReformat.run(doc, doc.getText(0, doc.getLength()), file);
        assertThat(ran).hasSize(1);
        assertThat(doc.getText(0, doc.getLength())).isEqualTo("function f() {\n  return 1;\n}\n");
        assertThat(said).containsExactly("Formatted with Prettier.");
        assertThat(ran.get(0)).contains("--stdin-filepath", file.getAbsolutePath());
    }

    @Test
    @DisplayName("a project with no Prettier configuration is told so, with the door that formats anyway; nothing runs, nothing changes")
    void noConfigurationSpeaks() throws Exception {
        File file = project.resolve("shop.js").toFile();
        Document doc = doc("function f() {\n      return 1;\n}\n");
        PrettierReformat.run(doc, doc.getText(0, doc.getLength()), file);
        assertThat(ran).as("no process for a project that never chose Prettier").isEmpty();
        assertThat(doc.getText(0, doc.getLength())).isEqualTo("function f() {\n      return 1;\n}\n");
        assertThat(said).containsExactly("Nothing was formatted: this project does not configure Prettier, and "
                + "NMOX Studio has no other formatter for this language. Format with Prettier, on the editor’s "
                + "right-click menu, formats the file with Prettier’s defaults.");
    }

    @Test
    @DisplayName("text typed while Prettier ran is not overwritten: the result is refused and says so")
    void typingDuringTheRunWins() throws Exception {
        Files.writeString(project.resolve(".prettierrc"), "{}");
        File file = project.resolve("shop.js").toFile();
        Document doc = doc("function f() {\n      return 1;\n}\n");
        String snapshot = doc.getText(0, doc.getLength());
        List<Runnable> edt = new ArrayList<>();
        PrettierReformat.onEventThread = edt::add;
        PrettierReformat.run(doc, snapshot, file);
        assertThat(ran).hasSize(1);
        doc.insertString(0, "// typed\n", null);
        edt.forEach(Runnable::run);
        assertThat(doc.getText(0, doc.getLength())).startsWith("// typed\n").contains("      return 1;");
        assertThat(said).containsExactly("Document changed while formatting — run it again.");
    }

    @Test
    @DisplayName("registered for the languages the platform has no formatter for, and for none it has one for")
    void registeredWhereNothingElseFormats() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/editor/format/PrettierReformat.java"), StandardCharsets.UTF_8);
        for (String mime : List.of("text/javascript", "text/typescript", "text/x-vue", "text/x-svelte",
                "text/x-astro", "text/x-graphql")) {
            assertThat(src).contains("@MimeRegistration(mimeType = \"" + mime + "\", service = ReformatTask.Factory.class");
        }
        for (String mime : List.of("text/html", "text/css", "text/x-json", "text/x-yaml", "text/scss", "text/less")) {
            assertThat(src).as("the platform formats " + mime + " itself: registering here would run both")
                    .doesNotContain("mimeType = \"" + mime + "\"");
        }
        // nothing but the text is read on the caller's thread: the walk up the disk and the process ride the lane
        String reformat = src.substring(src.indexOf("public void reformat()"), src.indexOf("static boolean wholeDocument"));
        assertThat(reformat).contains("lane.accept(").doesNotContain("projectOptedIn").doesNotContain("formatOnDemand");
    }
}
