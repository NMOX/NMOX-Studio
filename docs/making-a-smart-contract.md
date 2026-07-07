# Making a Smart Contract — a worked example

A real contract, built the way [Contract Studio](user-guide.md#contract-studio-6)
and the rack expect you to work: Foundry project, tests-first, a local
chain, and gates on the things that silently rot. Everything below was
actually run; the numbers are real (forge 1.7.1, Solidity 0.8.x).

The example: **RiversideEscrow**, order escrow for a small hardware
store. A buyer funds an order, the store marks it shipped, the buyer
confirms delivery to release payment — and if the store never ships,
the buyer reclaims after a deadline. Small enough to read whole, real
enough to have adversaries.

## 0. Why contracts are built differently

Deployed bytecode is immutable and holds money. There is no hotfix and
no rollback, and every bug has a bounty attached — paid to whoever finds
it. So the workflow inverts: the contract is a state machine you design
first, the tests are most of the work, and observation (events, gas,
size) is a first-class deliverable, not an afterthought.

## 1. Scaffold

```bash
forge init riverside-escrow
cd riverside-escrow
rm src/Counter.sol test/Counter.t.sol script/Counter.s.sol
```

Open the folder in NMOX Studio — `foundry.toml` is a recognized manifest,
so it opens as a FOUNDRY project and the rack aims itself. Load the
**Web3 Bench** preset: ANVIL (local chain), FORGE (build/test lanes),
and GOVERNOR (gas gate), pre-wired.

## 2. Design the state machine, then write it

Five states, four transitions, each transition guarded by *who* may call
it and *which state* it's valid in, and each emitting an event. The full
contract, `src/RiversideEscrow.sol`:

```solidity
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// @title RiversideEscrow — order escrow for a small hardware store
/// @notice Buyer funds an order; the store marks it shipped; the buyer
/// confirms delivery to release payment. If the store never ships, the
/// buyer reclaims after a deadline. One contract instance per order —
/// deliberately: no order bookkeeping to get wrong, nothing upgradeable,
/// every state transition emits an event because events are a contract's
/// only honest output.
contract RiversideEscrow {
    enum State { AwaitingPayment, AwaitingShipment, Shipped, Complete, Refunded }

    address public immutable store;
    address public immutable buyer;
    uint256 public immutable price;
    uint256 public immutable shipBy; // store must ship before this timestamp

    State public state;

    event Funded(address indexed buyer, uint256 amount);
    event MarkedShipped(uint256 at);
    event Released(address indexed store, uint256 amount);
    event Refunded(address indexed buyer, uint256 amount);

    error WrongState(State expected, State actual);
    error NotBuyer();
    error NotStore();
    error WrongAmount(uint256 expected, uint256 sent);
    error DeadlineNotReached(uint256 deadline);

    modifier only(address who, bytes4 err) {
        if (msg.sender != who) {
            if (err == NotBuyer.selector) revert NotBuyer();
            revert NotStore();
        }
        _;
    }

    modifier inState(State expected) {
        if (state != expected) revert WrongState(expected, state);
        _;
    }

    constructor(address _buyer, uint256 _price, uint256 _shipWindow) {
        store = msg.sender;
        buyer = _buyer;
        price = _price;
        shipBy = block.timestamp + _shipWindow;
        state = State.AwaitingPayment;
    }

    /// @notice Buyer pays the exact price into escrow.
    function fund() external payable only(buyer, NotBuyer.selector) inState(State.AwaitingPayment) {
        if (msg.value != price) revert WrongAmount(price, msg.value);
        state = State.AwaitingShipment;
        emit Funded(msg.sender, msg.value);
    }

    /// @notice Store declares the order shipped.
    function markShipped() external only(store, NotStore.selector) inState(State.AwaitingShipment) {
        state = State.Shipped;
        emit MarkedShipped(block.timestamp);
    }

    /// @notice Buyer confirms delivery; escrow pays the store.
    function confirmDelivery() external only(buyer, NotBuyer.selector) inState(State.Shipped) {
        state = State.Complete;
        emit Released(store, price);
        // state is final before the transfer: checks-effects-interactions,
        // so a reentrant store address finds nothing left to do
        (bool ok, ) = store.call{value: price}("");
        require(ok, "transfer failed");
    }

    /// @notice If the store never ships, the buyer reclaims after the deadline.
    function refund() external only(buyer, NotBuyer.selector) inState(State.AwaitingShipment) {
        if (block.timestamp < shipBy) revert DeadlineNotReached(shipBy);
        state = State.Refunded;
        emit Refunded(buyer, price);
        (bool ok, ) = buyer.call{value: price}("");
        require(ok, "transfer failed");
    }
}
```

Design decisions worth stealing:

- **One instance per order.** No mappings of orders, no admin, nothing
  upgradeable — the cheapest audit is code that isn't there.
- **`immutable` everywhere possible.** The parties and price are fixed
  at deploy; they read like storage but cost like constants.
- **Custom errors, not require-strings** — cheaper, and Contract
  Studio's Interact pane decodes them into readable revert reasons.
- **Checks-effects-interactions.** State goes final *before* ether
  moves, so a malicious recipient re-entering `confirmDelivery` hits
  `WrongState`, not a second payout.
- **`block.timestamp` for a days-scale deadline is fine** — validators
  can nudge it by seconds, which is noise against a 3-day window.
  (forge's linter flags it; for deadlines this coarse the flag is
  acknowledged, not fixed. Never use it for second-scale logic.)

## 3. Tests are most of the work

`test/RiversideEscrow.t.sol` covers the happy path, every wrong-actor
and wrong-state rejection, the deadline in both directions, a **fuzz
test** proving no amount but the exact price funds the escrow, and
event pins (events are the contract's public record — test them like
return values):

```solidity
function test_RefundAfterDeadline() public {
    vm.prank(buyer);
    escrow.fund{value: PRICE}();

    vm.warp(block.timestamp + WINDOW + 1);   // time travel
    vm.prank(buyer);
    escrow.refund();

    assertEq(buyer.balance, 10 ether);       // made whole
}

function testFuzz_RevertWhen_WrongAmount(uint96 amount) public {
    vm.assume(amount != PRICE);
    vm.deal(buyer, uint256(amount));
    vm.expectRevert(
        abi.encodeWithSelector(RiversideEscrow.WrongAmount.selector, PRICE, amount));
    vm.prank(buyer);
    escrow.fund{value: amount}();
}
```

`vm.prank` impersonates callers, `vm.warp` time-travels, `vm.deal`
mints test ether — the chain is yours in tests. The run (FORGE's test
lane, or `forge test`):

```
Ran 9 tests for test/RiversideEscrow.t.sol:RiversideEscrowTest
[PASS] testFuzz_RevertWhen_WrongAmount(uint96) (runs: 256, μ: 22449, ~: 22554)
[PASS] test_EmitsFundedAndReleased() (gas: 62013)
[PASS] test_HappyPath_FundShipConfirm() (gas: 59786)
[PASS] test_RefundAfterDeadline() (gas: 54421)
[PASS] test_RevertWhen_BuyerConfirmsBeforeShipment() (gas: 44800)
[PASS] test_RevertWhen_RefundAfterShipment() (gas: 50818)
[PASS] test_RevertWhen_RefundBeforeDeadline() (gas: 45193)
[PASS] test_RevertWhen_StoreShipsUnfunded() (gas: 14752)
[PASS] test_RevertWhen_StrangerFunds() (gas: 18539)
Suite result: ok. 9 passed; 0 failed; 0 skipped; finished in 14.84ms
```

Note `test_RevertWhen_RefundAfterShipment`: once the store ships, the
deadline no longer rescues the buyer. That's a *policy* encoded in
state transitions — the test documents it so nobody "fixes" it later.

## 4. Gate what silently rots

```bash
forge snapshot        # writes .gas-snapshot — commit it
forge build --sizes
```

Real numbers for this contract: runtime **4,119 bytes** — 20,457 under
the EIP-170 deploy ceiling (Contract Studio's Oversight pane shows this
verdict per contract). The committed `.gas-snapshot` is what the
**GOVERNOR** device gates on: edit the contract, and if a function's
gas regresses past your tolerance, the pipeline goes red before review.

## 5. The live loop

1. **Start ANVIL** in the rack — a local EVM, instant blocks, ten
   unlocked accounts with play ETH. Contract Studio's network chip
   connects by itself (and greys the moment the chain stops).
2. **Build** (FORGE lane) — artifacts appear in Contract Studio's tree
   automatically after every build.
3. **Deploy and poke** in the **Interact** pane: constructor args from
   the ABI, `CALL` for reads (decoded returns), `SEND` for writes
   (receipts, decoded custom errors when you get the state machine
   wrong on purpose — try `refund()` before the deadline and watch
   `DeadlineNotReached(uint256)` come back readable).
4. **Watch** streams blocks and decodes your `Funded`/`Released` events
   live — walk the whole order lifecycle and read it like a ledger.
5. **Oversight** holds the gas table, the size verdicts, and the
   deployment address book (persisted per-project in `.nmoxweb3.json`).

The safety rule the IDE enforces and you should too: **no private keys
in dev tooling, ever.** Sends go through the devnet's unlocked accounts;
secret RPC URLs live in the OS keychain and never serialize to disk.
When something graduates toward a real network, signing belongs in a
hardware wallet — and the contract belongs in front of a fuzzing
campaign and ideally an audit first.

## 6. Where to go from here

- Inherit, don't reimplement: OpenZeppelin for tokens/access control.
- `forge test --gas-report` for the per-function table; invariant tests
  (`forge` supports them) once state machines get bigger than this one.
- The **solidity/chisel Learning Space** (*New Learning Space…*) gives
  you a Solidity REPL in the rack for trying expressions before they
  earn a place in a contract.
