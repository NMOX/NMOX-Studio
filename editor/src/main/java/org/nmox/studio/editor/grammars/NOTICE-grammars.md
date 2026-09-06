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
| racket.tmLanguage.json | sha256 b6ddad8ec4b1433efac889fbd22b64c5fb683c1e769b1332b50c9410fbd8b308 | Eugleo/magic-racket syntaxes/racket.tmLanguage.json (MIT); patched v1.200.1: 20 shorthand/list capture values normalized to rule objects (upstream sha256 e57ecf8b2cd382c6286d3b2e82c5664fe3bf8683d300bd3875f1bc4a9bb818c7) |
| elm.tmLanguage.json | sha256 0f027bc8fe13fab9051f0438848883f23f4f3ba6c28973b3811693805495b6b4 | elm-tooling/elm-language-client-vscode syntaxes/elm-syntax.json (MIT) |
| rescript.tmLanguage.json | sha256 55230309058f73615085ff4493fe3e348f443159851d9f108730472345e7f662 | rescript-lang/rescript-vscode grammars/rescript.tmLanguage.json (MIT) |
| purescript.tmLanguage.json | sha256 e99c6695bafa5585022f493ab69b0276997a918ee128079c0346ab166f3e7f97 | nwolverson/vscode-language-purescript syntaxes/purescript.json (MIT) |
| move.tmLanguage.json | sha256 957e0ca327223ed26bd0d193728474df19061633bb207f6d8c275dbc58b3f0ca | damirka/move-syntax syntaxes/move.tmLanguage.json (MIT) |
| cairo.tmLanguage.json | sha256 3d4491dbaa543907bfd5cb28848408bdc3f169ac65be0b322280585e4f827eb8 | software-mansion/vscode-cairo syntaxes/cairo.tmLanguage.json (Apache-2.0) |
| aiken.tmLanguage.json | sha256 f3a6c56e03456f70198a85fdc7edeb655f8280ecaa69e4de7cd2cab50a437997 | aiken-lang/vscode-aiken syntaxes/aiken.tmLanguage.json (Apache-2.0) |
| tact.tmLanguage.json | sha256 f817bcfe08facbc544222c0ca3ad75f9176bbdb368ad670e88f1b9d68d136f75 | tact-lang/tact-vscode syntaxes/tact.json (Apache-2.0; FunC grammar deliberately NOT used: GPL-3.0 + archived) |
| http.tmLanguage.json | sha256 0f5d2d6ffe1127371b37256b8bf04a28d3208b24dea86266f0d930c16e4d9820 | Huachao/vscode-restclient syntaxes/http.tmLanguage.json (MIT) |
| clarity.tmLanguage.json | sha256 e32cf9c707dda399bb7bebb505afd10d4222eeadf39f1945cdbb42bc83ab2d5d | hirosystems/clarity.tmbundle Syntaxes/clarity.JSON-tmLanguage (MIT) |
| vlang.tmLanguage.json | sha256 b3b8a46ce457fca0f22dde2c256ad5237665b8e7725285d98f703db873bfa45a | vlang/vscode-vlang syntaxes/v.tmLanguage.json (MIT) |
| fortran.tmLanguage.json | sha256 b8df797e51e65b45c54511a123e20297c6dd04f3a4745c3a15da7383c6596808 | fortran-lang/vscode-fortran-support syntaxes/fortran_free-form.tmLanguage.json (MIT) |
| smalltalk.tmLanguage.json | sha256 8191beb90357837e13d8c07dcd7e613fcb7063b1b3bc5ecf3c6a21bd0d70cee0 | leocamello/vscode-smalltalk syntaxes/gnu-smalltalk.YAML-tmLanguage, converted YAML→JSON (MIT) |
| prolog.tmLanguage.json | sha256 438a6fba654c2b46fb5edd585436519cab6bcf36e4186020ae1221efd52c4227 | arthwang/vsc-prolog syntaxes/prolog.swi.tmLanguage.json (MIT) |
| tcl.tmLanguage.json | sha256 cd478026e11b2c3c43b7086f20ab356845178062f8445a05d65c10b67514ba65 | bitwisecook/vscode-tcl syntaxes/tcl.tmlanguage.yaml, converted YAML→JSON (MIT) |
| scheme.tmLanguage.json | sha256 34fa0997ce66ee67c471feeef7655909cd46c3f5171f123bdee51f53fcc39c17 | sjhuangx/vscode-scheme syntaxes/scheme.tmLanguage, converted plist→JSON (MIT) |
| ada.tmLanguage.json | sha256 e97e649d4369a18acc5a59259582d346eee5268bd91e24a17e963d5efcadb51f | textmate/ada.tmbundle Syntaxes/Ada.plist, converted plist→JSON (TextMate bundle license; AdaCore's grammar is GPL-3.0 and deliberately not used) |
| pascal.tmLanguage.json | sha256 ca80c6f52a27783d280cd828542963955f2e5d6df0fa4c306dd19c96e4d3c06b | alefragnani/vscode-language-pascal syntaxes/pascal.tmLanguage, converted plist→JSON (MIT) |
| odin.tmLanguage.json | sha256 d2a4331a0912a9bdf9b361596c6ce50f403eaa2ad686cdccc2f5d3516a85ca3d | DanielGavin/ols editors/vscode/syntaxes/odin.tmLanguage.json (MIT) |
| cobol.tmLanguage.json | sha256 172a6c7688c478089e3976317757168eb241d1950846269c1fbde2dcfa77b5b6 | spgennard/vscode_cobol syntaxes/COBOL.tmLanguage.json (MIT) |
| haxe.tmLanguage.json | sha256 c70988b8b367e9c4ea8bf0a4ee8cfdb7a5043c5a630f0a86cbc63e3ad0b447e1 | vshaxe/haxe-TmLanguage haxe.tmLanguage, converted plist→JSON (MIT) |
| janet.tmLanguage.json | sha256 fa3ec6a7b1b3bbbb7341e00ff8da484812b5ad221c759af2178028af7e1f684b | janet-lang/vscode-janet syntaxes/janet.tmLanguage, converted plist→JSON (MIT) |

Raku and Forth are deliberately absent: no cleanly-licensed TextMate
grammar exists for either (Raku's is NOASSERTION-licensed, Forth's
candidates are unlicensed) — the honest skip, like Odin's manifest.
| ocaml.tmLanguage.json | — | ocamllabs/vscode-ocaml-platform (ISC) |
| wit.tmLanguage.json | 426c1ef39db02d4f1aa7d9fd953612382241f1c29c95d71eccf506727571c3f6 | bytecodealliance/vscode-wit (Apache-2.0) — the WebAssembly Component Model IDL, the futures-2031 polyglot-substrate bet |
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
| xml.tmLanguage.json | sha256 98216b32694f2c0c5d2370aff37dcf8223daa61144fa18209678a8b214580c92 | microsoft/vscode 1.95.0 extensions/xml (atom/language-xml), embed-only scope text.xml; patched v1.200.1: comments rule had end/name nested inside captures (upstream sha256 bb51c7b202f20254772c88f86889e0dbcdc734e045b950fe25c0a23b591203d7) — TM4E CCE killed every including grammar | MIT |
| ng-template.tmLanguage.json | sha256 123875ebd14c7057aa9e5228e6ce9d173c4abc449a884e0e1d8993a881ded901 | angular/vscode-ng-language-service main syntaxes/template.json, INJECTION into text.html (v1.217.0) | MIT |
| ng-expression.tmLanguage.json | sha256 ea3d34fe734715305fc5a04e4f2bc0f6188871f13b84aff0f9f8fc149f7d8c3e | angular/vscode-ng-language-service main syntaxes/expression.json, injection | MIT |
| ng-template-blocks.tmLanguage.json | sha256 69d05ab37f883d7c265a0149044f3ea949cbfe336a7c48a74cf720f7ad90ce1e | angular/vscode-ng-language-service main syntaxes/template-blocks.json, injection (@if/@for/@switch/@defer) | MIT |
| ng-let-declaration.tmLanguage.json | sha256 97d6d32d9d2b2f41d514c4f0069c7fe43f024586b66001212aff789fe1588187 | angular/vscode-ng-language-service main syntaxes/let-declaration.json, injection (@let) | MIT |
| ng-template-tag.tmLanguage.json | sha256 b862cfed046f9ad88aa0b39ad1d6204dac22c5f1919f6fe070433b054c9d8d57 | angular/vscode-ng-language-service main syntaxes/template-tag.json, injection (ng-template attrs) | MIT |
| javascriptreact.tmLanguage.json | sha256 ea4a18b3bdc9d6c85e39183104de3fa5a532480eca8013532d567017c396f751 | microsoft/vscode 1.95.0 extensions/javascript (microsoft/TypeScript-TmLanguage), embed-only scope source.js.jsx | MIT |
| sass.tmLanguage.json | sha256 b2c2e4674f7fe777e072b016bcdb10c67ec8f1bf725f97ec1fe86990c64d654d | TheRealSyler/vscode-sass-indented master syntaxes/sass.tmLanguage.json — the canonical INDENTED-dialect grammar (v2.20.0; .sass shared the SCSS grammar approximately before this) | MIT |

YAML, TOML, Markdown, Dockerfile, SQL and diff are intentionally NOT
bundled as *editors*: the NetBeans ide cluster ships native editor
support for those mimes, and a second registration would duplicate
editors. (The embed-only YAML grammar above does not bind an editor.)

## Scope stubs (in-house, not vendored)

`stub-source.*.json` (v2.85.0) are empty-patterns grammars registered
only for their scope names — `source.x86_64`, `source.x86`,
`source.asm`, `source.arm`, `source.sql`, `source.sassdoc`,
`source.glsl`, `source.stylus`, `source.dockerfile`, `source.batchfile`,
`source.diff`, and a second batch of thirty-one (`source.js.regexp`,
`source.js.jquery`, `source.c++`, `text.elixir`, `text.html.elixir`,
`source.regexp.python`, `source.postscript`, `source.less`,
`source.cpp.embedded.macro`, `text.xml.xsl`, `text.tex.latex`,
`text.log`, `text.git-rebase`, `text.git-commit`, `text.bibtex`,
`source.twig`, `source.powershell`, `source.perl.6`, `source.objc`,
`source.json.comments`, `source.go`, `source.asp.vb.net`,
`source.css.postcss`, `text.html.javadoc`, `source.toml`,
`source.postcss`, `source.openesql`, `source.ocaml.ocamldoc`,
`source.ocaml.interface`, `source.json5`, `regexp`) — the scopes
vendored grammars (and the platform's own markdown fences) include and
this product ships no grammar for. A stub must go the day a real
grammar arrives for its scope; the embed test fails on a scope
registered twice. Without
them TM4E logged "No grammar source for scope" 235 times per boot and
pruned every including rule; with them the include resolves, the region
reads as plain text, and the log is quiet. They are written here, carry
no license, and are not counted among the vendored grammars.
