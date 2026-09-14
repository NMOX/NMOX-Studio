# 教程：用 KVASIR 解释一切

<!-- languages -->
[English](explain-anything.md) · [Español](explain-anything.es.md) · [Français](explain-anything.fr.md) · [Deutsch](explain-anything.de.md) · [Русский](explain-anything.ru.md) · [Українська](explain-anything.uk.md) · [Polski](explain-anything.pl.md) · [Português (Brasil)](explain-anything.pt.md) · [Bahasa Indonesia](explain-anything.id.md) · [Filipino](explain-anything.tl.md) · [Tiếng Việt](explain-anything.vi.md) · **简体中文** · [हिन्दी](explain-anything.hi.md) · [עברית](explain-anything.he.md) · [العربية](explain-anything.ar.md)
<!-- /languages -->

KVASIR 起初是一台解释失败运行的机架设备。如今它能到达四个地方 — 机架、编辑器、API 工作室和数据库工作室 — 而每一副面孔都遵守同样的三条规矩：**在任何东西离开你的机器之前，你都能准确看到将要离开的是什么**；**每个界面各自赢得自己的许可**（对构建错误说“是”，绝不等于授权发送代码或 SQL）；**密钥在构造上就不可能被捎带出去**（要披露的内容由拥有这份数据的工作室来组装，凭据类请求头会被去掉，密码根本够不着）。

![KVASIR 正在解释一次真实的失败运行](../images/kvasir-explain.png)

## 开始之前

一个密钥就覆盖全部四副面孔 — 来自你选择的任意一家服务商：Claude（Anthropic）、ChatGPT（OpenAI）或 Gemini（Google）。按 KVASIR 前面板上的 **KEY…** 选择服务商，把它的密钥存进你的操作系统钥匙串；或者导出 `ANTHROPIC_API_KEY`、`OPENAI_API_KEY` 或 `GEMINI_API_KEY`。没有密钥就没有调用 — 每一副面孔都会如实这样说。

## 四副面孔

1. **一次失败的运行（机架）。**装上 KVASIR，运行一个会失败的东西，按 **EXPLAIN**。发送的内容：命令、退出码，以及最多五行采样的错误行。完整的走法见 [KVASIR 教程](kvasir.zh.md)，包括那根能免手动自动解释 VERITAS 失败的跳线。

2. **你的代码（编辑器）。**在任何语言里选中代码 → 右键 → **就所选内容询问 KVASIR…**，然后输入一个问题。发送的内容：有上限的选中内容、文件名和语言 — 你项目里的其他东西一概不发。这副面孔有它*自己的*许可关卡，因为失败流程的许可明确承诺了源码绝不离开本机。

3. **一个 API 响应（API 工作室）。**发送之后，按**解释…**。发送的内容：方法、查询参数值已被遮蔽的网址、状态码、去掉并计数了凭据的请求头，以及有上限的响应体。一旦冒出一个 401 或者奇怪的 CORS 请求头，它马上就派得上用场。

4. **一个数据库错误（数据库工作室）。**一条失败的语句会在它的错误信息下方长出一个**解释…**按钮。发送的内容：你运行的 SQL — *包括其中的字面值，许可说明里也写明了这一点*，因为错误通常就出在某个字面值上 — 外加错误信息和引擎类型。绝不包括连接、密码或数据行。

每一副面孔都会打开一个对话窗口：接着追问，模型能看到这场交流的完整历史（上限为十轮交流，会写在对话记录里）。**快速/深入**的选择（Haiku/Sonnet）会被记住，并在每场对话里固定下来，这样对话记录永远不会谎报是谁回答的。

## 两分钟试一试

数据库工作室是最快能演示的一副面孔：打开 ⌥⌘7，建一个 SQLite 连接，对一个表名是 `users` 的数据库运行 `SELECT * FROM user;`，然后在错误上按**解释…**。同意之前先读一读许可对话框 — 那是这个产品的承诺，就写在一句话里。

## 你刚学到了什么

- 四个界面，一道接缝：每个工作室组装自己要披露的内容，许可对话框逐字引用它。
- 拒绝会被安静而彻底地遵守 — 不开窗口，不发调用。
- 结果属于产生它的那个工作区：切换项目会清掉响应和结果标签页，所以“解释”永远不会披露上一个项目的数据。
