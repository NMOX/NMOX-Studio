# 快速上手：五分钟让你的项目跑起来

<!-- languages -->
[English](quickstart.md) · [Español](quickstart.es.md) · [Français](quickstart.fr.md) · [Deutsch](quickstart.de.md) · [Русский](quickstart.ru.md) · [Українська](quickstart.uk.md) · [Polski](quickstart.pl.md) · [Português (Brasil)](quickstart.pt.md) · [Bahasa Indonesia](quickstart.id.md) · [Filipino](quickstart.tl.md) · [Tiếng Việt](quickstart.vi.md) · **简体中文** · [हिन्दी](quickstart.hi.md) · [עברית](quickstart.he.md) · [العربية](quickstart.ar.md)
<!-- /languages -->

这一页带你在 NMOX Studio 里把你自己的一个项目跑起来，只讲做到这一步需要的东西。[用户指南](user-guide.zh.md)是完整的手册。如果你用 VS Code，接下来读[从 VS Code 迁移](coming-from-vscode.zh.md)。

<a id="1-install-one-minute"></a>
## 1. 安装（一分钟）

**macOS，使用 Homebrew：**

```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

对任何第三方 tap，Homebrew 都会要求你运行一次 `brew trust`。以后更新时不会再问。

**macOS、Windows、Linux，不用 Homebrew：**从[版本发布页](https://github.com/NMOX/NMOX-Studio/releases/latest)下载适合你系统的最新版本：

| 系统 | 文件 | 然后 |
|---|---|---|
| macOS | `NMOX-Studio-<version>-macos.dmg` | 把应用拖进“应用程序”文件夹。 |
| Windows | `NMOX-Studio-<version>-windows-setup.exe` | 运行安装程序。 |
| Debian、Ubuntu | `nmox-studio_<version>_amd64.deb` | `sudo apt install ./nmox-studio_<version>_amd64.deb` |
| 其他 Linux | `NMOX-Studio-<version>-linux.tar.gz` | 解压后运行 `bin/nmoxstudio`。 |

这些文件都自带 Java 运行时，所以你不需要再装别的东西。只有便携版 zip 需要机器上已经装好 Java 21 或更新的版本。

在 macOS 上，应用经过了 Apple 公证。第一次打开时，macOS 会问是否打开这个从互联网下载的应用：点击**打开**。

<a id="2-open-your-project-one-minute"></a>
## 2. 打开你的项目（一分钟）

启动 **NMOX Studio**。它会打开三个标签页：**欢迎**、**任务机架**和**浏览器**。

要打开你的项目，选择**文件 ▸ 打开文件夹…**（macOS 上是 ⌥⌘O，Windows 和 Linux 上是 Ctrl+Alt+O），然后选中项目的文件夹。你也可以在终端里这样做，就像用 `code .` 一样：

```bash
cd ~/code/my-app
nmox .
```

命令会立即返回。如果 NMOX Studio 已经在运行，文件夹会交给它；如果没有，它会启动。Homebrew、Windows 安装程序和 Linux 软件包都会把 `nmox` 放进 PATH。用 DMG 安装的，请看[把 `nmox` 放进 PATH](user-guide.zh.md#2-first-launch)。

一个文件夹只要带有 `package.json`、`Cargo.toml`、`go.mod`、`pom.xml`、`composer.json`、`pyproject.toml` 或另外 57 种项目文件之一，就算一个项目。只有普通 HTML 文件的文件夹也算。

打开项目时会发生三件事：

- 左边的**项目工作室**显示你的文件。
- 底部的状态栏显示你的 git 分支和改动了的文件数。
- **任务机架**会按项目的类型配置好。Vite 项目会得到一个 Vite 控制台，Cargo 项目会得到运行、调试和测试通道，以此类推。

<a id="3-run-it-one-minute"></a>
## 3. 运行它（一分钟）

按工具栏上的 **▶**，或者 F6。它按项目自己的工具那样运行项目：`package.json` 里的 `dev`、`start` 或 `serve` 脚本，`cargo run`，`go run`。它用的是你项目自己的包管理器：npm、pnpm 或 yarn，Bun 项目则用 bun。

在一个项目里第一次运行任何东西时，NMOX Studio 会问你是否信任这个文件夹。你还没信任的项目不会运行它自己的任何代码：没有脚本、没有构建、没有测试。对你自己的代码，点击**信任工作区**。

如果你的项目是一个开发服务器，它的地址会出现在状态栏上的 **⇄** 符号旁边，页面会在**浏览器**标签页里打开。编辑一个文件并保存，页面就会重新加载。

要停止所有正在运行的东西，按 ▶ 旁边的 **■**，或者 ⌥⌘.（Option、Command 加句点）。

如果什么都没发生，看看底部的 **Output** 标签页。它会解释这次运行为什么没能启动，例如某个工具没装，或者依赖还没安装，并提出替你解决。**工具 ▸ 环境诊断…** 列出 NMOX Studio 能用的每一个工具，并显示哪些已经装好。

<a id="4-find-anything-thirty-seconds"></a>
## 4. 找到任何东西（三十秒）

按 **⌘I**（Windows 和 Linux 上是 Ctrl+I），然后输入。快速搜索能找到文件、菜单操作、符号、机架设备、正在运行的服务器和命令，以及你的 `package.json` 脚本。按回车打开或运行结果。

按 **⌘P** 按文件名打开文件。

<a id="5-test-it-thirty-seconds"></a>
## 5. 测试它（三十秒）

按 **⌃F6**（Ctrl+F6）运行项目的测试。想在运行任何测试之前看到项目里的每一个测试，用 ⌥⌘2 打开**测试**窗口。

<a id="if-you-have-no-project-handy"></a>
## 如果手边没有项目

- **文件 ▸ 新建项目…** 用模板创建一个真正的项目（Angular、Vue、Svelte、React 配 Vite、纯 JavaScript、PHP、Phoenix 等等）。它会创建文件、配置好 git，并安装依赖。
- **文件 ▸ 新建学习空间…** 打开一个带引导的教程。列表里排第一的是*你的第一个网页*。

<a id="where-to-go-next"></a>
## 接下来去哪里

- **[任务机架](user-guide.zh.md#4-the-task-rack)**。你运行的每个工具都是机架上的一台设备，设备之间的跳线把它们串起来：例如，构建一通过就运行测试。
- **[编辑器](user-guide.zh.md#5-the-editor)**。包括 Emmet、颜色色块、Node 和 Chrome 的断点调试，以及 Angular 模板。
- **[各工作室](user-guide.zh.md#6-the-studios)**。API、数据库、合约和块工作室，以及任务看板。
- **[术语表](glossary.zh.md)**解释本产品自己的词：机架、配线、插孔、通道、指向、KVASIR。
