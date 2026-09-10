# NMOX Studio — 用户指南

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · [Français](user-guide.fr.md) · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · [Tiếng Việt](user-guide.vi.md) · **简体中文** · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> 部分翻译：第 1–3 章为中文。其余内容请见[完整的英文指南](user-guide.md)。

如何使用本产品。本指南按你会遇到的顺序介绍功能：安装、首次启动、项目、机架、各工作室、向导以及各种安全网。

---

<a id="1-install"></a>
## 1. 安装

**macOS（推荐）：**
```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

`brew trust` 这一行是 Homebrew 对任何第三方 tap 的一次性确认：以后更新不会再问。应用做了 ad-hoc 签名但未做公证，因此被隔离的副本会在首次启动时被 Gatekeeper 拒绝：cask 会在 `postflight` 步骤中自行移除隔离属性，并在安装输出里说明这一点。没有任何动作是悄悄发生的。

**其他平台：**从[最新版本](https://github.com/NMOX/NMOX-Studio/releases/latest)下载文件 — macOS 用 `.dmg`，Windows 用 `-setup.exe`，Debian/Ubuntu 用 `.deb`，Linux 通用则用 `.tar.gz`。这四种都自带 Java 运行时，无需事先安装任何东西。`-portable.zip` 是唯一使用你自己的 Java 的构件（需要 PATH 中有 Java 21+，或用 `--jdkhome <jdk-路径>` 启动）。

> **macOS 首次启动：**应用做了 ad-hoc 签名但未做公证，所以 Gatekeeper 会在运行前询问。第一次请**右键点击应用 → 打开**并确认，或执行
> `xattr -d com.apple.quarantine "/Applications/NMOX Studio.app"`。两种做法都一劳永逸。

### 更新

IDE 会自我更新：**工具 ▸ 插件 ▸ 更新**会提供任何更新版本的模块。安装、按提示重启即可，无需重新下载整个应用。一点实话：自带的 Java 运行时和启动器只随完整安装程序更换，所以遇到较大的平台跨越，从版本文件重新安装仍然是正确做法。

<a id="2-first-launch"></a>
## 2. 首次启动

在终端里，`nmoxstudio --open <文件夹>` 会启动应用，把该文件夹作为项目打开并让机架指向它 — 与欢迎页上“打开文件夹…”所开的是同一扇门。

IDE 启动时会在编辑区旁打开整套标签页：**欢迎 → 任务机架 → 数据库工作室 → 合约工作室 → 基础设施设计器 → API 工作室 → Docker 面板** — 从第一分钟起，每个主要界面都只需一次点击。左侧停靠栏中有：**项目工作室**（文件树与模板）、作为基地的 **工作台**，以及 **NPM 浏览器**。系统会创建 `~/NMOX` 文件夹作为默认工作区；在你打开项目之前，机架都指向那里。

![首次启动 — 打开了所有标签页的欢迎页](images/welcome.png)

值得第一天就记住的快捷键（欢迎标签页上也全部列出）：

| 快捷键 | 打开 |
|---|---|
| **⌘I** | 快速搜索 — 什么都能找到 |
| **⌘9** | 任务机架 |
| **⌥⌘0** | 工作台 |
| **⌥⌘3** | IRC 聊天客户端 |
| **⌥⌘4** | 浏览器（内置 WebKit，带 DevTools） |
| **⌥⌘5** | 块工作室 |
| **⌥⌘6** | 合约工作室 |
| **⌥⌘7** | 数据库工作室 |
| **⌥⌘8** | API 工作室 |
| **⌥⌘9** | 基础设施设计器 |
| **⌘8** | Docker 面板 |
| **⌘7** | 当前文件的结构 |
| **⇧⌘N / ⌥⌘O** | 新建项目… / 打开文件夹… |
| **⇧⌘E / ⇧⌘L** | 新建实验… / 新建学习空间… |

<a id="3-projects"></a>
## 3. 项目

**打开：**任何带有 60 种可识别清单之一的文件夹都会作为真正的项目打开 — `package.json`、`Cargo.toml`、`go.mod`、`pom.xml`、`composer.json`、`foundry.toml`、`bower.json`、`Gruntfile.js` 等等 — 也包括各合约链自己的清单：一个 Aiken（`aiken.toml`）或 Clarinet（`Clarinet.toml`）仓库打开时，它真正的流程通道已经接好。一个只有 HTML 和 `<script>` 标签、**没有**任何清单的普通文件夹同样能打开，作为 STATIC 项目：经典网页在这里是一等公民，而不是一个错误。

**创建：***新建项目…* 提供真正可用的脚手架 — Angular、Vue、Svelte、原生 JavaScript、Elixir/Phoenix、PHP Web（LEMP）以及经典网页（jQuery）。每一个都自带接好的检查、格式化与测试配置，并已初始化 git 仓库：一次脚手架提交；当向导替你执行安装时，这次提交也包含锁文件，因此你的第一次 `git status` 是干净的。

**切换项目是安全的：**如果有设备正在运行（开发服务器、监视器），IDE 会在切换前询问并干净地把它们停掉。绝不会有任何东西在你背后继续运行。即使强制退出 IDE，也不会留下孤儿进程。

**实验**是尝试一套技术栈最快的方式。**文件 ▸ 新建实验…**（⇧⌘E）挑选一个模板，在 `~/.nmox/experiments` 下生成一个用完即弃的项目：没有 git、不进入最近列表、已经受信任、依赖已安装 — 这样**第一次运行就能成功**。它会打开自己的 `EXPERIMENT.md` 导览，告诉你该按什么、该改哪个文件，以及这套技术栈的 IDE 智能功能在哪里。留下有价值的：**文件 ▸ 实验…** ▸ **升级**把它移出并初始化 git，**复制**在旁边生成一份副本以尝试第二种做法，**丢弃**清理其余的。架子上显示每一个的存在时长和实测占用的磁盘空间。更想要带引导的路径？对话框会把 93 个学习空间放在前面。

![学习空间架 — 数量、磁盘占用、存在时长以及完整的生命周期](images/spaces-shelf.png)

![一个刚建好的 Express 实验：导览已打开，依赖已安装，API 已在提供服务](images/experiment-walkthrough.png)

**运行、构建、测试 — 以及停止：**工具栏的 ▶（F6）会按项目自己的工具链方式运行它：package.json 中若有 `start` 脚本就用它，否则 `cargo run`、`go run`、`dotnet run`；对于只有 HTML 的文件夹，则在从 8080 起的第一个空闲端口上启动一个小型静态服务器。构建、测试和清理就在旁边，也在“运行”菜单里。宣告了自己地址的开发服务器会点亮状态栏上的 ⇄ 标记，并在内置浏览器中打开该页面。所有这些第一次都要先通过工作区信任确认。无法启动的运行会如实说明，并提供打开环境体检的入口。要停止：调试右侧的 ■（⌥⌘.）会一次停掉所有正在运行的命令，并说明停掉了什么；**运行 ▸ 停止**只停一个，随后提供**重复**。■ 能看到产品替你启动的一切，包括各种安装；把光标停在上面，提示会准确说出按下去会停掉什么，以及每一项已经运行了多久。

**到处可用的 `.env`：**如果你的项目里有 `.env`，从机架启动的设备就会拿到这些变量。编辑它之后，状态栏会说明重启才会生效 — 正在运行的进程会诚实地保留它们原有的环境。
