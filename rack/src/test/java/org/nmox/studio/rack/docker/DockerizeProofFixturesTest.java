package org.nmox.studio.rack.docker;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.devices.ProjectInspector;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;
import org.nmox.studio.rack.projectstudio.ProjectTemplates;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Writes every container recipe the product generates as a project that can
 * be built: the generation half of {@code scripts/dockerize-proof.sh}.
 *
 * <p>"A Dockerfile that was never built is a claim, not a product" (v1.353.0).
 * That release built every generated Dockerfile by hand against a local
 * daemon, once; the base images have moved since and were only checked to
 * exist. This test turns the product's own generator into a directory of
 * small real projects, one per recipe, each holding exactly the files
 * Dockerize would write for it, and a runner with a daemon builds and runs
 * them (the Dockerize proof workflow).
 *
 * <pre>
 * mvn -B -pl rack -am test -Dtest=DockerizeProofFixturesTest \
 *     -Dnmox.dockerize.out=/some/empty/dir -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * <p>Without the property it writes into a temporary directory, so the laws
 * below hold on every build and not only when somebody asks for the proof.
 *
 * <p><b>The population is derived.</b> A kind has a recipe when the generator
 * answers it with something other than its "no toolchain detected" file; a
 * kind whose answer changes with {@code nodeBuildsStatic} has two. Every such
 * shape needs a fixture here or this test fails by name, so a recipe added to
 * the generator cannot ship unbuilt. Kinds with no recipe are written to
 * {@code no-recipe.txt} and all share one proof, {@code generic}.
 *
 * <p><b>Each fixture is asked the product's own questions.</b> The Docker
 * panel decides with {@code detectKind} and {@code buildsStatic} and names
 * the image after the folder; a fixture the product would not recognise as
 * its kind proves a recipe nobody would be handed. The files are written the
 * way the panel writes them, through {@link DockerRecipes#resolveInside}.
 *
 * <p>The fixtures have no dependencies wherever the recipe allows it, so a
 * red build names the Dockerfile and not the network. Two are not what they
 * look like, and say so in their own files: the static site's {@code vite}
 * is a local stub package (the name is what {@code buildsStatic} reads) with
 * a build script that copies one page into {@code dist/}; and the Python
 * recipe starts {@code uvicorn}, so that one package is installed from PyPI.
 */
class DockerizeProofFixturesTest {

    /** Where the proof wants its projects; unset on an ordinary build. */
    static final String OUT_PROPERTY = "nmox.dockerize.out";

    /** How a recipe is proven: its own image, the compose stack it ships with, or only that it stays up. */
    enum Mode {
        IMAGE, COMPOSE, IDLE
    }

    /**
     * One buildable project. {@code deep} is a second path that must answer
     * 200: a client route for the single-page app, a front-controller route
     * for PHP.
     */
    record Fixture(Mode mode, String deep, Map<String, String> files) {
    }

    /** One answer of the generator: a kind, and whether it was told the project builds a static bundle. */
    record Shape(ProjectKind kind, boolean statics) {
        String id() {
            return kind.name().toLowerCase(Locale.ROOT) + (statics ? "-static" : "");
        }
    }

    private static final String GENERIC_ID = "generic";
    private static final String LEMP_ID = "lemp-template";

    @Test
    @DisplayName("every recipe the generator has is written as a project the product would recognise, with exactly the files Dockerize writes")
    void writesEveryRecipeAsAProject(@TempDir Path tmp) throws IOException {
        Path out = outputDir(tmp);
        Map<String, Fixture> fixtures = fixtures();
        List<Shape> shapes = recipeShapes();
        assertThat(shapes).as("the derived recipes (a derivation that finds none proves nothing)")
                .contains(new Shape(ProjectKind.NODE, false), new Shape(ProjectKind.NODE, true),
                        new Shape(ProjectKind.GO, false));

        List<String> unbuilt = new ArrayList<>();
        List<String> manifest = new ArrayList<>();
        for (Shape shape : shapes) {
            Fixture fixture = fixtures.get(shape.id());
            if (fixture == null) {
                unbuilt.add(shape.id());
                continue;
            }
            Path dir = writeFixture(out, shape.id(), fixture);
            assertThat(ProjectInspector.detectKind(dir.toFile()))
                    .as("the product must recognise the %s fixture as its kind, or it would be handed another recipe", shape.id())
                    .isEqualTo(shape.kind());
            assertThat(DockerizeGenerator.buildsStatic(dir.toFile()))
                    .as("%s: what buildsStatic() says of the fixture", shape.id()).isEqualTo(shape.statics());
            Map<String, String> written = writeDockerize(dir, shape.kind(), shape.statics());
            int port = DockerizeGenerator.defaultPort(shape.kind(), shape.statics());
            boolean sidecar = written.get("compose.yaml").contains("\n    image:");
            assertThat(fixture.mode())
                    .as("%s: a recipe whose compose file brings a service it does not build is proven as that stack", shape.id())
                    .isEqualTo(sidecar ? Mode.COMPOSE : Mode.IMAGE);
            if (!sidecar) {
                assertThat(written.get("Dockerfile"))
                        .as("%s: the port the panel publishes is the port the image says it listens on", shape.id())
                        .contains("EXPOSE " + port + "\n");
            }
            manifest.add(line(shape.id(), dir, fixture.mode(), String.valueOf(port), fixture.deep()));
        }
        assertThat(unbuilt).as("recipes with no fixture in %s: a recipe nobody builds is a claim (v1.353.0)",
                getClass().getSimpleName()).isEmpty();
        assertThat(fixtures.keySet()).as("a fixture for a recipe the generator no longer has")
                .allSatisfy(id -> assertThat(id.equals(GENERIC_ID)
                        || shapes.stream().anyMatch(s -> s.id().equals(id))).as(id).isTrue());

        // every kind without a recipe is handed the same files, so one proof stands for all of them
        List<String> none = kindsWithoutRecipe();
        Path generic = writeFixture(out, GENERIC_ID, fixtures.get(GENERIC_ID));
        assertThat(ProjectInspector.detectKind(generic.toFile())).isEqualTo(ProjectKind.NONE);
        writeDockerize(generic, ProjectKind.NONE, false);
        manifest.add(line(GENERIC_ID, generic, Mode.IDLE, "-", null));

        // the project template that ships a stack of its own, written by the template itself
        Path lemp = out.resolve("proof-" + LEMP_ID);
        ProjectTemplates.PHP_WEB.generate(lemp.toFile(), "proof-lemp");
        String stack = Files.readString(lemp.resolve("docker-compose.yml"), StandardCharsets.UTF_8);
        Matcher published = Pattern.compile("- \"(\\d+):80\"").matcher(stack);
        assertThat(published.find()).as("the template's compose file publishes its web server").isTrue();
        assertThat(lemp.resolve("Dockerfile")).as("the template ships a stack of images, and builds none").doesNotExist();
        manifest.add(line(LEMP_ID, lemp, Mode.COMPOSE, published.group(1), "/api/health"));

        Files.write(out.resolve("proof.list"), manifest, StandardCharsets.UTF_8);
        Files.write(out.resolve("no-recipe.txt"), none, StandardCharsets.UTF_8);
        System.out.println("dockerize-proof: wrote " + manifest.size() + " projects into " + out);
        manifest.forEach(l -> System.out.println("dockerize-proof:   " + l));
        System.out.println("dockerize-proof: kinds with no recipe (" + none.size() + ", all proven by '"
                + GENERIC_ID + "'): " + String.join(" ", none));
    }

    @Test
    @DisplayName("a kind with no recipe gets the same files whatever it is, so the one generic proof stands for all of them")
    void kindsWithoutARecipeShareOneAnswer() {
        Map<String, String> none = DockerizeGenerator.generate(ProjectKind.NONE, "x", false);
        List<String> kinds = kindsWithoutRecipe();
        assertThat(kinds).contains("none", "maven", "ruby");
        for (String name : kinds) {
            ProjectKind kind = ProjectKind.valueOf(name.toUpperCase(Locale.ROOT));
            assertThat(DockerizeGenerator.generate(kind, "x", false)).as(name).isEqualTo(none);
            assertThat(DockerizeGenerator.generate(kind, "x", true)).as(name + ", told it builds a static bundle").isEqualTo(none);
        }
    }

    @Test
    @DisplayName("the image is named the way the Docker panel names it")
    void imageNameIsThePanels() throws IOException {
        // the panel's rule is private UI code; this test repeats it, so it reads the panel to know it still agrees
        String panel = Files.readString(Path.of("src", "main", "java", "org", "nmox", "studio", "rack", "docker",
                "DockerPanelTopComponent.java"), StandardCharsets.UTF_8);
        assertThat(panel).contains("dir.getName().toLowerCase(java.util.Locale.ROOT).replaceAll(\"[^a-z0-9_-]\", \"-\")");
        assertThat(imageName(new File("/x/Proof Go"))).isEqualTo("proof-go");
    }

    // ---------------------------------------------------------------- derivation

    /** Every (kind, static?) the generator answers with a recipe of its own. */
    static List<Shape> recipeShapes() {
        String generic = DockerizeGenerator.generate(ProjectKind.NONE, "x", false).get("Dockerfile");
        List<Shape> shapes = new ArrayList<>();
        for (ProjectKind kind : ProjectKind.values()) {
            String plain = DockerizeGenerator.generate(kind, "x", false).get("Dockerfile");
            if (!plain.equals(generic)) {
                shapes.add(new Shape(kind, false));
            }
            if (!DockerizeGenerator.generate(kind, "x", true).get("Dockerfile").equals(plain)) {
                shapes.add(new Shape(kind, true));
            }
        }
        return shapes;
    }

    static List<String> kindsWithoutRecipe() {
        TreeSet<String> with = new TreeSet<>();
        recipeShapes().forEach(s -> with.add(s.kind().name()));
        return Stream.of(ProjectKind.values()).map(Enum::name).filter(n -> !with.contains(n))
                .map(n -> n.toLowerCase(Locale.ROOT)).sorted().toList();
    }

    // ---------------------------------------------------------------- writing

    private static Path outputDir(Path tmp) throws IOException {
        String asked = System.getProperty(OUT_PROPERTY);
        if (asked == null || asked.isBlank()) {
            return tmp;
        }
        Path out = Path.of(asked).toAbsolutePath().normalize();
        Files.createDirectories(out);
        try (Stream<Path> entries = Files.list(out)) {
            assertThat(entries.findAny()).as("-D%s=%s must be an empty directory: nothing there is overwritten or removed",
                    OUT_PROPERTY, out).isEmpty();
        }
        return out;
    }

    private static Path writeFixture(Path out, String id, Fixture fixture) throws IOException {
        // "proof-go", never "go": the folder's name becomes the image's name and the
        // path of the binary inside it, and /go is a directory in the golang image
        Path dir = Files.createDirectories(out.resolve("proof-" + id));
        for (Map.Entry<String, String> file : fixture.files().entrySet()) {
            Path target = dir.resolve(file.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
        }
        return dir;
    }

    /** What the panel's Write button does, with what its preview holds. */
    private static Map<String, String> writeDockerize(Path dir, ProjectKind kind, boolean statics) throws IOException {
        Map<String, String> files = DockerizeGenerator.generate(kind, imageName(dir.toFile()), statics);
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path target = DockerRecipes.resolveInside(dir.toFile(), file.getKey());
            assertThat(target).as("%s is the product's to write: the fixture must not bring its own", file.getKey())
                    .doesNotExist();
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
        }
        return files;
    }

    static String imageName(File dir) {
        return dir.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
    }

    private static String line(String id, Path dir, Mode mode, String port, String deep) {
        return String.join(" ", id, dir.getFileName().toString(), mode.name().toLowerCase(Locale.ROOT), port,
                deep == null ? "-" : deep);
    }

    // ---------------------------------------------------------------- the fixtures

    private static Map<String, Fixture> fixtures() {
        Map<String, Fixture> all = new LinkedHashMap<>();
        all.put("node", new Fixture(Mode.IMAGE, null, node()));
        all.put("node-static", new Fixture(Mode.IMAGE, "/some/client/route", nodeStatic()));
        all.put("go", new Fixture(Mode.IMAGE, null, go()));
        all.put("rust", new Fixture(Mode.IMAGE, null, rust()));
        all.put("python", new Fixture(Mode.IMAGE, null, python()));
        all.put("deno", new Fixture(Mode.IMAGE, null, deno()));
        all.put("php", new Fixture(Mode.COMPOSE, "/some/front/controller/route", php()));
        all.put(GENERIC_ID, new Fixture(Mode.IDLE, null, Map.of("README.md", """
                # proof-generic

                No manifest on purpose: this is what Dockerize is handed when it detects no toolchain.
                """)));
        return all;
    }

    /** A server with no dependencies and the lockfile `npm ci` insists on. */
    private static Map<String, String> node() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("package.json", """
                {
                  "name": "proof-node",
                  "version": "1.0.0",
                  "private": true,
                  "scripts": {
                    "start": "node server.js"
                  }
                }
                """);
        f.put("package-lock.json", """
                {
                  "name": "proof-node",
                  "version": "1.0.0",
                  "lockfileVersion": 3,
                  "requires": true,
                  "packages": {
                    "": {
                      "name": "proof-node",
                      "version": "1.0.0"
                    }
                  }
                }
                """);
        f.put("server.js", """
                const http = require('node:http');

                const port = Number(process.env.PORT || 3000);
                http.createServer((req, res) => {
                  res.writeHead(200, { 'Content-Type': 'text/plain' });
                  res.end('proof-node ok\\n');
                }).listen(port, '0.0.0.0', () => console.log(`listening on ${port}`));
                """);
        return f;
    }

    /**
     * A single-page app as the static recipe sees one: `npm ci`, `npm run
     * build`, a {@code dist/} to hand nginx. The "vite" it depends on is a
     * local stub: the dependency's NAME is what makes the product choose
     * this recipe, and a real bundler would make the proof about npm.
     */
    private static Map<String, String> nodeStatic() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("package.json", """
                {
                  "name": "proof-node-static",
                  "version": "1.0.0",
                  "private": true,
                  "scripts": {
                    "build": "node build.js"
                  },
                  "devDependencies": {
                    "vite": "file:tools/vite"
                  }
                }
                """);
        f.put("package-lock.json", """
                {
                  "name": "proof-node-static",
                  "version": "1.0.0",
                  "lockfileVersion": 3,
                  "requires": true,
                  "packages": {
                    "": {
                      "name": "proof-node-static",
                      "version": "1.0.0",
                      "devDependencies": {
                        "vite": "file:tools/vite"
                      }
                    },
                    "node_modules/vite": {
                      "resolved": "tools/vite",
                      "link": true
                    },
                    "tools/vite": {
                      "version": "0.0.0-proof",
                      "dev": true
                    }
                  }
                }
                """);
        f.put("tools/vite/package.json", """
                {
                  "name": "vite",
                  "version": "0.0.0-proof",
                  "private": true,
                  "description": "NOT vite. A stand-in with vite's name, so NMOX Studio's Dockerize picks its static-site recipe without the proof depending on the npm registry."
                }
                """);
        f.put("build.js", """
                // Stands in for a bundler: the recipe only needs `npm run build` to leave a dist/.
                const fs = require('node:fs');

                fs.rmSync('dist', { recursive: true, force: true });
                fs.mkdirSync('dist/assets', { recursive: true });
                fs.copyFileSync('index.html', 'dist/index.html');
                fs.writeFileSync('dist/assets/app.js', "document.title = 'proof-node-static';\\n");
                console.log('built dist/');
                """);
        f.put("index.html", """
                <!doctype html>
                <html lang="en">
                  <head>
                    <meta charset="utf-8" />
                    <title>proof</title>
                    <script type="module" src="/assets/app.js"></script>
                  </head>
                  <body>
                    <div id="app">proof-node-static shell</div>
                  </body>
                </html>
                """);
        return f;
    }

    /** A module WITH A SECOND PACKAGE: the shape `go build -o /file ./...` refused (v1.353.0). */
    private static Map<String, String> go() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("go.mod", """
                module example.com/proofgo

                go 1.21
                """);
        f.put("main.go", """
                package main

                import (
                \t"fmt"
                \t"log"
                \t"net/http"

                \t"example.com/proofgo/internal/greet"
                )

                func main() {
                \thttp.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
                \t\tfmt.Fprintln(w, greet.Hello("proof-go"))
                \t})
                \tlog.Fatal(http.ListenAndServe(":8080", nil))
                }
                """);
        f.put("internal/greet/greet.go", """
                // Package greet is the module's second package.
                package greet

                // Hello greets by name.
                func Hello(name string) string {
                \treturn name + " ok"
                }
                """);
        return f;
    }

    /** A crate named after its folder (the recipe copies target/release/&lt;folder&gt;), standard library only. */
    private static Map<String, String> rust() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("Cargo.toml", """
                [package]
                name = "proof-rust"
                version = "0.1.0"
                edition = "2021"

                [dependencies]
                """);
        f.put("src/main.rs", """
                use std::io::{Read, Write};
                use std::net::TcpListener;

                fn main() -> std::io::Result<()> {
                    let listener = TcpListener::bind("0.0.0.0:8080")?;
                    println!("listening on 8080");
                    for stream in listener.incoming() {
                        let mut stream = match stream {
                            Ok(s) => s,
                            Err(_) => continue,
                        };
                        let mut request = [0u8; 2048];
                        let _ = stream.read(&mut request);
                        let body = "proof-rust ok\\n";
                        let _ = write!(
                            stream,
                            "HTTP/1.1 200 OK\\r\\nContent-Type: text/plain\\r\\nContent-Length: {}\\r\\nConnection: close\\r\\n\\r\\n{}",
                            body.len(),
                            body
                        );
                    }
                    Ok(())
                }
                """);
        return f;
    }

    /** The recipe starts `uvicorn main:app`, so uvicorn is the one thing installed; the app is bare ASGI. */
    private static Map<String, String> python() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("requirements.txt", """
                uvicorn==0.54.0
                """);
        f.put("main.py", """
                async def app(scope, receive, send):
                    if scope["type"] != "http":
                        return
                    body = b"proof-python ok\\n"
                    await send({
                        "type": "http.response.start",
                        "status": 200,
                        "headers": [(b"content-type", b"text/plain"), (b"content-length", str(len(body)).encode())],
                    })
                    await send({"type": "http.response.body", "body": body})
                """);
        return f;
    }

    /** The recipe runs `deno task start`; the standard runtime only. */
    private static Map<String, String> deno() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("deno.json", """
                {
                  "tasks": {
                    "start": "deno run --allow-net main.ts"
                  }
                }
                """);
        f.put("main.ts", """
                Deno.serve({ port: 8000, hostname: "0.0.0.0" }, () => new Response("proof-deno ok\\n"));
                """);
        return f;
    }

    /**
     * A front controller that REQUIRES composer's autoloader, so a 200 also
     * says the vendor directory made it from the composer stage into the
     * fpm image. No packages: composer writes the autoloader regardless.
     */
    private static Map<String, String> php() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("composer.json", """
                {
                  "name": "nmox/proof-php",
                  "description": "The PHP fixture of NMOX Studio's Dockerize proof.",
                  "type": "project",
                  "require": {
                    "php": ">=8.2"
                  },
                  "autoload": {
                    "psr-4": {
                      "Proof\\\\": "src/"
                    }
                  }
                }
                """);
        f.put("src/Greeting.php", """
                <?php

                declare(strict_types=1);

                namespace Proof;

                final class Greeting
                {
                    public function hello(string $name): string
                    {
                        return $name . ' ok';
                    }
                }
                """);
        f.put("public/index.php", """
                <?php

                declare(strict_types=1);

                require __DIR__ . '/../vendor/autoload.php';

                header('Content-Type: text/plain');
                echo (new Proof\\Greeting())->hello('proof-php'), ' ', parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH), "\\n";
                """);
        return f;
    }
}
