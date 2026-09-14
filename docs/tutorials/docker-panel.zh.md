# 教程：Docker 面板

<!-- languages -->
[English](docker-panel.md) · [Español](docker-panel.es.md) · [Français](docker-panel.fr.md) · [Deutsch](docker-panel.de.md) · [Русский](docker-panel.ru.md) · [Українська](docker-panel.uk.md) · [Polski](docker-panel.pl.md) · [Português (Brasil)](docker-panel.pt.md) · [Bahasa Indonesia](docker-panel.id.md) · [Filipino](docker-panel.tl.md) · [Tiếng Việt](docker-panel.vi.md) · **简体中文** · [हिन्दी](docker-panel.hi.md) · [עברית](docker-panel.he.md) · [العربية](docker-panel.ar.md)
<!-- /languages -->

Docker 面板是本地 Docker 引擎的控制台 — 容器、镜像、卷、网络 — 另外还有一个 **Dockerize** 标签页，为你的项目生成可用于生产的 Dockerfile。它在机架上的对应物是 **HARBOR** 设备。

![引擎已启动，一个 postgres 容器在运行 — 状态点、端口，以及那一排动作：启动、停止、日志、检查](../images/docker-panel.png)

## 开始之前

先让 Docker 在本地跑起来（`docker version` 应该成功；`工具 ▸ 环境诊断…` 会帮你确认）。

## 步骤

1. **打开面板。**按 `⌘8`，或者在欢迎页的“工具”栏里点 **Docker 面板**。**引擎**概览显示守护进程是否在运行。

2. **查看容器。****容器**标签页列出正在运行的东西 — 名称、镜像、端口、状态。**镜像**、**卷**和**网络**各有自己的标签页。

3. **给项目做 Dockerize。**瞄准一个项目，打开 **Dockerize** 标签页。它会生成可用于生产的 `Dockerfile`、`.dockerignore` 和一个 `compose` 文件，按你的工具链量身定做（Node 多阶段构建、PHP `php-fpm` 加 nginx 边车，等等）— 从不覆盖已有文件（已存在时，它会写一个 `.suggested` 副本在旁边）。

4. **提供数据库连接。**如果有数据库容器在运行，数据库工作室会自动为它提供一个连接 — 先按镜像名、再按端口推断，每个容器只提一次。

## 你刚学到了什么

- 这个面板是对 `docker` CLI 的真正异步封装；守护进程卡住时会报告出来，而不是跟着挂住。
- Dockerize 了解工具链，而且是幂等的。

## 下一步

- 在机架上装一台 **HARBOR**，就能从前面板上使用 PANEL/PRUNE/REFRESH。
- 在[数据库工作室](db-studio.zh.md)里连接一个容器化的数据库。
