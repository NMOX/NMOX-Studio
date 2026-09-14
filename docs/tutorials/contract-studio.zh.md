# 教程：合约工作室（Web3）

<!-- languages -->
[English](contract-studio.md) · [Español](contract-studio.es.md) · [Français](contract-studio.fr.md) · [Deutsch](contract-studio.de.md) · [Русский](contract-studio.ru.md) · [Українська](contract-studio.uk.md) · [Polski](contract-studio.pl.md) · [Português (Brasil)](contract-studio.pt.md) · [Bahasa Indonesia](contract-studio.id.md) · [Filipino](contract-studio.tl.md) · [Tiếng Việt](contract-studio.vi.md) · **简体中文** · [हिन्दी](contract-studio.hi.md) · [עברית](contract-studio.he.md) · [العربية](contract-studio.ar.md)
<!-- /languages -->

合约工作室是一整套智能合约工作台：Foundry/Hardhat 构件树、由 ABI 驱动并能解码返回值和回滚的交互、实时的区块/事件监视器，以及一个 gas 与体积的监督窗格 — 外加一条硬规矩：**私钥永远不碰 IDE**。

这是快速导览。想看一个完整的实例 — 写一个托管合约、测试它、在本地链上运行它 — 请读 [making-a-smart-contract.md](../making-a-smart-contract.md)。

![ANVIL 在机架上运行，合约工作室自己连上了它 — 链 31337，构件树里的合约显示着它的 EIP-170 体积占用](../images/contract-studio.png)

## 打开方式

`⌥⌘6`，或者 **合约工作室** 标签页。你需要装好 Foundry（`anvil`、`forge`）；用 `工具 ▸ 环境诊断…` 检查一下。

## 步骤

1. **启动一条本地链。**在机架上装一台 **ANVIL** 并按 GO — 它会运行一个本地 EVM 开发网，账户已解锁、预先充值。合约工作室会自动连上它。

2. **生成构件。**在一个 Foundry 项目里运行 `forge build`（用 **FORGE** 设备，或者 IDE 的构建）。合约工作室的构件树会填上你编译好的合约。

3. **部署并交互。**挑一个合约，按**部署**（它用的是一个已解锁的 anvil 账户 — 不用输入密钥），然后用**交互**窗格：`CALL` 一个 view 函数，看解码后的返回值；`SEND` 一笔交易，看着回执出来。回滚和自定义错误都会解码成可读的文字。

4. **监视这条链。****监视**窗格每隔几秒轮询一次新区块，并用你的 ABI 解码事件日志。**监督**窗格显示 gas 表、EIP-170 体积判定，以及一份部署地址簿。

## 你刚学到了什么

- 发送走的是开发网的**已解锁账户** — IDE 不持有任何密钥材料，也没有签名代码。
- 机密的 RPC 地址只存在钥匙串里，从不被序列化。
- 向任何**非本机回环**端点发送前都有一次确认，所以你不会不小心广播到一条真实的链上。

## 下一步

- 完整的托管合约导览：[making-a-smart-contract.md](../making-a-smart-contract.md)。
- GOVERNOR（gas 闸门）和 Web3 Bench 预设都在机架上。
