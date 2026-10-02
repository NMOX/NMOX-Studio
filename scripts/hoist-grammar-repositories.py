#!/usr/bin/env python3
"""Hoist a TextMate grammar's rule-local repositories to its top level.

    scripts/hoist-grammar-repositories.py <grammar.tmLanguage.json>...

A TextMate rule may carry a `repository` of its own, whose names are visible
only inside that rule and shadow the grammar's. VS Code's engine resolves an
`#include` against the nearest one. TM4E, the engine the editor runs, looks
only in the grammar's top-level repository: it logs "CANNOT find rule for
scopeName [#parens]" and drops the rule that asked. In Ruby that is every
percent literal - %w(...), %i[...], %q{...}, %r<...> - and the braces inside
a string interpolation (3.5: found as 109 warnings in the log each time a
Markdown or Ruby file opened).

The transform keeps the grammar's meaning and changes its shape: each local
definition moves to the top-level repository under a name no other
definition has (`parens` becomes `parens__2`), and every include that could
see it is rewritten to the new name. Run once per vendored grammar when it
is added or bumped; `RuleLocalRepositoriesGateTest` fails the build while a
shipped grammar still carries one. A file with none is left byte-identical.
"""
import json
import sys


def hoist(grammar):
    top = grammar.setdefault("repository", {})
    taken = set(top.keys())
    counter = [0]
    moved = []

    def fresh(name):
        while True:
            counter[0] += 1
            candidate = "%s__%d" % (name, counter[0])
            if candidate not in taken:
                taken.add(candidate)
                return candidate

    def walk(node, visible, is_root):
        if isinstance(node, list):
            for child in node:
                walk(child, visible, False)
            return
        if not isinstance(node, dict):
            return
        if not is_root and isinstance(node.get("repository"), dict):
            local = node.pop("repository")
            visible = dict(visible)
            renamed = {}
            for name in local:
                renamed[name] = fresh(name)
                visible[name] = renamed[name]
            # a local definition sees its siblings and itself
            for name, definition in local.items():
                walk(definition, visible, False)
                moved.append((renamed[name], definition))
        include = node.get("include")
        if isinstance(include, str) and include.startswith("#") and include[1:] in visible:
            node["include"] = "#" + visible[include[1:]]
        for key, child in node.items():
            if is_root and key == "repository":
                for definition in child.values():
                    walk(definition, visible, False)
            else:
                walk(child, visible, False)

    walk(grammar, {}, True)
    for name, definition in moved:
        top[name] = definition
    return len(moved)


def main(paths):
    for path in paths:
        with open(path, encoding="utf-8") as f:
            text = f.read()
        grammar = json.loads(text)
        count = hoist(grammar)
        if count == 0:
            print("%s: no rule-local repository, left as it is" % path)
            continue
        indent = "\t" if "\n\t" in text else 2
        with open(path, "w", encoding="utf-8") as f:
            json.dump(grammar, f, indent=indent, ensure_ascii=False)
            f.write("\n")
        print("%s: %d definitions hoisted" % (path, count))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv[1:])
