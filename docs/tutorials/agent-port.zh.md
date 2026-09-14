# Agent Port (MCP)：给智能体的只读端口

<!-- languages -->
[English](agent-port.md) · [Español](agent-port.es.md) · [Français](agent-port.fr.md) · [Deutsch](agent-port.de.md) · [Русский](agent-port.ru.md) · [Українська](agent-port.uk.md) · [Polski](agent-port.pl.md) · [Português (Brasil)](agent-port.pt.md) · [Bahasa Indonesia](agent-port.id.md) · [Filipino](agent-port.tl.md) · [Tiếng Việt](agent-port.vi.md) · **简体中文** · [हिन्दी](agent-port.hi.md) · [עברית](agent-port.he.md) · [العربية](agent-port.ar.md)
<!-- /languages -->

*把一个 AI 智能体指向你的 IDE — 让它读，绝不让它跑。*

![Agent Port 对话框 — 回环端点、每次启动生成的令牌（这张截图里是占位符），以及可直接复制的客户端配置](../images/tabs/agent-port.png)

NMOX Studio 自带一个 Model Context Protocol 服务器。任何会说 MCP 的智能体（Claude Code、编辑器里的助手、你自己的脚本）都可以连上来，问 IDE 它知道些什么：指向的是哪个项目、什么在提供服务、什么在运行、你正在编辑什么、某个名字在哪里声明、最近一次失败的是什么。它**在构造上就是只读的**：只要 Agent Port 包里有任何一个类哪怕只是提到启动进程、写文件或停止运行的途径，构建就会失败。

## 1. 启动它

**操作：**工具 ▸ **Agent Port (MCP)…**（选中它就会启动端口），然后点**复制配置**。

**结果：**一个对话框，里面有端点（仅限回环，端口每次都是新的）、一个仅限本次启动的 bearer 令牌，以及一份现成的客户端配置：

```json
{
  "mcpServers": {
    "nmox-studio": {
      "type": "http",
      "url": "http://127.0.0.1:PORT/mcp",
      "headers": { "Authorization": "Bearer TOKEN" }
    }
  }
}
```

把它粘贴进你的智能体的 `.mcp.json`。令牌只存在于那个对话框里 — 从不写入日志，也从不持久化 — 并随端口一起失效。**停止 Agent Port** 会结束它；退出 IDE 也会。它监听期间，状态栏上会显示 **⌁ agent port :N** — 能读取你 IDE 的端口永远不会是隐形的；这个标记的提示会统计正在接收流的智能体数量，点一下会重新打开对话框（查看配置，或者停止）。

## 2. 工具

每个工具都会同时返回一段给人看的文本，以及一份符合所声明 `outputSchema` 的带类型 `structuredContent`（构建会拿真实输出去校验这份 schema），并且都标注了 `readOnlyHint: true`。

| 工具 | 它回答什么 | 参数 |
|------|-----------------|-----------|
| `ide_context` | 一次调用拿到完整的定向快照：项目、工具链、服务器、运行、正在编辑的文件、最近一次失败、诊断数量 | — |
| `project_state` | 所指向的项目：名称、目录、git 分支、检测出的类型、Node 包管理器 | — |
| `run_history` | 飞行记录仪里的启动与退出，最新的在前，每次退出都带命令、退出码和耗时；你自己停掉的运行显示为 `stopped`，绝不是 `failed` | `limit` |
| `live_servers` | IDE 所知的每一个正在提供服务的开发服务器，附带其 URL | — |
| `live_runs` | 此刻正在运行的每一条命令（也就是工具栏的 ■ 会停掉的那些），附带开始时间 | — |
| `last_failure` | 最近一次失败的运行：设备、命令、退出码，最多五行错误 | — |
| `diagnostics` | 各代码检查器和检查工具当前报告的内容 | `file`（子串过滤） |
| `find_symbol` | 某个名字在哪里声明 — 与转到符号（⌥⇧⌘O）用的是同一份索引 | `query`、`limit` |
| `outline` | 单个文件的结构 — 就是导航器自己的那些条目 | `file` |
| `search_text` | 包含某个字面量的行，不区分大小写，有上限且每处截断都会报告；`.env` 文件、包管理器的 rc 文件和私钥从不参与搜索 | `query`、`limit` |
| `editor_state` | 正在编辑的文件（有焦点的编辑器标签页，否则是编辑区里正显示的那个），以及每个打开的标签页，未保存的会被标出 | — |
| `rack_devices` | 任务机架上装着的设备，按顺序排列 | — |

每个列表都有上限，而且会说出来：`find_symbol` 和 `outline` 会报告索引不完整，`search_text` 只有在确实还有更多匹配时才报告 `truncated`，`run_history` 会报告较早的事件被省略了。

## 3. 资源、提示词和事件流

同样的答案也可以作为资源浏览，供智能体作为上下文附加 — `nmox://context`、`nmox://project`、`nmox://history`、`nmox://servers`、`nmox://runs`、`nmox://editor`、`nmox://last-failure`、`nmox://diagnostics`、`nmox://devices` — 另外还有两个模板，对应需要参数的那两个工具：`nmox://outline/{file}` 和 `nmox://search/{query}`（百分号编码）。资源的文本就是对应工具的结构化 JSON，逐字节相同。

宁愿被通知、也不想反复询问的智能体可以**订阅**：对上面任意一个 URI 调用 `resources/subscribe`，端口的 GET 流（Streamable HTTP 从服务器到客户端的通道，`Accept:
text/event-stream`，同一个令牌，不带 `Origin`）就会在其背后的东西发生变化的那一刻，发来一帧 `notifications/resources/updated` — 一次运行开始，就通报 `nmox://runs`；一个服务器上线，就通报 `nmox://servers`；一个代码检查器报告结果，就通报 `nmox://diagnostics`；标签页切换或文件保存，就通报 `nmox://editor`；`nmox://context` 则跟随以上所有变化。这一帧只写出 URI，别的什么都没有；智能体自己重新读取它关心的内容。智能体附加的大纲也会跟随它的文件：订阅 `nmox://outline/src/app.ts`，文件在磁盘上发生变化时（保存、格式化、生成器），端口就会通报这个 URI，文件消失时再通报一次 — 必须是所指向项目内的普通文件，最多三十二个，每两秒轮询一次；项目之外的路径会得到 `-32002`，从不读取。

三个提示词会把实时状态折叠进一个问题：`diagnose_failure`（最近一次失败）、`review_setup`（完整上下文），以及 `where_is` — 唯一需要参数 `name` 的那个 — 它会折叠进该名字的符号命中结果。

要填这个参数，或者大纲模板的 `{file}` 的智能体，可以先问一问：`completion/complete`（规范里的第四种原语）会从符号索引里为 `where_is` 的 `name` 给出候选（与 `find_symbol` 返回的命中相同，去重，前缀命中在前），从项目自己的文件里为 `{file}` 给出候选（先前缀命中，再包含命中；搜索遍历的跳过列表同样适用，所以 `node_modules` 永远不会出现在补全里）— 最多 100 个值，被上限截断时给出 `hasMore`，只有在计数精确时才给出 `total`（文件列表的计数总是精确的；超过上限后符号索引只能给出一个下限，所以宁可不给数字，也不给错误的数字）。搜索模板的字面量可以是任何东西，所以它不补全任何内容；未知的提示词、模板或参数名会以 `-32602` 被拒绝。

同一条流还承载**日志消息**：每次运行打印的每一行都会以 `notifications/message` 的形式到达，`logger` 就是这次运行 — 生命周期消息为 `info`（`$ npm run build`、`[exit 0]`、`[exit 143]
stopped`；失败的退出为 `error`），stderr 为 `warning`，普通输出为 `debug`。级别从 `info` 开始，所以智能体只会听到运行的开始和结束，直到它主动要求更多：用 `debug` 调用 `logging/setLevel`，就打开了全量输出。一个打印速度快于客户端读取速度的构建，永远不会让端口的内存膨胀 — 未写出的行超过一千行后，溢出部分会被计数，并作为一行 `warning` 通报出来，从不悄悄丢失。规范中没有的级别会以 `-32602` 被拒绝。

## 4. 亲手走一遍

先把令牌放进一个 shell 变量里（永远不要写在你可能会拿去粘贴的命令行上）：

```bash
curl -s -X POST "$URL" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"find_symbol","arguments":{"query":"checkout"}}}'
```

**结果：**`checkout (function) — src/cart.js:12`，同样的内容也出现在 `structuredContent.hits[0]` 中。

亲手试一试事件流：在一个 shell 里打开它，在另一个 shell 里订阅 —

```bash
curl -N -s "$URL" -H "Authorization: Bearer $TOKEN" -H "Accept: text/event-stream"
```

```bash
curl -s -X POST "$URL" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"resources/subscribe","params":{"uri":"nmox://runs"}}'
curl -s -X POST "$URL" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"logging/setLevel","params":{"level":"debug"}}'
```

**结果：**先是 `: connected`，然后每十五秒一次 `: keepalive`；按下 ▶，第一个 shell 就会打印出针对 `nmox://runs` 的 `notifications/resources/updated`，以及这次运行打印的每一行，形式为 `notifications/message`（`$ npm run dev` 为 `info`，输出为 `debug`）；按下 ■，`[exit 143] stopped` 以 `info` 到达。

用**官方客户端**走同样一遍、一次覆盖所有原语的脚本，就放在仓库里：`scripts/agent-port-walk.mjs`（它的文件头说明了如何在一个临时目录里安装 `@modelcontextprotocol/sdk`，以及 URL 和令牌放在哪里 — shell 变量，绝不写在命令行上）。它每一步打印一行，最后以 WALK CLEAN 结束，或者把意外的次数作为退出码（在拒绝类的步骤里，得到一个答复才算意外），所以 CI 任务可以读取它；它监听期间在 IDE 里按下 ▶ 和 ■，日志消息就会到达。

## 5. 拒绝就是功能

| 你这样做 | 端口这样回答 |
|--------|---------------|
| 不带令牌，或带着过期的令牌调用 | `401` — 仅此而已，连工具列表都不给 |
| 从浏览器里的页面调用（带任何 `Origin`） | `403` |
| 普通的 `GET` | `405` — 端口不是网页；只有 SSE 的 `GET`（带 `Accept: text/event-stream`）会作为订阅流被响应 |
| 订阅 `nmox://nonesuch`，或订阅项目之外的大纲 | JSON-RPC `-32002`（资源不存在） |
| 订阅第三十三个大纲 | `-32602`，并指出上限 |
| 读取 `nmox://nonesuch` | JSON-RPC `-32002`（资源不存在） |
| 调用 `where_is` 却不带 `name` | `-32602`，并指出缺少的参数 |
| 请求项目之外的文件（`../../.zshrc`） | `outline` 拒绝 — *outside the aimed project*（不在所指向的项目内）— 并且从不读取它 |
| 搜索一个存放在 `.env`（或 `app.env`）、`.npmrc`、`.htpasswd`、`secrets.yaml`、`credentials.json` 或 `.pem` 里的值 — 或者请求它们的大纲 | 什么都没有 — 这些文件从不参与搜索、从不计数、从不出现在补全里，`outline` 会点名拒绝它们；IDE 自己的环境变量法则（只给键名，绝不给值）对智能体同样有效 |
| 把日志级别设为 `loud` | `-32602`，并列出八个级别 |
| 让它运行、写入或停止任何东西 | 根本没有这样的工具；台账测试保证它一直如此 |

最后一行就是设计本身。能启动你服务器的智能体也能停掉它，能写入的智能体也能删除；Agent Port 始终只是一个用来“问”的途径。如果将来某个版本加入执行能力，它会带着自己的授权设计一起到来，就像 KVASIR 向外发送数据那样。

另见：Kitchen Sink 的第 24 站，以及用户指南中关于 Agent Port 的那一段。
