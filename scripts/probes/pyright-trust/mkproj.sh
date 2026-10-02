#!/bin/bash
# mkproj.sh <dir> : a Python project full of things that write a marker if anything RUNS them
set -eu
D=$1; M=$(cd "$(dirname "$0")" && pwd)/markers; mkdir -p "$M"; rm -rf "$D"; mkdir -p "$D/.venv/bin" "$D/venv/bin" "$D/pkg"
REAL=$(command -v python3)
trap_py() { printf 'open(%s, "a").write("ran\\n")\n' "'$M/$1'" ; }
for f in sitecustomize usercustomize json conftest setup; do trap_py "$(basename "$D")-$f.py" > "$D/$f.py"; done
trap_py "$(basename "$D")-pkg-init" > "$D/pkg/__init__.py"
{ trap_py "$(basename "$D")-main"; echo 'import pkg'; echo 'print(undefined_name)'; } > "$D/main.py"
for v in .venv venv; do
  for b in python python3; do
    printf '#!/bin/bash\necho ran >> "%s"\nexec "%s" "$@"\n' "$M/$(basename "$D")-$v-$b" "$REAL" > "$D/$v/bin/$b"; chmod +x "$D/$v/bin/$b"
  done
  printf 'home = /usr/bin\ninclude-system-site-packages = false\nversion = 3.10.4\n' > "$D/$v/pyvenv.cfg"
  mkdir -p "$D/$v/lib/python3.10/site-packages"
  printf 'import os; open("%s","a").write("ran\\n")\n' "$M/$(basename "$D")-$v-pth" > "$D/$v/lib/python3.10/site-packages/trap.pth"
done
for b in python python3; do printf '#!/bin/bash\necho ran >> "%s"\nexec "%s" "$@"\n' "$M/$(basename "$D")-root-$b" "$REAL" > "$D/$b"; chmod +x "$D/$b"; done
