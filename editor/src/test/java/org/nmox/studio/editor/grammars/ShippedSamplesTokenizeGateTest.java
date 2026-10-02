package org.nmox.studio.editor.grammars;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilderFactory;
import org.eclipse.tm4e.core.grammar.IGrammar;
import org.eclipse.tm4e.core.grammar.IStateStack;
import org.eclipse.tm4e.core.grammar.IToken;
import org.eclipse.tm4e.core.grammar.ITokenizeLineResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.grammars.GrammarDependenciesLoadGateTest.Registered;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every sample file the product itself hands a learner is tokenized, whole,
 * by the grammar the editor would open it with (3.5.5).
 *
 * <p>3.5.4 found that an Elixir file opened as an empty tab, and that no
 * walk had ever opened one: the toolchains were walked through their run
 * buttons. The learning catalogue ships real code in some seventy kinds of
 * file, and New Learning Space opens it in the editor. This gate takes each
 * of those files, finds its grammar the way the product does (the file's
 * extension to a MIME type, the MIME type to a grammar, both read from the
 * layer this module generates), and runs every line through the real
 * engine. A grammar that throws, or that cannot finish a line in the time
 * the editor allows, fails here with the file and the line.
 *
 * <p>Kinds of file the platform's own lexers or this product's JavaScript
 * lexer open (HTML, CSS, JSON, XML, Java, PHP, JS, TS) have no TextMate
 * grammar bound to their extension and are not this gate's.
 */
class ShippedSamplesTokenizeGateTest {

    private static final Path CATALOGUE = Path.of(
            "../rack/src/main/resources/org/nmox/studio/rack/projectstudio/learn-catalog.json");

    /** Extension to MIME type, and MIME type to grammar scope, as the generated layer registers them. */
    private record Bindings(Map<String, String> mimeOfExtension, Map<String, String> scopeOfMime) {

        String scopeFor(String fileName) {
            int dot = fileName.lastIndexOf('.');
            String mime = dot < 0 ? null : mimeOfExtension.get(fileName.substring(dot + 1));
            return mime == null ? null : scopeOfMime.get(mime);
        }
    }

    private static Bindings bindings() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Document doc = dbf.newDocumentBuilder()
                .parse(Path.of("target/classes/META-INF/generated-layer.xml").toFile());
        Bindings found = new Bindings(new HashMap<>(), new HashMap<>());
        collect(doc.getDocumentElement(), "", found);
        return found;
    }

    private static void collect(Element el, String path, Bindings found) {
        NodeList children = el.getChildNodes();
        String mimeType = null;
        String scope = null;
        boolean injected = false;
        List<String> extensions = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element child)) {
                continue;
            }
            if ("attr".equals(child.getTagName())) {
                String name = child.getAttribute("name");
                if (name.equals("mimeType")) {
                    mimeType = child.getAttribute("stringvalue");
                } else if (name.startsWith("ext.")) {
                    extensions.add(child.getAttribute("stringvalue"));
                } else if (name.equals("textmate-grammar")) {
                    scope = child.getAttribute("stringvalue");
                } else if (name.equals("inject-to")) {
                    injected = true;
                }
            } else {
                collect(child, path + "/" + child.getAttribute("name"), found);
            }
        }
        if (mimeType != null) {
            for (String extension : extensions) {
                found.mimeOfExtension().put(extension, mimeType);
            }
        }
        // a grammar file sits in Editors/<type>/<subtype>/: that folder is the MIME type it colours
        String editors = "/Editors/";
        if (scope != null && !injected && path.startsWith(editors)) {
            String folder = path.substring(editors.length(), path.lastIndexOf('/'));
            if (folder.indexOf('/') > 0 && folder.indexOf('/') == folder.lastIndexOf('/')) {
                found.scopeOfMime().put(folder, scope);
            }
        }
    }

    private record Sample(String space, String path, String content) {
    }

    private static List<Sample> samples() throws Exception {
        Object parsed = new org.json.JSONTokener(Files.readString(CATALOGUE, StandardCharsets.UTF_8)).nextValue();
        JSONArray spaces = parsed instanceof JSONArray a ? a : ((JSONObject) parsed).getJSONArray("spaces");
        List<Sample> all = new ArrayList<>();
        for (int i = 0; i < spaces.length(); i++) {
            JSONObject space = spaces.getJSONObject(i);
            JSONArray files = space.optJSONArray("files");
            for (int f = 0; files != null && f < files.length(); f++) {
                JSONObject file = files.getJSONObject(f);
                all.add(new Sample(space.getString("slug"), file.getString("path"), file.getString("content")));
            }
        }
        return all;
    }

    /** What went wrong tokenizing the text with the grammar, or null. */
    private static String tokenize(IGrammar grammar, String text) {
        IStateStack state = null;
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].endsWith("\r") ? lines[i].substring(0, lines[i].length() - 1) : lines[i];
            ITokenizeLineResult<IToken[]> result;
            try {
                result = grammar.tokenizeLine(line, state, Duration.ofSeconds(10));
            } catch (RuntimeException | StackOverflowError e) {
                return "line " + (i + 1) + " threw " + e;
            }
            if (result.isStoppedEarly()) {
                return "line " + (i + 1) + " was not finished in ten seconds: " + line;
            }
            state = result.getRuleStack();
        }
        return null;
    }

    @Test
    @DisplayName("every sample file in the learning catalogue is tokenized by the grammar bound to its extension")
    void everyShippedSampleTokenizes() throws Exception {
        Bindings bindings = bindings();
        Registered set = GrammarDependenciesLoadGateTest.registered();
        Map<String, IGrammar> loaded = new HashMap<>();
        Map<String, Integer> tokenizedBy = new TreeMap<>();
        TreeSet<String> unbound = new TreeSet<>();
        List<String> broken = new ArrayList<>();
        List<Sample> all = samples();
        for (Sample sample : all) {
            String name = sample.path().substring(sample.path().lastIndexOf('/') + 1);
            String scope = bindings.scopeFor(name);
            if (scope == null) {
                unbound.add(name.contains(".") ? name.substring(name.lastIndexOf('.')) : name);
                continue;
            }
            IGrammar grammar = loaded.computeIfAbsent(scope, GrammarDependenciesLoadGateTest.loader(set));
            String wrong = tokenize(grammar, sample.content());
            if (wrong != null) {
                broken.add(sample.space() + "/" + sample.path() + " (" + scope + "): " + wrong);
            }
            tokenizedBy.merge(scope, 1, Integer::sum);
        }
        assertThat(all.size()).as("sample files in the catalogue").isGreaterThan(150);
        assertThat(tokenizedBy.size())
                .as("grammars exercised by a real file; the rest of the catalogue's extensions are " + unbound)
                .isGreaterThan(40);
        assertThat(tokenizedBy).as("the three 3.5.4 found broken are among them")
                .containsKeys("source.elixir", "source.hx", "source.svelte");
        assertThat(broken).as("a learner's own first file, not coloured or not opened").isEmpty();
    }

    @Test
    @DisplayName("the gate reports the file and the line where a grammar throws")
    void theGateBites() throws Exception {
        Registered holes = GrammarDependenciesLoadGateTest.fixtures(Map.of("fixture.throws", "hole-throws.json"));
        IGrammar throwing = GrammarDependenciesLoadGateTest.loader(holes).apply("fixture.throws");
        assertThat(tokenize(throwing, "one\ntwo |> three")).startsWith("line 2 threw").contains("look-behind");
        assertThat(tokenize(throwing, "one\ntwo")).as("the rule that cannot compile was never needed").isNull();
    }
}
