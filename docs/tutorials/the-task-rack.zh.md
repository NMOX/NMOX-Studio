# 教程：任务机架

<!-- languages -->
[English](the-task-rack.md) · [Español](the-task-rack.es.md) · [Français](the-task-rack.fr.md) · [Deutsch](the-task-rack.de.md) · [Русский](the-task-rack.ru.md) · [Українська](the-task-rack.uk.md) · [Polski](the-task-rack.pl.md) · [Português (Brasil)](the-task-rack.pt.md) · [Bahasa Indonesia](the-task-rack.id.md) · [Filipino](the-task-rack.tl.md) · [Tiếng Việt](the-task-rack.vi.md) · **简体中文** · [हिन्दी](the-task-rack.hi.md) · [עברית](the-task-rack.he.md) · [العربية](the-task-rack.ar.md)
<!-- /languages -->

任务机架是 NMOX Studio 的招牌想法：把你的构建/测试/服务工具排成一个硬件设备机架，再用跳线把它们连起来。设备运行的是真实的命令；跳线传递的是真实的信号。本教程搭一套很小的配线 — 运行点东西，在监视器上看它的输出 — 让这个比喻一下子就通了。

![机架瞄准一个真实项目 — 设备已上架并在运行](../images/task-rack.png)

![Tab 把机架翻过来 — 跳线在背面连接各台设备](../images/rack-rear.png)

## 开始之前

打开一个项目（任何 Node 项目都行；如果手头没有，就 `文件 ▸ 新建项目…` → “Vanilla Web”）。打开项目就会让机架**瞄准**它，于是每台设备都在这个项目的目录里运行。

## 步骤

1. **打开机架。**点 **任务机架** 标签页（或按 `⌘9`）。起步的机架上只有一台 **MONITOR** — 显示命令输出和错误行的控制台设备。

2. **加一台运行器。**从左侧的设备架里把 **IGNITION** 拖到机架上。IGNITION 是多语言的“运行”设备；瞄准一个 Node 项目时，它运行 `npm run dev`（它会自动识别你的包管理器和工具链）。

3. **把它接到监视器上。**点**背面（Tab）**（或按 Tab）看到背面，然后点 IGNITION 的 **OUT** 插孔，再点 MONITOR 的 **IN** 插孔 — 一根跳线就把它们连上了。（在插孔之间拖动也行；机架很宽时，点击更方便。）

4. **启动它。**翻回正面，按 IGNITION 的 **IGNITE** 按钮。它会启动进程；输出流进 MONITOR，状态指示灯亮起。如果这个项目还没有被信任，你会先看到一次性的工作区信任确认 — 正是这道关卡，让一个克隆下来的仓库没有你点头就跑不了它的脚本。

5. **保存配线。****保存配线**按钮会在你的项目旁边写出 `.nmoxrack.json`。之后重新打开这个项目，配线 — 设备、跳线、旋钮位置 — 会原样回来。

## 你刚学到了什么

- **设备是带前面板的工具。**旋钮选选项，GO 按钮执行，指示灯和液晶屏报告状态 — 而且每个控件都是真的（没有摆设的旋钮；有契约测试来保证）。
- **跳线协调各条通道。**OUT→IN 是最简单的连线；就绪闸门（`ENABLE`）、汇合屏障（`QUORUM`）和触发跳线，能让你组合出一整条会对自己作出反应的流水线。
- **一切都会保存下来。**配线是一个可以提交的文件；机架甚至能在崩溃后把一次正在运行的会话复活过来。

## 下一步

- 一共有 53 台设备 — 在 [devices.md](../devices.md) 或设备架里浏览它们（右键点一台装上的设备，选择 **IGNITION 的用法…** 这样的菜单项）。
- 让 [KVASIR](kvasir.zh.md) 解释一次失败的运行。
- 把配线导出成 GitHub Actions 工作流：机架的 **CI 导出**。
