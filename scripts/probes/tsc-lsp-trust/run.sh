#!/bin/bash
# What does TypeScript 7's own language server RUN in a project, and how
# does it report problems? (3.10.0, futures F5)
#
# TypeScript 7 is the Go port. It has no tsserver.js, so
# typescript-language-server cannot use it, and it carries a language
# server of its own: the compiler binary run as `tsc --lsp --stdio`.
# ServerTrust lists every language server as one that runs the project's
# code or one that only reads it, and LanguageServers decides how each is
# launched. Both decisions for this server rest on what is measured here.
#
# The trap project holds what a TypeScript server could run:
#   tsconfig.json naming a language-service plugin, "trap-plugin", which is
#   in node_modules and writes PLUGIN-RAN beside the project when loaded;
#   package.json scripts (prepare, postinstall) that would write markers.
#
#   control    typescript-language-server over the project's own
#              TypeScript 5: tsserver loads the plugin -> PLUGIN-RAN appears
#   treatment  TypeScript 7's native server on the same trap -> no marker
#
# The control is the point: it shows the trap sees a plugin being loaded,
# so the empty result under it is a result.
#
# Beside the markers the driver prints, for each server, whether it sent
# the file's problems by itself (PUSHED), what it returned when asked
# (PULLED), and what it answered to a shutdown carrying "params":null.
#
# Measured 2026-11-06 with typescript 7.0.2, typescript 5.9.3 and
# typescript-language-server 5.3.0 on macOS arm64:
#   control    PLUGIN-RAN written; PUSHED 2; asked, it answers
#              "Unhandled method textDocument/diagnostic"; SHUTDOWN ok
#   treatment  no marker; PUSHED nothing; PULLED 2; SHUTDOWN refused:
#              InvalidParams: expected no params, got null
# So: the user's own TypeScript 7 only reads a project (the one thing of
# the project's that can run is the compiler binary, when it comes from
# the project's node_modules); its problems must be asked for
# (PullDiagnostics); and the null params must go.
#
# usage: run.sh <scratch-dir>      (installs the three packages there with npm)
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
W=${1:?usage: run.sh <scratch-dir>}
mkdir -p "$W" && cd "$W"

mkdir -p tools7 tools5
[ -d tools7/node_modules/typescript ] || (cd tools7 && npm init -y >/dev/null && npm install --ignore-scripts typescript@7 >/dev/null)
[ -x tools5/node_modules/.bin/typescript-language-server ] \
    || (cd tools5 && npm init -y >/dev/null && npm install --ignore-scripts typescript-language-server@5 >/dev/null)
NATIVE=$(find "$W/tools7/node_modules/@typescript" -path '*/lib/tsc' -o -path '*/lib/tsc.exe' | head -1)
[ -n "$NATIVE" ] || { echo "no TypeScript 7 compiler for this machine was installed"; exit 1; }

trap_project() {
    rm -rf "$1"; mkdir -p "$1/src" "$1/node_modules/trap-plugin"
    printf '{"name":"trap","version":"1.0.0","scripts":{"prepare":"touch PREPARE-RAN","postinstall":"touch POSTINSTALL-RAN"}}\n' > "$1/package.json"
    printf '{"compilerOptions":{"strict":true,"noEmit":true,"plugins":[{"name":"trap-plugin"}]},"include":["src"]}\n' > "$1/tsconfig.json"
    printf '{"name":"trap-plugin","version":"1.0.0","main":"index.js"}\n' > "$1/node_modules/trap-plugin/package.json"
    cat > "$1/node_modules/trap-plugin/index.js" <<'JS'
require("fs").writeFileSync(require("path").join(__dirname, "..", "..", "PLUGIN-RAN"), "loaded by pid " + process.pid);
module.exports = function init() { return { create(info) { return info.languageService; } }; };
JS
    printf 'const n: number = "x"; export {};\n' > "$1/src/main.ts"
}

trap_project "$W/control"
# the project's own TypeScript 5, as a clone that arrived with its node_modules has it;
# copied in after, because an npm install here would remove the plugin as extraneous
(cd tools5 && npm install --ignore-scripts typescript@5 >/dev/null)
/bin/cp -R "$W/tools5/node_modules/typescript" "$W/control/node_modules/typescript"
trap_project "$W/treatment"

echo "== control: typescript-language-server over the project's TypeScript 5"
node "$HERE/drive.mjs" "$W/control" "$W/control/src/main.ts" 5 "$W/tools5/node_modules/.bin/typescript-language-server" --stdio
echo "== treatment: TypeScript 7's native server"
node "$HERE/drive.mjs" "$W/treatment" "$W/treatment/src/main.ts" 5 "$NATIVE" --lsp --stdio

echo "== markers"
ls "$W/control" "$W/treatment" | grep -E 'RAN$|^/' || true
[ -e "$W/control/PLUGIN-RAN" ] || { echo "THE CONTROL SAW NOTHING: the trap cannot see a plugin load, so it proves nothing"; exit 1; }
if ls "$W/treatment" | grep -q 'RAN$'; then
    echo "TYPESCRIPT 7's SERVER RAN PROJECT CODE: the user's own compiler no longer only reads; change ServerTrust.runsHere"
    exit 1
fi
echo "TypeScript 7's server reads: nothing of the project ran, and the control's plugin did"
