# 教程：KVASIR — AI 错误解释器

<!-- languages -->
[English](kvasir.md) · [Español](kvasir.es.md) · [Français](kvasir.fr.md) · [Deutsch](kvasir.de.md) · [Русский](kvasir.ru.md) · [Українська](kvasir.uk.md) · [Polski](kvasir.pl.md) · [Português (Brasil)](kvasir.pt.md) · [Bahasa Indonesia](kvasir.id.md) · [Filipino](kvasir.tl.md) · [Tiếng Việt](kvasir.vi.md) · **简体中文** · [हिन्दी](kvasir.hi.md) · [עברית](kvasir.he.md) · [العربية](kvasir.ar.md)
<!-- /languages -->

KVASIR 是一台机架设备，它读取你最近一次失败的运行，然后问你的 AI — Claude、ChatGPT 或 Gemini — 出了什么问题。这是用机架的比喻做的 AI 协助：一个按钮、一道清楚的许可关卡、一块诚实的液晶屏 — 不发送任何项目文件或密钥，只发送有上限的失败上下文。

![KVASIR 正在解释一次真实的失败运行：前面板上是经过许可的诊断，查看器里是完整的修复步骤](../images/zh/kvasir-explain.png)

## 开始之前

你需要 KVASIR 支持的三家服务商之一的 API 密钥：Anthropic（Claude）、OpenAI（ChatGPT）或 Google（Gemini）。按前面板上的 **KEY…** 选择服务商，把它的密钥存进操作系统钥匙串；或者导出该服务商的环境变量 — `ANTHROPIC_API_KEY` / `CLAUDE_API_KEY`、`OPENAI_API_KEY` / `CHATGPT_API_KEY`，或 `GEMINI_API_KEY` / `GOOGLE_API_KEY`。服务商的选择对 KVASIR 的每一副面孔都有效，也可以在 选项 ▸ 机架与云 里设置。

## 步骤

1. **制造一次失败。**运行一个会失败的东西 — 一次带语法错误的构建，一个会抛异常的测试。机架的飞行记录器会记下命令、退出码，以及最多五行采样的错误行。

2. **装上 KVASIR**（从面板的 OBSERVE 分类里拖出来），然后按 **EXPLAIN**。

3. **给出许可（第一次）。**KVASIR 有自己的一次性许可对话框，按服务商分别询问：它会点名接收数据的厂商，并准确说明哪些东西会离开你的机器：失败的命令、它的退出码、不超过 5 行的错误行、设备名和项目名 — 仅此而已（没有源码、没有环境变量、没有密钥）。工作区信任把守的是*运行*代码；这种向外流出的数据有它自己的关卡。

4. **读结论。**一段简短的诊断会出现在多行液晶屏上；按 **VIEW**，完整的解释会在一个对话窗口里打开。**MODEL** 旋钮在 FAST（默认）和 DEEP 之间选择 — 按你选的服务商，分别对应 Haiku / Sonnet、GPT-5 mini / GPT-5，或 Gemini Flash / Pro。

## 你刚学到了什么

- KVASIR 启动时不花任何代价，不按按钮就不发任何网络请求 — 密钥关卡和许可关卡都强制执行。
- 密钥只放在服务商的认证请求头里（`x-api-key`、`Authorization: Bearer`、`x-goog-api-key`）— 从不出现在网址、请求体或日志里。
- 密钥从不跨服务商使用，许可也按服务商分别给出：对 Anthropic 说了“是”，不等于对 Google 或 OpenAI 说了“是”。
- 降级是诚实的：没有密钥、没有许可、没有可解释的东西、离线，以及被拒绝，每一种都会在液晶屏上显示一条清楚的信息。

## 下一步

- 把它接成免手动：一根 `VERITAS FAIL → KVASIR EXPLAIN` 跳线会自动解释一次失败的测试运行（走跳线的路径从不弹出询问，并以 30 秒为限速）；它的 OUT 可以接到 MONITOR/PHOSPHOR。
