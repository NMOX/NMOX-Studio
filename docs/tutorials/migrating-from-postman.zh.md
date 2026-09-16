# 教程：从 Postman 迁移（以及 Insomnia 和浏览器）

<!-- languages -->
[English](migrating-from-postman.md) · [Español](migrating-from-postman.es.md) · [Français](migrating-from-postman.fr.md) · [Deutsch](migrating-from-postman.de.md) · [Русский](migrating-from-postman.ru.md) · [Українська](migrating-from-postman.uk.md) · [Polski](migrating-from-postman.pl.md) · [Português (Brasil)](migrating-from-postman.pt.md) · [Bahasa Indonesia](migrating-from-postman.id.md) · [Filipino](migrating-from-postman.tl.md) · [Tiếng Việt](migrating-from-postman.vi.md) · **简体中文** · [हिन्दी](migrating-from-postman.hi.md) · [עברית](migrating-from-postman.he.md) · [العربية](migrating-from-postman.ar.md)
<!-- /languages -->

API 工作室能读你手里已有的文件：Postman 的集合或环境、Insomnia v4 导出（工作区结构和 `{{ _.templates }}` 都会被转换）、开发者工具的 HAR 捕获文件、一条 curl 命令、一个 `.http` 文件、一份 OpenAPI 规范。
这一趟从头到尾处理一份真实的 Postman 导出 — 并展示 NMOX Studio 刻意与众不同的一点：**密钥落进你的操作系统钥匙串，绝不进一个可以提交的文件。**

![API 工作室，导入的东西落脚的地方：集合树、一个已发送的请求，以及它的安全响应头评分](../images/zh/api-studio.png)

## 开始之前

从 Postman 导出你的集合：集合 ▸ … ▸ Export ▸ **Collection v2.1**。（v1 导出会被拒绝，并写明修复办法 — 重新以 v2.1 导出。）环境是单独导出的，通过**导入… ▸ Postman 环境…**导入 — 普通的值会进来，同名的导入会合并而不覆盖你已经设好的东西，而被 Postman 标为 *secret* 的值会被挡在外面，并附一条提示指向由钥匙串支撑的 Auth 字段，因为 API 工作室的环境住在可以提交的 `.nmoxapi.json` 里。

## 步骤

1. **打开 API 工作室**（⌥⌘8），按**导入… ▸ Postman 集合…**。挑出你导出的 `.json`。

2. **看看进来了什么。**文件夹以“Folder / Request”这样的名字保留它们的身份。Postman 的 `{{variables}}` 会*原样*导入 — 它们本来就是 API 工作室自己的语法 — 集合变量会加入你当前的环境，而不覆盖你已经设好的任何东西。`:id` 这样的路径变量会变成 `{{id}}`。

3. **看一个原本带 bearer 令牌的请求的 Auth 标签页。**令牌*就在那里* — 但它是经由钥匙串支撑的 Auth 字段进来的，而不是一行请求头。尽管放心提交 `.nmoxapi.json`；密钥不在里面。导入没法表达的东西（multipart 请求体、脚本）会在状态栏里点名说明，绝不悄悄弄乱。

4. **导入一次浏览器捕获。**在开发者工具的 Network 标签页里选“Save all as HAR”，然后**导入… ▸ HAR 捕获文件…**。只有你的 XHR/fetch 流量会被导入（页面资源会被计数并明说），会话 cookie 会被丢弃 — 捕获到的 cookie 就是一份凭据 — 而录下来的 `Authorization` 要么移进钥匙串（Bearer/Basic），要么被丢弃并计数（任何不透明的形式）。

5. **发一个试试。**挑一个导入的请求，需要的话在环境里解析好 `{{baseUrl}}`，按**发送** — 顺便在标准标签页上读一读安全响应头的评分。

6. **反方向走一趟。****导入… ▸ 将集合导出为 .http…**会把整个集合写成 REST Client 方言，任何编辑器或 CI 运行器都能用。认证信息故意不写进文件；每个带认证的请求都附一条注释，点名需要补回什么。

## 你刚学到了什么

- 迁移就是一个菜单：curl / `.http` / OpenAPI / Postman / HAR 进，`.http` 出。
- 密钥的规矩在每一道边界都成立：从钥匙串进来，就留在钥匙串里。
- 拒绝会被点名，绝不无声 — 如果有什么没导进来，状态栏会说是什么、为什么。
