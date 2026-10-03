package org.nmox.studio.editor.snippets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.nmox.studio.editor.snippets.SnippetBody.Node;
import org.nmox.studio.editor.snippets.SnippetBody.Refused;
import org.nmox.studio.editor.snippets.SnippetBody.TabStop;
import org.nmox.studio.editor.snippets.SnippetBody.Text;
import org.nmox.studio.editor.snippets.SnippetBody.Transform;
import org.nmox.studio.editor.snippets.SnippetBody.Variable;

/**
 * A parsed VS Code snippet, translated into the platform's code-template
 * language, which is what gives it tab stops, mirrors and one undo.
 *
 * <p><b>The target grammar</b>, read from the RELEASE310 bytecode of
 * {@code ParametrizedTextParser} and {@code CodeTemplateParameterImpl}
 * and then driven for real (see {@code SnippetEngineTest}):
 * <ul>
 * <li>in text, {@code $$} is a dollar and <code>$&#123;</code> opens a
 *     parameter; a dollar before anything else is itself. So every dollar of a
 *     body's literal text is written {@code $$}, and nothing else in
 *     text needs escaping;</li>
 * <li>a parameter is {@code ${name hint=value hint="va lue"}}; the
 *     first of a name is the one typed into and later ones mirror it;
 *     {@code default} is the text it starts with, {@code ordering} its
 *     place in the Tab order, {@code editable=false} takes it out of
 *     that order; {@code ${cursor}} is where the caret ends and
 *     {@code ${no-indent}} tells the engine to leave the text as
 *     written, which this translation needs because it has already
 *     indented the body VS Code's way;</li>
 * <li>inside a quoted hint a quote ends the value and a backslash
 *     starts an escape, <b>and the engine's escape handling drops the
 *     character after the escape</b> (measured:
 *     {@code default="a\"bcd"} inserts {@code a"cd}). So a default
 *     holding a quote or a backslash is NOT written into the template;
 *     it travels beside it in {@link CodeTemplateText#values} and the
 *     engine is handed it through its own
 *     {@code CodeTemplateProcessor} door
 *     ({@link SnippetTemplateProcessor}). Every other default is
 *     written in place, where a dollar, a brace or a line break is an
 *     ordinary character.</li>
 * </ul>
 *
 * <p><b>What VS Code has and this engine has not</b>, and what is done
 * about each:
 * <ul>
 * <li><i>Placeholders inside a placeholder's default</i>
 *     ({@code ${1:foo ${2:bar}}}): the engine's parameters cannot nest.
 *     The outer one is the tab stop, starting as {@code foo bar}; the
 *     inner one is a stop of its own only where it also appears outside
 *     ({@code … $2}), and then it starts as {@code bar}.</li>
 * <li><i>Choices</i> ({@code ${1|one,two|}}): the engine has no list to
 *     offer, so the first choice is the default and the rest are not
 *     shown.</li>
 * <li><i>A transform on a tab stop</i>
 *     ({@code ${1/(.*)/${1:/upcase}/}}): written as a parameter that
 *     cannot be typed into, recomputed from its source whenever the
 *     source changes ({@link Live}); VS Code recomputes when the stop is
 *     left, this recomputes as it is typed. A stop that appears ONLY
 *     transformed has nothing to type into and refuses the snippet.</li>
 * <li><i>{@code ${0:text}}</i>: the text is inserted and the caret
 *     lands after it.</li>
 * </ul>
 *
 * <p>Indentation is VS Code's: every line of the body's text after its
 * first begins with the caret line's own leading whitespace, and each
 * leading tab of a body line becomes the editor's indent unit. The
 * values of variables are inserted as they are.
 *
 * <p>Pure: a function of the body and the {@link SnippetContext}.
 */
public final class SnippetTemplates {

    /** The parameter that tells the engine not to re-indent what it inserts. */
    static final String NO_INDENT = "${no-indent}";

    private SnippetTemplates() {
    }

    /**
     * A snippet as the code-template engine takes it.
     *
     * @param text the parametrized text, for {@code CodeTemplateManager.createTemporary}
     * @param values parameter name to starting text, for the defaults the grammar cannot carry
     * @param lives the parameters recomputed from another as it is typed
     * @param plain the text the snippet inserts before anything is typed, for an editor the engine cannot drive
     * @param plainCaret where in {@code plain} the caret ends
     */
    public record CodeTemplateText(String text, Map<String, String> values, List<Live> lives,
            String plain, int plainCaret) {
    }

    /**
     * A parameter that is another one, transformed.
     *
     * @param source the parameter typed into
     * @param target the parameter that shows it transformed
     * @param transform how
     */
    public record Live(String source, String target, Transform transform) {
    }

    /**
     * Translates {@code body} for the editor {@code ctx} describes.
     *
     * @throws Refused when a transform runs past its bounds on this
     *         file's values, or a tab stop is only ever transformed;
     *         nothing is inserted then
     */
    public static CodeTemplateText toCodeTemplate(SnippetBody body, SnippetContext ctx) throws Refused {
        return toCodeTemplate(body, ctx, BUDGET_NANOS);
    }

    /**
     * How long ALL the transforms of one translation may take together,
     * in nanoseconds. Each transform has its own clock
     * ({@link SnippetTransforms#BUDGET_NANOS}), and a body may hold
     * hundreds of them, each stopping just short of it; this is what an
     * accepted snippet can cost the event thread at the very most.
     */
    static final long BUDGET_NANOS = 250_000_000L;

    /** {@link #toCodeTemplate(SnippetBody, SnippetContext)} with the transforms given {@code budgetNanos} between them. */
    static CodeTemplateText toCodeTemplate(SnippetBody body, SnippetContext ctx, long budgetNanos) throws Refused {
        Run run = new Run(ctx, budgetNanos);
        List<Node> nodes = run.invent(body.nodes(), maxStop(body.nodes(), 0));
        run.collectDefaults(nodes);
        run.emit(nodes);
        for (Live live : run.lives) {
            int number = Integer.parseInt(live.source().substring(1));
            if (!run.masters.contains(number)) {
                throw new Refused("tab stop " + number
                        + " is only written with a transform, so there is nothing to type into");
            }
        }
        int caret = run.plainCaret < 0 ? run.plain.length() : run.plainCaret;
        return new CodeTemplateText(NO_INDENT + run.template, Map.copyOf(run.values),
                List.copyOf(run.lives), run.plain.toString(), caret);
    }

    private static int maxStop(List<Node> nodes, int max) {
        for (Node n : nodes) {
            if (n instanceof TabStop ts) {
                max = Math.max(max, maxStop(ts.children(), Math.max(max, ts.number())));
            } else if (n instanceof Variable v) {
                max = Math.max(max, maxStop(v.children(), max));
            }
        }
        return max;
    }

    /** Whether the template grammar carries {@code value} exactly inside a quoted hint. */
    static boolean grammarCarries(String value) {
        return value.indexOf('"') < 0 && value.indexOf('\\') < 0;
    }

    private static final class Run {

        private final SnippetContext ctx;
        private final Map<Integer, List<Node>> defaults = new HashMap<>();
        private final Map<Integer, String> defaultText = new HashMap<>();
        private final Map<String, Integer> invented = new LinkedHashMap<>();
        private final Map<Integer, Integer> transformed = new HashMap<>();
        private final Set<Integer> masters = new HashSet<>();
        private final StringBuilder template = new StringBuilder();
        private final StringBuilder plain = new StringBuilder();
        private final Map<String, String> values = new LinkedHashMap<>();
        private final List<Live> lives = new ArrayList<>();
        private final long budgetNanos;
        private final long deadline;
        private int next;
        private int plainCaret = -1;

        Run(SnippetContext ctx, long budgetNanos) {
            this.ctx = ctx;
            this.budgetNanos = budgetNanos;
            this.deadline = System.nanoTime() + budgetNanos;
        }

        /** One transform, on whatever is left of this translation's time. */
        private String transform(Transform transform, String input) throws Refused {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                throw new Refused("its transforms did not finish within "
                        + (budgetNanos / 1_000_000L) + " ms between them");
            }
            return SnippetTransforms.apply(transform, input, Math.min(left, SnippetTransforms.BUDGET_NANOS));
        }

        /**
         * VS Code's rule for a name it does not know: {@code ${foo}}
         * becomes a placeholder holding {@code foo}, numbered after the
         * body's own, and the same name twice is one placeholder twice.
         * A name it does not know WITH a default stays a variable, which
         * then inserts the default.
         */
        List<Node> invent(List<Node> nodes, int max) {
            if (next == 0) {
                next = max + 1;
            }
            List<Node> out = new ArrayList<>(nodes.size());
            for (Node n : nodes) {
                if (n instanceof Variable v) {
                    if (v.children().isEmpty() && !SnippetVariables.known(v.name())) {
                        Integer number = invented.get(v.name());
                        if (number == null) {
                            number = next++;
                            invented.put(v.name(), number);
                        }
                        out.add(new TabStop(number, List.of(new Text(v.name())), List.of(), null));
                    } else {
                        out.add(new Variable(v.name(), invent(v.children(), max), v.transform()));
                    }
                } else if (n instanceof TabStop ts) {
                    out.add(new TabStop(ts.number(), invent(ts.children(), max), ts.choices(), ts.transform()));
                } else {
                    out.add(n);
                }
            }
            return out;
        }

        /** The first occurrence of a number that HAS a value gives every occurrence its value. */
        void collectDefaults(List<Node> nodes) {
            for (Node n : nodes) {
                if (n instanceof TabStop ts) {
                    if (ts.number() > 0 && !defaults.containsKey(ts.number())) {
                        if (!ts.children().isEmpty()) {
                            defaults.put(ts.number(), ts.children());
                        } else if (!ts.choices().isEmpty()) {
                            defaults.put(ts.number(), List.of(new Text(ts.choices().get(0))));
                        }
                    }
                    collectDefaults(ts.children());
                } else if (n instanceof Variable v) {
                    collectDefaults(v.children());
                }
            }
        }

        private boolean atStart() {
            return plain.length() == 0;
        }

        private boolean afterBreak() {
            return plain.length() > 0 && plain.charAt(plain.length() - 1) == '\n';
        }

        void emit(List<Node> nodes) throws Refused {
            for (Node n : nodes) {
                if (n instanceof Text t) {
                    literal(adjust(t.value(), atStart(), afterBreak()));
                } else if (n instanceof Variable v) {
                    String value = value(v);
                    if (value != null) {
                        literal(value);
                    } else {
                        emit(v.children());
                    }
                } else if (n instanceof TabStop ts) {
                    emit(ts);
                }
            }
        }

        private void emit(TabStop ts) throws Refused {
            int number = ts.number();
            if (number == 0) {
                if (!ts.children().isEmpty()) {
                    literal(flat(ts.children(), atStart(), afterBreak(), new HashSet<>()));
                }
                if (plainCaret < 0) {
                    plainCaret = plain.length();
                    template.append("${cursor}");
                }
                return;
            }
            String start = defaultText(number);
            String name = "t" + number;
            if (ts.transform() != null) {
                int nth = transformed.merge(number, 1, Integer::sum);
                String target = name + "x" + nth;
                String shown = transform(ts.transform(), start);
                parameter(target, shown, " editable=false");
                lives.add(new Live(name, target, ts.transform()));
                plain.append(shown);
            } else if (masters.add(number)) {
                parameter(name, start, " ordering=" + number);
                plain.append(start);
            } else {
                template.append("${").append(name).append('}');
                plain.append(start);
            }
        }

        private String defaultText(int number) throws Refused {
            String known = defaultText.get(number);
            if (known == null) {
                List<Node> children = defaults.get(number);
                Set<Integer> visiting = new HashSet<>();
                visiting.add(number);
                known = children == null ? "" : flat(children, atStart(), afterBreak(), visiting);
                defaultText.put(number, known);
            }
            return known;
        }

        private void parameter(String name, String start, String hints) {
            template.append("${").append(name);
            if (grammarCarries(start)) {
                template.append(" default=\"").append(start).append('"');
            } else {
                values.put(name, start);
            }
            template.append(hints).append('}');
        }

        /** Literal text: in the template every dollar is doubled, or the engine would read a parameter. */
        private void literal(String text) {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '$') {
                    template.append('$');
                }
                template.append(c);
            }
            plain.append(text);
        }

        /** A variable's value, transformed when it carries a transform; null when it is not set. */
        private String value(Variable v) throws Refused {
            String raw = SnippetVariables.resolve(v.name(), ctx);
            if (v.transform() != null) {
                return transform(v.transform(), raw == null ? "" : raw);
            }
            return raw;
        }

        /** What a placeholder's default reads as: its text, with everything inside it at its own default. */
        private String flat(List<Node> nodes, boolean atStart, boolean afterBreak, Set<Integer> visiting)
                throws Refused {
            StringBuilder b = new StringBuilder();
            for (Node n : nodes) {
                boolean start = atStart && b.length() == 0;
                boolean broke = b.length() == 0 ? afterBreak : b.charAt(b.length() - 1) == '\n';
                if (n instanceof Text t) {
                    b.append(adjust(t.value(), start, broke));
                } else if (n instanceof Variable v) {
                    String value = value(v);
                    b.append(value != null ? value : flat(v.children(), start, broke, visiting));
                } else if (n instanceof TabStop ts) {
                    if (ts.number() == 0) {
                        b.append(flat(ts.children(), start, broke, visiting));
                    } else if (visiting.add(ts.number())) {
                        // a stop inside its own default (${1:a ${1}}) reads as nothing rather than forever
                        List<Node> inner = defaults.get(ts.number());
                        if (inner != null) {
                            b.append(flat(inner, start, broke, visiting));
                        }
                        visiting.remove(ts.number());
                    }
                }
            }
            return b.toString();
        }

        /**
         * VS Code's whitespace rule for one run of a body's text: each
         * line after the first takes the caret line's indentation, and so
         * does the first when the text before it ended a line; a line's
         * leading tabs become the editor's unit. The very start of a
         * snippet is only normalized, since it lands where the caret is.
         */
        private String adjust(String text, boolean atStart, boolean afterBreak) {
            if (text.indexOf('\n') < 0 && text.indexOf('\t') < 0 && !afterBreak) {
                return text;
            }
            StringBuilder out = new StringBuilder(text.length() + 16);
            int from = 0;
            boolean first = true;
            while (true) {
                int nl = text.indexOf('\n', from);
                String line = nl < 0 ? text.substring(from) : text.substring(from, nl);
                if (!first || afterBreak) {
                    out.append(ctx.lineIndent()).append(normalize(line));
                } else if (atStart) {
                    out.append(normalize(line));
                } else {
                    out.append(line);
                }
                if (nl < 0) {
                    return out.toString();
                }
                out.append('\n');
                from = nl + 1;
                first = false;
            }
        }

        /** A line with each of its LEADING tabs as the editor's indent unit; a tab further in is a tab. */
        private String normalize(String line) {
            int i = 0;
            StringBuilder lead = null;
            while (i < line.length() && (line.charAt(i) == '\t' || line.charAt(i) == ' ')) {
                if (line.charAt(i) == '\t') {
                    if (lead == null) {
                        lead = new StringBuilder(line.substring(0, i));
                    }
                    lead.append(ctx.indentUnit());
                } else if (lead != null) {
                    lead.append(' ');
                }
                i++;
            }
            return lead == null ? line : lead + line.substring(i);
        }
    }
}
