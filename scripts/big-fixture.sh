#!/bin/bash
# The 3.3 big-project fixture (docs/engineering/dx-plan-3.3.md, "How it is
# measured"): an npm-workspaces monorepo of 200 packages × 250 JS files
# (50,202 tracked files in 800 directories, committed so git has real work)
# with an IGNORED node_modules of 60,000 files, built in under a minute
# from a fixed seed — the same tree every time, so a measurement taken on
# it tomorrow compares with one taken today. Nothing is downloaded; no
# install runs; the "dependencies" are inert stubs.
#
# Usage: scripts/big-fixture.sh <empty-or-absent target dir>
# Then:  <the assembled app> --aim <dir>   (see the plan for the JFR recipe)
set -euo pipefail
DIR=${1:?usage: big-fixture.sh <target dir>}
if [ -e "$DIR" ] && [ -n "$(ls -A "$DIR" 2>/dev/null)" ]; then
  echo "REFUSED: $DIR exists and is not empty (this script never clobbers)"; exit 1
fi
mkdir -p "$DIR"
python3 - "$DIR" <<'EOF'
import os, random, sys, json
root = sys.argv[1]
rnd = random.Random(20260925)   # the seed IS the fixture
FIRST = ["alpha", "beta", "gamma", "delta", "epsilon", "zeta", "theta", "kappa",
         "lambda", "omega", "sigma", "build", "cache", "store", "parse", "fetch",
         "queue", "route", "render"]
WORDS = ["alpha", "beta", "gamma", "delta", "epsilon", "zeta", "theta", "kappa",
         "lambda", "omega", "build", "render"]
def name(used):
    while True:
        n = rnd.choice(FIRST) + rnd.choice(FIRST).capitalize() + "%03d" % rnd.randrange(250)
        if n not in used:
            used.add(n); return n
def module(base):
    lines = []
    for i in range(12):
        lines.append("export function %s_%d(x) { return x + %d; // %s\n}" % (base, i, i, rnd.choice(WORDS)))
    return "\n".join(lines) + "\n"
json.dump({"name": "big-monorepo", "private": True, "workspaces": ["packages/*"],
           "scripts": {"test": "node --test", "dev": "node packages/pkg-000/src/index.js"}},
          open(os.path.join(root, "package.json"), "w"), indent=2)
open(os.path.join(root, ".gitignore"), "w").write("node_modules/\ndist/\n*.log\n")
for p in range(200):
    pkg = os.path.join(root, "packages", "pkg-%03d" % p)
    for sub, count in (("src", 67), ("src/lib", 166), ("test", 17)):
        d = os.path.join(pkg, sub); os.makedirs(d, exist_ok=True)
        used = set()
        for _ in range(count):
            base = name(used)
            open(os.path.join(d, base + ".js"), "w").write(module(base))
    open(os.path.join(pkg, "package.json"), "w").write(
        '{"name": "@big/pkg-%03d", "version": "1.0.0", "main": "src/index.js"}' % p)
for d in range(600):   # the ignored weight: 600 stubs × 100 files
    dep = os.path.join(root, "node_modules", "dep-%03d" % d, "lib"); os.makedirs(dep)
    for m in range(100):
        open(os.path.join(dep, "m%d.js" % m), "w").write("module.exports = %d;\n" % m)
EOF
cd "$DIR"
git init -q
git add -A
git -c user.name=fixture -c user.email=fixture@example.invalid -c commit.gpgsign=false commit -qm big
echo "tracked: $(git ls-files | wc -l | tr -d ' ')  ignored: $(find node_modules -type f | wc -l | tr -d ' ')  dirs: $(find packages -type d | wc -l | tr -d ' ')"
