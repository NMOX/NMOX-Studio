# 教程：学习空间

<!-- languages -->
[English](learning-spaces.md) · [Español](learning-spaces.es.md) · [Français](learning-spaces.fr.md) · [Deutsch](learning-spaces.de.md) · [Русский](learning-spaces.ru.md) · [Українська](learning-spaces.uk.md) · [Polski](learning-spaces.pl.md) · [Português (Brasil)](learning-spaces.pt.md) · [Bahasa Indonesia](learning-spaces.id.md) · [Filipino](learning-spaces.tl.md) · [Tiếng Việt](learning-spaces.vi.md) · **简体中文** · [हिन्दी](learning-spaces.hi.md) · [עברית](learning-spaces.he.md) · [العربية](learning-spaces.ar.md)
<!-- /languages -->

![新建学习空间选择器 — 在内置教程中搜索，可用性探测会事先告诉你这台机器上有没有这个空间要用的工具](../images/tabs/learning-spaces.png)

学习空间是一个自足的沙盒，用来学一门语言、一个框架或一个库：NMOX Studio 会生成示例代码、一份带着你走的教程，以及一个已经接好线的机架，里面有一个你可以直接敲字的**真正的机架内 REPL**。内置的一共有 93 个。

<!-- screenshot: a learning space open — sample code, the tutorial pane, and the REPL device with typed input -->

## 打开方式

`文件 ▸ 新建学习空间…`（启动器会列出每一个内置空间）。

## 步骤

1. **挑一个空间。**选一个 — Python、Rust、Solid、htmx、Solidity、Elm、某门系统语言的 REPL、一个 E2E/Playwright 空间，等等。选择器会先探测解释器或工具链是否可用。

2. **让它生成。**NMOX Studio 会在 `~/.nmox/learn/<slug>` 下创建这个空间：一个最小但能跑的示例，外加一份带你走一遍的教程，并指向相关的控制台或设备。

3. **在 REPL 里敲字。**接好线的机架里有一台 **REPL** 设备，它的 ENGINE 旋钮已经设成这个空间的语言（26 种引擎，每一种都预置了强制交互的参数）。输入一个表达式，按回车 — 输出会流到 REPL 的屏幕上。缺解释器？**INSTALL** 按钮会直接从机架上把它装好。

4. **跟着教程走。**一步步做下去；示例代码是真实可运行的，这个空间也归你随意改。

## 你刚学到了什么

- 学习空间是一整套项目 + 教程 + 接好线的机架，而不只是一段代码片段。
- REPL 是一个真正的交互进程，不是预录的回放。
- 你可以添加自己的：把一个 `*.json` 放进 `~/.nmox/learn-catalog.d/`，它就会加入选择器（结构见 [learning-spaces.md](../learning-spaces.md)）。

## 下一步

- 框架类空间（Astro/SvelteKit/Nuxt/Next）会指向它们在机架上的控制台（COSMOS/KINETIC/NIMBUS/NEXUS）。
