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
| 转到定义 | F12 | **F12** 或 ⌘B | F12 | **F12** 或 Ctrl+B |
| 查找引用 | ⇧F12 | **⇧F12** — 查找用法 | Shift+F12 | **Shift+F12** |
| 重命名符号 | F2 | **F2** 或 ⌃R | F2 | **F2** 或 Ctrl+R |
| 快速修复 | ⌘. | **⌘.** 或 ⌃↩ | Ctrl+. | **Alt+Enter** |
| 转到行 | ⌃G | **⌃G** | Ctrl+G | **Ctrl+G** |
| 后退 / 前进 | ⌃- / ⌃⇧- | **⌃- / ⌃⇧-** | Alt+← / Alt+→ | **Alt+← / Alt+→** |
| 切换行注释 | ⌘/ | **⌘/** | Ctrl+/ | **Ctrl+/** |
| 切换块注释 | ⇧⌥A | **⇧⌥A** | Shift+Alt+A（Linux 上是 Ctrl+Shift+A） | **Shift+Alt+A**（Linux 上还有 Ctrl+Shift+A） |
| 切换自动换行 | ⌥Z | **⌥Z** — 视图 ▸ 自动换行 | Alt+Z | **Alt+Z** |
| 显示建议 | ⌃Space | **⌃Space** | Ctrl+Space | **Ctrl+Space** |
| 把下一处出现加入选区 | ⌘D | **⌘D** 或 ⌘J | Ctrl+D | **Ctrl+D** 或 Ctrl+J |
| 选中所有出现 | ⇧⌘L | **⌃⇧⌘J** | Ctrl+Shift+L | **Ctrl+Alt+Shift+J** |
| 在上方 / 下方添加光标 | ⌥⌘↑ / ⌥⌘↓ | **⌥⌘↑ / ⌥⌘↓** | Ctrl+Alt+↑ / ↓ | **Alt+Shift+[ / ]** |
| 把行上移 / 下移 | ⌥↑ / ⌥↓ | **⌃⇧↑ / ⌃⇧↓** | Alt+↑ / ↓ | **Alt+Shift+↑ / ↓** |
| 向下复制行 | ⇧⌥↓ | **⌥⇧↓** | Shift+Alt+↓ | **Ctrl+Shift+↓** |
| 删除行 | ⇧⌘K | **⌘E** | Ctrl+Shift+K | **Ctrl+E** |
| 增加行缩进 | ⌘] | **⌘]** | Ctrl+] | **Alt+Shift+→** |
| 替换 | ⌥⌘F | **⌥⌘F** 或 ⌘R | Ctrl+H | **Ctrl+H** |
| 格式化文档 | ⇧⌥F | **⇧⌥F** 或 ⌃⇧F | Shift+Alt+F | **Alt+Shift+F** |
| 关闭编辑器标签页 | ⌘W | **⌘W** | Ctrl+W | **Ctrl+W** |
| 复制正在编辑的文件的路径 | ⌥⌘C | **⌥⌘C** — 编辑 ▸ 复制路径 | Shift+Alt+C | **Ctrl+Alt+C** |
| 复制它的相对路径 | ⇧⌥⌘C | 编辑 ▸ 复制相对路径（无组合键） | Ctrl+K Ctrl+Shift+Alt+C | 编辑 ▸ 复制相对路径（无组合键） |
| 问题面板 | ⇧⌘M | **⌘6** — 操作项（在这里 ⇧⌘M 切换书签） | Ctrl+Shift+M | **Ctrl+6** |
| 切换断点 | F9 | **⌘F8** | F9 | **Ctrl+F8** |
| 开始调试 | F5 | **⇧⌘F5** — 调试文件 | F5 | **Ctrl+Shift+F5** |
| 运行但不调试 | ⌃F5 | **F6** — 运行项目 | Ctrl+F5 | **F6** |
| 设置 | ⌘, | **⌘,** — NMOX Studio ▸ Settings… | Ctrl+, | 工具 ▸ 选项（无组合键） |

表中 NMOX 的每个组合键都是从发布出来的键盘映射里读出来的，不是凭记忆写的（⌘, 是 macOS 应用菜单自己的）。有几件事表格的单元格里说不下：

- **调试时 F5 已被占用。**在这里它表示*继续*（Continue），和每个 NetBeans 系的 IDE 一样，所以调试运行从 **⇧⌘F5**（Ctrl+Shift+F5）开始，用 F5 继续。
- **⌃R 在这里是重命名**，所以*切换项目*放在 ⌥⌘P 上，而不是 VS Code 的“打开最近”组合键。只要文件背后的语言支持，重命名就能用。
- **复制相对路径没有组合键。** VS Code 的 ⇧⌥⌘C 在 PC 上是 Ctrl+Alt+Shift+C，而它在每个键位配置里都是平台的 *Clear Split*；这一项在编辑菜单里，⇧⌘P 也能用 VS Code 自己的标题找到它：*File: Copy Relative Path of Active File*。
- **Windows 和 Linux 上的 Ctrl+,** 在你的编辑历史里后退，NetBeans 一直是这样；设置在 工具 ▸ 选项 下面（macOS 上是应用菜单的 **Settings…**，⌘,）。

**帮助 ▸ 键盘快捷键…** 列出你当前键盘映射里的每个 NMOX 组合键，包括这四个 VS Code 组合键，它是从正在运行的键盘映射里读的，所以不可能和按键实际做的事情走岔。

### 每个编辑组合键，都实测过

你从 VS Code 带来的手在编辑时会去按的组合键，每一个都在 macOS 上默认配置随产品发布的键盘映射里查过。VS Code 的组合键在这里空着的，现在做的就是 VS Code 做的事（写着“相同”的那几行）；已经有了 NetBeans 用户依赖的含义的，保留这个含义，那一行会告诉你 VS Code 的这个操作在哪里。

| VS Code，macOS | VS Code 做什么 | 在 NMOX Studio 里 |
|---|---|---|
| F12 | Go to Definition | **相同**：转到声明，和 ⌘B 一样 |
| ⇧F12 | Go to References | **相同**：查找用法，和 ⌃F7 一样 |
| F2 | Rename Symbol | **相同**：重命名，和 ⌃R 一样 |
| ⌘. | Quick Fix | **相同**：这一行的修复，和 ⌃↩ 显示的一样 |
| ⌥↑ / ⌥↓ | Move Line Up / Down | 上一个 / 下一个标记的出现位置；移动行是 ⌃⇧↑ / ⌃⇧↓ |
| ⇧⌥↑ / ⇧⌥↓ | Copy Line Up / Down | 相同，一直如此 |
| ⇧⌘K | Delete Line | 插入下一个匹配词（根据文件补全单词）；删除行是 ⌘E |
| ⌘L | Expand Line Selection | 选中标识符，它在默认键盘映射里保留这个组合键；展开行选择在快速搜索里，用这个名字（Expand Line Selection）就能找到，在 IntelliJ 和 Emacs 键盘映射配置里它就在 ⌘L 上，因为这两套配置把这个组合键空着 |
| ⇧⌘L | Select All Occurrences | 编辑器里的按行粘贴；选中所有出现位置是 ⌃⇧⌘J |
| ⌘/ | Toggle Line Comment | 相同，一直如此 |
| ⇧⌥A | Toggle Block Comment | **相同**：用该语言的块注释定界符把选区（或光标所在的行）包起来，再按一次就去掉；选区里已经有定界符时会被拒绝，而不是把代码弄坏 |
| ⌘] | Indent Line | **相同**：右移 |
| ⌘[ | Outdent Line | 跳到匹配的括号，一直如此；减少缩进是 ⇧Tab 或 ⌃⇧← |
| ⌥Z | Toggle Word Wrap | **相同**：视图 ▸ 自动换行。它切换的是该文件所属语言的所有编辑器的换行，而不是某一个标签页，这一选择会被保存 |
| ⌘B | Toggle Sidebar | 转到声明；⇧⌘↩ 只显示编辑器，⇧Esc 最大化你所在的窗口 |
| ⌘J | Toggle Panel | 在编辑器里添加下一处出现（和 ⌘D 一样）；Output 窗口是 ⌘4 |
| ⌘\ | Split Editor | 编辑器里的补全代码；拆分编辑器是 ⌃⇧⌘V |
| ⇧⌘T | Reopen Closed Editor | 相同，一直如此：打开最近的文件的组合键会重新打开最后关闭的文件 |
| ⌃- / ⌃⇧- | Go Back / Go Forward | **相同**：在你编辑过的位置之间后退和前进，和 ⌃← / ⌃→ 一样（macOS 通常把这两个组合键留给切换桌面） |
| ⌘G / ⇧⌘G | Find Next / Previous | 相同，一直如此 |
| ⌥⌘F | Replace | **相同**：替换，和 ⌘R 一样 |
| ⇧⌘F | Find in Files | 相同，一直如此：在项目中查找 |
| ⇧⌘O | Go to Symbol in Editor | 打开项目；文件的符号在快速搜索里：按 ⌘I，再像在 VS Code 里那样输入 `@name`（导航 ▸ 转到此文件中的符号… 会替你输入 `@`），在导航器（⌘7）里则是一棵树 |
| ⌘T | Go to Symbol in Workspace | 在编辑器里交换光标两边的两个字母；项目的符号是 ⌥⇧⌘O |
| ⌃G | Go to Line | 相同，一直如此 |
| ⌘K ⌘S | Keyboard Shortcuts | ⌘K 是插入上一个匹配词；快捷键一览在 **帮助 ▸ 键盘快捷键…** |
| ⌘, | Settings | 相同，一直如此：NMOX Studio ▸ Settings… |
| ⇧⌥F | Format Document | **相同**：格式化，和 ⌃⇧F 一样 |

标着“相同”的组合键装在每一套把它们空着的键盘映射配置里；某套配置给了其中某个组合键自己的含义，就保留那个含义：Eclipse、Emacs 和 NetBeans 5.5 配置里的 F12，除默认配置之外每套配置里的 F2，Emacs 和 NetBeans 5.5 里的 ⇧F12，Emacs 和 IntelliJ 里的 ⌃- 和 ⌃⇧-，IntelliJ 里的 ⇧⌥F。在 Windows 和 Linux 上，F12、⇧F12 和 F2 也是这样；VS Code 在那里的其他组合键不一样，上面的表格两种都列出了。

切换块注释和切换自动换行在 Windows 和 Linux 上也带着它们在 VS Code 里的组合键。自动换行在每套配置里都是 Alt+Z。切换块注释在默认、Emacs 和 IntelliJ 配置里是 Shift+Alt+A（Eclipse 和 NetBeans 5.5 配置在那里保留它们自己的 Alt+Shift+A），在 Linux 上，全部五套配置里它还多一个 Ctrl+Shift+A，也就是 VS Code 在 Linux 上的组合键。展开行选择只在 IntelliJ 配置里是 Windows 和 Linux 上的 Ctrl+L；在 Emacs 配置里，那里的 Ctrl+L 仍然是 Emacs 自己的 recenter（重新居中）。

没有块注释的语言（Python 就是一个）会在状态栏上这样说明。在 HTML、Vue 或 Svelte 文件里，`<script>` 或 `<style>` 块按它自己的语言来注释。没有选区时，这个组合键切换的是当前行；它不会去找仅仅把光标围在里面的注释，所以要去掉一段跨好几行的注释，请先选中它。

在快速搜索里，文件的符号是 **此文件中的符号** 这个类别。只输入 `@` 会从文件开头起把它们列出来；`m name`（字母 m、一个空格，再加名字）只搜索这个类别，不搜别的。


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

它会立即返回，第二个 `nmox` 会把它的文件夹交给已经在运行的 IDE。也接受列号（`src/app.ts:42:7`），编辑器会打开到那一行的行首；不存在的名字会在终端里被拒绝，而不会启动任何东西。`-r` 可以使用，`-n` 在那唯一的窗口里打开，`-a` 和 `-v` 会被按名字拒绝。

`-w`（`--wait`）会打开一个文件并一直等到你关闭它的标签页，`-d`（`--diff`）会把两个文件并排比较，所以 NMOX Studio 可以像 `code --wait` 那样，充当 git 的编辑器、difftool 和 mergetool：

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
git config --global merge.tool nmox
git config --global mergetool.nmox.cmd 'nmox -w "$MERGED"'
git config --global mergetool.nmox.trustExitCode false
```

之后 `git commit` 会在 IDE 里打开提交信息；保存并关闭标签页，git 就会继续。文件还开着时退出 IDE，也会把它交还回去，内容是已经保存的部分。`git mergetool` 也用同样的方式逐个打开有冲突的文件。VS Code 在冲突上方放 *Accept Current Change | Accept Incoming Change | Accept Both Changes*，NMOX Studio 则给两边着色，并在 `<<<<<<<` 那一行放一条警告；点边栏里的灯泡，或者把光标放在那一行用快速修复（Mac 上按 ⌘.，其他系统按 Alt+Enter），都会给出同样的三个选择，每个都是一次可撤销的编辑。保存、关闭标签页，git 就转到下一个文件。无论用不用 `git mergetool`，只要文件里有冲突标记，着色和这三个选择就都在。**团队 ▸ 在 Git 中使用 NMOX Studio…** 会先告诉你这些行现在各是什么值，再替你设好。Homebrew、Windows 安装程序（*Add "nmox" to PATH*）和 Linux 软件包都会把它放进 PATH；用 DMG 安装的，[用户指南](user-guide.zh.md#2-first-launch)给出了那一行建链接的命令。

<a id="where-each-vs-code-idea-lives"></a>
## VS Code 的每个概念住在哪儿

| 在 VS Code 里 | 在 NMOX Studio 里 |
|---|---|
| **资源管理器** | **项目工作室**（⇧⌘E）— 文件树（在文件上右键，可以“复制路径”“复制相对路径”和“在访达中显示”）、模板，以及项目的 `package.json` 编辑器。**工作台**（⌥⌘0）是母港：打开的文件、最近的文件、最近的项目，以及正在运行的一切。 |
| **命令面板** | **快速搜索**（⇧⌘P 或 ⌘I）— 操作、文件、最近的项目、机架设备、活动服务器、API 工作室的请求、符号。VS Code 自己的命令名也能用：输入 *Format Document*、*Toggle Terminal*、*Git: Commit* 或 *Open Settings*，会在 **VS Code 命令** 下面列出在这里做同一件事的操作，带着它自己的名字和组合键。 |
| **扩展** | **工具 ▸ 插件**安装和更新模块，NMOX 自己的更新也包括在内。VS Code 扩展在这里装不上，所以 **工具 ▸ 推荐的 VS Code 扩展…** 回答的是仓库的 `.vscode/extensions.json` 提出的那个问题：它推荐的每个扩展，在 NMOX Studio 里由什么来做那件事 —— 一项内置功能、一个它能打开的窗口、一台机架设备、一个语言服务器（以及这个服务器装没装），或者什么都没有 —— 它不认识的扩展会明说不认识，而不是去猜。VS Code 里很多靠扩展添加的东西，在这里是一台**机架设备** —— 你可以在 `~/.nmox/devices.d` 里用一个 JSON 文件写一台（[设备文件](device-files.md)）。 |
| **`tasks.json`** | 你仓库里的 `.vscode/tasks.json` 会被读取：在快速搜索（⇧⌘P 或 ⌘I）里输入某个任务的名字，在 *运行任务：build — make all* 上回车就会运行它 —— 或者从 **运行 ▸ 运行任务…** 显示的列表里选它；对你还没信任的项目，工作区信任会先问你，输出在 Output 窗口，用工具栏的 ■ 停止。它 `dependsOn` 的任务会先运行，`${file}` 是编辑器里打开着的文件，`${input:…}` 会在任何东西启动之前先问你。在它旁边，项目自己的脚本照写好的样子运行：工具栏的运行 / 构建 / 测试（F6、F11、⌃F6），`package.json` 脚本行上的**运行脚本**，**NPM 浏览器**，以及**任务机架**（⌘9），在那里任务就是你连起来的设备。 |
| **`launch.json`** | 你仓库里的 `.vscode/launch.json` 会被读取：在快速搜索（⇧⌘P 或 ⌘I）里输入某个配置的名字，在 *调试：Launch Program — ${workspaceFolder}/server.js* 上回车，就会对那个程序启动断点调试器 —— **调试 ▸ 开始调试…** 列出的是同样这些配置 —— 工作区信任会先问你。Node（`node`、`pwa-node`）配置在它们的 `cwd` 里调试它们的 `program`，带上它们的 `args`、它们的 `env` 和 `envFile`，由它们的 `runtimeExecutable` 和 `runtimeArgs` 来启动 —— 所以 `npm run dev`、`tsx` 或 `--experimental-strip-types` 这样的配置会照写好的样子启动 —— 而 Node 的 `"request": "attach"` 会附加到本机上的一个 `node --inspect` 进程。Python（`python`、`debugpy`）配置调试它们的 `program`，带上 `args`、`env`、`envFile` 以及它们的 `python` 指定的解释器；Chrome（`chrome`、`pwa-chrome`）配置用它们的 `webRoot` 打开它们的 `url`（或 `file`）。`"program": "${file}"` 调试的是你的编辑器显示着的文件，而指定了你的 `tasks.json` 里某个任务的 `preLaunchTask` 会先运行：任务成功之后，调试器才启动。没有 `launch.json` 时，**调试文件**（⇧⌘F5）和工具栏的调试按钮会从项目本身推断出要启动什么 —— `start` 脚本的入口、`main`、`index.js` —— 而机架设备 **INSPECTOR** 会把启动调试器作为流水线中的一步。 |
| **集成终端** | **终端**窗口（⌃\`）：第一次按下会在项目文件夹里启动一个 shell，之后再按会把它调回来。 |
| **`settings.json`** | 工具 ▸ 选项（macOS 上是 NMOX Studio ▸ Settings…）。仓库的 `.vscode/settings.json` 也会被读取：`editor.tabSize`、`editor.insertSpaces` 和 `editor.indentSize` 在你输入时设定它的缩进，`files.trimTrailingWhitespace` 和 `files.insertFinalNewline`（值为 `true` 时）在保存时生效，`files.eol` 是写文件时用的行尾，`"editor.formatOnSave": false` 让保存不去重新格式化文件，而像 `"[typescript]"` 这样的语言块会为它的语言覆盖这些设置。如果仓库里还有 `.editorconfig`，两者都有规定的地方以 `.editorconfig` 为准。 |
| **问题面板** | **操作项**（⌘6），或者点击状态栏上的 **✕ ⚠** 计数：语言服务器报告的错误和警告，以及机架上 PURITY 和 TYPEGUARD 设备的代码检查和类型检查结果。和 VS Code 一样，有些服务器只报告你打开着的文件；gopls 报告整个包。 |
| **搜索视图** (`search.useIgnoreFiles`) | **在项目中查找**（⇧⌘F）。和 VS Code 一样，它会跳过仓库的 `.gitignore` 文件和 `.git/info/exclude` 所忽略的内容，所以当 `.gitignore` 列出 `node_modules` 和 `dist/` 时，它们不会出现在结果里；在仓库之外，它按名字跳过 `node_modules`、`dist`、`build` 以及其他构建文件夹。在它的对话框中勾选**在生成的源代码中搜索**，就会连它们也一起搜索。你的全局 git 排除文件不会被读取。 |
| **大纲** | **导航器**（⌘7）。 |
| **代码片段** (`.vscode/*.code-snippets`) | 你的团队提交的片段文件会照原样读取：输入一个前缀，按 ⌃Space，片段就会以 *前缀 — 名称 (描述)* 的形式列出来，旁边是它所在的文件，并且只在它的 `scope` 指定的语言里出现。接受它会插入片段的正文，连同其中的制表位、镜像、变量（`TM_FILENAME`、`CURRENT_YEAR`、`UUID` 等等）和 `/regex/format/` 变换；Tab 在各个制表位之间移动，Enter 则继续往下走，过了最后一个就落在 `$0`。 |
| **自动保存** (`files.autoSave`) | **文件 ▸ 自动保存** 用来开关它；多久保存一次、文件失去焦点时是否也保存，在设置里“编辑器”类别的“自动保存”标签页中。这是你自己的偏好：仓库的 `files.autoSave` 不会被读取。 |
| **Markdown 预览** | Markdown 编辑器顶部的 **预览** 标签页，就在 **源代码** 旁边。 |
| **时间线**（本地历史） | 每个编辑器顶部的 **历史** 标签页：你每次保存时 IDE 留下的该文件的各个版本，每一个都可以和文件现在的样子比较，也可以恢复。 |
| **导航路径** | **视图 ▸ 显示导航路径**。 |
| **源代码管理** | 状态栏上的 git 标记（分支和改动，一次点击就到历史）以及**团队**菜单。 |
| **工作区信任** | 同样的理念，在运行仓库所选择的任何东西之前强制执行：打开一个克隆下来的项目，在你信任它之前什么都不会运行。 |
| **键盘快捷方式编辑器** | 工具 ▸ 选项 ▸ 键盘映射（macOS 上是 Settings… ▸ 键盘映射）— 修改任何组合键，或者把整套配置切换成 Eclipse、Emacs 或 IntelliJ。 |

第一次打开带有 `.vscode/tasks.json`、`launch.json`、`settings.json` 或 `extensions.json` 的仓库时，会有一条通知说明找到了什么、它在哪儿；点击它就会打开快速搜索（如果是扩展，打开的则是说明每个扩展由什么来承担的那张表）。每个项目只提示一次。

<a id="what-is-honestly-different"></a>
## 如实说明哪些地方不一样

- **⌘D 在默认键盘映射里会添加下一处出现，但不是每套配置都这样。**Eclipse 配置把 ⌘D 保留为 Eclipse 的*删除行*，NetBeans 5.5 配置保留为*行左移*，Emacs 配置保留为*删除单词*（在 Windows 和 Linux 上把 Ctrl+D 保留为*删除字符*）；IntelliJ 配置在 macOS 上有 ⌘D，在 Windows 和 Linux 上把 Ctrl+D 保留为*复制行*。这个操作的另一个组合键也因配置而异：默认配置里是 ⌘J（Ctrl+J），Eclipse 和 IntelliJ 里是 ⌃J（Alt+J），Emacs 和 NetBeans 5.5 里没有，可以在键盘映射里给它设一个。
- **⌃\` 会打开终端并让它获得焦点；它不会把终端隐藏起来。**而且当终端有焦点时，按键属于你的 shell，所以第二次按下会传给 shell，而不是把你带回编辑器。
- **`launch.json` 会被读取，调试器无法照办的部分会被拒绝。**这里的调试器传递程序、它的工作文件夹、它的 `args`（一个字符串列表）、它的 `env` 和 `envFile`，以及启动它的运行时（Node 用 `runtimeExecutable` 和 `runtimeArgs`，Python 用 `python`）；对 Node，它还能附加到一个已经在运行的进程。设置了 `postDebugTask`、`restart` 或任何它还没学会的字段的配置，会被列出来但不会启动：回车会在状态栏上说出这些字段的名字。不带上它们就启动程序，调试的就不是文件里写的那个东西了。写成一个字符串的 `args` 或 `runtimeArgs`（VS Code 会把它交给 shell）和值为 `null` 的 `env`（它会删除一个变量）也会以同样的方式被拒绝；`compounds` 条目、这里没有适配器的类型（`go`、`msedge`、`cppdbg` 等等）、对 Node 以外任何东西的附加、只有 VS Code 才能提供的值（`${input:…}`、`${command:…}`），以及在项目之外的程序、工作文件夹或 `envFile` 也是一样。只影响调试器显示内容的字段 —— `skipFiles`、`outFiles`、`sourceMaps`、`console`、`justMyCode`、`presentation` —— 会被接受，但不会应用；程序的输出进入 Output 窗口。按回车之前，有五件事值得知道：
  - **`preLaunchTask` 会先运行，而且必须成功。**这个任务的运行方式，和在快速搜索里对它按回车一样 —— 它 `dependsOn` 的任务、它的提问、它自己的 Output 标签页 —— 等它以退出码 0 结束，调试器才启动；任务失败之后，VS Code 会问 *Debug Anyway?*，而这里状态栏会说该配置没有启动。由这个任务构建出来的 `program`（`dist/server.js`）是在任务之后去找的，而不是之前。`tasks.json` 没有定义的标签、两个任务共用的标签、对象形式（`{"type": "npm", "script": "build"}`）以及后台任务（`"isBackground": true`，一个永不结束的监视任务）会在任何东西运行之前被按名字拒绝。
  - **`envFile` 不存在就会被拒绝**，而 VS Code 会不带它照样启动程序。它的变量会加进环境里，`env` 条目优先于这个文件，和 VS Code 一样。这个文件按普通的 `NAME=value` 行来读（注释行、`export`、值两边的一对引号都没问题）；VS Code 会读出不同结果的行 —— 双引号里的转义、值后面的 `#`、反引号，以及 Python 配置里的 `export` 或 `${NAME}` —— 会被拒绝，并说出文件和行号，但绝不说出值。
  - **`${file}`、`${relativeFile}`、`${fileBasename}`、`${fileBasenameNoExtension}`、`${fileExtname}`、`${fileDirname}` 以及其他文件变量，指的都是活动编辑器的文件**：有焦点的标签页是编辑器时就是它，否则就是编辑器区域里正显示着的那个标签页 —— 和任务的 `${file}` 指的是同一个文件。没有打开文件时，配置会被拒绝并说出是哪个变量；项目之外的 `${file}` 会像那里的任何其他程序一样被拒绝。
  - **`runtimeExecutable` 是一个名字或一个绝对路径。**名字（`npm`、`tsx`、`nodemon`）会先在你的 PATH 里找，再到项目的 `node_modules/.bin` 里找；`${workspaceFolder}/node_modules/.bin/tsx` 必须确实在那里；相对路径会被拒绝，因为 VS Code 会把它当作名字去找。当运行时就是整条命令时（`npm run dev`，没有 `program`），它启动的脚本会作为一个独立的会话来调试，列在 Sessions 窗口里。
  - **附加只针对本机。**`port`（没写就是 9229），以及值为 `localhost`、`127.0.0.1` 或 `::1` 的 `address`；其他任何地址都会被拒绝，因为这不是远程开发。如果那里没有任何程序在监听，状态栏会这样说明；结束会话之后，你的程序会继续运行。
- **`tasks.json` 会被读取，无法照写法运行的会被拒绝。**任务运行时，它 `dependsOn` 的任务会先运行（一起运行，或者在 `"dependsOrder": "sequence"` 时一个接一个地运行），`${file}`、`${relativeFile}`、`${lineNumber}`、`${selectedText}` 以及这一族的其他变量由编辑器里打开着的文件来填，它的 `${input:…}` 提问（`promptString`、`pickString`）会在任何东西启动之前问完。整次运行会先定下来：只要其中有一个任务无法照写法运行，就什么都不运行，回车会在状态栏上说出是哪个任务、为什么。这包括文件没有定义的 `dependsOn` 标签、本身是后台任务的依赖（问题匹配器不会被读取，所以没有什么能说明它何时就绪）、没有打开文件时的 `${file}`，以及被你取消的提问。某个依赖失败，运行就停在那里。仍然会被按名字拒绝的有：只有 VS Code 才能提供的值（`${config:…}`、`${command:…}`、`"type": "command"` 的输入项）、写成对象（`{"type": "npm", …}`）而不是标签的依赖、扩展提供的任务类型（`gulp`、`typescript`），以及项目之外的工作文件夹。
- **代码片段用的是你仓库里的，有三处不同。**选择占位符（`${1|a,b|}`）一开始就取它的第一个选项，没有可供挑选的列表；嵌在另一个占位符的默认值里的占位符，会成为那个默认值的一部分；变换无法安全执行的片段（Java 编译不了的正则表达式，或者可能跑不完的正则表达式）会被略去并在日志里点名，而不是只插入一半。输入前缀本身不会打开列表：要按 ⌃Space。没有 `prefix` 的片段、用户级片段和 `isFileTemplate` 不会被读取。
- **`"type": "shell"` 任务在 VS Code 会使用的那个 shell 里运行。**在 macOS 和 Linux 上，那是你的 `$SHELL` 加上 `-c`（macOS 上的 zsh、bash 或 fish 会作为登录 shell 启动，即 `-l`，和 VS Code 的默认配置一样）；在 Windows 上是 PowerShell，装了 `pwsh` 就用 `pwsh`。`options.shell` 按 VS Code 的方式处理：指定一个 `executable`，它就恰好带着你给的 `args` 运行，所以 bash 需要 `"args": ["-c"]`。在 Windows 上只运行 PowerShell（`args` 以 `-Command` 结尾）和 `cmd.exe`（`args` 以 `/c` 结尾）；那里的其他任何 shell 都会被按名字拒绝，而不是交给它一条靠猜测加引号的命令行。
- **没有“VS Code”键盘映射配置。**上面这些组合键同时挂在默认配置和其他四套配置上。有一个例外是有意的：在 **Eclipse** 配置里，⇧⌘E 仍然是 Eclipse 自己的*切换到编辑器*，而在编辑器内部，⇧⌘P 和 ⇧⌘X 保持 Eclipse 的含义（匹配括号、转为大写）—— 选了 Eclipse 的人期待的就是 Eclipse。
- **在 Linux 上，Ctrl+\` 打开的是终端，不是窗口切换器。**切换器在 Ctrl+Tab 上。在自己占用 Ctrl+Tab 的桌面上（例如 KDE），**窗口 ▸ 文档…**会列出打开的文件。
- **Ctrl+Alt 组合键可能和 AltGr 冲突。**在 Windows 上，用 AltGr 输入字符的键盘布局（例如波兰语布局）会为它发送 Ctrl+Alt。如果 Ctrl+Alt+P 或 Ctrl+Alt+K 替你打出了一个字符，就在键盘映射里把*切换项目*或实验的组合键挪走。
- **VS Code 扩展在这里装不上。**语言智能来自 NMOX 认识的语言服务器（环境诊断会列出缺什么、怎么装）、编辑器自己的语法，以及为 NetBeans 平台构建的插件。
