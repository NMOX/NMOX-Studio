package org.nmox.studio.tools.npm.search;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * npm scripts in Quick Search (v3.1.0, dx-plan row 9): "dev" in ⌘I lists
 * {@code Run script: dev — vite}, and Enter hands exactly that script and
 * directory to the trust-gated NPM Service lane.
 */
class NpmScriptSearchProviderTest {

    private static final String PACKAGE_JSON = """
            {
              "name": "shop",
              "dependencies": { "dev": "1.0.0" },
              "scripts": {
                "dev": "vite",
                "build": "vite build --mode dev",
                "test": "vitest run",
                "test:unit": "vitest --dir unit",
                "lint": "eslint ."
              }
            }
            """;

    @TempDir
    Path project;

    private final BiConsumer<File, String> realRunner = NpmScriptSearchProvider.runner;
    private final java.util.function.Predicate<File> realTrust = NpmScriptSearchProvider.trustCheck;
    private final java.util.function.Consumer<String> realStatus = NpmScriptSearchProvider.statusSink;
    private final List<String> events = java.util.Collections.synchronizedList(new ArrayList<>());

    @org.junit.jupiter.api.BeforeEach
    void quietSeams() {
        NpmScriptSearchProvider.statusSink = s -> events.add("said: " + s);
        NpmScriptSearchProvider.trustCheck = d -> {
            events.add("asked: " + d.getName());
            return true;
        };
    }

    @AfterEach
    void restoreRunner() {
        NpmScriptSearchProvider.runner = realRunner;
        NpmScriptSearchProvider.trustCheck = realTrust;
        NpmScriptSearchProvider.statusSink = realStatus;
    }

    private void writePackageJson(String json) throws Exception {
        Files.writeString(project.resolve("package.json"), json);
    }

    private static List<String> names(List<NpmScriptSearchProvider.Item> items) {
        return items.stream().map(NpmScriptSearchProvider.Item::name).toList();
    }

    @Test
    @DisplayName("\"dev\" lists the dev script first, labelled with its command, from the project's own package.json")
    void devFindsTheDevScript() throws Exception {
        writePackageJson(PACKAGE_JSON);
        List<NpmScriptSearchProvider.Item> items = NpmScriptSearchProvider.itemsFor("dev", project.toFile());

        assertThat(names(items)).as("a name hit outranks a command-only hit").containsExactly("dev", "build");
        assertThat(items.get(0).label()).isEqualTo("Run script: dev — vite");
        assertThat(items.get(0).command()).isEqualTo("vite");
        assertThat(items.get(0).dir()).isEqualTo(project.toFile());
    }

    @Test
    @DisplayName("the house matcher decides: word boundaries, no junk, and a dependency is not a script")
    void matchingRidesSearchTerms() throws Exception {
        writePackageJson(PACKAGE_JSON);
        File dir = project.toFile();

        assertThat(names(NpmScriptSearchProvider.itemsFor("test", dir)))
                .as("both test scripts, name order when the matcher ties").containsExactly("test", "test:unit");
        assertThat(names(NpmScriptSearchProvider.itemsFor("unit test", dir)))
                .as("terms in any order").containsExactly("test:unit");
        assertThat(names(NpmScriptSearchProvider.itemsFor("eslint", dir))).containsExactly("lint");
        assertThat(NpmScriptSearchProvider.itemsFor("postgres", dir)).isEmpty();
        assertThat(names(NpmScriptSearchProvider.itemsFor("it", dir)))
                .as("a two-letter term never matches mid-word (vitest, lint)").isEmpty();
        assertThat(NpmScriptSearchProvider.itemsFor("  ", dir)).isEmpty();
    }

    @Test
    @DisplayName("the controlled vocabulary lists every script, and the project's own manager joins it")
    void vocabularyAndManager() throws Exception {
        writePackageJson(PACKAGE_JSON);
        File dir = project.toFile();

        assertThat(names(NpmScriptSearchProvider.itemsFor("npm", dir)))
                .containsExactly("build", "dev", "lint", "test", "test:unit");
        assertThat(NpmScriptSearchProvider.itemsFor("pnpm", dir))
                .as("an npm project does not answer to pnpm").isEmpty();

        Files.writeString(project.resolve("pnpm-lock.yaml"), "lockfileVersion: '9.0'\n");
        assertThat(names(NpmScriptSearchProvider.itemsFor("pnpm dev", dir))).containsExactly("dev", "build");
    }

    @Test
    @DisplayName("no project, no package.json, no scripts, a broken package.json: the category stays empty")
    void silenceIsCorrect() throws Exception {
        assertThat(NpmScriptSearchProvider.itemsFor("dev", null)).isEmpty();
        assertThat(NpmScriptSearchProvider.itemsFor("dev", project.toFile())).as("no package.json").isEmpty();

        writePackageJson("{ \"name\": \"shop\" }");
        assertThat(NpmScriptSearchProvider.itemsFor("npm", project.toFile())).as("no scripts block").isEmpty();

        Path broken = Files.createDirectory(project.resolve("broken"));
        Files.writeString(broken.resolve("package.json"), "{ \"scripts\": { \"dev\": ");
        assertThat(NpmScriptSearchProvider.itemsFor("dev", broken.toFile())).as("malformed").isEmpty();
    }

    @Test
    @DisplayName("a monorepo root without package.json reaches the Node lane's directory, the one NPM Explorer shows")
    void nodeLaneDirectory() throws Exception {
        Files.writeString(project.resolve("Cargo.toml"), "[package]\nname = \"api\"\n");
        Path web = Files.createDirectory(project.resolve("web"));
        Files.writeString(web.resolve("package.json"), PACKAGE_JSON);

        List<NpmScriptSearchProvider.Item> items = NpmScriptSearchProvider.itemsFor("dev", project.toFile());
        assertThat(names(items)).startsWith("dev");
        assertThat(items.get(0).dir()).as("the script runs where its package.json is").isEqualTo(web.toFile());
    }

    @Test
    @DisplayName("labels: long commands clip by code points, markup is escaped, a blank command reads bare")
    void labels() {
        String emojiAtCut = "x".repeat(NpmScriptSearchProvider.MAX_COMMAND - 2) + "🚀🚀tail";
        String clipped = NpmScriptSearchProvider.clip(emojiAtCut, NpmScriptSearchProvider.MAX_COMMAND);
        assertThat(clipped.codePointCount(0, clipped.length())).isEqualTo(NpmScriptSearchProvider.MAX_COMMAND);
        assertThat(clipped).endsWith("🚀…");
        assertThat(Character.isHighSurrogate(clipped.charAt(clipped.length() - 2)))
                .as("never a stranded half of a surrogate pair").isFalse();
        assertThat(NpmScriptSearchProvider.clip("vite", 48)).isEqualTo("vite");

        assertThat(NpmScriptSearchProvider.label("dev", "echo <b>hi</b> && vite"))
                .isEqualTo("Run script: dev — echo &lt;b&gt;hi&lt;/b&gt; &amp;&amp; vite");
        assertThat(NpmScriptSearchProvider.label("noop", "")).isEqualTo("Run script: noop");
        assertThat(NpmScriptSearchProvider.oneLine("  vite\n  --port\t3000\r\n")).isEqualTo("vite --port 3000");

        List<NpmScriptSearchProvider.Item> items = NpmScriptSearchProvider.items("dev",
                Map.of("dev", "vite\n\n--host"), "npm", new File("."));
        assertThat(items.get(0).label()).isEqualTo("Run script: dev — vite --host");
    }

    @Test
    @DisplayName("Enter hands the listed script and its directory to the lane, off the calling thread")
    void enterRunsThroughTheLane() {
        AtomicReference<File> dir = new AtomicReference<>();
        AtomicReference<String> script = new AtomicReference<>();
        AtomicReference<String> thread = new AtomicReference<>();
        NpmScriptSearchProvider.runner = (d, s) -> {
            dir.set(d);
            script.set(s);
            thread.set(Thread.currentThread().getName());
        };

        NpmScriptSearchProvider.run(project.toFile(), "dev").waitFinished();

        assertThat(dir.get()).isEqualTo(project.toFile());
        assertThat(script.get()).isEqualTo("dev");
        assertThat(thread.get()).as("never the caller's (EDT) thread")
                .isNotEqualTo(Thread.currentThread().getName())
                .contains("npm-script");
    }

    @Test
    @DisplayName("\"Running …\" is said only after the trust question is answered yes; Keep Safe says nothing")
    void runningIsSaidOnlyAfterTrust() {
        NpmScriptSearchProvider.runner = (d, s) -> events.add("ran: " + s);
        NpmScriptSearchProvider.trustCheck = d -> {
            events.add("asked");
            return false;
        };
        NpmScriptSearchProvider.run(project.toFile(), "dev").waitFinished();
        assertThat(events).as("the review's finding (v3.1.0): the status claimed a run Keep Safe had refused")
                .containsExactly("asked");

        events.clear();
        NpmScriptSearchProvider.trustCheck = d -> {
            events.add("asked");
            return true;
        };
        NpmScriptSearchProvider.run(project.toFile(), "dev").waitFinished();
        assertThat(events).containsExactly("asked", "said: Running \"dev\"…", "ran: dev");
    }

    @Test
    @DisplayName("the default run is NpmService.runScript — the trust-gated lane — and this class spawns nothing itself")
    void noNewSpawnSite() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/tools/npm/search/NpmScriptSearchProvider.java"));
        String code = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
        assertThat(code).contains("runner = NpmScriptSearchProvider::runOnLane")
                .contains("npm.runScript(dir, script, npm.detectPackageManager(dir))");
        for (String spawn : new String[] {"CommandExecutor", "ProcessBuilder", "ProcessSupport", "Runtime.getRuntime"}) {
            assertThat(code).as("no spawn of its own: " + spawn).doesNotContain(spawn);
        }
        assertThat(code).as("labels go through the house matcher, never a raw contains()")
                .contains("SearchTerms.matches(").doesNotContain(".contains(");
    }

    @Test
    @DisplayName("registered under QuickSearch/NpmScripts at a position no other NMOX category holds, with its name translated")
    void registered() throws Exception {
        Element folder = quickSearchFolders(Path.of("src/main/resources/org/nmox/studio/tools/layer.xml"))
                .stream().filter(f -> f.getAttribute("name").equals("NpmScripts")).findFirst().orElseThrow();
        assertThat(attr(folder, "position")).isEqualTo("277");
        assertThat(attr(folder, "SystemFileSystem.localizingBundle")).isEqualTo("org.nmox.studio.tools.npm.search.Bundle");
        String instance = ((Element) folder.getElementsByTagName("file").item(0)).getAttribute("name");
        String className = instance.substring(0, instance.length() - ".instance".length()).replace('-', '.');
        Class<?> provider = Class.forName(className);
        assertThat(SearchProvider.class.isAssignableFrom(provider)).isTrue();
        assertThat(provider.getConstructor().newInstance()).isNotNull();

        assertThat(Files.readString(Path.of("pom.xml")))
                .contains("<OpenIDE-Module-Layer>org/nmox/studio/tools/layer.xml</OpenIDE-Module-Layer>");

        // every other NMOX Quick Search category, read from the sibling modules' layers
        List<String> taken = new ArrayList<>();
        try (Stream<Path> modules = Files.list(Path.of(".."))) {
            for (Path module : modules.filter(m -> !m.getFileName().toString().equals("tools")).toList()) {
                Path resources = module.resolve("src/main/resources");
                if (!Files.isDirectory(resources)) {
                    continue;
                }
                List<Path> layers;
                try (Stream<Path> walk = Files.walk(resources)) {
                    layers = walk.filter(p -> p.getFileName().toString().equals("layer.xml")).toList();
                }
                for (Path layer : layers) {
                    for (Element other : quickSearchFolders(layer)) {
                        String pos = attr(other, "position");
                        if (pos != null) {
                            taken.add(pos);
                        }
                    }
                }
            }
        }
        assertThat(taken).as("sanity: the siblings were read").contains("275", "276", "310");
        assertThat(taken).doesNotContain("277");

        for (String lang : new String[] {"", "_es", "_fr", "_de", "_ru", "_hi", "_uk", "_pl", "_pt",
                "_id", "_tl", "_vi", "_zh", "_he", "_ar"}) {
            java.util.Properties p = new java.util.Properties();
            try (var in = Files.newBufferedReader(Path.of(
                    "src/main/resources/org/nmox/studio/tools/npm/search/Bundle" + lang + ".properties"))) {
                p.load(in);
            }
            assertThat(p.stringPropertyNames()).as("Bundle" + lang).containsExactlyInAnyOrder(
                    "QuickSearch/NpmScripts", "NpmScriptSearchProvider_run", "NpmScriptSearchProvider_runBare");
            assertThat(p.getProperty("QuickSearch/NpmScripts")).as("the tool's name is not translated").contains("npm");
        }
    }

    private static List<Element> quickSearchFolders(Path layer) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setValidating(false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        NodeList roots = f.newDocumentBuilder().parse(layer.toFile()).getDocumentElement().getChildNodes();
        List<Element> out = new ArrayList<>();
        for (int i = 0; i < roots.getLength(); i++) {
            if (roots.item(i) instanceof Element e && e.getTagName().equals("folder")
                    && e.getAttribute("name").equals("QuickSearch")) {
                NodeList kids = e.getChildNodes();
                for (int k = 0; k < kids.getLength(); k++) {
                    if (kids.item(k) instanceof Element c && c.getTagName().equals("folder")) {
                        out.add(c);
                    }
                }
            }
        }
        return out;
    }

    private static String attr(Element folder, String name) {
        NodeList attrs = folder.getChildNodes();
        for (int i = 0; i < attrs.getLength(); i++) {
            if (attrs.item(i) instanceof Element a && a.getTagName().equals("attr")
                    && a.getAttribute("name").equals(name)) {
                return a.hasAttribute("intvalue") ? a.getAttribute("intvalue") : a.getAttribute("stringvalue");
            }
        }
        return null;
    }
}
