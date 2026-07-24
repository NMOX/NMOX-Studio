package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Contract Kit: scaffold a smart contract for any chain the studio
 * speaks — Solidity/Foundry, Soroban (Stellar), Solana, CosmWasm, ink!,
 * Cairo, or Move (Sui) — into the aimed project. Every template is the
 * v1.130–v1.137 arc's LIVE-PROVEN starter (each ran green against its
 * real toolchain before shipping), name-templated for your contract.
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
        MOVE("Move (Sui)", "sui");

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

    // --- plumbing ---------------------------------------------------------------

    private static void notes(File dir, List<Outcome> out, String content) throws IOException {
        write(dir, "CONTRACT-NOTES.md", content, out);
    }

    /** The Classic Kit write law: never clobber, suggest alongside. */
    private static void write(File dir, String path, String content, List<Outcome> out)
            throws IOException {
        File target = new File(dir, path);
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        if (!target.exists()) {
            Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
            out.add(new Outcome(path, "written", true));
            return;
        }
        if (content.equals(Files.readString(target.toPath(), StandardCharsets.UTF_8))) {
            out.add(new Outcome(path, "already exists, untouched", false));
            return;
        }
        File suggested = new File(dir, path + ".suggested");
        if (suggested.exists()) {
            out.add(new Outcome(path,
                    "skipped — " + path + " and " + path + ".suggested both exist", false));
            return;
        }
        Files.writeString(suggested.toPath(), content, StandardCharsets.UTF_8);
        out.add(new Outcome(path + ".suggested",
                "existing " + path + " kept — suggestion written alongside", true));
    }
}
