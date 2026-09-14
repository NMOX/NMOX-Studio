# 教程：块工作室

<!-- languages -->
[English](block-studio.md) · [Español](block-studio.es.md) · [Français](block-studio.fr.md) · [Deutsch](block-studio.de.md) · [Русский](block-studio.ru.md) · [Українська](block-studio.uk.md) · [Polski](block-studio.pl.md) · [Português (Brasil)](block-studio.pt.md) · [Bahasa Indonesia](block-studio.id.md) · [Filipino](block-studio.tl.md) · [Tiếng Việt](block-studio.vi.md) · **简体中文** · [हिन्दी](block-studio.hi.md) · [עברית](block-studio.he.md) · [العربية](block-studio.ar.md)
<!-- /languages -->

块工作室是一个像 Scratch 那样的编排器，拼出来的是**真正的** Web Component。你把带类型的积木扣在一起，它就生成一个自足的自定义元素（shadow DOM、状态、监听器）— 外加一个实时预览服务器，让你看着它跑。点一块积木，它生成的那几行代码就会高亮。

![块工作室 — 积木面板、带组件根节点的画布，以及生成的自定义元素，点哪块积木就映射到哪段代码](../images/tabs/block-studio.png)

## 打开方式

`⌥⌘5`，或者 **块工作室** 标签页。

## 步骤

1. **给元素起名。**每个自定义元素都需要一个带连字符的标签名。新建一个组件，给它起个像 `hello-badge` 这样的标签。

2. **从面板添加积木。**拖一块 **Element** 积木（一个 DOM 节点），给它文字；加一个 **State** 字段；再加一个 **Listener**，点击时切换一个 class。只有合法的嵌套才被允许 — 画布会预先显示有效的放置位置，并拒绝非法的，就连载入时也一样。

3. **读代码。**中间的窗格显示生成的 `text/javascript` — 一个完整的自定义元素。点任意一块积木，它生成的那几行就会高亮；映射是精确的。

4. **看它跑起来。**按**预览**。块工作室从一个内存中的服务器提供这个组件并把它呈现出来；`⇄` 和快速搜索会显示实时地址。同一个工作区里的组件甚至可以互相使用。

5. **保存它。****保存组件**会写出 `src/components/<tag>.js` — 原子写入，绝不覆盖一个手工改过的文件。整个工作区住在 `.nmoxblocks.json` 里；**打开组件…**会把你（或这个工作室）写过的文件重新导入，只要它仍然是积木方言。

## 你刚学到了什么

- 产出是一个真正的、不依赖任何框架的自定义元素，可以直接发布。
- 积木↔代码的映射是双向的：方言之内的修改能干净地重新导入。
- 一个工作区装得下许多组件；切换组件是一道撤销边界。

## 下一步

- 用组件组合组件 — 一块写着兄弟组件标签名的积木，会在预览里把它嵌套呈现出来。
