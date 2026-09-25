# NMOX Studio 教程

<!-- languages -->
[English](README.md) · [Español](README.es.md) · [Français](README.fr.md) · [Deutsch](README.de.md) · [Русский](README.ru.md) · [Українська](README.uk.md) · [Polski](README.pl.md) · [Português (Brasil)](README.pt.md) · [Bahasa Indonesia](README.id.md) · [Filipino](README.tl.md) · [Tiếng Việt](README.vi.md) · **简体中文** · [हिन्दी](README.hi.md) · [עברית](README.he.md) · [العربية](README.ar.md)
<!-- /languages -->

一组简短、动手做的导览，讲的是让 NMOX Studio 有别于普通 IDE 的那些系统。每一篇一次坐下就能做完 — 打开窗口，跟着步骤走，你就真的用过了这个功能。

想看全面的参考（安装、每个菜单、每一张安全网），请读[用户指南](../user-guide.zh.md)。完整的设备列表见 [devices.md](../devices.md)。

## 各个系统

| 教程 | 你会做什么 | 从哪里打开 |
|----------|----------------|------------|
| [任务机架](the-task-rack.zh.md) | 接一条“运行→监视”的配线，看着它触发 | ⌘9 / 任务机架标签页 |
| [编写你自己的设备](your-own-device.zh.md) | 用文本编辑器给机架添一台设备 — 不写 Java，不用重启 | `~/.nmox/devices.d/` |
| [工作台](workbench.zh.md) | 用这个母港在项目和工具之间跳转 | ⌥⌘0 |
| [项目工作室](project-studio.zh.md) | 搭一个项目的脚手架，不开终端就把它跑起来 | 项目工作室标签页 |
| [API 工作室](api-studio.zh.md) | 发一个请求，对它做断言，读安全评分 | ⌥⌘8 |
| [数据库工作室](db-studio.zh.md) | 连上 SQLite，在网格里改一行 | ⌥⌘7 |
| [合约工作室](contract-studio.zh.md) | 编译、部署到本地链、调用一个合约 | ⌥⌘6（Web3） |
| [基础设施设计器](infra-designer.zh.md) | 画一台 droplet 加防火墙，试运行部署 | ⌥⌘9 |
| [块工作室](block-studio.zh.md) | 用互锁的积木搭出一个 Web Component | ⌥⌘5 |
| [多语言编辑与调试](polyglot-editing-and-debugging.zh.md) | 在 Node 应用里设一个断点并让它命中 | 打开任意项目 |
| [从浏览器到源码](browser-to-source.zh.md) | 在页面里点一个元素，落到它的源码上，再从 DevTools 改它的样式 | ⌥⌘4 → DevTools → DOM |
| [Agent Port (MCP)](agent-port.zh.md) | 让 AI 代理读取 IDE 的实时状态 — 构造上就是只读的 | 工具 ▸ Agent Port (MCP)… |
| [第二周](the-second-week.zh.md) | 提交、审阅 diff、解决冲突、开拉取请求、追查堆栈跟踪 — 都是 git 自己的步骤，就在你工作的窗口里 | 团队 ▸ 在 Git 中使用 NMOX Studio… |
| [Docker 面板](docker-panel.zh.md) | 查看容器，并给项目做 Dockerize | Docker 标签页 |
| [任务看板与冲刺](task-board.zh.md) | 用一个签入版本库的文件跑看板：计时、一键站会、冲刺燃尽图 | ⌥⌘1 |
| [讲给一屋子人看](show-it-to-a-room.zh.md) | 在 IDE 里演示、分享、截图 — 从演示模式到将项目树复制为 Markdown | 视图 ▸ 演示模式 |
| [KVASIR](kvasir.zh.md) | 问 AI 一次运行为什么失败 | 机架 → KVASIR |
| [解释一切](explain-anything.zh.md) | 用 KVASIR 的四副面孔：运行、代码、API 响应、数据库错误 | 任何出错的地方 |
| [从 Postman 迁移](migrating-from-postman.zh.md) | 导入你的集合、HAR 捕获文件等等 — 密钥进钥匙串 | ⌥⌘8 → 导入… |
| [Image Kit (Web)](image-kit.zh.md) | 压一遍项目里的图片：更小的 JPEG、WebP 副本、诚实的报告 | 文件 ▸ 添加到项目 ▸ Image Kit (Web)… |
| [学习空间](learning-spaces.zh.md) | 开一个带实时 REPL 的引导式沙盒 | 新建学习空间… |
| [向导与套件](wizards-and-kits.zh.md) | 添加 PWA、标准文件或经典网页的脚手架 | 文件 ▸ 添加到项目 |

> **关于快捷键。**在 macOS 上，各工作室住在 `⌥⌘`（Option-Command）这一族 — `⌥⌘6`–`⌥⌘9`、`⌥⌘5`、`⌥⌘0` — 因为普通的 `⇧⌘` 组合键已被平台占用。在 Linux/Windows 上修饰键是 `Alt+`；菜单（窗口 ▸ …）无论如何都能用。

首次启动会显示三个标签页 — 欢迎、任务机架和浏览器 — 旁边停靠着项目工作室、工作台和 NPM 浏览器。其余每个窗口都只差一个快捷键，并且都列在欢迎页的“工具”栏里。
