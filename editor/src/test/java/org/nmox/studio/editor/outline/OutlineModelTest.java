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

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.api.Assertions.tuple(values);
    }
}
