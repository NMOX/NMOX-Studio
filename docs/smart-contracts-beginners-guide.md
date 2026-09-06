# A Beginner's Guide to Smart Contracts

You don't need to own any cryptocurrency, run a server, or hold a
single key to understand — or even write and test — smart contracts.
Everything in this guide runs locally, costs nothing, and every code
sample is real: it's the same code NMOX Studio's
[Contract Kit](user-guide.md#8-wizards--kits) scaffolds and its
[learning spaces](user-guide.md#7-learning-spaces) teach, and each one
ran green against its real toolchain before it shipped.

Already comfortable with the ideas? Skip to
**[Making a Smart Contract](making-a-smart-contract.md)** — a full
worked example with tests, gas gates, and a live local chain.

---

## 1. What a smart contract actually is

A smart contract is a small program whose execution is **checked by
many machines that don't trust each other**. That single property
explains almost everything that feels strange about them:

- **The rules can't be quietly changed.** Once deployed, the code (or
  the spending condition) is what everyone verifies. There is no
  hotfix, no rollback, no "we'll patch it Monday."
- **Someone pays for every step.** Computation that thousands of
  machines repeat isn't free, so execution is metered — *gas*, *fees*,
  *execution units*. Cost is part of correctness.
- **The failure paths matter most.** A web app that rejects a bad
  request saved you a support ticket. A contract that *fails* to
  reject a bad withdrawal loses the money — permanently, publicly, to
  whoever noticed first.

That's the whole mental shift. The rest is vocabulary and tooling.

## 2. Five shapes a "contract" can take

"Smart contract" is one phrase covering several genuinely different
designs. NMOX Studio's Contract Kit spans eleven chains precisely
because seeing the shapes side by side is the fastest way to
understand any one of them:

| Shape | Chains | The mental model |
|---|---|---|
| **Deployed program** | Ethereum/EVM, Solana, CosmWasm, ink!, Cairo | Code lives at an address; anyone can call its functions; it owns its state (and often funds). |
| **Pure validator** | Cardano (Aiken) | A function that answers exactly one question — *may this spend happen?* — given the locked state, the spender's argument, and the transaction. |
| **Spending condition** | Bitcoin (Miniscript) | No deployed programs at all. Rules are locked onto coins themselves: *two signatures, or one backup key after a day.* |
| **Actor** | TON (Tact) | The contract is a mailbox: it holds state and answers typed messages. A refusal is a *bounced message*, not an exception. |
| **Decidable script** | Stacks (Clarity) | Interpreted on-chain — the source you read *is* the contract — and deliberately not Turing-complete, so costs are knowable before you run. |

Don't memorize the table. Just remember that when something about one
chain confuses you, another chain's shape is usually the explanation:
Bitcoin makes sense once you stop looking for the program; Cardano
makes sense once you see the validator never *does* anything, it only
*permits*.

## 3. The words that actually matter

- **Transaction** — a signed request to change chain state. Everything
  happens through one.
- **Address** — where a contract or a wallet lives. Derived from keys,
  but not a key itself.
- **Wallet / keys** — the private key signs transactions. **Keys are
  not code.** They never belong in a repo, an IDE, or an environment
  variable you'd commit. (NMOX Studio enforces this by construction:
  there are no key fields anywhere in the product; signing always
  belongs to the chain's own CLI or your wallet.)
- **Gas / fees** — the metered cost of execution.
- **State / datum / storage** — the data the contract keeps between
  transactions.
- **Devnet / testnet / mainnet** — your machine's throwaway chain, a
  public rehearsal chain with worthless tokens, and the real thing.
  You will live on devnets for a long time, happily.
- **Deploy** — publishing the program (or locking the condition).
  After this, see "no hotfix" above.

## 4. Truths beginners learn the hard way (learn them the easy way)

**The refusals ARE the contract.** A counter anyone can increment is a
toy; *only the owner can reset it* is a contract. So test the refusal
as carefully as the success — every chain has an idiom for exactly
this, and the kit's starters use all of them:

```aiken
// Cardano — the refusal is a DECLARED test: it must fail to pass
test vault_refuses_wrong_word() fail {
  ...
}
```

```typescript
// TON — the refusal is a bounced transaction, asserted as such
expect(refused.transactions).toHaveTransaction({
  to: contract.address, success: false });
```

```clarity
;; Clarity — the refusal is a first-class error value
(asserts! (is-eq tx-sender contract-owner) err-owner-only)  ;; (err u100)
```

```text
Bitcoin — the refusal is unspendability itself:
or(and(pk(OwnerA),pk(OwnerB)),and(pk(Backup),older(144)))
— no path matches, no spend. The condition is the whole contract.
```

**Tests come first, and they run in milliseconds.** None of the tests
above need a node, a network, or a token. `forge test`, `aiken check`,
`clarinet check` + a simnet, TON's in-memory sandbox — the loop is as
fast as any unit test you've ever run. If someone tells you contract
development means waiting on a blockchain, they're doing it wrong.

**Immutability inverts the workflow.** Because there's no hotfix, the
contract is a state machine you design *before* you write it, the
tests are most of the work, and observation — events, gas, size — is
a deliverable, not an afterthought. This is the single biggest
difference from web development.

**Costs are correctness.** A function that works but costs too much
gas is broken; a contract over the EVM's size limit won't deploy at
all. That's why the rack has a GOVERNOR gas gate and Contract Studio
renders EIP-170 size verdicts — the same way VERITAS gates coverage.

## 5. Your first contract, in about ten minutes

Three paths, easiest first. All of them: no network, no tokens, no
keys. Pick one — they end at the same place, a green test that
includes a refusal.

### Path A — Clarity (the most readable start)

1. `brew install clarinet` (or let the space's INSTALL button do it).
2. In NMOX Studio: **File ▸ New Learning Space… ▸ Clarity (Stacks)**.
3. Press **GO** — `clarinet check` type-checks the counter instantly.
4. Follow the tutorial's two SOLDER lines (`npm install`, `npm test`):
   three tests run on an in-memory Stacks chain, including the
   stranger whose reset comes back `(err u100)`.

The whole contract is ~20 lines and reads almost like the table of
rules it is. Start here if you've never seen a contract before.

### Path B — Cardano/Aiken (the purest shape)

1. `brew install aiken`.
2. **File ▸ New Learning Space… ▸ Cardano (Aiken)**, then **GO** —
   `aiken check` compiles the vault and runs both tests, including the
   one *declared to fail*.
3. Read `validators/vault.ak`: the entire contract is one pure
   function answering "may this spend happen?"

Start here if you like functional programming, or you want the
clearest possible view of what a validator is.

### Path C — Solidity/Foundry (the ecosystem standard)

1. In any project: **File ▸ Contract Kit (Web3)… ▸ Solidity (EVM)**,
   name your contract, OK.
2. Follow `CONTRACT-NOTES.md`'s one-time line (`forge install
   foundry-rs/forge-std`), then **VERITAS ▸ GO** runs `forge test`.
3. When you're ready for a *live* loop: rack an **ANVIL** device (a
   local EVM chain), and Contract Studio (⌥⌘6) auto-connects — CALL
   and SEND with decoded returns, watch blocks and events stream, all
   against unlocked devnet accounts that hold nothing real.

Start here if you're heading toward the EVM ecosystem, which is where
most contract work is today — then graduate to
**[the worked escrow example](making-a-smart-contract.md)**.

## 6. Where to go next

- **[Making a Smart Contract](making-a-smart-contract.md)** — the full
  workflow on a real escrow: state machine first, tests-first,
  adversarial tests, gas gates, live ANVIL loop.
- **Eight more Web3 learning spaces** — Stellar/Soroban, Solana,
  CosmWasm, ink!, Cairo, Move, Bitcoin/Miniscript, TON — each one a
  real project proven against its real toolchain
  (**File ▸ New Learning Space…**, search "contract").
- **[The device reference](devices.md)** — STELLAR, ANCHOR, ANVIL, and
  GOVERNOR, the rack's chain consoles and gates.
- **Ask KVASIR** — select any contract code you don't understand,
  right-click, *Ask KVASIR About Selection…*, and keep asking
  follow-ups. It only ever sees the selection you chose.

One honest closing note: this guide gets you to *understanding* and
*local testing*, which is most of the craft. Deploying to a real
network with real value is a different level of care — audits, staged
rollouts, monitoring — and no beginner's guide should pretend
otherwise. When you get there, the habits this page taught you (test
the refusals, respect immutability, meter your costs, keep keys out of
code) are exactly the ones that transfer.
