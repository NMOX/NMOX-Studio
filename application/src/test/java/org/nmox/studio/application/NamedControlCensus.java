package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Every Swing control the product constructs, with the variable it lands in —
 * the population the three accessible-name gates ask their question of.
 *
 * <p>The three gates (v2.85.0) each matched a DECLARATION:
 * {@code JPasswordField doToken = new JPasswordField(28)}. A field declared at
 * class level and assigned in a builder method — the ordinary shape for a
 * panel whose controls outlive one method — matched nothing, so it was never
 * asked. That is how three {@link javax.swing.JPasswordField}s holding CLOUD
 * API TOKENS sat unnamed in the rack's Options panel with all three gates
 * green: a screen reader announced "text field" for a DigitalOcean token.
 *
 * <p>So the population is derived from the CONSTRUCTION and the variable is
 * found by reading backwards to it, which is the outcome the law is about — a
 * control exists, and something must name it — rather than the one spelling
 * the first cut happened to see. Widening it named twelve controls across six
 * modules in five files nobody had looked at: inputs 95 → 107, text areas
 * 20 → 28, tables/lists/trees 31 → 37.
 *
 * <p>Three shapes it now reads that the declaration pattern could not:
 * <ul>
 *   <li>{@code doToken = new JPasswordField(28)} — declared elsewhere;
 *   <li>{@code fields[i] = new JPasswordField(32)} — an array element;
 *   <li>{@code JTable t = PlainTables\n.disableHtml(new JTable(m))} — a
 *       wrapper call split across lines, which the old pattern's contiguous
 *       {@code PlainTables\.} could not cross.
 * </ul>
 *
 * <p>Two shapes are deliberately NOT offenders, each for a stated reason. A
 * construction handed straight back ({@code return new JList<>(model)}) is a
 * FACTORY — the caller holds it and names it, which is the rule all three
 * gates already stated in prose. A construction assigned to nothing at all IS
 * an offender, because a control no variable holds is a control nothing can
 * name; measured at zero today, so the shape is closed by construction rather
 * than by a list.
 *
 * <p>Comments are stripped first ({@link GateSources}): a gate that matches a
 * literal is one comment away from decorative, in both directions — a
 * commented-out construction must not be counted, and a commented-out
 * {@code setAccessibleName} must not satisfy the law (v2.178.0).
 */
final class NamedControlCensus {

    private static final List<String> MODULES = List.of("core", "editor", "tools", "rack",
            "project", "ui", "apiclient", "dbstudio", "web3", "infra");

    private NamedControlCensus() {
    }

    /**
     * One construction of a control type.
     *
     * @param var the variable it is assigned to, or null when it is assigned
     *            to nothing (which is itself a finding — nothing can name it)
     * @param args the constructor's arguments verbatim, for the callers whose
     *             law depends on them (a {@code JCheckBox} built with its own
     *             text names itself)
     */
    record Site(String module, String file, int line, String var, String type,
            String args, boolean named) {

        String describe() {
            return module + "/" + file + ":" + line
                    + " (" + (var == null ? "assigned to nothing" : var) + " " + type + ")";
        }
    }

    /**
     * Every construction of the given types across the product's ten modules.
     *
     * @param typeAlternation a regex alternation of simple class names,
     *                        e.g. {@code "JTable|JList|JTree"}
     */
    static List<Site> sites(String typeAlternation) throws IOException {
        Pattern construction = Pattern.compile(
                "\\bnew\\s+(" + typeAlternation + ")\\b\\s*(?:<[^()\\n]*>)?\\s*\\(");
        List<Site> sites = new ArrayList<>();
        for (String module : MODULES) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                    String body = GateSources.stripComments(Files.readString(p));
                    Matcher m = construction.matcher(body);
                    while (m.find()) {
                        if (returned(body, m.start())) {
                            continue; // a factory: the caller holds it and names it
                        }
                        String var = assignedVar(body, m.start());
                        sites.add(new Site(module, p.getFileName().toString(),
                                1 + (int) body.chars().limit(m.start()).filter(c -> c == '\n').count(),
                                var, m.group(1), args(body, m.start()), named(body, var)));
                    }
                }
            }
        }
        return sites;
    }

    /** True when the construction is handed straight back to the caller. */
    private static boolean returned(String body, int newAt) {
        int i = newAt - 1;
        while (i >= 0 && Character.isWhitespace(body.charAt(i))) {
            i--;
        }
        int end = i + 1;
        while (i >= 0 && Character.isJavaIdentifierPart(body.charAt(i))) {
            i--;
        }
        return body.substring(i + 1, end).equals("return");
    }

    /**
     * Reads backwards from {@code new Type(} to the variable it lands in, or
     * null when it lands in none. Crosses any wrapper calls between the
     * {@code =} and the {@code new} ({@code x = PlainTables.plain(new JTable(}),
     * including ones broken across lines, and takes an array element's whole
     * subscript ({@code fields[i]}) because that is the text a later
     * {@code fields[i].getAccessibleContext()} will spell.
     */
    private static String assignedVar(String body, int newAt) {
        int i = newAt - 1;
        while (i >= 0 && Character.isWhitespace(body.charAt(i))) {
            i--;
        }
        while (i >= 0 && body.charAt(i) == '(') {
            i--;
            while (i >= 0 && (Character.isJavaIdentifierPart(body.charAt(i))
                    || body.charAt(i) == '.' || Character.isWhitespace(body.charAt(i)))) {
                i--;
            }
        }
        if (i < 0 || body.charAt(i) != '=') {
            return null;
        }
        if (i > 0 && "=!<>+-*/%&|^".indexOf(body.charAt(i - 1)) >= 0) {
            return null; // ==, +=, <=, … : a comparison or compound assign, not a binding
        }
        i--;
        while (i >= 0 && Character.isWhitespace(body.charAt(i))) {
            i--;
        }
        int end = i + 1;
        if (i >= 0 && body.charAt(i) == ']') {
            while (i >= 0 && body.charAt(i) != '[') {
                i--;
            }
            i--;
        }
        while (i >= 0 && (Character.isJavaIdentifierPart(body.charAt(i)) || body.charAt(i) == '.')) {
            i--;
        }
        String name = body.substring(i + 1, end);
        if (name.startsWith("this.")) {
            name = name.substring("this.".length());
        }
        return name.isEmpty() ? null : name;
    }

    /** The constructor's arguments verbatim, or "" when they cannot be read. */
    private static String args(String body, int newAt) {
        int open = body.indexOf('(', newAt);
        int depth = 0;
        for (int i = open; i >= 0 && i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return body.substring(open + 1, i).strip();
            }
        }
        return "";
    }

    /**
     * Swing derives a control's accessible name from an explicit name OR from
     * the label that labels it — measured, not assumed: a bare
     * {@code JPasswordField} answers null, the same field after a label's
     * {@code setLabelFor} answers that label's text, and an explicit name
     * still wins where one is set.
     *
     * <p>A name anywhere in the FILE counts, which is how all three gates have
     * always read: these controls are built in one class and named in another
     * of its methods. The cost is that two locals of the same name in one file
     * share one answer — the DB Studio results grid rode the editable grid's
     * name that way until it was looked at.
     */
    private static boolean named(String body, String var) {
        if (var == null) {
            return false;
        }
        return body.contains(var + ".getAccessibleContext().setAccessibleName(")
                || body.contains("this." + var + ".getAccessibleContext().setAccessibleName(")
                || Pattern.compile("setLabelFor\\(\\s*(?:this\\.)?" + Pattern.quote(var) + "\\s*\\)")
                        .matcher(body).find();
    }
}
