# 教程：数据库工作室

<!-- languages -->
[English](db-studio.md) · [Español](db-studio.es.md) · [Français](db-studio.fr.md) · [Deutsch](db-studio.de.md) · [Русский](db-studio.ru.md) · [Українська](db-studio.uk.md) · [Polski](db-studio.pl.md) · [Português (Brasil)](db-studio.pt.md) · [Bahasa Indonesia](db-studio.id.md) · [Filipino](db-studio.tl.md) · [Tiếng Việt](db-studio.vi.md) · **简体中文** · [हिन्दी](db-studio.hi.md) · [עברית](db-studio.he.md) · [العربية](db-studio.ar.md)
<!-- /languages -->

数据库工作室是一套数据库工具，支持 SQLite、PostgreSQL、MySQL/MariaDB、MongoDB 和 CouchDB — 驱动随附，控制台了解各种引擎，结果网格可以就地编辑。本教程用 SQLite，因为它不需要服务器。

![一个 SQLite 连接、一条查询、网格里的实时数据行 — 以及网格只读时，状态栏给出的诚实理由](../images/db-studio.png)

## 打开方式

`⌥⌘7`，或者 **数据库工作室** 标签页。

## 步骤

1. **创建一个 SQLite 连接。**点**添加**，选 **SQLite**，再挑一个文件路径（一个“另存为”式的文件选择器能让你新建一个 `.db`）。它会出现在连接树里。

2. **跑点 SQL。**在控制台里输入并运行：

   ```sql
   CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, active BOOLEAN);
   INSERT INTO users (name, active) VALUES ('Ada', 1), ('Bob', 0);
   SELECT * FROM users;
   ```

   每条语句在下方都有自己的结果网格，并附带耗时。

3. **在网格里改一行。**双击 Bob 的 `name` 单元格，改掉它，然后按**应用…**。只有当数据库工作室能构造出一条安全的单行 `UPDATE`（单张表、有主键）时，它才允许在网格里编辑 — 运行之前会先给你看确切的 SQL，然后重新查询以确认真实结果。如果某一行没法安全地编辑，它会告诉你原因。

4. **导出。**在任意结果网格上按 **CSV** 或 **JSON**。CSV 导出会自动化解电子表格的公式注入。

5. **对查询做 EXPLAIN。**选中一条 `SELECT`，按 **EXPLAIN**，就能看到该引擎原生的查询计划。

6. **让 KVASIR 解释一次失败。**运行 `SELECT * FROM user;`（注意这个拼写错误）。错误信息下方会出现一个**解释…**按钮。按下它，一个许可对话框会准确说出将要发送什么 — 你运行的 SQL（包括其中的字面值）、错误信息和引擎类型；绝不包括连接、密码或任何数据行。同意之后，KVASIR 会解释这个错误并给出修复建议，回答出现在一个可以继续追问的对话窗口里。

## 你刚学到了什么

- 密码只存在操作系统钥匙串里，从不写进 `.nmoxdb.json`。
- 控制台了解引擎类型：SQL 引擎用 SQL，MongoDB/CouchDB 用 JSON 文档控制台。
- 历史和保存的查询按项目保存；`.env` 文件里的 `DATABASE_URL`/`DB_*` 连接会自动提供给你。

## 下一步

- 数据库跑在 Docker 里？数据库工作室会为它提供一个连接 — 见 [Docker 面板](docker-panel.zh.md)。
