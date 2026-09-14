# 教程：编写你自己的机架设备

<!-- languages -->
[English](your-own-device.md) · [Español](your-own-device.es.md) · [Français](your-own-device.fr.md) · [Deutsch](your-own-device.de.md) · [Русский](your-own-device.ru.md) · [Українська](your-own-device.uk.md) · [Polski](your-own-device.pl.md) · [Português (Brasil)](your-own-device.pt.md) · [Bahasa Indonesia](your-own-device.id.md) · [Filipino](your-own-device.tl.md) · [Tiếng Việt](your-own-device.vi.md) · **简体中文** · [हिन्दी](your-own-device.hi.md) · [עברית](your-own-device.he.md) · [العربية](your-own-device.ar.md)
<!-- /languages -->

*一次坐下来就能做完。你会用一个文本编辑器给机架加一台设备，按下它的按钮，看它运行一条真正的命令，再把它的输出接进 MONITOR — 一行 Java 都不用写。*

2.0.0 新增。机架自带五十三台设备，而在此之前，想加第五十四台只有一条路：编写一个 NetBeans 插件。本教程讲的是另一条路。

![任务机架：左侧的设备货架就是 ~/.nmox/devices.d 中的设备出现的地方，和内置设备排在一起](../images/tabs/the-task-rack.png)

## 1. 建好文件夹

```bash
mkdir -p ~/.nmox/devices.d
```

安装步骤就这一步。机架是按需读取这个文件夹的，所以不需要重启任何东西。

## 2. 写设备

把下面的内容放进 `~/.nmox/devices.d/counter.json`：

```json
{
  "id": "com.example.counter",
  "title": "COUNTER",
  "tagline": "counts the files in the project",
  "accent": "#7FB3D5",
  "category": "OBSERVE",
  "usage": "COUNT lists the project's files of the dialled KIND and shows how many.\nPatch OUT into MONITOR to read the list, or DONE onward to chain.",
  "knobs": [
    { "key": "kind", "label": "KIND", "options": ["js", "ts", "css", "md"] }
  ],
  "ports": [
    { "id": "count", "label": "COUNT", "direction": "IN", "signal": "TRIGGER" },
    { "id": "done", "label": "DONE", "direction": "OUT", "signal": "TRIGGER" },
    { "id": "out", "label": "OUT", "direction": "OUT", "signal": "DATA" }
  ],
  "buttons": [
    { "label": "COUNT", "role": "QUERY",
      "command": ["git", "ls-files", "*.{{kind}}"],
      "emit": "done", "trigger": "count" }
  ]
}
```

里面的每一项都有用处：**旋钮**会变成命令里的 `{{kind}}`；**QUERY** 角色把按钮涂成蓝色（颜色法则：蓝色是询问，绿色是执行，红色是停止）；两个端口让它可以接线。

## 3. 装上机架

打开**任务机架**（`⌘9`，或者点“任务机架”标签页），在货架的**观察**抽屉里找。COUNTER 就在那里，下面是你写的标语。把它拖到一条导轨上。

把光标停在它的“用法”卡片上 — 那就是你的 `usage` 文本，这也是格式为什么坚持要求两行真正的说明。

## 4. 按下它

> 注意这里没有 `units` 这一行：货架会量出面板的大小，挑出能装下的最小高度（这一台因为有旋钮，需要 2U）。只有想要额外空间时才声明 `units`。

让机架指向一个 git 项目，把 **KIND** 拨到 `js`，然后按 **COUNT**。

第一次按下会弹出**工作区信任**确认，因为设备文件运行的是真正的命令，而宿主对每一次启动的把关方式与内置设备完全相同。授予信任后，液晶屏会先显示这条命令，再显示输出的最后一行。DONE 插孔会闪一下绿灯。

如果拒绝，什么都不会启动 — 拒绝本身就是功能。

## 5. 接上线

从 COUNTER 的 **OUT** 拉一根跳线到 MONITOR 的 **TAP**。再按一次 COUNT：每一行都会落到监视器上，因为声明为 `OUT`/`DATA` 的端口不需要任何额外配置就能收到这次运行的输出。

再从 TEMPO 的节拍输出拉一根线到 COUNTER 的 **COUNT** 输入。你在文本编辑器里写出的这台设备，现在按时钟运转了。

## 6. 故意弄坏它

编辑这个文件，把命令改成带管道的写法：

```json
"command": ["sh", "-c", "git ls-files | wc -l"]
```

保存后，COUNTER 会从货架上*消失*。这是格式在拒绝一条 shell 命令行：命令必须是一个 argv 数组，这样读这个文件的人 — 六个月后的你，或者正在审查它的同事 — 能准确看出会运行什么。IDE 日志会说明跳过了哪个文件以及原因：

```
device file counter.json skipped: button "COUNT" command token
"git ls-files | wc -l" contains "|" — commands are argv, never a shell line
```

改回数组形式，它就回来了。用路径指定的工具（`./x.sh`）、未知的 `{{variable}}`、或者只有一行的 `usage` 也是一样：整个文件被跳过，而不是只加载一半，因为标签说谎的设备比没有设备更糟。

## 你刚学到了什么

- 设备就是一个**文件**：`~/.nmox/devices.d/*.json`，按需读取，不用重启，不用构建。
- 旋钮变成 `{{variables}}`；角色决定颜色；端口让它可以接线，也让它的输出可以被读取。
- **规矩由宿主来守** — 每次启动都经过工作区信任、颜色法则、端口词汇表、货架法则 — 所以设备文件就算想，也写不出一条绕过把关的命令或一个红色的 GO。
- 拒绝在日志里说得清清楚楚，效果上则是彻底的。

## 下一步

- [device-files.md](../device-files.md) — 完整参考
- [任务机架](the-task-rack.zh.md) — 接线、闸门和预设
- [device-spi.md](../device-spi.md) — Java SPI，适合需要真正状态的设备：自定义绘制、轮询、长连接
