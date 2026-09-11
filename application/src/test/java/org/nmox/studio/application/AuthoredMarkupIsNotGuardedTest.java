package org.nmox.studio.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A value the product AUTHORS as markup is never handed to the guard that
 * exists to stop markup.
 *
 * <p>The two halves of the v2.86.0 markup-render class pull in opposite
 * directions. {@code PlainText.plain} prepends a space so Swing declines to
 * parse text as HTML — the right answer wherever a directory name, a branch,
 * or a tool's output could reach a label. But roughly three dozen bundle
 * values in this product BEGIN with {@code <html>} on purpose: a wrapped
 * paragraph, a {@code <small>} note under a password field, a two-line
 * tooltip. Guarding one of those does not make it safer. It prints the tags
 * at the user and destroys the layout the value existed for.
 *
 * <p>That happened. v2.84.0 gave the Agent Port's disclosure a width-bounded
 * body so the one sentence a person must read would wrap; v2.86.0's sweep
 * wrapped it in {@code plain} — writing {@code PLAIN-LABEL-EXEMPT} on the
 * same line, so the comment and the code disagreed — and a German walk
 * photographed a screenful of literal {@code <html><body style='width: 720'>}
 * running off the dialog. Every gate was green: the label gate only asks that
 * a site be guarded OR exempt, and this one was both.
 *
 * <p>So the population comes from the ASSEMBLED cluster's own bundles, not
 * from a list: any key whose shipped English value starts with {@code <html}
 * is authored markup, and no source line may pass it through a guard. Bound
 * to {@code packaged-app-gates} — a gate reading {@code target/} in the test
 * phase passes on a stale cluster.
 */
class AuthoredMarkupIsNotGuardedTest {

    private static final Path MODULES = Path.of("target", "nmoxstudio", "nmoxstudio", "modules");

    private static final Path REPO = Path.of("..");

    private static final List<String> SOURCE_MODULES = List.of(
            "core", "editor", "tools", "rack", "project", "ui", "apiclient", "dbstudio", "web3", "infra");

    /** The guards whose whole job is to stop a string from being parsed as markup. */
    private static final List<String> GUARDS = List.of("PlainText.plain(", "PlainDialogs.plain(");

    @Test
    @DisplayName("the cluster carries authored-markup values, so this gate has a population")
    void thePopulationIsReal() throws IOException {
        Set<String> keys = authoredMarkupKeys();
        assertThat(keys).as("keys whose shipped value begins with <html>")
                .hasSizeGreaterThan(20)
                .contains("AgentPortAction_disclosure", "WorkspaceTrust_message");
    }

    @Test
    @DisplayName("no authored-markup value is passed through a markup guard")
    void authoredMarkupIsNeverGuarded() throws IOException {
        Set<String> keys = authoredMarkupKeys();
        List<String> guarded = new ArrayList<>();
        for (String module : SOURCE_MODULES) {
            Path src = REPO.resolve(module).resolve("src").resolve("main");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(src)) {
                for (Path java : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String body = Files.readString(java, StandardCharsets.UTF_8);
                    // the direct form, plus ONE hop: the real instance read
                    // plain(disclosureHtml(...)) and the key lived inside
                    // disclosureHtml, so a same-file method that produces an
                    // authored-markup value carries that value's law
                    Set<String> carriers = new TreeSet<>();
                    for (String key : keys) {
                        if (mentions(body, key)) {
                            carriers.add(key);
                            carriers.addAll(methodsMentioning(body, key));
                        }
                    }
                    if (carriers.isEmpty()) {
                        continue;
                    }
                    for (String guard : GUARDS) {
                        int at = -1;
                        while ((at = body.indexOf(guard, at + 1)) >= 0) {
                            String arg = argumentOf(body, at + guard.length());
                            for (String carrier : carriers) {
                                // word-bounded, and the boundary excludes a
                                // word character ONLY: a preceding '_' is what
                                // makes Foo_editSprint( look like editSprint(,
                                // while a preceding '.' is the Bundle.Key(…)
                                // form this gate exists to catch — excluding
                                // both let a planted direct guard survive
                                if (Pattern.compile("(?<!\\w)" + Pattern.quote(carrier) + "\\s*\\(")
                                        .matcher(arg).find()
                                        || arg.contains("\"" + carrier + "\"")) {
                                    guarded.add(REPO.relativize(java) + ": " + guard + carrier
                                            + "…) — a guard prints this value's tags at the user");
                                }
                            }
                        }
                    }
                }
            }
        }
        assertThat(guarded).as("authored markup handed to the guard that exists to stop markup").isEmpty();
    }

    /** Bundle.Key(…) is the generated accessor; "Key" the hand-written NbBundle form. */
    private static boolean mentions(String body, String key) {
        return body.contains("." + key + "(") || body.contains("\"" + key + "\"");
    }

    /**
     * Names of methods declared in this file that RETURN the key's value —
     * the one hop the real instance needed ({@code disclosureHtml} is
     * {@code return Bundle.AgentPortAction_disclosure(…)}).
     *
     * <p>"Returns it", not "mentions it": the first cut asked only for a
     * mention, and its first run named the Tasks window's {@code editSprint},
     * a long method that renders a velocity line somewhere in its middle and
     * never hands it to anything. A method that merely touches a value does
     * not carry that value's law.
     *
     * <p>Two hops are deliberately NOT followed: past that a source scan is
     * guessing, and the per-site render test (AgentPortDisclosureTest) is the
     * backstop that actually builds the component and asks Swing whether it
     * parsed the markup.
     */
    private static List<String> methodsMentioning(String body, String key) {
        List<String> names = new ArrayList<>();
        Matcher m = METHOD.matcher(body);
        while (m.find()) {
            int open = body.indexOf('{', m.end() - 1);
            if (open < 0) {
                continue;
            }
            int depth = 0;
            int i = open;
            while (i < body.length()) {
                char c = body.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                }
                i++;
            }
            if (returnsKey(body.substring(open, Math.min(body.length(), i + 1)), key)) {
                names.add(m.group(1));
            }
        }
        return names;
    }

    /** True when a method body hands the key's value straight back to its caller. */
    private static boolean returnsKey(String methodBody, String key) {
        int at = -1;
        while ((at = methodBody.indexOf("return", at + 1)) >= 0) {
            int end = methodBody.indexOf(';', at);
            if (end < 0) {
                return false;
            }
            if (mentions(methodBody.substring(at, end), key)) {
                return true;
            }
            at = end;
        }
        return false;
    }

    /** A method declaration with a body: modifiers, a return type, a name, parameters, an opening brace. */
    private static final Pattern METHOD = Pattern.compile(
            "(?m)^\\s{4,}(?:@\\w+\\s+)*(?:public|protected|private|static|final|synchronized|\\s)*"
            + "[\\w.<>,?\\[\\]]+\\s+(\\w+)\\s*\\([^;{]*\\)\\s*\\{");

    /** The argument text of a call whose open paren has just been consumed, to its matching close. */
    private static String argumentOf(String body, int from) {
        int depth = 1;
        int i = from;
        while (i < body.length() && depth > 0) {
            char c = body.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            i++;
        }
        return body.substring(from, Math.max(from, i - 1));
    }

    /** Every key the SHIPPED bundles give a value beginning with {@code <html}. */
    private static Set<String> authoredMarkupKeys() throws IOException {
        assertThat(MODULES).as("the assembled cluster's own modules").isDirectory();
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> jars = Files.list(MODULES)) {
            for (Path jarPath : jars.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (JarFile jar = new JarFile(jarPath.toFile())) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry e = entries.nextElement();
                        // the English base bundle only: a translation that
                        // dropped the markup is a different law's business
                        if (!e.getName().endsWith("/Bundle.properties")) {
                            continue;
                        }
                        Properties props = new Properties();
                        try (InputStream in = jar.getInputStream(e)) {
                            props.load(in);
                        }
                        for (String key : props.stringPropertyNames()) {
                            if (props.getProperty(key).toLowerCase(Locale.ROOT).startsWith("<html")) {
                                keys.add(key);
                            }
                        }
                    }
                }
            }
        }
        return keys;
    }
}
