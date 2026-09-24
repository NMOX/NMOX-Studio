# 从 VS Code 迁移

<!-- languages -->
[English](coming-from-vscode.md) · [Español](coming-from-vscode.es.md) · [Français](coming-from-vscode.fr.md) · [Deutsch](coming-from-vscode.de.md) · [Русский](coming-from-vscode.ru.md) · [Українська](coming-from-vscode.uk.md) · [Polski](coming-from-vscode.pl.md) · [Português (Brasil)](coming-from-vscode.pt.md) · [Bahasa Indonesia](coming-from-vscode.id.md) · [Filipino](coming-from-vscode.tl.md) · [Tiếng Việt](coming-from-vscode.vi.md) · **简体中文** · [हिन्दी](coming-from-vscode.hi.md) · [עברית](coming-from-vscode.he.md) · [العربية](coming-from-vscode.ar.md)
<!-- /languages -->

你的手已经知道东西在哪里。这一页把这些习惯对应到 NMOX Studio：先讲组合键，再讲 VS Code 的每个概念在这里住在哪儿，最后如实说明哪些地方不一样。

VS Code 用户最先按下的四个组合键，行为和他们期待的一样：**⇧⌘P** 打开命令面板，**⇧⌘E** 打开文件树，**⇧⌘X** 打开插件，**⌃\`** 打开终端。它们在平台自带的全部五套键盘映射配置里都注册过，并且有一道构建闸门在 macOS、Windows 和 Linux 上通过组装好的键盘映射逐一解析它们，确保不会有别的东西抢先触发。

<a id="the-chords"></a>
## 组合键

macOS 各列使用菜单栏上的符号（⌃ Control、⌥ Option、⇧ Shift、⌘ Command）；Windows 和 Linux 各列是 PC 键盘上的同一个组合键。

| 你想要 | VS Code，macOS | NMOX，macOS | VS Code，Win/Linux | NMOX，Win/Linux |
|---|---|---|---|---|
| 命令面板 | ⇧⌘P | **⇧⌘P**（或 ⌘I）— 快速搜索 | Ctrl+Shift+P | **Ctrl+Shift+P**（或 Ctrl+I） |
| 按名字打开文件 | ⌘P | **⌘P** — 转到文件 | Ctrl+P | **Ctrl+P** |
| 文件树 | ⇧⌘E | **⇧⌘E** — 项目工作室 | Ctrl+Shift+E | **Ctrl+Shift+E** |
| 扩展 | ⇧⌘X | **⇧⌘X** — 工具 ▸ 插件 | Ctrl+Shift+X | **Ctrl+Shift+X** |
| 终端，在项目文件夹里 | ⌃\` | **⌃\`** | Ctrl+\` | **Ctrl+\`** |
| 打开最近的项目 | ⌃R | **⌥⌘P** — 切换项目… | Ctrl+R | **Ctrl+Alt+P** |
| 转到项目中的符号 | ⌘T | **⌥⇧⌘O** | Ctrl+T | **Ctrl+Alt+Shift+O** |
| 转到定义 | F12 | **⌘B** | F12 | **Ctrl+B** |
| 重命名符号 | F2 | **⌃R** | F2 | **Ctrl+R** |
| 转到行 | ⌃G | **⌃G** | Ctrl+G | **Ctrl+G** |
| 切换行注释 | ⌘/ | **⌘/** | Ctrl+/ | **Ctrl+/** |
| 显示建议 | ⌃Space | **⌃Space** | Ctrl+Space | **Ctrl+Space** |
| 把下一处出现加入选区 | ⌘D | **⌘D** 或 ⌘J | Ctrl+D | **Ctrl+D** 或 Ctrl+J |
| 选中所有出现 | ⇧⌘L | **⌃⇧⌘J** | Ctrl+Shift+L | **Ctrl+Alt+Shift+J** |
| 在上方 / 下方添加光标 | ⌥⌘↑ / ⌥⌘↓ | **⌥⌘↑ / ⌥⌘↓** | Ctrl+Alt+↑ / ↓ | **Alt+Shift+[ / ]** |
| 把行上移 / 下移 | ⌥↑ / ⌥↓ | **⌃⇧↑ / ⌃⇧↓** | Alt+↑ / ↓ | **Alt+Shift+↑ / ↓** |
| 向下复制行 | ⇧⌥↓ | **⌥⇧↓** | Shift+Alt+↓ | **Ctrl+Shift+↓** |
| 删除行 | ⇧⌘K | **⌘E** | Ctrl+Shift+K | **Ctrl+E** |
| 格式化文档 | ⇧⌥F | **⌃⇧F** | Shift+Alt+F | **Alt+Shift+F** |
| 关闭编辑器标签页 | ⌘W | **⌘W** | Ctrl+W | **Ctrl+W** |
| 问题面板 | ⇧⌘M | **⌘6** — 操作项（在这里 ⇧⌘M 切换书签） | Ctrl+Shift+M | **Ctrl+6** |
| 切换断点 | F9 | **⌘F8** | F9 | **Ctrl+F8** |
| 开始调试 | F5 | **⇧⌘F5** — 调试文件 | F5 | **Ctrl+Shift+F5** |
| 运行但不调试 | ⌃F5 | **F6** — 运行项目 | Ctrl+F5 | **F6** |
| 设置 | ⌘, | **⌘,** — NMOX Studio ▸ Settings… | Ctrl+, | 工具 ▸ 选项（无组合键） |

表中 NMOX 的每个组合键都是从发布出来的键盘映射里读出来的，不是凭记忆写的（⌘, 是 macOS 应用菜单自己的）。有几件事表格的单元格里说不下：

- **调试时 F5 已被占用。**在这里它表示*继续*（Continue），和每个 NetBeans 系的 IDE 一样，所以调试运行从 **⇧⌘F5**（Ctrl+Shift+F5）开始，用 F5 继续。
- **⌃R 在这里是重命名**，所以*切换项目*放在 ⌥⌘P 上，而不是 VS Code 的“打开最近”组合键。只要文件背后的语言支持，重命名就能用。
- **Windows 和 Linux 上的 Ctrl+,** 在你的编辑历史里后退，NetBeans 一直是这样；设置在 工具 ▸ 选项 下面（macOS 上是应用菜单的 **Settings…**，⌘,）。

**帮助 ▸ 键盘快捷键…** 列出你当前键盘映射里的每个 NMOX 组合键，包括这四个 VS Code 组合键，它是从正在运行的键盘映射里读的，所以不可能和按键实际做的事情走岔。


<a id="from-the-terminal"></a>
## 从终端

`code .` 就是 `nmox .`：

```bash
cd ~/code/my-app
nmox .          # open this folder (manifest or not) and aim the IDE at it
nmox src/app.ts # open one file
nmox src/app.ts:42  # open it at line 42 (code -g's form; -g itself is accepted)
nmox            # just start the IDE
```

它会立即返回，第二个 `nmox` 会把它的文件夹交给已经在运行的 IDE。也接受列号（`src/app.ts:42:7`），编辑器会打开到那一行的行首；不存在的名字会在终端里被拒绝，而不会启动任何东西。Homebrew、Windows 安装程序（*Add "nmox" to PATH*）和 Linux 软件包都会把它放进 PATH；用 DMG 安装的，[用户指南](user-guide.zh.md#2-first-launch)给出了那一行建链接的命令。

<a id="where-each-vs-code-idea-lives"></a>
## VS Code 的每个概念住在哪儿

| 在 VS Code 里 | 在 NMOX Studio 里 |
|---|---|
| **资源管理器** | **项目工作室**（⇧⌘E）— 文件树、模板，以及项目的 `package.json` 编辑器。**工作台**（⌥⌘0）是母港：打开的文件、最近的文件、最近的项目，以及正在运行的一切。 |
| **命令面板** | **快速搜索**（⇧⌘P 或 ⌘I）— 操作、文件、最近的项目、机架设备、活动服务器、API 工作室的请求、符号。VS Code 自己的命令名也能用：输入 *Format Document*、*Toggle Terminal*、*Git: Commit* 或 *Open Settings*，会在 **VS Code 命令** 下面列出在这里做同一件事的操作，带着它自己的名字和组合键。 |
| **扩展** | **工具 ▸ 插件**安装和更新模块，NMOX 自己的更新也包括在内。VS Code 里很多靠扩展添加的东西，在这里是一台**机架设备** —— 你可以在 `~/.nmox/devices.d` 里用一个 JSON 文件写一台（[设备文件](device-files.md)）。 |
| **`tasks.json`** | 你仓库里的 `.vscode/tasks.json` 会被读取：在快速搜索（⇧⌘P 或 ⌘I）里输入某个任务的名字，在 *运行任务：build — make all* 上回车就会运行它；对你还没信任的项目，工作区信任会先问你，输出在 Output 窗口，用工具栏的 ■ 停止。在它旁边，项目自己的脚本照写好的样子运行：工具栏的运行 / 构建 / 测试（F6、F11、⌃F6），`package.json` 脚本行上的**运行脚本**，**NPM 浏览器**，以及**任务机架**（⌘9），在那里任务就是你连起来的设备。 |
| **`launch.json`** | 你仓库里的 `.vscode/launch.json` 会被读取：在快速搜索（⇧⌘P 或 ⌘I）里输入某个配置的名字，在 *调试：Launch Program — ${workspaceFolder}/server.js* 上回车，就会对那个程序启动断点调试器，工作区信任会先问你。Node（`node`、`pwa-node`）和 Python（`python`、`debugpy`）配置在它们的 `cwd` 里调试它们的 `program`，带上它们的 `args` 和 `env`；Chrome（`chrome`、`pwa-chrome`）配置用它们的 `webRoot` 打开它们的 `url`（或 `file`）。没有 `launch.json` 时，**调试文件**（⇧⌘F5）和工具栏的调试按钮会从项目本身推断出要启动什么 —— `start` 脚本的入口、`main`、`index.js` —— 而机架设备 **INSPECTOR** 会把启动调试器作为流水线中的一步。 |
| **集成终端** | **终端**窗口（⌃\`）：第一次按下会在项目文件夹里启动一个 shell，之后再按会把它调回来。 |
| **`settings.json`** | 工具 ▸ 选项（macOS 上是 NMOX Studio ▸ Settings…）。仓库的 `.vscode/settings.json` 也会被读取：`editor.tabSize`、`editor.insertSpaces` 和 `editor.indentSize` 在你输入时设定它的缩进，`files.trimTrailingWhitespace` 和 `files.insertFinalNewline`（值为 `true` 时）在保存时生效，而像 `"[typescript]"` 这样的语言块会为它的语言覆盖这些设置。如果仓库里还有 `.editorconfig`，两者都有规定的地方以 `.editorconfig` 为准。 |
| **问题面板** | **操作项**（⌘6），或者点击状态栏上的 **✕ ⚠** 计数：语言服务器报告的错误和警告，以及机架上 PURITY 和 TYPEGUARD 设备的代码检查和类型检查结果。和 VS Code 一样，有些服务器只报告你打开着的文件；gopls 报告整个包。 |
| **大纲** | **导航器**（⌘7）。 |
| **源代码管理** | 状态栏上的 git 标记（分支和改动，一次点击就到历史）以及**团队**菜单。 |
| **工作区信任** | 同样的理念，在运行仓库所选择的任何东西之前强制执行：打开一个克隆下来的项目，在你信任它之前什么都不会运行。 |
| **键盘快捷方式编辑器** | 工具 ▸ 选项 ▸ 键盘映射（macOS 上是 Settings… ▸ 键盘映射）— 修改任何组合键，或者把整套配置切换成 Eclipse、Emacs 或 IntelliJ。 |

第一次打开带有 `.vscode/tasks.json`、`launch.json` 或 `settings.json` 的仓库时，会有一条通知说明找到了什么、它在哪儿；点击它就会打开快速搜索。每个项目只提示一次。

<a id="what-is-honestly-different"></a>
## 如实说明哪些地方不一样

- **⌘D 在默认键盘映射里会添加下一处出现，但不是每套配置都这样。**Eclipse 配置把 ⌘D 保留为 Eclipse 的*删除行*，NetBeans 5.5 配置则保留为*行左移*；在那两套配置里，同样的操作是 ⌘J（Ctrl+J）。
- **⌃\` 会打开终端并让它获得焦点；它不会把终端隐藏起来。**而且当终端有焦点时，按键属于你的 shell，所以第二次按下会传给 shell，而不是把你带回编辑器。
- **`launch.json` 会被读取，调试器无法照办的部分会被拒绝。**这里的调试器传递程序、它的工作文件夹、它的 `args`（一个字符串列表）和它的 `env`（加进继承来的环境里的字符串），所以设置了 `envFile`、`runtimeExecutable`、`runtimeArgs`、`preLaunchTask` 或任何它还没学会的字段的配置，会被列出来但不会启动：回车会在状态栏上说出这些字段的名字。不带上它们就启动程序，调试的就不是文件里写的那个东西了。写成一个字符串的 `args`（VS Code 会把它交给 shell）和值为 `null` 的 `env`（它会删除一个变量）也会以同样的方式被拒绝；`"request": "attach"`、`compounds` 条目、这里没有适配器的类型（`go`、`msedge`、`cppdbg` 等等）、只有 VS Code 才能提供的值（`${file}`、`${input:…}`），以及项目之外的路径也是一样。只影响调试器显示内容的字段 —— `skipFiles`、`outFiles`、`sourceMaps`、`console`、`justMyCode`、`presentation` —— 会被接受，但不会应用；程序的输出进入 Output 窗口。
- **`tasks.json` 会被读取，无法照写法运行的会被拒绝。**使用了只有 VS Code 才能提供的值（`${input:…}`、`${file}`、`${config:…}`、`${command:…}`）或者 `dependsOn` 另一个任务的任务，会被列出来但不会运行：回车会在状态栏上说出是哪个变量或哪个任务。把那个值留空去运行，或者不带它所依赖的任务去运行，运行的就不是文件里写的那个东西了。扩展提供的任务类型（`gulp`、`typescript`）和项目之外的工作文件夹也是如此。
- **`"type": "shell"` 任务在 VS Code 会使用的那个 shell 里运行。**在 macOS 和 Linux 上，那是你的 `$SHELL` 加上 `-c`（macOS 上的 zsh、bash 或 fish 会作为登录 shell 启动，即 `-l`，和 VS Code 的默认配置一样）；在 Windows 上是 PowerShell，装了 `pwsh` 就用 `pwsh`。`options.shell` 按 VS Code 的方式处理：指定一个 `executable`，它就恰好带着你给的 `args` 运行，所以 bash 需要 `"args": ["-c"]`。在 Windows 上只运行 PowerShell（`args` 以 `-Command` 结尾）和 `cmd.exe`（`args` 以 `/c` 结尾）；那里的其他任何 shell 都会被按名字拒绝，而不是交给它一条靠猜测加引号的命令行。
- **没有“VS Code”键盘映射配置。**上面这些组合键同时挂在默认配置和其他四套配置上。有一个例外是有意的：在 **Eclipse** 配置里，⇧⌘E 仍然是 Eclipse 自己的*切换到编辑器*，而在编辑器内部，⇧⌘P 和 ⇧⌘X 保持 Eclipse 的含义（匹配括号、转为大写）—— 选了 Eclipse 的人期待的就是 Eclipse。
- **在 Linux 上，Ctrl+\` 打开的是终端，不是窗口切换器。**切换器在 Ctrl+Tab 上。在自己占用 Ctrl+Tab 的桌面上（例如 KDE），**窗口 ▸ 文档…**会列出打开的文件。
- **Ctrl+Alt 组合键可能和 AltGr 冲突。**在 Windows 上，用 AltGr 输入字符的键盘布局（例如波兰语布局）会为它发送 Ctrl+Alt。如果 Ctrl+Alt+P 或 Ctrl+Alt+K 替你打出了一个字符，就在键盘映射里把*切换项目*或实验的组合键挪走。
- **VS Code 扩展在这里装不上。**语言智能来自 NMOX 认识的语言服务器（环境诊断会列出缺什么、怎么装）、编辑器自己的语法，以及为 NetBeans 平台构建的插件。
