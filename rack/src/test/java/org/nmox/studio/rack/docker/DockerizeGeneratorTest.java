package org.nmox.studio.rack.docker;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.DockerDevice;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;

import static org.assertj.core.api.Assertions.assertThat;

/** The Dockerize generator: deterministic, production-shaped output. */
class DockerizeGeneratorTest {

    @Test
    @DisplayName("Static Node projects get a multi-stage build into nginx")
    void nodeStatic() {
        Map<String, String> files = DockerizeGenerator.generate(ProjectKind.NODE, "shop", true);
        assertThat(files).containsKeys(
                "Dockerfile", "docker/nginx.conf", ".dockerignore", "compose.yaml");
        assertThat(files.get("Dockerfile"))
                .contains("AS build").contains("npm ci").contains("nginx").contains("EXPOSE 80")
                // the conf must actually be installed, not just generated
                .contains("COPY docker/nginx.conf /etc/nginx/conf.d/default.conf");
        // buildsStatic() keys on SPA bundlers; a client-routed SPA under
        // stock nginx 404s on deep-link refresh, so the conf serves the shell
        assertThat(files.get("docker/nginx.conf"))
                .contains("try_files $uri $uri/ /index.html");
        assertThat(files.get(".dockerignore")).contains("node_modules");
        assertThat(files.get("compose.yaml")).contains("shop:").contains("\"80:80\"");
        // a plain node server gets no nginx conf - nothing serves static files
        assertThat(DockerizeGenerator.generate(ProjectKind.NODE, "api", false))
                .doesNotContainKey("docker/nginx.conf");
    }

    @Test
    @DisplayName("Node servers run as the non-root node user with prod deps only")
    void nodeServer() {
        String df = DockerizeGenerator.generate(ProjectKind.NODE, "api", false).get("Dockerfile");
        assertThat(df).contains("USER node").contains("--omit=dev").contains("EXPOSE 3000");
    }

    @Test
    @DisplayName("Go gets a distroless static binary built from the root package")
    void goDistroless() {
        String df = DockerizeGenerator.generate(ProjectKind.GO, "svc", false).get("Dockerfile");
        assertThat(df).contains("CGO_ENABLED=0").contains("distroless");
        // under /app, never /<name>: the name is the folder's, and a folder called
        // go, bin or etc names a directory the image already has
        assertThat(df).contains("ENTRYPOINT [\"/app/svc\"]");
        // `go build -o /file ./...` refuses the moment the module grows a
        // second package ("cannot write multiple packages to non-directory") -
        // found by building the generated Dockerfile against a real daemon
        assertThat(df).contains("go build -o /out/svc .\n");
        assertThat(df).doesNotContain("./...");
    }

    @Test
    @DisplayName("Rust builds through BuildKit cache mounts; Python carries its toolchain")
    void rustAndPython() {
        String rs = DockerizeGenerator.generate(ProjectKind.RUST, "rs", false).get("Dockerfile");
        // without the cache mounts every code edit recompiles all dependencies;
        // with target/ in a mount the binary must be cp'd out in the same RUN
        assertThat(rs)
                .contains("--mount=type=cache,target=/usr/local/cargo/registry")
                .contains("--mount=type=cache,target=/src/target")
                .contains("cargo build --release && mkdir -p /out && cp target/release/rs /out/rs")
                .contains("COPY --from=build /out/rs /usr/local/bin/rs");
        assertThat(DockerizeGenerator.generate(ProjectKind.PYTHON, "py", false).get("Dockerfile"))
                .contains("pip install").contains("uvicorn");
    }

    @Test
    @DisplayName("Base images are current majors (each tag existence-checked on Docker Hub)")
    void baseImageCurrency() {
        // measured 2026-11-13; existence-checked on the registries, not built
        assertThat(DockerizeGenerator.generate(ProjectKind.NODE, "x", false).get("Dockerfile"))
                .contains("FROM node:26-alpine");
        assertThat(DockerizeGenerator.generate(ProjectKind.NODE, "x", true).get("Dockerfile"))
                .contains("FROM node:26-alpine AS build");
        assertThat(DockerizeGenerator.generate(ProjectKind.GO, "x", false).get("Dockerfile"))
                .contains("FROM golang:1.27")
                .contains("FROM gcr.io/distroless/static-debian13");
        assertThat(DockerizeGenerator.generate(ProjectKind.RUST, "x", false).get("Dockerfile"))
                .contains("FROM rust:1.100");
        assertThat(DockerizeGenerator.generate(ProjectKind.PYTHON, "x", false).get("Dockerfile"))
                .contains("FROM python:3.14-slim");
        assertThat(DockerizeGenerator.generate(ProjectKind.PHP, "x", false).get("Dockerfile"))
                .contains("FROM php:8.5-fpm-alpine");
        assertThat(DockerizeGenerator.generate(ProjectKind.PHP, "x", false).get("compose.yaml"))
                .contains("image: nginx:1.30-alpine");
        assertThat(DockerizeGenerator.generate(ProjectKind.NODE, "x", true).get("Dockerfile"))
                .contains("FROM nginx:1.30-alpine\n");
        assertThat(DockerizeGenerator.generate(ProjectKind.DENO, "x", false).get("Dockerfile"))
                .contains("FROM denoland/deno:alpine-2.9.8\n");
        assertThat(DockerizeGenerator.generate(ProjectKind.BUN, "x", false).get("Dockerfile"))
                .contains("FROM oven/bun:1.4-alpine\n");
    }

    /** Every image the generator names, for every kind it can be asked about: "FROM x" and compose "image: x". */
    static java.util.Set<String> generatedImages() {
        java.util.Set<String> images = new java.util.TreeSet<>();
        java.util.regex.Pattern named = java.util.regex.Pattern.compile("(?m)^(?:FROM|\\s+image:)\\s+(\\S+)");
        for (ProjectKind kind : ProjectKind.values()) {
            for (boolean statics : new boolean[] {false, true}) {
                for (String content : DockerizeGenerator.generate(kind, "x", statics).values()) {
                    java.util.regex.Matcher m = named.matcher(content);
                    while (m.find()) {
                        images.add(m.group(1));
                    }
                }
            }
        }
        return images;
    }

    @Test
    @DisplayName("No generated image floats: every FROM and every compose image carries a version, and nginx has one")
    void noImageFloats() {
        java.util.Set<String> images = generatedImages();
        // derived from the generator's own output, so a new recipe is held the day it is added
        assertThat(images).as("the derivation found the images").hasSizeGreaterThan(8)
                .contains("node:26-alpine", "gcr.io/distroless/static-debian13");
        for (String image : images) {
            int colon = image.lastIndexOf(':');
            // distroless names its debian release in the repository and has no tag;
            // debian's version is its release name
            String version = colon < 0 ? image.substring(image.lastIndexOf('/') + 1) : image.substring(colon + 1);
            assertThat(version.matches(".*[0-9].*") || image.equals("debian:trixie-slim"))
                    .as("%s: a tag such as :alpine or :latest moves under a Dockerfile already written", image)
                    .isTrue();
        }
        assertThat(images.stream().filter(i -> i.startsWith("nginx")).toList())
                .as("nginx is named in the static-site image and the PHP sidecar, from one constant")
                .containsExactly("nginx:1.30-alpine");
    }

    @Test
    @DisplayName("PHP stages composer deps into slim FPM with an nginx sidecar")
    void phpFpmWithNginxSidecar() {
        Map<String, String> files = DockerizeGenerator.generate(ProjectKind.PHP, "shop", false);
        assertThat(files).containsKeys(
                "Dockerfile", ".dockerignore", "compose.yaml", "docker/nginx.conf");
        assertThat(files.get("Dockerfile"))
                .contains("FROM composer:2 AS deps")
                // installed from the manifests alone, then the optimized
                // autoloader is written once the source it maps is there
                .contains("composer install --no-dev --no-interaction --no-scripts --no-autoloader")
                .contains("composer dump-autoload --no-dev --optimize")
                .contains("php:8.5-fpm-alpine")
                .contains("COPY --from=deps /app/vendor ./vendor");
        assertThat(files.get(".dockerignore")).contains("vendor").contains(".env");
        // the sidecar publishes 80 and hands .php to the fpm container by name
        assertThat(files.get("compose.yaml"))
                .contains("shop:").contains("nginx:").contains("\"80:80\"")
                .contains("./docker/nginx.conf");
        assertThat(files.get("docker/nginx.conf"))
                .contains("root /var/www/html/public")
                .contains("fastcgi_pass shop:9000");
        assertThat(DockerizeGenerator.defaultPort(ProjectKind.PHP, false)).isEqualTo(80);
    }

    @Test
    @DisplayName("Unknown toolchains say so instead of guessing")
    void genericIsHonest() {
        assertThat(DockerizeGenerator.generate(ProjectKind.NONE, "x", false).get("Dockerfile"))
                .contains("No toolchain detected");
    }

    @Test
    @DisplayName("Default ports follow the runtime")
    void defaultPorts() {
        assertThat(DockerizeGenerator.defaultPort(ProjectKind.NODE, true)).isEqualTo(80);
        assertThat(DockerizeGenerator.defaultPort(ProjectKind.NODE, false)).isEqualTo(3000);
        assertThat(DockerizeGenerator.defaultPort(ProjectKind.PYTHON, false)).isEqualTo(8000);
    }

    @Test
    @DisplayName("HARBOR's reclaimable total sums mixed units readably")
    void reclaimableTotal() {
        var rows = java.util.List.of(
                new DockerClient.DfRow("Images", "12", "4", "6.5GB", "4.2GB (64%)"),
                new DockerClient.DfRow("Build Cache", "88", "0", "2.1GB", "512MB"),
                new DockerClient.DfRow("Containers", "3", "1", "10MB", "0B"));
        assertThat(DockerDevice.totalReclaimable(rows)).isEqualTo("4.7GB");
    }

    @Test
    @DisplayName("Deno projects ride the official denoland image and cache deps first")
    void deno() {
        Map<String, String> files = DockerizeGenerator.generate(ProjectKind.DENO, "api", false);
        assertThat(files.get("Dockerfile"))
                .contains("FROM denoland/deno:alpine-")
                .contains("RUN deno install")
                .contains("deno cache")
                .contains("CMD [\"deno\", \"task\", \"start\"]")
                .contains("EXPOSE 8000");
        assertThat(files.get("compose.yaml")).contains("\"8000:8000\"");
    }
}
