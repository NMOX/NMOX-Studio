package org.nmox.studio.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Welcome's Clone link reaches into the git module by class name
 * (3.1.0, {@code CloneRepository}): the wizard's action only runs with a
 * git {@code ContextHolder} in its context, and the git module does not
 * export that class. A platform upgrade that renames either class would
 * make the link fall back to its refusal; this holds both names, and the
 * constructor the link calls, against the assembled cluster's own bytes.
 */
class CloneRepositoryReflectionTest {

    private static final Path CLUSTER = Path.of("target", "nmoxstudio");

    @Test
    @DisplayName("the classes the Clone link names exist in the cluster, with the constructor it calls")
    void theNamedClassesExist() throws Exception {
        String src = Files.readString(Path.of("..", "ui", "src", "main", "java", "org", "nmox", "studio", "ui",
                "actions", "CloneRepository.java"));
        String holder = constant(src, "HOLDER");
        String context = constant(src, "VCS_CONTEXT");
        assertThat(entry(holder)).as("%s ships", holder).isTrue();
        assertThat(entry(context)).as("%s ships", context).isTrue();
        byte[] holderBytes = bytes(holder);
        // the constructor descriptor (Lorg/.../VCSContext;)V sits in the constant pool
        assertThat(new String(holderBytes, java.nio.charset.StandardCharsets.ISO_8859_1))
                .as("ContextHolder(VCSContext)")
                .contains("(L" + context.replace('.', '/') + ";)V");
    }

    private static String constant(String src, String name) {
        Matcher m = Pattern.compile("static final String " + name + " = \"([^\"]+)\"").matcher(src);
        assertThat(m.find()).as(name).isTrue();
        return m.group(1);
    }

    private static boolean entry(String className) throws Exception {
        return bytes(className) != null;
    }

    private static byte[] bytes(String className) throws Exception {
        String entry = className.replace('.', '/') + ".class";
        try (Stream<Path> jars = Files.walk(CLUSTER)) {
            for (Path jar : jars.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (ZipFile z = new ZipFile(jar.toFile())) {
                    var e = z.getEntry(entry);
                    if (e != null) {
                        return z.getInputStream(e).readAllBytes();
                    }
                }
            }
        }
        return null;
    }
}
