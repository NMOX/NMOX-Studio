package org.nmox.studio.editor.outline;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.outline.OutlineModel.Item;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The outline is built from heuristics, so it earns its keep only if the
 * heuristics are pinned. Each case feeds a realistic snippet and asserts
 * the names, kinds and nesting a developer would expect to navigate by.
 */
class OutlineModelTest {

    private List<Item> outline(String mime, String src) {
        return OutlineModel.extract(mime, src);
    }

    @Test
    @DisplayName("JS/TS: classes contain their methods; top-level functions and arrows surface")
    void javascript() {
        String src = """
                export function setup(opts) { return opts; }
                export const handler = async (req) => {
                  return req;
                };
                export class Widget {
                  constructor() {}
                  render(props) {
                    return null;
                  }
                }
                """;
        List<Item> items = outline("text/typescript", src);
        assertThat(items).extracting(Item::name)
                .contains("setup", "handler", "Widget", "render");
        Item widget = items.stream().filter(i -> i.name().equals("Widget")).findFirst().orElseThrow();
        Item render = items.stream().filter(i -> i.name().equals("render")).findFirst().orElseThrow();
        assertThat(widget.kind()).isEqualTo(OutlineKind.CLASS);
        assertThat(widget.depth()).isEqualTo(0);
        assertThat(render.kind()).isEqualTo(OutlineKind.METHOD);
        assertThat(render.depth()).as("method nests under its class").isGreaterThan(widget.depth());
        // a brace inside a string must not throw off nesting
        assertThat(items).noneMatch(i -> i.name().equals("constructor"));
    }

    @Test
    @DisplayName("JS outline ignores declarations inside comments and template literals")
    void jsSkipsCommentsAndTemplates() {
        String src = String.join("\n",
                "function real() {}",
                "/* function commented() {} */",
                "const sql = `",
                "  export function inTemplate() {",
                "`;",
                "function alsoReal() {}");
        List<Item> items = outline("text/javascript", src);
        assertThat(items).extracting(Item::name).contains("real", "alsoReal");
        assertThat(items).extracting(Item::name).doesNotContain("commented", "inTemplate");
    }

    @Test
    @DisplayName("JS test files: describe/it blocks become navigable")
    void jsTests() {
        String src = """
                describe('the rack', () => {
                  it('wires devices', () => {});
                  test('persists patches', () => {});
                });
                """;
        assertThat(outline("text/javascript", src)).extracting(Item::kind, Item::name)
                .contains(tuple(OutlineKind.TEST, "the rack"),
                        tuple(OutlineKind.TEST, "wires devices"),
                        tuple(OutlineKind.TEST, "persists patches"));
    }

    @Test
    @DisplayName("CSS/SCSS: selectors and at-rules, not declarations")
    void css() {
        String src = """
                .card {
                  color: red;
                }
                @media (min-width: 600px) {
                  .card { color: blue; }
                }
                #app .title {
                  font-weight: bold;
                }
                """;
        List<Item> items = outline("text/x-scss", src);
        assertThat(items).extracting(Item::name).contains(".card", "#app .title");
        assertThat(items).anyMatch(i -> i.kind() == OutlineKind.RULE && i.name().contains("@media"));
        assertThat(items).noneMatch(i -> i.name().contains("color"));
    }

    @Test
    @DisplayName("Markdown: headings nest by level, fenced code is ignored")
    void markdown() {
        String src = """
                # Title
                ## Section
                ### Detail
                ```
                # not a heading
                ```
                ## Another
                """;
        List<Item> items = outline("text/x-markdown", src);
        assertThat(items).extracting(Item::name)
                .containsExactly("Title", "Section", "Detail", "Another");
        assertThat(items.get(0).depth()).isEqualTo(0);
        assertThat(items.get(1).depth()).isEqualTo(1);
        assertThat(items.get(2).depth()).isEqualTo(2);
    }

    @Test
    @DisplayName("JSON: top-level keys")
    void json() {
        String src = """
                {
                  "name": "nmox",
                  "scripts": {
                    "build": "vite"
                  },
                  "version": "1.4.3"
                }
                """;
        List<Item> items = outline("text/x-json", src);
        assertThat(items).extracting(Item::name).contains("name", "scripts", "version");
        // nested "build" is one level deeper
        Item scripts = items.stream().filter(i -> i.name().equals("scripts")).findFirst().orElseThrow();
        assertThat(scripts.depth()).isEqualTo(0);
    }

    @Test
    @DisplayName("YAML keys nest by indentation")
    void yaml() {
        String src = """
                name: CI
                on:
                  push:
                    branches: [main]
                jobs:
                  build:
                    runs-on: ubuntu
                """;
        List<Item> items = outline("text/x-yaml", src);
        assertThat(items).extracting(Item::name).contains("name", "on", "push", "jobs", "build");
        Item push = items.stream().filter(i -> i.name().equals("push")).findFirst().orElseThrow();
        Item on = items.stream().filter(i -> i.name().equals("on")).findFirst().orElseThrow();
        assertThat(push.depth()).isGreaterThan(on.depth());
    }

    @Test
    @DisplayName("Python: defs and classes nest by indentation")
    void python() {
        String src = """
                import os

                class Service:
                    def start(self):
                        pass

                    def stop(self):
                        pass

                def main():
                    pass
                """;
        List<Item> items = outline("text/x-python", src);
        assertThat(items).extracting(Item::name).containsExactly("Service", "start", "stop", "main");
        assertThat(items.get(0).kind()).isEqualTo(OutlineKind.CLASS);
        assertThat(items.get(1).depth()).as("method under class").isEqualTo(1);
        assertThat(items.get(3).depth()).as("module function at top").isEqualTo(0);
    }

    @Test
    @DisplayName("Rust: fn/struct/enum/trait/impl")
    void rust() {
        String src = """
                pub struct Rack {
                    devices: Vec<Device>,
                }
                impl Rack {
                    pub fn new() -> Self { Rack { devices: vec![] } }
                }
                enum Signal { Trigger, Data }
                """;
        List<Item> items = outline("text/x-rust", src);
        assertThat(items).extracting(Item::kind, Item::name)
                .contains(tuple(OutlineKind.TYPE, "Rack"),
                        tuple(OutlineKind.MODULE, "Rack"),
                        tuple(OutlineKind.FUNCTION, "new"),
                        tuple(OutlineKind.ENUM, "Signal"));
    }

    @Test
    @DisplayName("Go: funcs and named struct/interface types")
    void go() {
        String src = """
                package main

                type Server struct {
                    addr string
                }

                func (s *Server) Start() error {
                    return nil
                }

                func main() {}
                """;
        assertThat(outline("text/x-go", src)).extracting(Item::name)
                .contains("Server", "Start", "main");
    }

    @Test
    @DisplayName("Elixir: defmodule contains its defs, nested by indentation")
    void elixir() {
        String src = """
                defmodule MyApp.Repo do
                  def all(query) do
                    query
                  end

                  defp sanitize(q), do: q

                  defmacro __using__(opts) do
                    quote do: unquote(opts)
                  end
                end

                defmodule MyApp.Other do
                end
                """;
        List<Item> items = outline("text/x-elixir", src);
        assertThat(items).extracting(Item::kind, Item::name).containsExactly(
                tuple(OutlineKind.MODULE, "MyApp.Repo"),
                tuple(OutlineKind.METHOD, "all"),
                tuple(OutlineKind.METHOD, "sanitize"),
                tuple(OutlineKind.METHOD, "__using__"),
                tuple(OutlineKind.MODULE, "MyApp.Other"));
        assertThat(items).extracting(Item::line).containsExactly(0, 1, 5, 7, 12);
        Item module = items.get(0);
        Item all = items.get(1);
        assertThat(all.depth()).as("def nests under its defmodule").isGreaterThan(module.depth());
        assertThat(items.get(4).depth()).as("second module back at top level").isEqualTo(0);
    }

    @Test
    @DisplayName("Clojure: ns, defn variants, and type forms surface as a flat outline")
    void clojure() {
        String src = """
                (ns myapp.core
                  (:require [clojure.string :as str]))

                (def default-port 8080)

                (defn- helper [x]
                  (inc x))

                (defn start!
                  [opts]
                  (run opts))

                (defmacro with-conn [& body]
                  `(do ~@body))

                (defrecord Server [port])
                (defprotocol Lifecycle
                  (start [this]))
                """;
        List<Item> items = outline("text/x-clojure", src);
        assertThat(items).extracting(Item::kind, Item::name).containsExactly(
                tuple(OutlineKind.MODULE, "myapp.core"),
                tuple(OutlineKind.FIELD, "default-port"),
                tuple(OutlineKind.FUNCTION, "helper"),
                tuple(OutlineKind.FUNCTION, "start!"),
                tuple(OutlineKind.FUNCTION, "with-conn"),
                tuple(OutlineKind.TYPE, "Server"),
                tuple(OutlineKind.INTERFACE, "Lifecycle"));
        assertThat(items).extracting(Item::line).containsExactly(0, 3, 5, 8, 12, 15, 16);
        // indented forms like (start [this]) are not top-level; flat list, no nesting
        assertThat(items).extracting(Item::name).doesNotContain("start");
        assertThat(items).allMatch(i -> i.depth() == 0);
    }

    @Test
    @DisplayName("Erlang: module attributes and column-0 function clause heads")
    void erlang() {
        String src = """
                -module(myapp_server).
                -behaviour(gen_server).
                -export([start_link/0, init/1]).

                start_link() ->
                    gen_server:start_link({local, ?MODULE}, ?MODULE, [], []).

                init(Args) when is_list(Args) ->
                    {ok, #{}}.

                handle_call(_Req, _From, State) ->
                    {reply, ok, State}.
                """;
        List<Item> items = outline("text/x-erlang", src);
        assertThat(items).extracting(Item::kind, Item::name).containsExactly(
                tuple(OutlineKind.MODULE, "myapp_server"),
                tuple(OutlineKind.INTERFACE, "gen_server"),
                tuple(OutlineKind.SECTION, "export"),
                tuple(OutlineKind.FUNCTION, "start_link"),
                tuple(OutlineKind.FUNCTION, "init"),
                tuple(OutlineKind.FUNCTION, "handle_call"));
        assertThat(items).extracting(Item::line).containsExactly(0, 1, 2, 4, 7, 10);
        // indented calls like gen_server:start_link(...) are bodies, not heads
        assertThat(items).extracting(Item::name).doesNotContain("gen_server:start_link");
    }

    @Test
    @DisplayName("Haskell: module, signatures, bindings, and data/class/instance decls at column 0")
    void haskell() {
        String src = """
                module Rack.Wire (connect) where

                import Data.Map (Map)

                data Device = Trigger | Sampler
                newtype Cable = Cable Int
                type Patch = (Device, Device)

                class Wired a where
                  wire :: a -> a

                instance Wired Device where
                  wire = id

                connect :: Device -> Device -> Patch
                connect a b = (a, b)
                """;
        List<Item> items = outline("text/x-haskell", src);
        assertThat(items).extracting(Item::kind, Item::name).contains(
                tuple(OutlineKind.MODULE, "Rack.Wire"),
                tuple(OutlineKind.TYPE, "Device"),
                tuple(OutlineKind.TYPE, "Cable"),
                tuple(OutlineKind.TYPE, "Patch"),
                tuple(OutlineKind.INTERFACE, "Wired"),
                tuple(OutlineKind.TYPE, "Device"),
                tuple(OutlineKind.FIELD, "connect"),
                tuple(OutlineKind.FUNCTION, "connect"));
        // the signature line and the binding line are different lines
        Item sig = items.stream()
                .filter(i -> i.name().equals("connect") && i.kind() == OutlineKind.FIELD)
                .findFirst().orElseThrow();
        Item bind = items.stream()
                .filter(i -> i.name().equals("connect") && i.kind() == OutlineKind.FUNCTION)
                .findFirst().orElseThrow();
        assertThat(sig.line()).isEqualTo(14);
        assertThat(bind.line()).isEqualTo(15);
        assertThat(items).allMatch(i -> i.depth() == 0);
        // indented `wire = id` is a class/instance body, not a top-level binding
        assertThat(items).extracting(Item::name).doesNotContain("wire", "import");
    }

    @Test
    @DisplayName("OCaml: let/let rec bindings, type, and module at column 0")
    void ocaml() {
        String src = """
                module Rack = struct
                  let hidden = 1
                end

                type signal = Trigger | Data

                let rec length = function
                  | [] -> 0
                  | _ :: t -> 1 + length t

                let start opts = run opts
                """;
        List<Item> items = outline("text/x-ocaml", src);
        assertThat(items).extracting(Item::kind, Item::name).containsExactly(
                tuple(OutlineKind.MODULE, "Rack"),
                tuple(OutlineKind.TYPE, "signal"),
                tuple(OutlineKind.FUNCTION, "length"),
                tuple(OutlineKind.FUNCTION, "start"));
        assertThat(items).extracting(Item::line).containsExactly(0, 4, 6, 10);
        // the indented `let hidden` is inside the module body, not surfaced
        assertThat(items).extracting(Item::name).doesNotContain("hidden");
        // module type keeps its own name, type params skip to the type's name,
        // val surfaces (mli files), and `let _ =` discards stay out
        assertThat(outline("text/x-ocaml",
                "module type DEVICE = sig end\ntype 'a slot = 'a option\n"
                + "val version : string\nlet _ = ignore\n"))
                .extracting(Item::kind, Item::name).containsExactly(
                        tuple(OutlineKind.MODULE, "DEVICE"),
                        tuple(OutlineKind.TYPE, "slot"),
                        tuple(OutlineKind.FIELD, "version"));
    }

    @Test
    @DisplayName("R: function assignments via <- and =, plus S4 setClass/setGeneric")
    void r() {
        String src = """
                library(dplyr)

                greet <- function(name) {
                  paste("hi", name)
                }

                add = function(a, b) a + b

                setClass("Device", representation(id = "numeric"))

                setGeneric("fire", function(obj) standardGeneric("fire"))

                x <- 42
                """;
        List<Item> items = outline("text/x-r", src);
        assertThat(items).extracting(Item::kind, Item::name).containsExactly(
                tuple(OutlineKind.FUNCTION, "greet"),
                tuple(OutlineKind.FUNCTION, "add"),
                tuple(OutlineKind.CLASS, "Device"),
                tuple(OutlineKind.FUNCTION, "fire"));
        assertThat(items).extracting(Item::line).containsExactly(2, 6, 8, 10);
        // a plain assignment `x <- 42` is not a function def
        assertThat(items).extracting(Item::name).doesNotContain("x");
    }

    @Test
    @DisplayName("Perl: sub declarations and package statements, flat")
    void perl() {
        String src = """
                package Rack::Wire;
                use strict;

                sub new {
                    my $class = shift;
                    return bless {}, $class;
                }

                sub connect {
                    my ($self, $a, $b) = @_;
                }

                1;
                """;
        List<Item> items = outline("text/x-perl", src);
        assertThat(items).extracting(Item::kind, Item::name).containsExactly(
                tuple(OutlineKind.MODULE, "Rack::Wire"),
                tuple(OutlineKind.FUNCTION, "new"),
                tuple(OutlineKind.FUNCTION, "connect"));
        assertThat(items).extracting(Item::line).containsExactly(0, 3, 8);
        assertThat(items).allMatch(i -> i.depth() == 0);
    }

    @Test
    @DisplayName("Julia: function/struct/module/macro nest by indentation")
    void julia() {
        String src = """
                module Rack
                    struct Device
                        id::Int
                    end

                    function wire(a, b)
                        connect(a, b)
                    end

                    macro trace(ex)
                        ex
                    end
                end

                function top()
                    nothing
                end

                gain(x) = 2 * x
                """;
        List<Item> items = outline("text/x-julia", src);
        assertThat(items).extracting(Item::kind, Item::name).contains(
                tuple(OutlineKind.MODULE, "Rack"),
                tuple(OutlineKind.TYPE, "Device"),
                tuple(OutlineKind.METHOD, "wire"),
                tuple(OutlineKind.METHOD, "trace"),
                tuple(OutlineKind.FUNCTION, "top"),
                tuple(OutlineKind.FUNCTION, "gain"));
        Item module = items.stream().filter(i -> i.name().equals("Rack")).findFirst().orElseThrow();
        Item device = items.stream().filter(i -> i.name().equals("Device")).findFirst().orElseThrow();
        Item wire = items.stream().filter(i -> i.name().equals("wire")).findFirst().orElseThrow();
        assertThat(device.depth()).as("struct nests under its module").isGreaterThan(module.depth());
        assertThat(wire.depth()).as("indented function is a method").isEqualTo(1);
        Item top = items.stream().filter(i -> i.name().equals("top")).findFirst().orElseThrow();
        assertThat(top.depth()).as("top-level function at column 0").isEqualTo(0);
        Item gain = items.stream().filter(i -> i.name().equals("gain")).findFirst().orElseThrow();
        assertThat(gain.depth()).as("short-form def at column 0").isEqualTo(0);
        assertThat(gain.line()).isEqualTo(18);
    }

    @Test
    @DisplayName("F#: let/let rec, type, module, member at column 0")
    void fsharp() {
        String src = """
                module Rack.Wire

                type Device =
                    { Id: int }

                let connect a b = (a, b)

                let rec length xs =
                    match xs with
                    | [] -> 0
                    | _ :: t -> 1 + length t

                type Cable() =
                    member this.Length = 0
                """;
        List<Item> items = outline("text/x-fsharp", src);
        assertThat(items).extracting(Item::kind, Item::name).contains(
                tuple(OutlineKind.MODULE, "Rack.Wire"),
                tuple(OutlineKind.TYPE, "Device"),
                tuple(OutlineKind.FUNCTION, "connect"),
                tuple(OutlineKind.FUNCTION, "length"),
                tuple(OutlineKind.TYPE, "Cable"),
                tuple(OutlineKind.METHOD, "Length"));
    }

    @Test
    @DisplayName("Crystal: def/class/module/struct/macro nest by indentation, Ruby-like")
    void crystal() {
        String src = """
                module Rack
                  class Device
                    def initialize(@id : Int32)
                    end

                    def self.version
                      "1.0"
                    end

                    def wire(other)
                      other
                    end
                  end

                  macro wired(name)
                  end

                  struct Cable
                  end
                end

                def top
                end
                """;
        List<Item> items = outline("text/x-crystal", src);
        assertThat(items).extracting(Item::kind, Item::name).contains(
                tuple(OutlineKind.MODULE, "Rack"),
                tuple(OutlineKind.CLASS, "Device"),
                tuple(OutlineKind.METHOD, "initialize"),
                tuple(OutlineKind.METHOD, "self.version"),
                tuple(OutlineKind.METHOD, "wire"),
                tuple(OutlineKind.METHOD, "wired"),
                tuple(OutlineKind.TYPE, "Cable"),
                tuple(OutlineKind.FUNCTION, "top"));
        Item device = items.stream().filter(i -> i.name().equals("Device")).findFirst().orElseThrow();
        Item wire = items.stream().filter(i -> i.name().equals("wire")).findFirst().orElseThrow();
        assertThat(wire.depth()).as("method nests under its class").isGreaterThan(device.depth());
        Item top = items.stream().filter(i -> i.name().equals("top")).findFirst().orElseThrow();
        assertThat(top.depth()).as("top-level def is a plain function").isEqualTo(0);
    }

    @Test
    @DisplayName("Zig: pub fn/fn, const Name = struct, and test blocks")
    void zig() {
        String src = """
                const std = @import("std");

                pub const Device = struct {
                    id: u32,

                    pub fn wire(self: *Device, other: *Device) void {
                        _ = other;
                    }
                };

                const Signal = enum { trigger, data };

                fn helper() void {}

                test "wiring connects devices" {
                    try std.testing.expect(true);
                }
                """;
        List<Item> items = outline("text/x-zig", src);
        assertThat(items).extracting(Item::kind, Item::name).contains(
                tuple(OutlineKind.TYPE, "Device"),
                tuple(OutlineKind.FUNCTION, "wire"),
                tuple(OutlineKind.ENUM, "Signal"),
                tuple(OutlineKind.FUNCTION, "helper"),
                tuple(OutlineKind.TEST, "wiring connects devices"));
        Item test = items.stream().filter(i -> i.kind() == OutlineKind.TEST).findFirst().orElseThrow();
        assertThat(test.line()).isEqualTo(14);
    }

    @Test
    @DisplayName("Solidity: contract contains functions/events/errors; interface, library, struct and enum surface")
    void solidity() {
        String src = """
                // SPDX-License-Identifier: MIT
                pragma solidity ^0.8.24;

                interface IVault {
                    function deposit(uint256 amount) external;
                }

                library SafeMath {
                    function add(uint256 a, uint256 b) internal pure returns (uint256) {
                        return a + b;
                    }
                }

                abstract contract Ownable {
                    modifier onlyOwner() {
                        _;
                    }
                }

                contract Token is Ownable {
                    struct Account {
                        uint256 balance;
                    }

                    enum Phase { Setup, Live }

                    event Transfer(address indexed from, address indexed to, uint256 value);
                    error InsufficientBalance(uint256 available, uint256 required);

                    constructor(uint256 supply) {
                        total = supply;
                    }

                    uint256 public total;

                    function transfer(address to, uint256 value) public returns (bool) {
                        return true;
                    }

                    /* function commentedOut() public {} */
                }
                """;
        List<Item> items = outline("text/x-solidity", src);
        assertThat(items).extracting(Item::kind, Item::name).contains(
                tuple(OutlineKind.INTERFACE, "IVault"),
                tuple(OutlineKind.MODULE, "SafeMath"),
                tuple(OutlineKind.CLASS, "Ownable"),
                tuple(OutlineKind.METHOD, "onlyOwner"),
                tuple(OutlineKind.CLASS, "Token"),
                tuple(OutlineKind.TYPE, "Account"),
                tuple(OutlineKind.ENUM, "Phase"),
                tuple(OutlineKind.FIELD, "Transfer"),
                tuple(OutlineKind.FIELD, "InsufficientBalance"),
                tuple(OutlineKind.METHOD, "constructor"),
                tuple(OutlineKind.METHOD, "transfer"));
        // members nest under their contract by brace depth
        Item token = items.stream().filter(i -> i.name().equals("Token")).findFirst().orElseThrow();
        Item transfer = items.stream().filter(i -> i.name().equals("transfer")).findFirst().orElseThrow();
        assertThat(token.depth()).isEqualTo(0);
        assertThat(transfer.depth()).as("function nests under its contract").isGreaterThan(token.depth());
        // the modifier and event carry their detail badges
        assertThat(items).anyMatch(i -> i.name().equals("onlyOwner") && "modifier".equals(i.detail()));
        assertThat(items).anyMatch(i -> i.name().equals("Transfer") && "event".equals(i.detail()));
        // a declaration inside a block comment never surfaces
        assertThat(items).extracting(Item::name).doesNotContain("commentedOut");
    }

    @Test
    @DisplayName("CoffeeScript: classes contain bound methods; top-level arrows surface; comments are immune")
    void coffeescript() {
        String src = """
                # a 2012-flavoured Backbone view
                class App.TaskView extends Backbone.View
                  render: ->
                    @$el.html @template()
                    this

                  onClick: (event) =>
                    event.preventDefault()

                helper = (x) ->
                  x * 2

                square = (n) -> n * n

                # render: -> (a commented-out method must not surface)
                ###
                inBlock: ->
                ###
                """;
        List<Item> items = outline("text/coffeescript", src);
        assertThat(items).extracting(Item::kind, Item::name).contains(
                tuple(OutlineKind.CLASS, "App.TaskView"),
                tuple(OutlineKind.METHOD, "render"),
                tuple(OutlineKind.METHOD, "onClick"),
                tuple(OutlineKind.FUNCTION, "helper"),
                tuple(OutlineKind.FUNCTION, "square"));
        // methods nest under their class by indentation
        Item view = items.stream().filter(i -> i.name().equals("App.TaskView")).findFirst().orElseThrow();
        Item render = items.stream().filter(i -> i.name().equals("render")).findFirst().orElseThrow();
        assertThat(view.depth()).isEqualTo(0);
        assertThat(render.depth()).as("bound method nests under its class").isGreaterThan(view.depth());
        // neither the # line nor the ### block leaks a symbol
        assertThat(items).extracting(Item::name).doesNotContain("inBlock");
        assertThat(items).hasSize(5);
    }

    @Test
    @DisplayName("Config sections: INI and TOML headers")
    void configSections() {
        assertThat(outline("text/x-ini", "[*]\nindent_style = space\n[*.md]\n"))
                .extracting(Item::name).containsExactly("*", "*.md");
        assertThat(outline("text/x-toml", "[package]\nname = \"x\"\n[[bin]]\n"))
                .extracting(Item::name).contains("[package]", "[[bin]]");
    }

    @Test
    @DisplayName("GraphQL types and Makefile targets")
    void graphqlAndMake() {
        assertThat(outline("text/x-graphql", "type Query {\n  me: User\n}\nenum Role { ADMIN }\n"))
                .extracting(Item::kind, Item::name)
                .contains(tuple(OutlineKind.TYPE, "Query"), tuple(OutlineKind.ENUM, "Role"));
        assertThat(outline("text/x-makefile", "build:\n\tgo build\ntest:\n\tgo test\nVAR = 1\n"))
                .extracting(Item::name).containsExactly("build", "test");
    }

    @Test
    @DisplayName("Unknown language falls back to action markers")
    void genericTodos() {
        String src = "line one\n// TODO wire the thing\nplain\n# FIXME: leak\n";
        assertThat(outline("text/x-unknown", src)).extracting(Item::kind, Item::name)
                .contains(tuple(OutlineKind.TODO, "TODO: wire the thing"),
                        tuple(OutlineKind.TODO, "FIXME: leak"));
    }

    @Test
    @DisplayName("Empty and null inputs are safe")
    void edgeCases() {
        assertThat(OutlineModel.extract(null, "x")).isEmpty();
        assertThat(OutlineModel.extract("text/javascript", null)).isEmpty();
        assertThat(OutlineModel.extract("text/javascript", "")).isEmpty();
    }

    @Test
    @DisplayName("netBraces ignores braces in strings and comments")
    void netBraces() {
        assertThat(OutlineModel.netBraces("class X {")).isEqualTo(1);
        assertThat(OutlineModel.netBraces("const s = \"a { b } c\";")).isEqualTo(0);
        assertThat(OutlineModel.netBraces("} // closing }")).isEqualTo(-1);
        assertThat(OutlineModel.netBraces("foo() { bar(); }")).isEqualTo(0);
    }

    @Test
    @DisplayName("Fortran: program/module/subroutine/function blocks and derived types")
    void fortran() {
        String src = """
                module shapes
                  implicit none
                  type, public :: point
                    real :: x, y
                  end type point
                contains
                  function area(r) result(a)
                    real, intent(in) :: r
                    real :: a
                    a = 3.14159 * r * r
                  end function area
                  subroutine reset(p)
                    type(point), intent(out) :: p
                  end subroutine reset
                end module shapes

                program demo
                  use shapes
                end program demo
                """;
        List<Item> items = outline("text/x-fortran", src);
        assertThat(items).extracting(Item::kind, Item::name)
                .contains(tuple(OutlineKind.MODULE, "shapes"),
                        tuple(OutlineKind.TYPE, "point"),
                        tuple(OutlineKind.FUNCTION, "area"),
                        tuple(OutlineKind.FUNCTION, "reset"),
                        tuple(OutlineKind.FUNCTION, "demo"));
        // a variable declaration `type(point) :: p` must NOT become an outline entry
        assertThat(items).extracting(Item::name).doesNotContain("p");
    }

    @Test
    @DisplayName("Scheme rides the Racket extractor: (define ...) forms outline")
    void scheme() {
        String src = """
                (define (greet name)
                  (string-append "Hello, " name "!"))

                (define pi 3.14159)
                """;
        assertThat(outline("text/x-scheme", src)).extracting(Item::name)
                .contains("greet", "pi");
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.api.Assertions.tuple(values);
    }
}
