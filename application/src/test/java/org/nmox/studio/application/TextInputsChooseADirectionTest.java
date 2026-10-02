package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every text input says what it holds: prose, or machine text (3.5.3).
 *
 * <p>A window's direction reaches every text component in it. That is right
 * for a card's title and a question to KVASIR, which a Hebrew or Arabic
 * reader writes right to left. It is wrong for a host name, a port, a path,
 * an address, a command, a colour, a contract's ABI or a log: those run
 * left to right in every language, and mirrored they are right-aligned
 * with their punctuation moved to the other end. The right-to-left walk
 * photographed the Browser's address as {@code /http://127.0.0.1:3000}.
 *
 * <p>Which one a field holds cannot be derived; it is a decision. So the
 * decision is written at the constructor, {@code TextDirection.keepLeftToRight}
 * or {@code TextDirection.followsReader}, and this gate derives the
 * POPULATION: every text field, area, pane and password field any module
 * creates. 118 were classified when it was written; a new one fails here
 * until somebody has decided.
 */
class TextInputsChooseADirectionTest {

    private static final Pattern CREATED = Pattern.compile(
            "new (?:javax\\.swing\\.)?(JTextField|JTextArea|JEditorPane|JTextPane|JPasswordField)\\b(?!\\s*\\[)");

    static List<String> undecided(Path repo) throws IOException {
        List<String> out = new ArrayList<>();
        for (String module : List.of("core", "editor", "tools", "rack", "project",
                "ui", "infra", "apiclient", "dbstudio", "web3")) {
            Path src = repo.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String raw = Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
                    String[] code = GateSources.stripComments(raw).split("\n", -1);
                    for (int i = 0; i < code.length; i++) {
                        Matcher m = CREATED.matcher(code[i]);
                        while (m.find()) {
                            // the decision wraps the constructor: on this line before it,
                            // or opening on the line above when the call was wrapped
                            String before = code[i].substring(0, m.start());
                            String above = i > 0 ? code[i - 1].stripTrailing() : "";
                            boolean decided = decides(before)
                                    || (before.isBlank() && above.endsWith("(") && decides(above));
                            if (!decided) {
                                out.add(module + "/" + p.getFileName() + ":" + (i + 1) + " " + m.group(1));
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    private static boolean decides(String code) {
        return code.contains("keepLeftToRight(") || code.contains("followsReader(");
    }

    @Test
    @DisplayName("every text input in the product is marked as prose or as machine text")
    void everyInputHasDecided() throws IOException {
        assertThat(undecided(Path.of("..")))
                .as("what does it hold? TextDirection.followsReader(…) for words a person writes, "
                        + "TextDirection.keepLeftToRight(…) for a path, an address, a command, code")
                .isEmpty();
    }

    @Test
    @DisplayName("the gate reads constructors in code, on one line or wrapped onto the next")
    void theGateBites(@org.junit.jupiter.api.io.TempDir Path repo) throws IOException {
        Path dir = Files.createDirectories(repo.resolve("ui/src/main/java/x"));
        Files.writeString(dir.resolve("A.java"), String.join("\n",
                "class A {",
                "  // new JTextField() in a comment decides nothing and needs nothing",
                "  Object a = new javax.swing.JTextField(8);",
                "  Object b = TextDirection.keepLeftToRight(new JTextField(\"localhost\"));",
                "  Object c = TextDirection.followsReader(new JTextArea(3, 20));",
                "  Object d = TextDirection.keepLeftToRight(",
                "          new JTextArea(sql));",
                "  Object e = new JPasswordField[3];",
                "  Object f = new JPasswordField(28);",
                "  Object g = wrap(",
                "          new JTextPane());",
                "}"));

        assertThat(undecided(repo)).containsExactly(
                "ui/A.java:3 JTextField",
                "ui/A.java:9 JPasswordField",
                "ui/A.java:11 JTextPane");
    }
}
