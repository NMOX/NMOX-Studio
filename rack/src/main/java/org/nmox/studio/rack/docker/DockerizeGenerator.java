package org.nmox.studio.rack.docker;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import org.nmox.studio.rack.devices.ProjectInspector;

/**
 * Turns what the rack already knows about a project into production
 * container files: a multi-stage Dockerfile tuned to the detected
 * toolchain, a .dockerignore that keeps the context lean, and a
 * compose.yaml to run it. This is the "more power than docker gives
 * you" move - docker can build an image, but it cannot look at your
 * package.json and write the Dockerfile.
 */
public final class DockerizeGenerator {

    private DockerizeGenerator() {
    }

    /** What we will write: filename -> content. Deterministic, no I/O. */
    public static Map<String, String> generate(ProjectInspector.ProjectKind kind,
            String projectName, boolean nodeBuildsStatic) {
        Map<String, String> files = new LinkedHashMap<>();
        String name = projectName == null || projectName.isBlank() ? "app" : projectName;
        switch (kind) {
            case NODE -> {
                files.put("Dockerfile", nodeBuildsStatic ? nodeStatic() : nodeServer());
                files.put(".dockerignore", ignore("node_modules", "dist", ".git", "*.log"));
            }
            case GO -> {
                files.put("Dockerfile", go(name));
                files.put(".dockerignore", ignore("bin", ".git"));
            }
            case RUST -> {
                files.put("Dockerfile", rust(name));
                files.put(".dockerignore", ignore("target", ".git"));
            }
            case PYTHON -> {
                files.put("Dockerfile", python());
                files.put(".dockerignore", ignore("__pycache__", ".venv", ".git"));
            }
            case PHP -> {
                files.put("Dockerfile", php());
                files.put("docker/nginx.conf", phpNginxConf(name));
                files.put(".dockerignore", ignore("vendor", ".env", ".git"));
            }
            default -> {
                files.put("Dockerfile", generic());
                files.put(".dockerignore", ignore(".git"));
            }
        }
        // php-fpm doesn't speak HTTP; its compose pairs the app with an
        // nginx sidecar instead of publishing the app port directly
        files.put("compose.yaml", kind == ProjectInspector.ProjectKind.PHP
                ? phpCompose(name)
                : compose(name, defaultPort(kind, nodeBuildsStatic)));
        return files;
    }

    /** The port the generated container listens on. */
    public static int defaultPort(ProjectInspector.ProjectKind kind, boolean nodeBuildsStatic) {
        return switch (kind) {
            case NODE -> nodeBuildsStatic ? 80 : 3000;
            case GO, RUST -> 8080;
            case PYTHON -> 8000;
            case PHP -> 80; // the nginx sidecar fronts the fpm container
            default -> 8080;
        };
    }

    /** True when the Node project builds a static bundle (vite & friends). */
    public static boolean buildsStatic(File projectDir) {
        return ProjectInspector.firstDependency(projectDir,
                "vite", "react-scripts", "@angular/cli", "svelte", "parcel") != null;
    }

    private static String ignore(String... extra) {
        StringBuilder sb = new StringBuilder("""
                Dockerfile
                compose.yaml
                .dockerignore
                """);
        for (String e : extra) {
            sb.append(e).append('\n');
        }
        return sb.toString();
    }

    private static String nodeStatic() {
        return """
                # Build the static bundle, serve it with nginx - tiny final image
                FROM node:22-alpine AS build
                WORKDIR /app
                COPY package*.json ./
                RUN npm ci
                COPY . .
                RUN npm run build

                FROM nginx:alpine
                COPY --from=build /app/dist /usr/share/nginx/html
                EXPOSE 80
                """;
    }

    private static String nodeServer() {
        return """
                # Install with the lockfile, run as the non-root node user
                FROM node:22-alpine
                WORKDIR /app
                ENV NODE_ENV=production
                COPY package*.json ./
                RUN npm ci --omit=dev
                COPY . .
                USER node
                EXPOSE 3000
                CMD ["npm", "start"]
                """;
    }

    private static String go(String name) {
        return """
                # Static binary in a distroless image - ~10MB, no shell to exploit
                FROM golang:1.23 AS build
                WORKDIR /src
                COPY go.* ./
                RUN go mod download
                COPY . .
                RUN CGO_ENABLED=0 go build -o /%s ./...

                FROM gcr.io/distroless/static-debian12
                COPY --from=build /%s /%s
                EXPOSE 8080
                ENTRYPOINT ["/%s"]
                """.formatted(name, name, name, name);
    }

    private static String rust(String name) {
        return """
                # Release build, then a minimal runtime layer
                FROM rust:1.83 AS build
                WORKDIR /src
                COPY . .
                RUN cargo build --release

                FROM debian:bookworm-slim
                COPY --from=build /src/target/release/%s /usr/local/bin/%s
                EXPOSE 8080
                CMD ["%s"]
                """.formatted(name, name, name);
    }

    private static String python() {
        return """
                FROM python:3.13-slim
                WORKDIR /app
                COPY requirements.txt ./
                RUN pip install --no-cache-dir -r requirements.txt
                COPY . .
                EXPOSE 8000
                CMD ["python", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
                """;
    }

    private static String php() {
        return """
                # Composer resolves dependencies in its own stage; the runtime
                # image is slim FPM and never carries composer itself
                FROM composer:2 AS deps
                WORKDIR /app
                COPY . .
                RUN composer install --no-dev --optimize-autoloader --no-interaction

                FROM php:8.3-fpm-alpine
                WORKDIR /var/www/html
                COPY --from=deps /app/vendor ./vendor
                COPY . .
                EXPOSE 9000
                CMD ["php-fpm"]
                """;
    }

    /** The nginx sidecar's server block: static files, then the front controller. */
    private static String phpNginxConf(String name) {
        return """
                server {
                    listen 80;
                    server_name _;

                    root /var/www/html/public;
                    index index.php;

                    location / {
                        try_files $uri $uri/ /index.php?$query_string;
                    }

                    location ~ \\.php$ {
                        include fastcgi_params;
                        fastcgi_pass %s:9000;
                        fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;
                    }
                }
                """.formatted(name);
    }

    /** fpm app + nginx sidecar, nginx published on 80. */
    private static String phpCompose(String name) {
        return """
                services:
                  %s:
                    build: .
                    restart: unless-stopped

                  nginx:
                    image: nginx:1.27-alpine
                    ports:
                      - "80:80"
                    volumes:
                      - ./docker/nginx.conf:/etc/nginx/conf.d/default.conf:ro
                      - .:/var/www/html:ro
                    depends_on:
                      - %s
                    restart: unless-stopped
                """.formatted(name, name);
    }

    private static String generic() {
        return """
                # No toolchain detected - a starting point, not a guess
                FROM debian:bookworm-slim
                WORKDIR /app
                COPY . .
                CMD ["/bin/sh", "-c", "echo 'edit this Dockerfile for your runtime' && sleep infinity"]
                """;
    }

    private static String compose(String name, int port) {
        return """
                services:
                  %s:
                    build: .
                    ports:
                      - "%d:%d"
                    restart: unless-stopped
                """.formatted(name, port, port);
    }
}
