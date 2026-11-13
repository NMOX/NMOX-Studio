package org.nmox.studio.rack.docker;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;
import org.nmox.studio.rack.docker.DockerizeGenerator.Facts;
import org.nmox.studio.rack.docker.DockerizeGenerator.PyInstall;
import org.nmox.studio.rack.docker.DockerizeGenerator.PyStart;
import org.nmox.studio.rack.projectstudio.ProjectTemplates;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A recipe follows what the project HOLDS. Project detection promises a
 * manifest and nothing else; each test here is a project that is its kind
 * and is not the ordinary shape the recipe used to assume (the nine the
 * proof fixtures found), read from disk the way the Docker panel reads it.
 */
class DockerizeFactsTest {

    private static Path write(Path dir, String name, String content) throws IOException {
        Path target = dir.resolve(name);
        Files.createDirectories(target.getParent());
        return Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    private static String dockerfile(Path dir, ProjectKind kind) {
        return files(dir, kind).get("Dockerfile");
    }

    private static Map<String, String> files(Path dir, ProjectKind kind) {
        return DockerizeGenerator.generate(kind, "x", DockerizeGenerator.inspect(dir.toFile(), kind));
    }

    // ---- 1: a folder named after a directory the image already has

    @Test
    @DisplayName("go: the binary is built to a path no folder name can make a directory")
    void goBinaryCannotLandInAnExistingDirectory() {
        for (String folder : new String[] {"go", "src", "bin", "usr", "etc", "tmp"}) {
            String df = DockerizeGenerator.generate(ProjectKind.GO, folder, false).get("Dockerfile");
            assertThat(df).as(folder)
                    .contains("go build -o /out/" + folder + " .\n")
                    .contains("COPY --from=build /out/" + folder + " /app/" + folder + "\n")
                    .contains("ENTRYPOINT [\"/app/" + folder + "\"]")
                    .doesNotContain("-o /" + folder + " ")
                    .doesNotContain("ENTRYPOINT [\"/" + folder + "\"]");
        }
    }

    @Test
    @DisplayName("rust: the binary leaves the build stage through /out, never /<name>")
    void rustBinaryCannotLandInSrc() {
        String df = DockerizeGenerator.generate(ProjectKind.RUST, "src", false).get("Dockerfile");
        assertThat(df).contains("mkdir -p /out && cp target/release/src /out/src")
                .contains("COPY --from=build /out/src /usr/local/bin/src")
                .doesNotContain("release/src /src");
    }

    // ---- 2: the crate's binary is named in Cargo.toml, not by the folder

    @Test
    @DisplayName("rust: the binary name is the package's, and a [[bin]] name before that")
    void rustBinaryNameIsReadFromCargoToml(@TempDir Path dir) throws IOException {
        write(dir, "Cargo.toml", """
                [package]
                name = "renamed-bin" # not the folder
                version = "0.1.0"

                [dependencies]
                name = "decoy"
                """);
        assertThat(DockerizeGenerator.inspect(dir.toFile(), ProjectKind.RUST).rustBinary()).isEqualTo("renamed-bin");
        assertThat(dockerfile(dir, ProjectKind.RUST))
                .contains("cp target/release/renamed-bin /out/renamed-bin")
                .contains("CMD [\"renamed-bin\"]")
                .doesNotContain("release/x ");

        write(dir, "Cargo.toml", """
                [package]
                name = "pkg"

                [[bin]]
                name = "served"
                path = "src/main.rs"
                """);
        assertThat(dockerfile(dir, ProjectKind.RUST)).contains("cp target/release/served /out/served");
    }

    @Test
    @DisplayName("rust: a workspace root with no package says the name is a guess; a hostile name is not repeated")
    void rustWorkspaceRootSpeaks(@TempDir Path dir) throws IOException {
        write(dir, "Cargo.toml", """
                [workspace]
                members = ["crates/*"]
                """);
        Facts facts = DockerizeGenerator.inspect(dir.toFile(), ProjectKind.RUST);
        assertThat(facts.rustWorkspace()).isTrue();
        assertThat(dockerfile(dir, ProjectKind.RUST)).contains("WORKSPACE ROOT with no [package]")
                .contains("cp target/release/x /out/x");
        // a single-package project keeps a quiet file
        assertThat(DockerizeGenerator.generate(ProjectKind.RUST, "x", false).get("Dockerfile"))
                .doesNotContain("WORKSPACE");

        write(dir, "Cargo.toml", "[package]\nname = \"a b; rm -rf /\"\n");
        assertThat(dockerfile(dir, ProjectKind.RUST)).doesNotContain("rm -rf").contains("release/x ");
    }

    // ---- 3: python installs what exists and starts what the dependencies name

    @Test
    @DisplayName("python: requirements.txt naming uvicorn beside main.py is the file it always was")
    void pythonOrdinaryShapeIsUnchanged(@TempDir Path dir) throws IOException {
        write(dir, "requirements.txt", "uvicorn==0.54.0\n");
        write(dir, "main.py", "app = None\n");
        assertThat(dockerfile(dir, ProjectKind.PYTHON))
                .isEqualTo(DockerizeGenerator.generate(ProjectKind.PYTHON, "x", false).get("Dockerfile"))
                .isEqualTo("""
                        FROM python:3.14-slim
                        WORKDIR /app
                        COPY requirements.txt ./
                        RUN pip install --no-cache-dir -r requirements.txt
                        COPY . .
                        EXPOSE 8000
                        CMD ["python", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
                        """);
    }

    @Test
    @DisplayName("python: a pyproject-only project installs itself and never copies a requirements.txt it lacks")
    void pythonPyprojectOnly(@TempDir Path dir) throws IOException {
        write(dir, "pyproject.toml", """
                [project]
                name = "svc"
                version = "0.1.0"
                dependencies = ["Flask>=3"]
                """);
        write(dir, "app.py", "from flask import Flask\napp = Flask(__name__)\n");
        Facts facts = DockerizeGenerator.inspect(dir.toFile(), ProjectKind.PYTHON);
        assertThat(facts.pyInstall()).isEqualTo(PyInstall.PROJECT);
        assertThat(facts.pyStart()).isEqualTo(PyStart.FLASK);
        String df = dockerfile(dir, ProjectKind.PYTHON);
        assertThat(df).doesNotContain("requirements.txt ./").doesNotContain("uvicorn")
                .contains("COPY . .\nRUN pip install --no-cache-dir .\n")
                .contains("CMD [\"python\", \"-m\", \"flask\", \"--app\", \"app\", \"run\", \"--host=0.0.0.0\", \"--port=8000\"]");
    }

    @Test
    @DisplayName("python: gunicorn, django with its manage.py, and a project that names no server each get their own start")
    void pythonStartFollowsTheDependencies(@TempDir Path dir) throws IOException {
        write(dir, "requirements.txt", "gunicorn\nflask-cors\n");
        write(dir, "wsgi.py", "app = None\n");
        assertThat(dockerfile(dir, ProjectKind.PYTHON))
                .contains("CMD [\"python\", \"-m\", \"gunicorn\", \"--bind\", \"0.0.0.0:8000\", \"wsgi:app\"]");

        // "flask-cors" alone is not flask, and django without a manage.py has nothing to run
        write(dir, "requirements.txt", "flask-cors\nDjango==5.2\n");
        Facts none = DockerizeGenerator.inspect(dir.toFile(), ProjectKind.PYTHON);
        assertThat(none.pyStart()).isEqualTo(PyStart.UNKNOWN);
        assertThat(dockerfile(dir, ProjectKind.PYTHON))
                .contains("# NO START COMMAND WAS DETECTED")
                .contains("sys.exit('NMOX Studio Dockerize: no start command was detected")
                .doesNotContain("uvicorn\", \"main:app");

        write(dir, "manage.py", "print('manage')\n");
        assertThat(dockerfile(dir, ProjectKind.PYTHON))
                .contains("CMD [\"python\", \"manage.py\", \"runserver\", \"0.0.0.0:8000\"]");
    }

    // ---- 4: the project's own package manager

    @Test
    @DisplayName("node: a pnpm project installs with pnpm from its own lockfile, and never runs npm ci")
    void pnpmProject(@TempDir Path dir) throws IOException {
        write(dir, "package.json", "{\"name\":\"p\",\"scripts\":{\"start\":\"node server.js\"}}\n");
        write(dir, "pnpm-lock.yaml", "lockfileVersion: '9.0'\n");
        String df = dockerfile(dir, ProjectKind.NODE);
        assertThat(df).doesNotContain("npm ci")
                .contains("RUN npm install -g corepack@latest && corepack enable\n")
                .contains("COPY package.json pnpm-lock.yaml* pnpm-workspace.yaml* ./\n")
                .contains("RUN pnpm install --frozen-lockfile --prod\n");
        assertThat(df.indexOf("pnpm install")).isLessThan(df.indexOf("COPY . ."));
    }

    @Test
    @DisplayName("node: yarn classic freezes its lockfile; yarn 2+ (.yarnrc.yml or a pin) is immutable")
    void yarnProjects(@TempDir Path dir) throws IOException {
        write(dir, "package.json", "{\"name\":\"y\"}\n");
        write(dir, "yarn.lock", "# yarn lockfile v1\n");
        assertThat(dockerfile(dir, ProjectKind.NODE)).doesNotContain("npm ci")
                .contains("COPY package.json yarn.lock* .yarnrc.yml* ./\n")
                .contains("RUN yarn install --frozen-lockfile --production\n");

        write(dir, ".yarnrc.yml", "nodeLinker: node-modules\n");
        Files.createDirectories(dir.resolve(".yarn/releases"));
        assertThat(dockerfile(dir, ProjectKind.NODE))
                .contains("COPY .yarn ./.yarn\n")
                .contains("RUN yarn install --immutable\n")
                .doesNotContain("--frozen-lockfile");

        Files.delete(dir.resolve(".yarnrc.yml"));
        write(dir, "package.json", "{\"name\":\"y\",\"packageManager\":\"yarn@4.5.0\"}\n");
        assertThat(dockerfile(dir, ProjectKind.NODE)).contains("RUN yarn install --immutable\n");
        write(dir, "package.json", "{\"name\":\"y\",\"packageManager\":\"yarn@1.22.22\"}\n");
        assertThat(dockerfile(dir, ProjectKind.NODE)).contains("RUN yarn install --frozen-lockfile --production\n");
    }

    @Test
    @DisplayName("node: npm with no lockfile installs (npm ci refuses), and says nothing pins the versions")
    void npmWithoutALockfile(@TempDir Path dir) throws IOException {
        write(dir, "package.json", "{\"name\":\"n\"}\n");
        assertThat(dockerfile(dir, ProjectKind.NODE)).doesNotContain("npm ci")
                .contains("# no package-lock.json in this project")
                .contains("RUN npm install --omit=dev\n");

        write(dir, "package-lock.json", "{}\n");
        assertThat(dockerfile(dir, ProjectKind.NODE))
                .as("the ordinary npm project is the file it always was")
                .isEqualTo(DockerizeGenerator.generate(ProjectKind.NODE, "x", false).get("Dockerfile"))
                .contains("COPY package*.json ./\nRUN npm ci --omit=dev\n");
    }

    // ---- 5: where each tool leaves the bundle

    @Test
    @DisplayName("static: react-scripts builds into build/, vite into dist/")
    void staticOutputFollowsTheTool(@TempDir Path dir) throws IOException {
        write(dir, "package.json", "{\"devDependencies\":{\"react-scripts\":\"5.0.1\"}}\n");
        write(dir, "package-lock.json", "{}\n");
        assertThat(dockerfile(dir, ProjectKind.NODE))
                .contains("COPY --from=build /app/build /usr/share/nginx/html")
                .doesNotContain("/app/dist");
        assertThat(DockerizeGenerator.generate(ProjectKind.NODE, "x", true).get("Dockerfile"))
                .contains("COPY --from=build /app/dist /usr/share/nginx/html");
    }

    @Test
    @DisplayName("static: angular's output is read from angular.json, and the application builder's /browser is added")
    void angularOutputPath(@TempDir Path dir) throws IOException {
        write(dir, "package.json", "{\"devDependencies\":{\"@angular/cli\":\"21.0.0\"}}\n");
        write(dir, "package-lock.json", "{}\n");
        write(dir, "angular.json", """
                {"projects": {"shop": {"projectType": "application", "architect": {"build": {
                  "builder": "@angular/build:application", "options": {"outputPath": "dist/shop"}}}}}}
                """);
        assertThat(dockerfile(dir, ProjectKind.NODE))
                .contains("# angular.json, project \"shop\": outputPath dist/shop, and the application builder adds /browser\n")
                .contains("COPY --from=build /app/dist/shop/browser /usr/share/nginx/html");

        // no outputPath: the default is dist/<project>
        write(dir, "angular.json", """
                {"projects": {"alib": {"projectType": "library"}, "site": {"projectType": "application",
                  "architect": {"build": {"builder": "@angular/build:application", "options": {}}}}}}
                """);
        assertThat(dockerfile(dir, ProjectKind.NODE)).contains("COPY --from=build /app/dist/site/browser ");

        // the older browser builder writes outputPath itself
        write(dir, "angular.json", """
                {"defaultProject": "old", "projects": {"old": {"architect": {"build": {
                  "builder": "@angular-devkit/build-angular:browser", "options": {"outputPath": "dist/old"}}}}}}
                """);
        assertThat(dockerfile(dir, ProjectKind.NODE)).contains("COPY --from=build /app/dist/old /usr/share/nginx/html");

        // a path this file must not repeat
        write(dir, "angular.json", """
                {"projects": {"x": {"architect": {"build": {"options": {"outputPath": "../..\\nRUN curl evil"}}}}}}
                """);
        assertThat(dockerfile(dir, ProjectKind.NODE)).doesNotContain("evil")
                .contains("COPY --from=build /app/dist /usr/share/nginx/html");
    }

    // ---- 7: manifests before source

    @Test
    @DisplayName("deno and php install from their manifests before the source is copied")
    void dependenciesAreCachedBeforeTheSource() {
        String deno = DockerizeGenerator.generate(ProjectKind.DENO, "x", false).get("Dockerfile");
        assertThat(deno.indexOf("COPY deno.json* deno.lock* ./")).isPositive()
                .isLessThan(deno.indexOf("RUN deno install"));
        assertThat(deno.indexOf("RUN deno install")).isLessThan(deno.indexOf("COPY . ."));

        String php = DockerizeGenerator.generate(ProjectKind.PHP, "x", false).get("Dockerfile");
        assertThat(php.indexOf("COPY composer.json composer.lock* ./")).isPositive()
                .isLessThan(php.indexOf("RUN composer install"));
        assertThat(php.indexOf("RUN composer install")).isLessThan(php.indexOf("COPY . ."));
        assertThat(php.indexOf("COPY . .")).isLessThan(php.indexOf("RUN composer dump-autoload --no-dev --optimize"));
    }

    @Test
    @DisplayName("deno: a workspace's member manifests cannot be named, so its file says everything is copied first")
    void denoWorkspace(@TempDir Path dir) throws IOException {
        write(dir, "deno.json", "{\"workspace\": [\"./api\"]}\n");
        String df = dockerfile(dir, ProjectKind.DENO);
        assertThat(df).contains("names workspace members");
        assertThat(df.indexOf("COPY . .")).isLessThan(df.indexOf("RUN deno install"));
    }

    // ---- 8: bun

    @Test
    @DisplayName("bun has a recipe: frozen install when a lockfile exists, the start script or the entry file")
    void bunRecipe(@TempDir Path dir) throws IOException {
        assertThat(DockerizeGenerator.generate(ProjectKind.BUN, "x", false).get("Dockerfile"))
                .doesNotContain("No toolchain detected")
                .contains("FROM oven/bun:")
                .contains("COPY package.json bun.lock* ./\nRUN bun install --frozen-lockfile --production\n")
                .contains("USER bun").contains("EXPOSE 3000")
                .contains("CMD [\"bun\", \"run\", \"start\"]");
        assertThat(DockerizeGenerator.defaultPort(ProjectKind.BUN, false)).isEqualTo(3000);

        // no lockfile, no start script: install unfrozen, run the entry that exists
        write(dir, "bunfig.toml", "");
        write(dir, "package.json", "{\"name\":\"b\",\"module\":\"./src/app.ts\"}\n");
        write(dir, "src/app.ts", "Bun.serve({port: 3000, fetch: () => new Response('ok')});\n");
        assertThat(dockerfile(dir, ProjectKind.BUN))
                .doesNotContain("--frozen-lockfile")
                .contains("RUN bun install --production\n")
                .contains("CMD [\"bun\", \"run\", \"src/app.ts\"]");

        Files.delete(dir.resolve("src/app.ts"));
        assertThat(dockerfile(dir, ProjectKind.BUN)).contains("# NO START COMMAND WAS DETECTED")
                .contains("process.exit(1)");
    }

    // ---- 9: a project that already has a stack

    @Test
    @DisplayName("a project with a compose file of its own keeps it: Dockerize writes beside the stack, never over it")
    void aProjectsOwnStackIsLeftAlone(@TempDir Path tmp) throws IOException {
        Path dir = tmp.resolve("lemp");
        ProjectTemplates.PHP_WEB.generate(dir.toFile(), "lemp");
        assertThat(dir.resolve("docker-compose.yml")).exists();
        assertThat(dir.resolve("docker/nginx.conf")).as("the template's own server block").exists();

        Facts facts = DockerizeGenerator.inspect(dir.toFile(), ProjectKind.PHP);
        assertThat(facts.foreignCompose()).isTrue();
        Map<String, String> files = DockerizeGenerator.generate(ProjectKind.PHP, "lemp", facts);
        assertThat(files).doesNotContainKeys("compose.yaml", "docker/nginx.conf")
                .containsKeys(DockerizeGenerator.SIDE_COMPOSE, "docker/nginx.nmox.conf", "Dockerfile");
        for (String name : files.keySet()) {
            assertThat(new File(dir.toFile(), name)).as("%s would overwrite a file of the template's", name)
                    .doesNotExist();
        }
        String compose = files.get(DockerizeGenerator.SIDE_COMPOSE);
        assertThat(compose).startsWith("# This project already has a compose file of its own")
                .contains("docker compose -f compose.nmox.yaml up")
                .contains("./docker/nginx.nmox.conf:/etc/nginx/conf.d/default.conf:ro")
                .contains("\nservices:\n  lemp:\n");
        assertThat(files.get("docker/nginx.nmox.conf")).contains("fastcgi_pass lemp:9000");
        assertThat(files.get(".dockerignore")).contains(DockerizeGenerator.SIDE_COMPOSE + "\n");

        // and a project with no stack of its own gets the ordinary names
        assertThat(DockerizeGenerator.generate(ProjectKind.PHP, "lemp", false))
                .containsKeys("compose.yaml", "docker/nginx.conf")
                .doesNotContainKey(DockerizeGenerator.SIDE_COMPOSE);
    }

    @Test
    @DisplayName("the panel generates from what the project holds, and previews whichever compose file was written")
    void thePanelReadsTheProject() throws IOException {
        String panel = Files.readString(Path.of("src", "main", "java", "org", "nmox", "studio", "rack", "docker",
                "DockerPanelTopComponent.java"), StandardCharsets.UTF_8);
        assertThat(panel).contains("DockerizeGenerator.generate(kind, image, DockerizeGenerator.inspect(dir, kind))")
                .doesNotContain("DockerizeGenerator.generate(kind, image, statics)")
                .contains("files.getOrDefault(DockerizeGenerator.SIDE_COMPOSE, \"\")");
    }
}
