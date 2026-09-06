package org.nmox.studio.application;

import java.io.IOException;
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
 * Every table, list and tree in the product speaks its name (v2.85.0,
 * the third leg of the accessibility sweep after text areas and
 * inputs): the census found 26 — the device shelf, the wizard's
 * template list, the Docker tables, SONAR's ports, BLACKBOX's timeline,
 * API Studio's collections and tables, DB Studio's tree and grid,
 * Contract Studio's artifacts and reports — that a screen reader could
 * only call "table". A name or a labelFor anywhere in the file counts;
 * a factory names what it builds.
 */
class CollectionsNamedGateTest {

    private static final Pattern COLLECTION = Pattern.compile(
            "(JTable|JList<[^>]*>|JList|JTree)\\s+(\\w+)\\s*=\\s*(?:[\\w.]*PlainTables\\.\\w+\\(\\s*)?new\\s+(?:JTable|JList|JTree)\\b");

    @Test
    @DisplayName("every JTable, JList and JTree constructed in the product is named or labelled")
    void everyCollectionIsNamed() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String module : new String[]{"core", "editor", "tools", "rack", "project",
            "ui", "apiclient", "dbstudio", "web3", "infra"}) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String body = Files.readString(p);
                    Matcher m = COLLECTION.matcher(body);
                    while (m.find()) {
                        String var = m.group(2);
                        boolean named = body.contains(var + ".getAccessibleContext().setAccessibleName(")
                                || Pattern.compile("setLabelFor\\(\\s*" + Pattern.quote(var) + "\\s*\\)").matcher(body).find();
                        if (!named) {
                            int line = 1 + (int) body.chars().limit(m.start()).filter(c -> c == '\n').count();
                            offenders.add(module + "/" + p.getFileName() + ":" + line + " (" + var + ")");
                        }
                    }
                }
            }
        }
        assertThat(offenders)
                .as("a table, list or tree without an accessible name — a screen reader hears only the role")
                .isEmpty();
    }
}
