package org.nmox.studio.editor.grammars;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilderFactory;
import org.eclipse.tm4e.core.grammar.IGrammar;
import org.eclipse.tm4e.core.grammar.IStateStack;
import org.eclipse.tm4e.core.grammar.IToken;
import org.eclipse.tm4e.core.grammar.ITokenizeLineResult;
import org.eclipse.tm4e.core.registry.IGrammarSource;
import org.eclipse.tm4e.core.registry.IRegistryOptions;
import org.eclipse.tm4e.core.registry.Registry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every grammar a registered grammar includes is loaded by the engine the
 * editor runs (3.5.4).
 *
 * <p>Before TM4E tokenizes with a grammar it walks that grammar's rules and
 * loads each grammar they include. The walk has two holes. It does not look
 * inside {@code captures}, so a grammar included only from a capture is never
 * loaded. And it keeps the rules it has visited in a {@code HashSet}, where a
 * rule is a {@code HashMap}: a rule that <em>reads the same</em> as one seen
 * earlier in another grammar is taken for visited. {@code {"include":
 * "#comments"}} is such a rule, and what it leads to is different in every
 * grammar that has one. Either way the include then resolves to nothing: the
 * engine logs {@code CANNOT find grammar for scopeName} and drops the rule.
 *
 * <p>Measured over the registered set: JavaScript between backticks in a
 * CoffeeScript file and the shell command of an {@code exec} line in a git
 * rebase todo were never highlighted, a Nim file's Markdown doc comments lost
 * every fenced language, and Markdown itself logged a warning for Groovy's
 * javadoc in every session that opened a Markdown file.
 *
 * <p>{@code scripts/name-grammar-dependencies.py} gives a grammar a rule the
 * walk cannot miss: one that never matches and names the grammars to load.
 * This gate takes its population from the layer this module generates (and
 * the platform's Markdown grammar from its own jar), loads each registered
 * grammar through the real engine the way the platform does, and compares
 * what the engine asked for with what the grammar reaches.
 */
class GrammarDependenciesLoadGateTest {

    private static final String PLATFORM_MARKDOWN = "/org/netbeans/modules/markdown/markdown.tmLanguage.json";

    /** What the platform's registry is built from: scope to resource, and scope to the scopes injected into it. */
    record Registered(Map<String, String> resources, Map<String, List<String>> injections) {

        InputStream open(String scope) {
            return GrammarDependenciesLoadGateTest.class.getResourceAsStream(resources.get(scope));
        }
    }

    static Registered registered() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Document doc = dbf.newDocumentBuilder()
                .parse(Path.of("target/classes/META-INF/generated-layer.xml").toFile());
        Map<String, String> resources = new TreeMap<>();
        Map<String, List<String>> injections = new HashMap<>();
        collect(doc.getDocumentElement(), resources, injections);
        // the one grammar in the assembled product that another module registers
        resources.put("text.html.markdown", PLATFORM_MARKDOWN);
        return new Registered(resources, injections);
    }

    private static void collect(Element el, Map<String, String> resources, Map<String, List<String>> injections) {
        NodeList children = el.getChildNodes();
        String scope = null;
        String injectTo = null;
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element child)) {
                continue;
            }
            if ("attr".equals(child.getTagName())) {
                if ("textmate-grammar".equals(child.getAttribute("name"))) {
                    scope = child.getAttribute("stringvalue");
                } else if ("inject-to".equals(child.getAttribute("name"))) {
                    injectTo = child.getAttribute("stringvalue");
                }
            } else {
                collect(child, resources, injections);
            }
        }
        if (scope != null) {
            String url = el.getAttribute("url");
            assertThat(url).as("the registration of " + scope).startsWith("nbresloc:/");
            resources.put(scope, url.substring("nbresloc:".length()));
            if (injectTo != null) {
                for (String host : injectTo.split(",")) {
                    injections.computeIfAbsent(host, h -> new ArrayList<>()).add(scope);
                }
            }
        }
    }

    private static JSONObject read(Registered set, String scope) throws IOException {
        try (InputStream in = set.open(scope)) {
            assertThat(in).as("the grammar registered for " + scope).isNotNull();
            return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /**
     * The registered scopes a grammar reaches, the way the engine compiles
     * rules: from a grammar's patterns and injections, through includes into
     * its repository, into nested patterns and into captures.
     */
    static Set<String> reaches(String top, Registered set, Map<String, JSONObject> parsed) throws IOException {
        Set<String> reached = new TreeSet<>();
        Set<String> seenReferences = new HashSet<>();
        Map<Object, Boolean> seenRules = new IdentityHashMap<>();
        Deque<String[]> queue = new ArrayDeque<>();
        queue.add(new String[] {top, null});
        while (!queue.isEmpty()) {
            String[] reference = queue.poll();
            String scope = reference[0];
            if (!seenReferences.add(scope + "#" + reference[1]) || !set.resources().containsKey(scope)) {
                continue;
            }
            reached.add(scope);
            if (reference[1] == null) {
                for (String injected : set.injections().getOrDefault(scope, List.of())) {
                    queue.add(new String[] {injected, null});
                }
            }
            JSONObject grammar = parsed.get(scope);
            if (grammar == null) {
                grammar = read(set, scope);
                parsed.put(scope, grammar);
            }
            JSONObject repository = grammar.optJSONObject("repository");
            Deque<Object> rules = new ArrayDeque<>();
            if (reference[1] == null) {
                rules.add(grammar.opt("patterns") == null ? new JSONArray() : grammar.get("patterns"));
                if (grammar.optJSONObject("injections") != null) {
                    rules.add(new JSONArray(grammar.getJSONObject("injections").toMap().values()));
                }
            } else if (repository != null && repository.opt(reference[1]) != null) {
                rules.add(repository.get(reference[1]));
            }
            while (!rules.isEmpty()) {
                Object node = rules.poll();
                if (node instanceof JSONArray list) {
                    for (int i = 0; i < list.length(); i++) {
                        rules.add(list.get(i));
                    }
                    continue;
                }
                if (!(node instanceof JSONObject rule) || seenRules.put(rule, Boolean.TRUE) != null) {
                    continue;
                }
                if (rule.opt("include") instanceof String include) {
                    if (include.equals("$self") || include.equals("$base")) {
                        rules.add(grammar.opt("patterns") == null ? new JSONArray() : grammar.get("patterns"));
                    } else if (include.startsWith("#")) {
                        if (repository != null && repository.opt(include.substring(1)) != null) {
                            rules.add(repository.get(include.substring(1)));
                        }
                    } else {
                        int hash = include.indexOf('#');
                        queue.add(hash < 0 ? new String[] {include, null}
                                : new String[] {include.substring(0, hash), include.substring(hash + 1)});
                    }
                }
                if (rule.opt("patterns") != null) {
                    rules.add(rule.get("patterns"));
                }
                for (String captures : List.of("captures", "beginCaptures", "endCaptures", "whileCaptures")) {
                    if (rule.optJSONObject(captures) != null) {
                        JSONObject byIndex = rule.getJSONObject(captures);
                        for (String index : byIndex.keySet()) {
                            rules.add(byIndex.get(index));
                        }
                    }
                }
            }
        }
        return reached;
    }

    /** A registry over the registered set, the shape of the platform's own; {@code asked} hears each scope it is asked for. */
    private static Registry registry(Registered set, Set<String> asked) {
        return new Registry(new IRegistryOptions() {
            @Override
            public IGrammarSource getGrammarSource(String scopeName) {
                asked.add(scopeName);
                String resource = set.resources().get(scopeName);
                return resource == null ? null
                        : IGrammarSource.fromResource(GrammarDependenciesLoadGateTest.class, resource);
            }

            @Override
            public Collection<String> getInjections(String scopeName) {
                return set.injections().get(scopeName);
            }
        });
    }

    /** The scopes the engine asks its registry for when {@code top} is loaded the way the platform loads it. */
    static Set<String> loads(String top, Registered set) {
        Set<String> asked = new HashSet<>();
        assertThat(registry(set, asked).loadGrammar(top)).as("TM4E loads " + top).isNotNull();
        return asked;
    }

    /** Each line's tokens as "text → scopes", the state carried from line to line. */
    static List<String> tokenize(Registered set, String top, String... lines) {
        IGrammar grammar = registry(set, new HashSet<>()).loadGrammar(top);
        List<String> out = new ArrayList<>();
        IStateStack state = null;
        for (String line : lines) {
            ITokenizeLineResult<IToken[]> result = grammar.tokenizeLine(line, state, Duration.ofSeconds(20));
            for (IToken token : result.getTokens()) {
                int end = Math.min(token.getEndIndex(), line.length());
                if (token.getStartIndex() < end) {
                    out.add(line.substring(token.getStartIndex(), end) + " → " + token.getScopes());
                }
            }
            state = result.getRuleStack();
        }
        return out;
    }

    /**
     * The commands that would close a miss. The rule goes into the shipped
     * grammar that was loaded and includes the missed one; where only a
     * grammar this module does not ship includes it (the platform's
     * Markdown), into the grammar that was being loaded.
     */
    private static List<String> remedy(String top, Set<String> never, Registered set, Map<String, JSONObject> parsed)
            throws IOException {
        Map<String, Set<String>> byFile = new TreeMap<>();
        for (String missedScope : never) {
            List<String> includers = new ArrayList<>();
            for (String scope : reaches(top, set, parsed)) {
                boolean ours = set.resources().get(scope).startsWith("/org/nmox/");
                String text = parsed.get(scope).toString();
                if (ours && !never.contains(scope) && (text.contains("\"include\":\"" + missedScope + "\"")
                        || text.contains("\"include\":\"" + missedScope + "#"))) {
                    includers.add(scope);
                }
            }
            for (String includer : includers.isEmpty() ? List.of(top) : includers) {
                byFile.computeIfAbsent("editor/src/main/resources" + set.resources().get(includer), f -> new TreeSet<>())
                        .add(missedScope);
            }
        }
        List<String> commands = new ArrayList<>();
        byFile.forEach((file, scopes) -> commands.add(
                "scripts/name-grammar-dependencies.py " + file + " " + String.join(" ", scopes)));
        return commands;
    }

    @Test
    @DisplayName("the engine loads every registered grammar a registered grammar reaches")
    void everyReachedGrammarIsLoaded() throws Exception {
        Registered set = registered();
        assertThat(set.resources().size()).as("grammars registered in the generated layer").isGreaterThan(120);
        assertThat(set.injections()).as("the Angular template grammars are injected into HTML")
                .containsKey("text.html.basic");

        Map<String, JSONObject> parsed = new HashMap<>();
        List<String> missed = new ArrayList<>();
        int reachedSomething = 0;
        for (String top : set.resources().keySet()) {
            Set<String> never = new TreeSet<>(reaches(top, set, parsed));
            if (never.size() > 1) {
                reachedSomething++;
            }
            never.removeAll(loads(top, set));
            if (!never.isEmpty()) {
                missed.add(top + " never loads " + never + " — run: " + String.join("; ", remedy(top, never, set, parsed)));
            }
        }
        assertThat(reachedSomething).as("grammars that include another registered grammar").isGreaterThan(20);
        assertThat(missed)
                .as("an include of a grammar the engine did not load resolves to nothing: "
                        + "the rule is dropped and a WARNING logged")
                .isEmpty();
    }

    @Test
    @DisplayName("the walk this gate compares against sees an include inside a capture")
    void theComparisonReachesIntoCaptures() throws Exception {
        Registered set = registered();
        assertThat(reaches("text.git-rebase", set, new HashMap<>()))
                .as("exec's command is a capture that includes the shell grammar")
                .contains("source.shell");
        assertThat(reaches("source.coffee", set, new HashMap<>())).contains("source.js");
        assertThat(Collections.max(List.of(reaches("source.nim", set, new HashMap<>()).size(),
                reaches("text.html.markdown", set, new HashMap<>()).size())))
                .as("Markdown's fenced blocks reach most of the set").isGreaterThan(50);
    }

    @Test
    @DisplayName("an exec line of a rebase todo is shell, and backticks in CoffeeScript are JavaScript")
    void whatWasNeverLoadedIsHighlighted() throws Exception {
        Registered set = registered();

        List<String> todo = tokenize(set, "text.git-rebase", "exec echo \"done\"");
        assertThat(todo).as("the command after exec, read by the shell grammar")
                .anyMatch(token -> token.startsWith("\"done\" → ") || token.startsWith("done → ")
                        ? token.contains("string.quoted.double.shell") : false);

        List<String> coffee = tokenize(set, "source.coffee", "a = `var y = 1`");
        assertThat(coffee).as("JavaScript between backticks, read by the JavaScript grammar")
                .anyMatch(token -> token.startsWith("var → ") && token.contains("storage.type"));
    }

    @Test
    @DisplayName("no registered grammar is reported missing when any registered grammar compiles its rules")
    void nothingRegisteredIsReportedMissing() throws Exception {
        Registered set = registered();
        // the engine's own word on it: RuleFactory logs this line for an
        // include it could not resolve, at the moment it drops the rule
        List<String> reported = Collections.synchronizedList(new ArrayList<>());
        Logger factory = Logger.getLogger("org.eclipse.tm4e.core.internal.rule.RuleFactory");
        Handler listener = new Handler() {
            @Override
            public void publish(LogRecord record) {
                String message = String.valueOf(record.getMessage());
                if (record.getParameters() != null) {
                    message = java.text.MessageFormat.format(message, record.getParameters());
                }
                reported.add(message);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        factory.addHandler(listener);
        boolean parent = factory.getUseParentHandlers();
        factory.setUseParentHandlers(false);
        List<String> missing = new ArrayList<>();
        try {
            for (String top : set.resources().keySet()) {
                reported.clear();
                // one line is enough: the first tokenized line compiles every rule the grammar reaches
                tokenize(set, top, "x");
                for (String line : reported) {
                    int at = line.indexOf("CANNOT find grammar for scopeName [");
                    if (at < 0) {
                        continue;
                    }
                    String scope = line.substring(at + "CANNOT find grammar for scopeName [".length());
                    scope = scope.substring(0, scope.indexOf(']'));
                    if (set.resources().containsKey(scope) && !missing.contains(top + " → " + scope)) {
                        missing.add(top + " → " + scope);
                    }
                }
            }
        } finally {
            factory.removeHandler(listener);
            factory.setUseParentHandlers(parent);
        }
        assertThat(missing).as("registered grammars the engine could not find while compiling").isEmpty();
    }
}
