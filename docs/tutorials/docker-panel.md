# Tutorial: The Docker panel

The Docker panel is a control surface for your local Docker engine —
containers, images, volumes, networks — plus a **Dockerize** tab that
generates a production Dockerfile for your project. Its rack counterpart
is the **HARBOR** device.

<!-- screenshot: the Docker panel Containers tab with a running container, and the Dockerize tab -->

## Before you start

Have Docker running locally (`docker version` should succeed;
`Tools ▸ Environment Doctor` will confirm).

## Steps

1. **Open the panel.** Click the **Docker** tab (open by default on first
   launch). The **Engine** overview shows whether the daemon is up.

2. **Inspect containers.** The **Containers** tab lists what's running —
   names, images, ports, status. **Images**, **Volumes**, and
   **Networks** each have their own tab.

3. **Dockerize a project.** Open the **Dockerize** tab with a project
   aimed. It generates a production `Dockerfile`, `.dockerignore`, and a
   `compose` file tailored to your toolchain (Node multi-stage, PHP
   `php-fpm` + nginx sidecar, etc.) — never clobbering existing files
   (it writes `.suggested` siblings when one exists).

4. **Offer a DB connection.** If a database container is running, DB
   Studio automatically offers a connection for it — image name then port
   inference, once per container.

## What you just learned

- The panel is a real async wrapper over the `docker` CLI; a wedged
  daemon is reported, not hung.
- Dockerize is toolchain-aware and idempotent.

## Next

- Mount **HARBOR** in the rack for PANEL/PRUNE/REFRESH from a faceplate.
- Connect to a containerized database in [DB Studio](db-studio.md).
