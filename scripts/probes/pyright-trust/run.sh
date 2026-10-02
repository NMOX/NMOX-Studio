#!/bin/bash
# What does pyright RUN in a project it is pointed at? (3.5.10, ledger 134)
#
# ServerTrust lists every language server as one that runs the project's
# code or one that only reads it. pyright was listed as running, on the
# reading that a pyrightconfig.json can name an environment whose
# interpreter it then starts. This measures it instead.
#
# Each scratch project holds everything that would write a marker if it
# were run: sitecustomize.py, usercustomize.py, a json.py that shadows the
# standard library, .venv/bin/python and venv/bin/python, a .pth file in
# each venv, a python in the project root, the package's own __init__.py.
#
#   A  no configuration                      -> expected: no marker
#   B  pyrightconfig.json naming .venv       -> expected: no marker
#   C  a pyproject.toml with no pyright table-> expected: no marker
#   D  a lone file, no workspace, config and venv beside it -> no marker
#   control: the CLIENT names B's .venv interpreter (python.pythonPath in
#            its answer to workspace/configuration) -> the marker appears
#
# The control is the point: it shows the probe sees an interpreter being
# run, so the empty results above it are results. The platform's client
# answers every workspace/configuration item with null (read from
# LanguageClientImpl), so the control's case does not arise in the product.
#
# Measured 2026-10-02 with pyright 1.1.414 and Python 3.10.4: A, B, C, D
# wrote nothing; the control wrote B-.venv-python.
#
# usage: run.sh <scratch-dir>      (installs pyright there with npm)
set -eu
HERE=$(cd "$(dirname "$0")" && pwd)
W=${1:?usage: run.sh <scratch-dir>}
mkdir -p "$W" && cd "$W"
[ -x node_modules/.bin/pyright-langserver ] || { npm init -y >/dev/null; npm install pyright >/dev/null; }
/bin/cp -f "$HERE/drive.py" "$HERE/mkproj.sh" .
chmod +x mkproj.sh
rm -rf markers; mkdir -p markers neutral
for p in A B C D; do ./mkproj.sh "$W/$p"; done
printf '{"venvPath": ".", "venv": ".venv"}\n' > B/pyrightconfig.json
printf '[project]\nname = "c"\nversion = "0.1"\n' > C/pyproject.toml
printf '{"venvPath": ".", "venv": ".venv"}\n' > D/pyrightconfig.json
S="$W/node_modules/.bin/pyright-langserver"
for p in A B C; do echo "== $p"; python3 drive.py "$S" "$W/$p" "$W/$p" "$W/$p/main.py" 8; done
echo "== D (a lone file)"; python3 drive.py "$S" "$W/neutral" - "$W/D/main.py" 8
echo "== markers after A, B, C, D (expected: none)"; ls markers
[ -z "$(ls markers)" ] || { echo "PYRIGHT RAN PROJECT CODE: move it back to RUNS in ServerTrust"; exit 1; }
echo "== control"; PYR_PYTHONPATH="$W/B/.venv/bin/python" python3 drive.py "$S" "$W/B" "$W/B" "$W/B/main.py" 8
echo "== markers after the control (expected: B-.venv-python)"; ls markers
[ -e markers/B-.venv-python ] || { echo "THE CONTROL SAW NOTHING: the probe cannot see an interpreter run, so it proves nothing"; exit 1; }
echo "pyright reads: nothing of the project ran, and the control did"
