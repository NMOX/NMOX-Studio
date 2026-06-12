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

To refresh: bump the tag in this table and re-download; the holder
classes in this package register each grammar with the platform's
textmate-lexer module.
