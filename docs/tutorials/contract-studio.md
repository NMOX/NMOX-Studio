# Tutorial: Contract Studio (Web3)

Contract Studio is a full smart-contract workbench: a Foundry/Hardhat
artifact tree, ABI-driven interaction with decoded returns and reverts,
a live block/event watcher, and a gas + size oversight pane — with a
hard rule that **no private keys ever touch the IDE**.

This is the quick tour. For a complete worked example — writing an
escrow contract, testing it, and running it against a local chain — see
[making-a-smart-contract.md](../making-a-smart-contract.md).

<!-- screenshot: Contract Studio Interact pane with a decoded call result, Watch pane streaming blocks -->

## Open it

`⌥⌘6`, or the **Contract Studio** tab. You'll want Foundry (`anvil`,
`forge`) installed; check with `Tools ▸ Environment Doctor`.

## Steps

1. **Start a local chain.** In the rack, mount **ANVIL** and press GO —
   it runs a local EVM devnet with unlocked, pre-funded accounts.
   Contract Studio auto-connects to it.

2. **Build artifacts.** In a Foundry project, run `forge build` (the
   **FORGE** device, or the IDE's Build). Contract Studio's artifact
   tree fills in with your compiled contracts.

3. **Deploy and interact.** Pick a contract, press **Deploy** (it uses
   an unlocked anvil account — no key entry), then use the **Interact**
   pane: `CALL` a view function and see the decoded return; `SEND` a
   transaction and watch the receipt. Reverts and custom errors are
   decoded to readable text.

4. **Watch the chain.** The **Watch** pane polls new blocks every couple
   seconds and decodes event logs against your ABIs. The **Oversight**
   pane shows the gas table, EIP-170 size verdicts, and a deployment
   address book.

## What you just learned

- Sends go through a devnet's **unlocked accounts** — the IDE holds no
  key material and has no signing code.
- Secret RPC URLs are keychain-only and never serialized.
- A confirmation guards any send to a **non-loopback** endpoint, so you
  can't accidentally broadcast to a real chain.

## Next

- The full escrow walkthrough:
  [making-a-smart-contract.md](../making-a-smart-contract.md).
- GOVERNOR (gas gate) and the Web3 Bench preset live in the rack.
