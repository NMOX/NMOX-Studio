# 教程：Image Kit (Web) — 压缩你的图片

<!-- languages -->
[English](image-kit.md) · [Español](image-kit.es.md) · [Français](image-kit.fr.md) · [Deutsch](image-kit.de.md) · [Русский](image-kit.ru.md) · [Українська](image-kit.uk.md) · [Polski](image-kit.pl.md) · [Português (Brasil)](image-kit.pt.md) · [Bahasa Indonesia](image-kit.id.md) · [Filipino](image-kit.tl.md) · [Tiếng Việt](image-kit.vi.md) · **简体中文** · [हिन्दी](image-kit.hi.md) · [עברית](image-kit.he.md) · [العربية](image-kit.ar.md)
<!-- /languages -->

图片通常是一个网站发出去最重的东西。Image Kit 会找出你项目里的 JPEG 和 PNG，为网页压缩它们：用纯 Java 重新编码生成更小的 `.min.jpg` 副本（什么都不用装），可选的缩小尺寸，以及在装了 `cwebp` 时借你自己的 `cwebp` 生成 `.webp` 副本。在这个版本的实测里，一张 17.8 MB 的壁纸变成了 347 KB 的 `.min.jpg` 和 342 KB 的 `.webp` — 小了 98%。

## 它守的规矩

- **原图从不被碰。**输出都是旁边的副本（`photo.min.jpg`、`photo.webp`），已经存在的输出会被跳过并说明 — 绝不覆盖。
- **什么都没省下的“优化”会被丢弃**：一次压缩如果省下不到 10%，结果会被删掉，并报告为*已经很紧凑*，而不是交出一个更大的“优化后”文件。（缩小了尺寸的输出无论如何都会保留 — 像素变少本来就是目的。）
- **故意不做 PNG 重新编码。**ImageIO 赢不了真正的 PNG 优化器，所以对 PNG 来说，诚实的收获是那份 WebP 副本。

## 步骤

1. **瞄准一个项目**，选择**文件 ▸ 添加到项目 ▸ Image Kit (Web)…**。对话框会告诉你它找到了多少张图片、总共有多重（node_modules 和构建产物会被跳过，它自己的 `.min.` 输出也一样 — 对压缩结果再压一次只会让损失叠加）。

2. **挑一种压法。**JPEG 质量（85 视觉无损 / 80 网页默认 / 70 激进），一个可选的最大宽度（2560 视网膜大图 / 1600 正文内容 / 800 缩略图），以及 — 如果 `cwebp` 在你的 PATH 里 — WebP 副本。如果不在，复选框会这样说明，并告诉你去哪里取（`brew install webp`）；环境诊断也会探测它。

3. **读报告。**每个文件一行：写出了什么、之前 → 之后的大小，或者什么都没写的诚实理由（“已经存在”、“已经很紧凑”）。省下的总字节数在最上面，旁边还有一段可以直接复制的 `<picture>` 片段：在支持的地方提供 WebP，否则退回到原图。

## 你刚学到了什么

- 网页图片优化，一样必需的工具都不用装 — 有 `cwebp` 时就用你自己的 `cwebp`。
- 套件家族“从不覆盖、如实报告”的规矩，对像素同样成立。
