# TextMate grammar attribution

The `.tmLanguage.json` files in this package are sourced from the
Visual Studio Code repository, tag **1.95.0**
(https://github.com/microsoft/vscode/tree/1.95.0/extensions), which
redistributes them under MIT-compatible licenses. Upstream origins:

| Grammar | VS Code extension | Upstream |
|---|---|---|
| java.tmLanguage.json | extensions/java | atom/language-java (MIT) |
| c.tmLanguage.json / cpp.tmLanguage.json | extensions/cpp | jeff-hykin/better-cpp-syntax (MIT) |
| python.tmLanguage.json | extensions/python | MagicStack/MagicPython (MIT) |
| ruby.tmLanguage.json | extensions/ruby | textmate/ruby.tmbundle (TextMate bundle license) |
| rust.tmLanguage.json | extensions/rust | dustypomerleau/rust-syntax (MIT) |
| php.tmLanguage.json | extensions/php | KapitanOczywisty/language-php (MIT) |
| shell.tmLanguage.json | extensions/shellscript | atom/language-shellscript (MIT) |
| json.tmLanguage.json | extensions/json | microsoft/vscode-JSON.tmLanguage (MIT) |

| elixir.tmLanguage.json | — | elixir-lsp/vscode-elixir-ls (MIT) |
| erlang.tmLanguage.json | — | erlang-ls/grammar (Apache-2.0), converted plist→JSON |
| clojure.tmLanguage.json | extensions/clojure | atom/language-clojure (MIT) |
| commonlisp.tmLanguage.json | — | qingpeng9802/vscode-common-lisp (MIT) |
| lua.tmLanguage.json | extensions/lua | sumneko/lua.tmbundle (MIT) |
| swift.tmLanguage.json | extensions/swift | textmate/swift.tmbundle (MIT) |
| kotlin.tmLanguage.json | — | fwcd/vscode-kotlin (MIT) |

| csharp/fsharp/groovy/perl/r/julia .tmLanguage.json | extensions/* | VS Code 1.95.0 bundled (MIT-compatible) |
| dart.tmLanguage.json | — | Dart-Code/Dart-Code (MIT) |
| scala.tmLanguage.json | — | scala/vscode-scala-syntax (MIT) |
| haskell.tmLanguage.json | — | JustusAdam/language-haskell (BSD-3), converted YAML→JSON |
| zig.tmLanguage.json | — | ziglang/vscode-zig (MIT) |
| gleam.tmLanguage.json | sha256 837c3a234a4a8fd11e1b6942f0c15c323fefbbbaa7ed0ebdf4c8cc0c7a7d9388 | gleam-lang/vscode-gleam (Apache-2.0) |
| nim.tmLanguage.json | sha256 c13fd45c842aa738fdc003c9c6f09b3d64667e76e4ce3a17d5d9a0f24726e428 | nim-lang/vscode-nim syntaxes/nim.json (MIT) |
| d.tmLanguage.json | sha256 66824108f51fadc7104619379116254d1c352cb306b654514af23a7b4b83df2a | Pure-D/code-d syntaxes/d.json (MIT) |
| racket.tmLanguage.json | sha256 e57ecf8b2cd382c6286d3b2e82c5664fe3bf8683d300bd3875f1bc4a9bb818c7 | Eugleo/magic-racket syntaxes/racket.tmLanguage.json (MIT) |
| elm.tmLanguage.json | sha256 0f027bc8fe13fab9051f0438848883f23f4f3ba6c28973b3811693805495b6b4 | elm-tooling/elm-language-client-vscode syntaxes/elm-syntax.json (MIT) |
| rescript.tmLanguage.json | sha256 55230309058f73615085ff4493fe3e348f443159851d9f108730472345e7f662 | rescript-lang/rescript-vscode grammars/rescript.tmLanguage.json (MIT) |
| purescript.tmLanguage.json | sha256 e99c6695bafa5585022f493ab69b0276997a918ee128079c0346ab166f3e7f97 | nwolverson/vscode-language-purescript syntaxes/purescript.json (MIT) |
| ocaml.tmLanguage.json | — | ocamllabs/vscode-ocaml-platform (ISC) |
| crystal.tmLanguage.json | — | crystal-lang-tools/vscode-crystal-lang (MIT) |

To refresh: bump the tag in this table and re-download; the holder
classes in this package register each grammar with the platform's
textmate-lexer module.

## Config-layer tranche (added 2026-06-12)

| Grammar | Source | License |
|---|---|---|
| ini.tmLanguage.json | microsoft/vscode 1.95.0 extensions/ini | MIT |
| ignore.tmLanguage.json | microsoft/vscode 1.95.0 extensions/git-base | MIT |
| pug.tmLanguage.json | microsoft/vscode 1.95.0 extensions/pug | MIT |
| handlebars.tmLanguage.json | microsoft/vscode 1.95.0 extensions/handlebars | MIT |
| makefile.tmLanguage.json | microsoft/vscode 1.95.0 extensions/make | MIT |
| graphql.tmLanguage.json | graphql/graphiql vscode-graphql-syntax | MIT |
| vue.tmLanguage.json | vuejs/language-tools | MIT |
| svelte.tmLanguage.json | sveltejs/language-tools, converted YAML→JSON | MIT |
| astro.tmLanguage.json | withastro/language-tools | MIT |
| liquid.tmLanguage.json | Shopify/liquid-tm-grammar | MIT |
| nginx.tmLanguage.json | ahmadalli/vscode-nginx-conf, converted plist→JSON | MIT |
| proto.tmLanguage.json | zxh0/vscode-proto3 (proto3.tmLanguage.json) | MIT |
| prisma.tmLanguage.json | prisma/language-tools | Apache-2.0 |

## Apache config (added 2026-07-04)

| Grammar | Source | License |
|---|---|---|
| apache.tmLanguage.json | mrmlnc/vscode-apache tag **1.2.0** (syntaxes/Apache.tmLanguage), converted plist→JSON | MIT |

The Apache grammar is self-contained (no cross-grammar includes) and
scoped `source.apacheconf`. Its upstream `fileTypes` metadata claims
bare `conf` — inert here: NetBeans resolution comes from our own
registrations, which deliberately do NOT claim the generic `.conf`
extension (only `.vhost` plus the exact names `.htaccess`,
`httpd.conf`, `apache2.conf` via ConfigFileResolver).

## Solidity (added 2026-07-04)

| Grammar | Source | License |
|---|---|---|
| solidity.tmLanguage.json | juanfranblanco/vscode-solidity tag **0.0.187** (syntaxes/solidity.json) | MIT |

The Solidity grammar is self-contained (no cross-grammar includes) and
scoped `source.solidity`. Its upstream `fileTypes` metadata claims
`sol` — which matches our own `.sol` extension registration in
`SolidityGrammar`.

## CoffeeScript (added 2026-07-05)

| Grammar | Source | License |
|---|---|---|
| coffeescript.tmLanguage.json | microsoft/vscode tag **1.95.0** extensions/coffeescript (upstream atom/language-coffee-script commit 0f6db9143663e18b1ad00667820f46747dba495e, per the grammar's own `version` field) | MIT |

Scoped `source.coffee`; no upstream `fileTypes` metadata (the VS Code
extension claims `.coffee`/`.cson`/`.iced` in package.json) — our own
registration in `CoffeeScriptGrammar` claims `coffee`, `litcoffee`
and `cson`. Two cross-grammar includes: `source.js` (backtick embedded
JavaScript) resolves through the embed-only registry below;
`source.js.regexp` is unresolvable upstream-wide — VS Code ships no
grammar with that scope either — so TM4E prunes that inner include and
regex literals keep their `string.regexp.coffee` colouring without
sub-token detail, exactly as in VS Code. Registering `source.coffee`
also makes the coffee fences already referenced by the pug, scss, vue
and svelte grammars resolvable.

## Embed-only grammars (scope registry, no editor binding)

These are registered under synthetic `text/x-nmox-embed-*` mimes purely
so TM4E can resolve cross-grammar includes (markdown fences, YAML front
matter, inline HTML); no file resolves to those mimes and the real
editors for those languages stay with their existing owners (custom
JS/TS lexer, platform YAML module). See `EmbeddedScopeGrammars`.

| Grammar | Source | License |
|---|---|---|
| yaml.tmLanguage.json | microsoft/vscode 1.95.0 extensions/yaml (textmate/yaml.tmbundle) | MIT-compatible |
| javascript.tmLanguage.json | microsoft/vscode 1.95.0 extensions/javascript (microsoft/TypeScript-TmLanguage) | MIT |
| typescript.tmLanguage.json | microsoft/vscode 1.95.0 extensions/typescript-basics (microsoft/TypeScript-TmLanguage) | MIT |
| typescriptreact.tmLanguage.json | microsoft/vscode 1.95.0 extensions/typescript-basics (microsoft/TypeScript-TmLanguage) | MIT |
| html-derivative.tmLanguage.json | microsoft/vscode 1.95.0 extensions/html (textmate/html.tmbundle) | MIT-compatible |

YAML, TOML, Markdown, Dockerfile, SQL and diff are intentionally NOT
bundled as *editors*: the NetBeans ide cluster ships native editor
support for those mimes, and a second registration would duplicate
editors. (The embed-only YAML grammar above does not bind an editor.)
