# 第二周

<!-- languages -->
[English](the-second-week.md) · [Español](the-second-week.es.md) · [Français](the-second-week.fr.md) · [Deutsch](the-second-week.de.md) · [Русский](the-second-week.ru.md) · [Українська](the-second-week.uk.md) · [Polski](the-second-week.pl.md) · [Português (Brasil)](the-second-week.pt.md) · [Bahasa Indonesia](the-second-week.id.md) · [Filipino](the-second-week.tl.md) · [Tiếng Việt](the-second-week.vi.md) · **简体中文** · [हिन्दी](the-second-week.hi.md) · [עברית](the-second-week.he.md) · [العربية](the-second-week.ar.md)
<!-- /languages -->

*提交、审阅、解决、提议 — 不用离开去别的工具。*

第一个小时是打开一个项目并运行它。第二周则是代码周围的一切：一天二十次提交，每次提交前要读的 diff，pull 之后的冲突，push 之后的拉取请求，要追查的堆栈跟踪，要保持诚实的 README。这篇演练是在一个你已经有的 git 仓库里坐下来一次走完，每一步都是你明天还会再做的事。

## 1. 让 NMOX Studio 成为 git 的编辑器

**操作：**团队 ▸ **在 Git 中使用 NMOX Studio…**

**结果：**让 NMOX Studio 成为 git 的编辑器、difftool 和 mergetool 的六项全局 git 设置，每一项旁边都是它**现在**的值，所以不会有任何东西在你看不见的情况下被替换。**应用**会设置它们（**关闭**是默认按钮，因为这会写入你的全局 git 配置）；**复制命令**则改为把 `git config` 这几行放到剪贴板上。如果 git 已经在使用 NMOX Studio，对话框会这样说，并且不提供“应用”。

如果你更喜欢终端，下面是同样的几行：

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
git config --global merge.tool nmox
git config --global mergetool.nmox.cmd 'nmox -w "$MERGED"'
git config --global mergetool.nmox.trustExitCode false
```

## 2. 提交

**操作：**修改一个文件，然后在终端里：

```bash
git commit -a
```

**结果：**提交信息在 NMOX Studio 中打开，状态栏会说有一个终端正在等它。git 的 `#` 行是注释；只有你写的内容会做拼写检查；摘要行超过 72 个字符（git 自己的工具会在那里截断）时，从第 73 个字符起会收到警告。保存、关闭标签页，提交就完成了 — 终端一直在等你这样做。如果在信息仍然打开时退出 IDE，它同样会交还给 git，内容是已保存的部分。

`git rebase -i` 以同样的方式打开它的列表：每条命令和每个提交都会高亮，**切换注释**可以去掉一行而不删除它。

## 3. 知道自己在哪儿

**结果：**状态栏上的 **⎇ 标记** — `⎇ main ±3 ↑2 ↓1` 表示你的分支、三个已更改的文件、两个待推送的提交和一个待拉取的提交（只有在有东西要推送或拉取时才会出现箭头）。它的菜单以**切换分支…**、**提交…**、**拉取…**和**推送…**开头。

**操作：**把光标放在一个受跟踪文件的任意一行上。

**结果：**标记旁边会显示谁最后修改了这一行、多久以前、为什么：`Ada Lovelace，3 天前 · Fix the parser`。你还没有提交的行会这样说明，有未保存更改的文件会说明这一点，而不是报出错误的作者。点击这条提示可以看到整个文件的逐行作者；**视图 ▸ 行作者**可以把它关掉。

## 4. 审阅一个 diff

**操作：**

```bash
git difftool
```

**结果：**每个已更改的文件都在 NMOX Studio 的差异视图中并排显示，上方有**上一处差异 / 下一处差异**和“第 2 处差异，共 5 处”。新增或删除的文件会把缺失的一侧显示为空白窗格（“无文件”）；二进制文件显示为二进制，横条会说明两个二进制文件是否不同。关闭标签页，git 就会转到下一个文件。

## 5. 解决冲突

**操作：**合并一个会产生冲突的分支，然后：

```bash
git mergetool
```

**结果：**有冲突的文件在编辑器中打开，当前一侧和传入一侧都着了色，每一行 `<<<<<<<` 上都有警告。把光标放在上面并按 ⌘.（其他系统上是 Alt+Enter），或者使用**源代码 ▸ 修复代码…**：**接受当前更改**、**接受传入的更改**或**接受两项更改**，每一项都是一次可撤销的编辑。自提供修复以来已经改变的块会被拒绝，而不是被猜测。保存、关闭标签页，然后回答 git。

## 6. 提出它

**操作：**推送，然后选择团队 ▸ **在 GitHub 上新建拉取请求**（标记的菜单里也有）。

**结果：**GitHub 自己为你的分支打开的 New Pull Request 页面，在你自己的、已经登录的浏览器中。在编辑器里，**编辑 ▸ 在 GitHub 上打开**和**复制 GitHub 链接**给出你所在的一行或几行；在项目工作室的树上，它们给出一个文件或文件夹。

## 7. 追查一次失败

**操作：**在终端（⌃\`）里运行测试，直到有一个失败。

**结果：**输出中的位置 — `src/app.ts:42:7`、一个堆栈帧 `(/abs/app.js:10:5)`、`--> src/main.rs:3:5`、`File "x.py", line 12` — 按住 ⌘ 点击（在 Windows 和 Linux 上是 Ctrl 点击）就会在那一行那一列打开。URL 或 `localhost:3000` 永远不是链接，不存在的路径会指名拒绝，而不是被猜测。

## 8. 让 README 保持诚实

**操作：**工具 ▸ **检查 Markdown 链接…**

**结果：**项目 Markdown 中的每个相对链接和图片都按 GitHub 的渲染方式检查 — 文件必须存在，`#heading` 必须是该文件的一个标题。失效的链接是错误，缺失的标题是警告，两者都以波浪线显示，也会出现在操作项中，状态栏上会有一句说明。没有任何东西离开你的电脑：带有协议头的链接不做检查。

## 9. 交给智能体

**操作：**工具 ▸ **Agent Port (MCP)…**，勾选**保留此地址和令牌**，然后点**为 Claude Code 复制**，并把复制的那一行运行一次。

**结果：**一个能读取 IDE 所知内容的智能体 — 指向的项目、正在提供服务和运行的东西、你正在编辑的内容、最近一次失败 — 而且明天仍能连上，因为令牌保存在你系统的钥匙串中，端口也会被复用。这个端口在构造上仍然是只读的。取消勾选**保留此地址和令牌**会删除钥匙串中的条目。

## 你做了什么

你写了一条提交信息，读了一个 diff，解决了一个冲突，开了一个拉取请求，查到了一行是谁写的，顺着一个堆栈跟踪找了过去，还检查了一个 README — 全都在你本来就在的那个窗口里。这些都没有取代 git：每一步都是 git 自己的，只是在你工作的地方打开。
