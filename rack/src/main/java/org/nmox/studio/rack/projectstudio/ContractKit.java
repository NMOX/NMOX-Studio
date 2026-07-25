package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Contract Kit: scaffold a smart contract for any chain the studio
 * speaks — Solidity/Foundry, Soroban (Stellar), Solana, CosmWasm, ink!,
 * Cairo, Move (Sui), Bitcoin (Script/Miniscript), Clarity (Stacks),
 * Cardano (Aiken), or TON (Tact) —
 * into the aimed project. Every template is a LIVE-PROVEN starter (each
 * ran green against its real toolchain before shipping), name-templated
 * for your contract.
 *
 * House laws, same as Standards/PWA/Classic Kit: idempotent and
 * never-clobbering (an existing file gets a {@code .suggested} sibling,
 * an identical file is left untouched), pure generation with zero UI so
 * plain tests reach everything, and no keys — deploy identities belong
 * to each chain's own CLI.
 */
public final class ContractKit {

    private ContractKit() {
    }

    /** The chains the kit scaffolds, in shelf order. */
    public enum Chain {
        FOUNDRY("Solidity (EVM) — Foundry", "forge"),
        SOROBAN("Stellar — Soroban", "stellar"),
        SOLANA("Solana — native program", "cargo"),
        COSMWASM("CosmWasm (Cosmos)", "cargo"),
        INK("ink! (Polkadot)", "cargo"),
        CAIRO("Cairo (Starknet)", "scarb"),
        MOVE("Move (Sui)", "sui"),
        BITCOIN("Bitcoin — Script/Miniscript", "cargo"),
        CLARITY("Clarity (Stacks)", "clarinet"),
        AIKEN("Cardano — Aiken", "aiken"),
        TACT("TON — Tact", "npm");

        public final String label;
        /** The tool the scaffold's test lane needs on PATH. */
        public final String tool;

        Chain(String label, String tool) {
            this.label = label;
            this.tool = tool;
        }
    }

    public record Outcome(String path, String status, boolean changed) {
    }

    /** A valid contract name: an identifier the chain dialects all accept. */
    public static String validate(String name) {
        if (name == null || name.isBlank()) {
            return "Name the contract — it becomes the module/struct/crate name.";
        }
        if (!name.matches("[A-Za-z][A-Za-z0-9_]*")) {
            return "Contract names are identifiers: letters, digits, underscores, "
                    + "starting with a letter — got \"" + name + "\".";
        }
        return null;
    }

    /** MyToken. */
    static String pascal(String name) {
        String[] parts = name.split("_");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
        }
        return out.toString();
    }

    /** my_token. */
    static String snake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    /** my-token — Clarity contract names are lowercase-hyphen (SIP-003). */
    static String kebab(String name) {
        return snake(name).replace('_', '-');
    }

    /** Scaffolds the chain's starter into {@code dir}; never clobbers. */
    public static List<Outcome> scaffold(File dir, Chain chain, String name)
            throws IOException {
        String invalid = validate(name);
        if (invalid != null) {
            throw new IllegalArgumentException(invalid);
        }
        List<Outcome> outcomes = new ArrayList<>();
        switch (chain) {
            case FOUNDRY -> foundry(dir, name, outcomes);
            case SOROBAN -> soroban(dir, name, outcomes);
            case SOLANA -> solana(dir, name, outcomes);
            case COSMWASM -> cosmwasm(dir, name, outcomes);
            case INK -> ink(dir, name, outcomes);
            case CAIRO -> cairo(dir, name, outcomes);
            case MOVE -> move(dir, name, outcomes);
            case BITCOIN -> bitcoin(dir, name, outcomes);
            case CLARITY -> clarity(dir, name, outcomes);
            case AIKEN -> aiken(dir, name, outcomes);
            case TACT -> tact(dir, name, outcomes);
        }
        return outcomes;
    }

    // --- Solidity / Foundry -------------------------------------------------

    private static void foundry(File dir, String name, List<Outcome> out) throws IOException {
        String p = pascal(name);
        write(dir, "foundry.toml", """
                [profile.default]
                src = "src"
                out = "out"
                libs = ["lib"]
                """, out);
        write(dir, "src/" + p + ".sol", """
                // SPDX-License-Identifier: MIT
                pragma solidity ^0.8.24;

                /// A minimal, honest starting point: owned counter with an event.
                contract %P% {
                    address public immutable owner;
                    uint256 public count;

                    event Bumped(address indexed by, uint256 newCount);

                    constructor() {
                        owner = msg.sender;
                    }

                    function bump() external {
                        count += 1;
                        emit Bumped(msg.sender, count);
                    }
                }
                """.replace("%P%", p), out);
        write(dir, "test/" + p + ".t.sol", """
                // SPDX-License-Identifier: MIT
                pragma solidity ^0.8.24;

                import {Test} from "forge-std/Test.sol";
                import {%P%} from "../src/%P%.sol";

                contract %P%Test is Test {
                    %P% c;

                    function setUp() public {
                        c = new %P%();
                    }

                    function test_Bumps() public {
                        c.bump();
                        assertEq(c.count(), 1);
                    }
                }
                """.replace("%P%", p), out);
        notes(dir, out, """
                # %P% — next steps (Foundry)

                One-time: the test imports forge-std, so from this directory:

                    git init            # if this isn't a repo yet
                    forge install foundry-rs/forge-std

                Then the rack takes over: FORGE builds (`forge build`),
                VERITAS tests (`forge test`), GOVERNOR holds the gas line,
                ANVIL runs your local chain, and Contract Studio (⌥⌘6)
                connects to it for CALL/SEND with decoded returns.
                """.replace("%P%", pascal(name)));
    }

    // --- Soroban (Stellar) --------------------------------------------------

    private static void soroban(File dir, String name, List<Outcome> out) throws IOException {
        String s = snake(name);
        String p = pascal(name);
        write(dir, "Cargo.toml", """
                [package]
                name = "%S%"
                version = "0.1.0"
                edition = "2021"

                [lib]
                crate-type = ["cdylib", "rlib"]

                [dependencies]
                soroban-sdk = "27"

                [dev-dependencies]
                soroban-sdk = { version = "27", features = ["testutils"] }

                [profile.release]
                opt-level = "z"
                overflow-checks = true
                debug = 0
                strip = "symbols"
                debug-assertions = false
                panic = "abort"
                codegen-units = 1
                lto = true
                """.replace("%S%", s), out);
        write(dir, "src/lib.rs", """
                #![no_std]
                use soroban_sdk::{contract, contractimpl, symbol_short, vec, Env, Symbol, Vec};

                #[contract]
                pub struct %P%;

                #[contractimpl]
                impl %P% {
                    pub fn hello(env: Env, to: Symbol) -> Vec<Symbol> {
                        vec![&env, symbol_short!("Hello"), to]
                    }
                }

                #[cfg(test)]
                mod test {
                    use super::*;
                    use soroban_sdk::{symbol_short, vec, Env};

                    #[test]
                    fn says_hello() {
                        let env = Env::default();
                        let id = env.register(%P%, ());
                        let client = %P%Client::new(&env, &id);
                        let words = client.hello(&symbol_short!("Dev"));
                        assert_eq!(words, vec![&env, symbol_short!("Hello"), symbol_short!("Dev")]);
                    }
                }
                """.replace("%P%", p), out);
        notes(dir, out, """
                # %P% — next steps (Soroban)

                Tests run natively: VERITAS (`cargo test`). Build real WASM
                with the STELLAR device's BUILD (`stellar contract build`;
                one-time `rustup target add wasm32v1-none`). Its ACTION knob
                starts the quickstart local network; deploy/invoke are SOLDER
                one-liners with identities the stellar CLI keeps itself.
                """.replace("%P%", p));
    }

    // --- Solana ---------------------------------------------------------------

    private static void solana(File dir, String name, List<Outcome> out) throws IOException {
        String s = snake(name);
        write(dir, "Cargo.toml", """
                [package]
                name = "%S%"
                version = "0.1.0"
                edition = "2021"

                [lib]
                crate-type = ["cdylib", "lib"]

                [dependencies]
                solana-program = "4"
                """.replace("%S%", s), out);
        write(dir, "src/lib.rs", """
                use solana_program::{
                    account_info::AccountInfo, entrypoint, entrypoint::ProgramResult, msg,
                    pubkey::Pubkey,
                };

                entrypoint!(process_instruction);

                pub fn process_instruction(
                    _program_id: &Pubkey,
                    _accounts: &[AccountInfo],
                    instruction_data: &[u8],
                ) -> ProgramResult {
                    msg!("counter incremented to {}", bump(instruction_data.first().copied()));
                    Ok(())
                }

                /// Pure logic tests natively — no validator, just cargo test.
                pub fn bump(current: Option<u8>) -> u8 {
                    current.unwrap_or(0).saturating_add(1)
                }

                #[cfg(test)]
                mod test {
                    use super::*;

                    #[test]
                    fn bumps_from_empty_and_saturates() {
                        assert_eq!(bump(None), 1);
                        assert_eq!(bump(Some(41)), 42);
                        assert_eq!(bump(Some(u8::MAX)), u8::MAX);
                    }
                }
                """, out);
        notes(dir, out, """
                # %P% — next steps (Solana)

                VERITAS runs the native tests (`cargo test`). The ANCHOR
                device's START boots `solana-test-validator` with a live RPC
                URL and a truthful SERVING gate; on-chain builds use the SBF
                toolchain (`cargo build-sbf`, ships with the Solana CLI).
                Keypairs live in the solana CLI's own config.
                """.replace("%P%", pascal(name)));
    }

    // --- CosmWasm ---------------------------------------------------------------

    private static void cosmwasm(File dir, String name, List<Outcome> out) throws IOException {
        String s = snake(name);
        write(dir, "Cargo.toml", """
                [package]
                name = "%S%"
                version = "0.1.0"
                edition = "2021"

                [lib]
                crate-type = ["cdylib", "rlib"]

                [dependencies]
                cosmwasm-std = "3"
                serde = { version = "1", features = ["derive"] }
                """.replace("%S%", s), out);
        write(dir, "src/lib.rs", """
                use cosmwasm_std::{
                    entry_point, to_json_binary, Binary, Deps, DepsMut, Env, MessageInfo,
                    Response, StdResult,
                };
                use serde::{Deserialize, Serialize};

                #[derive(Serialize, Deserialize)]
                pub struct InstantiateMsg {}

                #[derive(Serialize, Deserialize)]
                pub enum QueryMsg {
                    Greet { name: String },
                }

                #[entry_point]
                pub fn instantiate(
                    _deps: DepsMut, _env: Env, _info: MessageInfo, _msg: InstantiateMsg,
                ) -> StdResult<Response> {
                    Ok(Response::new().add_attribute("action", "instantiate"))
                }

                #[entry_point]
                pub fn query(_deps: Deps, _env: Env, msg: QueryMsg) -> StdResult<Binary> {
                    match msg {
                        QueryMsg::Greet { name } => to_json_binary(&greeting(&name)),
                    }
                }

                pub fn greeting(name: &str) -> String {
                    format!("Hello, {name}! Welcome to the interchain.")
                }

                #[cfg(test)]
                mod test {
                    use super::*;
                    use cosmwasm_std::testing::{message_info, mock_dependencies, mock_env};

                    #[test]
                    fn instantiates_and_greets() {
                        let mut deps = mock_dependencies();
                        let creator = deps.api.addr_make("creator");
                        let res = instantiate(
                            deps.as_mut(), mock_env(), message_info(&creator, &[]), InstantiateMsg {},
                        ).unwrap();
                        assert_eq!(res.attributes[0].value, "instantiate");
                        assert_eq!(greeting("Dev"), "Hello, Dev! Welcome to the interchain.");
                    }
                }
                """, out);
        notes(dir, out, """
                # %P% — next steps (CosmWasm)

                VERITAS runs the tests against an in-memory chain
                (`cargo test`). Chain builds target
                `wasm32-unknown-unknown`; deploys ride a chain daemon like
                wasmd — SOLDER one-liners, keys in the chain CLI's keyring.
                """.replace("%P%", pascal(name)));
    }

    // --- ink! ---------------------------------------------------------------

    private static void ink(File dir, String name, List<Outcome> out) throws IOException {
        String s = snake(name);
        String p = pascal(name);
        write(dir, "Cargo.toml", """
                [package]
                name = "%S%"
                version = "0.1.0"
                edition = "2021"

                [dependencies]
                ink = { version = "5", default-features = false }

                [lib]
                path = "src/lib.rs"

                [features]
                default = ["std"]
                std = ["ink/std"]
                ink-as-dependency = []
                """.replace("%S%", s), out);
        write(dir, "src/lib.rs", """
                #![cfg_attr(not(feature = "std"), no_std, no_main)]

                #[ink::contract]
                mod %S% {

                    #[ink(storage)]
                    pub struct %P% {
                        value: bool,
                    }

                    impl %P% {
                        #[ink(constructor)]
                        pub fn new(init: bool) -> Self {
                            Self { value: init }
                        }

                        #[ink(message)]
                        pub fn flip(&mut self) {
                            self.value = !self.value;
                        }

                        #[ink(message)]
                        pub fn get(&self) -> bool {
                            self.value
                        }
                    }

                    #[cfg(test)]
                    mod tests {
                        use super::*;

                        #[ink::test]
                        fn flips() {
                            let mut c = %P%::new(false);
                            assert!(!c.get());
                            c.flip();
                            assert!(c.get());
                        }
                    }
                }
                """.replace("%S%", s).replace("%P%", p), out);
        notes(dir, out, """
                # %P% — next steps (ink!)

                VERITAS runs the off-chain tests (`cargo test`). Deployable
                bundles come from cargo-contract (`cargo contract build`) and
                substrate-contracts-node runs a local chain — SOLDER
                one-liners; keys stay in the node/CLI keyring.
                """.replace("%P%", p));
    }

    // --- Cairo ---------------------------------------------------------------

    private static void cairo(File dir, String name, List<Outcome> out) throws IOException {
        String s = snake(name);
        write(dir, "Scarb.toml", """
                [package]
                name = "%S%"
                version = "0.1.0"
                edition = "2024_07"

                [dev-dependencies]
                cairo_test = "2"
                """.replace("%S%", s), out);
        write(dir, "src/lib.cairo", """
                /// Provable computation: Cairo functions compile to STARK-provable traces.
                pub fn fib(n: u32) -> u128 {
                    let mut a: u128 = 0;
                    let mut b: u128 = 1;
                    let mut i: u32 = 0;
                    while i != n {
                        let next = a + b;
                        a = b;
                        b = next;
                        i += 1;
                    };
                    a
                }

                #[cfg(test)]
                mod tests {
                    use super::fib;

                    #[test]
                    fn fib_works() {
                        assert!(fib(0) == 0, "base");
                        assert!(fib(10) == 55, "tenth");
                    }
                }
                """, out);
        notes(dir, out, """
                # %P% — next steps (Cairo/Starknet)

                Every lane speaks scarb: VERITAS tests (`scarb test`, with a
                gas estimate beside each pass), FORGE builds. Starknet
                contracts add `#[starknet::contract]` modules and deploy with
                Starknet Foundry (snforge/sncast) — SOLDER one-liners; account
                keys live in sncast's own config.
                """.replace("%P%", pascal(name)));
    }

    // --- Move (Sui) ---------------------------------------------------------------

    private static void move(File dir, String name, List<Outcome> out) throws IOException {
        String s = snake(name);
        write(dir, "Move.toml", """
                [package]
                name = "%S%"
                edition = "2024"

                [dependencies]
                """.replace("%S%", s), out);
        write(dir, "sources/counter.move", """
                /// An owned Counter object: Move's resource types make assets
                /// impossible to copy or silently drop.
                module %S%::counter {

                    public struct Counter has key, store {
                        id: UID,
                        value: u64,
                    }

                    #[allow(lint(self_transfer))]
                    public fun create(ctx: &mut TxContext) {
                        transfer::public_transfer(
                            Counter { id: object::new(ctx), value: 0 },
                            tx_context::sender(ctx),
                        );
                    }

                    public fun bump(counter: &mut Counter) {
                        counter.value = counter.value + 1;
                    }

                    public fun value(counter: &Counter): u64 {
                        counter.value
                    }
                }
                """.replace("%S%", s), out);
        write(dir, "tests/counter_tests.move", """
                #[test_only]
                module %S%::counter_tests {
                    use %S%::counter::{Self, Counter};
                    use sui::test_scenario;

                    #[test]
                    fun bumps() {
                        let dev = @0xCAFE;
                        let mut sc = test_scenario::begin(dev);
                        counter::create(sc.ctx());
                        sc.next_tx(dev);
                        {
                            let mut c = sc.take_from_sender<Counter>();
                            assert!(counter::value(&c) == 0, 0);
                            counter::bump(&mut c);
                            assert!(counter::value(&c) == 1, 1);
                            sc.return_to_sender(c);
                        };
                        sc.end();
                    }
                }
                """.replace("%S%", s), out);
        notes(dir, out, """
                # %P% — next steps (Move on Sui)

                VERITAS runs `sui move test` against an in-memory chain;
                FORGE builds. `sui start` runs a local network and
                `sui client publish` deploys — SOLDER one-liners; addresses
                and keys live in the sui CLI's own config.
                """.replace("%P%", pascal(name)));
    }

    // --- Bitcoin (Script / Miniscript) -------------------------------------

    private static void bitcoin(File dir, String name, List<Outcome> out) throws IOException {
        String s = snake(name);
        write(dir, "Cargo.toml", """
                [package]
                name = "%S%"
                version = "0.1.0"
                edition = "2021"

                [dependencies]
                miniscript = { version = "13", features = ["compiler"] }
                """.replace("%S%", s), out);
        write(dir, "src/lib.rs", """
                //! A Bitcoin contract is a SPENDING CONDITION: rules locked onto
                //! coins that the whole network enforces. This one is a 2-of-2
                //! vault with a timelocked recovery path, written as a Miniscript
                //! policy and compiled to analyzable consensus Script.
                use miniscript::policy::Concrete;
                use miniscript::{Miniscript, Segwitv0};
                use std::str::FromStr;

                /// Both owners sign, OR the backup key signs after ~1 day (144 blocks).
                pub const POLICY: &str =
                    "or(and(pk(OwnerA),pk(OwnerB)),and(pk(Backup),older(144)))";

                pub fn compile() -> Miniscript<String, Segwitv0> {
                    Concrete::<String>::from_str(POLICY)
                        .expect("policy parses")
                        .compile()
                        .expect("policy compiles")
                }

                #[cfg(test)]
                mod tests {
                    use super::*;

                    #[test]
                    fn vault_compiles_and_is_sane() {
                        let ms = compile();
                        ms.sanity_check().expect("consensus-sane");
                        assert!(ms.script_size() > 0);
                        assert!(format!("{ms}").contains("older(144)"));
                    }
                }
                """, out);
        notes(dir, out, """
                # %P% — next steps (Bitcoin)

                VERITAS runs the policy tests (`cargo test`) — no node needed;
                the compiler proves the script is consensus-sane. For a real
                chain, `brew install bitcoin` and SOLDER two lines:

                    bitcoind -regtest -fallbackfee=0.0001
                    bitcoin-cli -regtest createwallet dev

                Descriptor wallets take Miniscript policies directly. Keys live
                in Core's own wallet; the IDE never sees them. Want richer
                contracts anchored to Bitcoin? Clarity on Stacks, or RSK via the
                Foundry/ANVIL lanes.
                """.replace("%P%", pascal(name)));
    }

    // --- Clarity / Stacks ---------------------------------------------------

    private static void clarity(File dir, String name, List<Outcome> out) throws IOException {
        String k = kebab(name);
        // clarinet's own scaffold defaults telemetry to TRUE — ours never does
        write(dir, "Clarinet.toml", """
                [project]
                name = "%K%"
                telemetry = false
                cache_dir = "./.cache"

                [contracts.%K%]
                path = "contracts/%K%.clar"
                clarity_version = 5
                epoch = "latest"
                """.replace("%K%", k), out);
        // Clarity source is ASCII-ONLY, comments included — the live proof
        // failed on an em-dash before a single line of product code existed
        write(dir, "contracts/" + k + ".clar", """
                ;; %K% - a value only its deployer can reset.
                ;; Clarity is decidable and interpreted on-chain: what you read
                ;; here is exactly what the network runs. ASCII only, by rule.
                (define-data-var count uint u0)
                (define-constant contract-owner tx-sender)
                (define-constant err-owner-only (err u100))

                (define-read-only (get-count)
                  (var-get count))

                (define-public (increment)
                  (begin
                    (var-set count (+ (var-get count) u1))
                    (ok (var-get count))))

                (define-public (reset)
                  (begin
                    (asserts! (is-eq tx-sender contract-owner) err-owner-only)
                    (var-set count u0)
                    (ok true)))
                """.replace("%K%", k), out);
        write(dir, "tests/" + k + ".test.ts", """
                import { describe, expect, it } from "vitest";
                import { Cl } from "@stacks/transactions";

                const accounts = simnet.getAccounts();
                const wallet1 = accounts.get("wallet_1")!;
                const deployer = accounts.get("deployer")!;

                describe("%K%", () => {
                  it("starts at zero", () => {
                    const { result } = simnet.callReadOnlyFn("%K%", "get-count", [], wallet1);
                    expect(result).toBeUint(0);
                  });

                  it("increment bumps the count", () => {
                    simnet.callPublicFn("%K%", "increment", [], wallet1);
                    simnet.callPublicFn("%K%", "increment", [], wallet1);
                    const { result } = simnet.callReadOnlyFn("%K%", "get-count", [], wallet1);
                    expect(result).toBeUint(2);
                  });

                  it("only the owner can reset", () => {
                    simnet.callPublicFn("%K%", "increment", [], wallet1);
                    const stranger = simnet.callPublicFn("%K%", "reset", [], wallet1);
                    expect(stranger.result).toBeErr(Cl.uint(100));
                    const owner = simnet.callPublicFn("%K%", "reset", [], deployer);
                    expect(owner.result).toBeOk(Cl.bool(true));
                    const { result } = simnet.callReadOnlyFn("%K%", "get-count", [], wallet1);
                    expect(result).toBeUint(0);
                  });
                });
                """.replace("%K%", k), out);
        write(dir, "package.json", """
                {
                  "name": "%K%-tests",
                  "private": true,
                  "type": "module",
                  "scripts": {
                    "test": "vitest run"
                  },
                  "dependencies": {
                    "@stacks/clarinet-sdk": "^3.9.0",
                    "@stacks/transactions": "^7.2.0",
                    "vitest": "^4.1.8",
                    "vitest-environment-clarinet": "^3.0.0"
                  }
                }
                """.replace("%K%", k), out);
        write(dir, "vitest.config.ts", """
                import { defineConfig } from "vitest/config";
                import {
                  vitestSetupFilePath,
                  getClarinetVitestsArgv,
                } from "@stacks/clarinet-sdk/vitest";

                // vitest-environment-clarinet boots the simnet and exposes the
                // global `simnet` plus the Clarity matchers (toBeUint, toBeErr).
                export default defineConfig({
                  test: {
                    environment: "clarinet",
                    pool: "forks",
                    isolate: false,
                    maxWorkers: 1,
                    setupFiles: [vitestSetupFilePath],
                    environmentOptions: {
                      clarinet: { ...getClarinetVitestsArgv() },
                    },
                  },
                });
                """, out);
        write(dir, "tsconfig.json", """
                {
                  "compilerOptions": {
                    "target": "ESNext",
                    "module": "ESNext",
                    "lib": ["ESNext"],
                    "skipLibCheck": true,
                    "moduleResolution": "bundler",
                    "isolatedModules": true,
                    "noEmit": true,
                    "strict": true
                  },
                  "include": [
                    "node_modules/@stacks/clarinet-sdk/vitest-helpers/src",
                    "tests"
                  ]
                }
                """, out);
        // clarinet refuses to run without this file (the live proof caught
        // it). These are clarinet's own PUBLISHED devnet defaults — the same
        // public mnemonics every `clarinet new` scaffold on GitHub carries,
        // the anvil-unlocked-accounts class: never real funds, never a secret.
        write(dir, "settings/Devnet.toml", """
                # clarinet's published devnet accounts - public, never real funds
                [network]
                name = "devnet"

                [accounts.deployer]
                mnemonic = "twice kind fence tip hidden tilt action fragile skin nothing glory cousin green tomorrow spring wrist shed math olympic multiply hip blue scout claw"
                balance = 100_000_000_000_000

                [accounts.wallet_1]
                mnemonic = "sell invite acquire kitten bamboo drastic jelly vivid peace spawn twice guilt pave pen trash pretty park cube fragile unaware remain midnight betray rebuild"
                balance = 100_000_000_000_000

                [accounts.wallet_2]
                mnemonic = "hold excess usual excess ring elephant install account glad dry fragile donkey gaze humble truck breeze nation gasp vacuum limb head keep delay hospital"
                balance = 100_000_000_000_000
                """, out);
        // an npm harness without an ignore file turns `git add .` into
        // staging the world — same never-clobber law as every kit file
        write(dir, ".gitignore", """
                node_modules/
                .cache/
                logs/
                costs-reports.json
                """, out);
        notes(dir, out, """
                # %P% — next steps (Clarity on Stacks)

                `clarinet check` proves the contract type-checks — no install,
                no network. For the full simnet test suite, SOLDER two lines:

                    npm install
                    npm test

                Three tests run against an in-memory Stacks chain: the counter
                starts at zero, increments, and only the deployer can reset it
                (the stranger gets `(err u100)`).

                A local devnet (`clarinet devnet start`, Docker) gives you a
                real chain with explorer; deploy accounts are clarinet's own
                published devnet defaults — never real funds, and the IDE never
                holds a key. Clarity is Bitcoin-anchored: every Stacks block
                settles to Bitcoin, which is why this chain pairs with the
                kit's Bitcoin/Miniscript starter.
                """.replace("%P%", pascal(name)));
    }

    // --- Cardano / Aiken ----------------------------------------------------

    private static void aiken(File dir, String name, List<Outcome> out) throws IOException {
        String s = snake(name);
        write(dir, "aiken.toml", """
                name = "nmox/%S%"
                version = "0.1.0"
                compiler = "v1.1.23"
                plutus = "v3"
                license = "Apache-2.0"
                description = "Aiken contract scaffolded by the NMOX Contract Kit"

                [[dependencies]]
                name = "aiken-lang/stdlib"
                version = "v3.1.0"
                source = "github"
                """.replace("%S%", s), out);
        // the live-proven vault: spend only with the magic word AND the
        // owner's signature — both the pass and the refusal are tested
        write(dir, "validators/" + s + ".ak", """
                // %S%: spending is allowed only when the redeemer says the
                // magic word AND the transaction is signed by the owner from
                // the datum. `aiken check` runs the tests below — no node,
                // no network, no keys.
                use cardano/transaction.{OutputReference, Transaction}
                use aiken/collection/list

                pub type Datum {
                  owner: ByteArray,
                }

                pub type Redeemer {
                  msg: ByteArray,
                }

                validator %S% {
                  spend(
                    datum: Option<Datum>,
                    redeemer: Redeemer,
                    _own_ref: OutputReference,
                    self: Transaction,
                  ) {
                    expect Some(d) = datum
                    let says_open = redeemer.msg == "open"
                    let owner_signed = list.has(self.extra_signatories, d.owner)
                    says_open && owner_signed
                  }

                  else(_) {
                    fail
                  }
                }

                test %S%_opens_for_owner() {
                  let d = Datum { owner: "alice" }
                  let r = Redeemer { msg: "open" }
                  let tx =
                    Transaction { ..transaction.placeholder, extra_signatories: ["alice"] }
                  %S%.spend(
                    Some(d),
                    r,
                    OutputReference { transaction_id: "", output_index: 0 },
                    tx,
                  )
                }

                test %S%_refuses_wrong_word() fail {
                  let d = Datum { owner: "alice" }
                  let r = Redeemer { msg: "sesame" }
                  let tx =
                    Transaction { ..transaction.placeholder, extra_signatories: ["alice"] }
                  expect
                    %S%.spend(
                      Some(d),
                      r,
                      OutputReference { transaction_id: "", output_index: 0 },
                      tx,
                    )
                }
                """.replace("%S%", s), out);
        notes(dir, out, """
                # %P% — next steps (Cardano / Aiken)

                `aiken check` compiles the validator and runs both tests — the
                owner-signed spend passes, the wrong-magic-word spend fails as
                declared (`test ... fail`). No node, no network, no keys.
                `aiken build` emits plutus.json (the CIP-57 blueprint) for
                off-chain tooling.

                VERITAS speaks these through SOLDER lines:

                    aiken check
                    aiken build

                A real chain needs a provider (cardano-node or a hosted API)
                and wallet software; signing keys live in your wallet, never
                the IDE. Aiken docs: aiken-lang.org.
                """.replace("%P%", pascal(name)));
    }

    // --- TON / Tact ---------------------------------------------------------

    private static void tact(File dir, String name, List<Outcome> out) throws IOException {
        String p = pascal(name);
        // FunC's grammar is GPL+archived — never vendored; Tact (MIT) is
        // TON's modern language and the honest route in. The whole loop is
        // npm-local: compile via @tact-lang/compiler, tests on @ton/sandbox.
        write(dir, "package.json", """
                {
                  "name": "%K%-contracts",
                  "private": true,
                  "scripts": {
                    "build": "tact --config tact.config.json",
                    "test": "tact --config tact.config.json && jest"
                  },
                  "devDependencies": {
                    "@tact-lang/compiler": "^1.6.13",
                    "@ton/core": "^0.60.0",
                    "@ton/sandbox": "^0.24.0",
                    "@ton/test-utils": "^0.5.0",
                    "@types/jest": "^29.5.0",
                    "jest": "^29.7.0",
                    "ts-jest": "^29.1.0",
                    "typescript": "^5.4.0"
                  }
                }
                """.replace("%K%", kebab(name)), out);
        write(dir, "tact.config.json", """
                {
                  "projects": [
                    {
                      "name": "%S%",
                      "path": "./contracts/%S%.tact",
                      "output": "./build"
                    }
                  ]
                }
                """.replace("%S%", snake(name)), out);
        write(dir, "contracts/" + snake(name) + ".tact", """
                // %P%: anyone may increment, only the deployer may reset.
                contract %P% {
                    owner: Address;
                    val: Int as uint32;

                    init() {
                        self.owner = sender();
                        self.val = 0;
                    }

                    receive("increment") {
                        self.val = self.val + 1;
                    }

                    receive("reset") {
                        require(sender() == self.owner, "owner only");
                        self.val = 0;
                    }

                    get fun value(): Int {
                        return self.val;
                    }
                }
                """.replace("%P%", p), out);
        write(dir, "tests/" + snake(name) + ".spec.ts", """
                import { Blockchain, SandboxContract, TreasuryContract } from '@ton/sandbox';
                import { toNano } from '@ton/core';
                import { %P% } from '../build/%S%_%P%';
                import '@ton/test-utils';

                describe('%P%', () => {
                  let blockchain: Blockchain;
                  let deployer: SandboxContract<TreasuryContract>;
                  let stranger: SandboxContract<TreasuryContract>;
                  let contract: SandboxContract<%P%>;

                  beforeEach(async () => {
                    blockchain = await Blockchain.create();
                    deployer = await blockchain.treasury('deployer');
                    stranger = await blockchain.treasury('stranger');
                    contract = blockchain.openContract(await %P%.fromInit());
                    await contract.send(deployer.getSender(), { value: toNano('0.05') }, 'increment');
                  });

                  it('increments for anyone', async () => {
                    await contract.send(stranger.getSender(), { value: toNano('0.05') }, 'increment');
                    expect(await contract.getValue()).toBe(2n);
                  });

                  it('only the owner resets', async () => {
                    const refused = await contract.send(
                        stranger.getSender(), { value: toNano('0.05') }, 'reset');
                    expect(refused.transactions).toHaveTransaction({
                      to: contract.address, success: false });
                    expect(await contract.getValue()).toBe(1n);

                    await contract.send(deployer.getSender(), { value: toNano('0.05') }, 'reset');
                    expect(await contract.getValue()).toBe(0n);
                  });
                });
                """.replace("%P%", p).replace("%S%", snake(name)), out);
        write(dir, "tsconfig.json", """
                {
                  "compilerOptions": {
                    "target": "ES2020",
                    "module": "commonjs",
                    "esModuleInterop": true,
                    "strict": true,
                    "skipLibCheck": true,
                    "types": ["jest", "node"]
                  }
                }
                """, out);
        write(dir, "jest.config.js", """
                module.exports = { preset: 'ts-jest', testEnvironment: 'node' };
                """, out);
        write(dir, ".gitignore", """
                node_modules/
                build/
                """, out);
        notes(dir, out, """
                # %P% — next steps (TON / Tact)

                One-time, then the rack takes over — SOLDER two lines:

                    npm install
                    npm test

                `npm test` compiles the Tact contract (typecheck + BoC
                bytecode + generated TypeScript wrappers under build/) and
                runs both sandbox tests on an in-memory TON blockchain: the
                increment passes for anyone, and a stranger's reset BOUNCES —
                the refusal is asserted, not assumed. No node, no network;
                deploy keys belong to your TON wallet, never the IDE.
                Tact docs: tact-lang.org.
                """.replace("%P%", p));
    }

    // --- plumbing ---------------------------------------------------------------

    private static void notes(File dir, List<Outcome> out, String content) throws IOException {
        write(dir, "CONTRACT-NOTES.md", content, out);
    }

    /** The one kit write law lives in {@link KitFiles}; kits never inline it. */
    private static void write(File dir, String path, String content, List<Outcome> out)
            throws IOException {
        KitFiles.Write w = KitFiles.writeNeverClobber(dir, path, content);
        out.add(new Outcome(w.path(), w.status(), w.changed()));
    }
}
