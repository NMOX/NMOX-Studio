# 教程：多语言编辑与调试

<!-- languages -->
[English](polyglot-editing-and-debugging.md) · [Español](polyglot-editing-and-debugging.es.md) · [Français](polyglot-editing-and-debugging.fr.md) · [Deutsch](polyglot-editing-and-debugging.de.md) · [Русский](polyglot-editing-and-debugging.ru.md) · [Українська](polyglot-editing-and-debugging.uk.md) · [Polski](polyglot-editing-and-debugging.pl.md) · [Português (Brasil)](polyglot-editing-and-debugging.pt.md) · [Bahasa Indonesia](polyglot-editing-and-debugging.id.md) · [Filipino](polyglot-editing-and-debugging.tl.md) · [Tiếng Việt](polyglot-editing-and-debugging.vi.md) · **简体中文** · [हिन्दी](polyglot-editing-and-debugging.hi.md) · [עברית](polyglot-editing-and-debugging.he.md) · [العربية](polyglot-editing-and-debugging.ar.md)
<!-- /languages -->

NMOX Studio 能编辑 70 多种语言，带真正的语法高亮、导航器大纲和语言服务器的智能功能 — 而且开箱即可调试 JavaScript/TypeScript（以及浏览器），断点是真的会停下来的。本教程在一个 Node 应用里命中一个断点。

![一个 JavaScript 断点命中：执行已暂停，Node 调用栈，以及实时的 V8 变量](../images/debug-javascript.png)

## 开始之前

打开（或者新搭）一个小型 Node 项目，里面要有一个你能运行的脚本，比如一个 Express 路由，或者一个普通的 `node server.js`。

## 步骤

1. **打开一个源文件。**高亮、括号匹配、代码折叠和标记相同出现处，全都会自动就位。**导航器**显示文件的大纲；语言服务器（按 `工具 ▸ 环境诊断…` 的提示安装）会加上补全和诊断。

2. **设一个断点。**在你的处理函数里某一行的编辑器左侧边栏上点一下 — 出现一个断点圆点。

3. **调试这个文件。**运行**调试文件（断点）**（HTML/JS 页面则用“在 Chrome 中调试（断点）”）。一次性的工作区信任确认会先把守这次启动；然后内置的 `js-debug` 适配器会启动你的程序。

4. **命中断点。**触发那条代码路径（发出请求，或者让脚本走到那一行）。执行会在你的断点处**停下** — 查看变量，沿着调用栈走，单步跳过或单步进入。调试浏览器时，一个用完即弃配置的 Chrome 会在你正在运行的开发服务器地址上打开，页面里的断点会映射回 IDE。

## 你刚学到了什么

- 编辑器把 70 多种语言都当作一等公民（TextMate 语法 + CSL + LSP）；配置文件（YAML、TOML、Dockerfile、nginx…）也都覆盖到了。
- JS/TS 调试是内置的 — 一个会话多路复用器把 js-debug 的子会话摊平，好让平台的单会话调试器能驱动它。
- 每一次调试启动都要先过信任这一关，停止时会把整棵进程树一起杀掉（不留孤儿进程）。

## 下一步

- **运行聚焦测试**会按各语言的方式运行单个测试方法。
- 来自机架工具（eslint/tsc/phpstan）的诊断会落进平台的 Action Items 窗口。
