#!/usr/bin/env python3
"""Give every rule a shipped grammar includes, and nobody defines, a definition.

    scripts/stub-dangling-grammar-includes.py <grammar-directory>

A TextMate grammar includes a rule by name: `#arithmetic_dollar` is a rule
in its own repository, `source.cpp#root_context` one in another grammar's.
Upstream grammars rename and delete rules and leave the includes behind: 29
of them in 14 of the grammars this product ships, and three more across
grammars (3.5.4). VS Code's engine passes over an include it cannot resolve.
TM4E, the engine the editor runs, passes over it too and logs a WARNING each
time the including rule is compiled: seven lines in the log of every session
that opens a Markdown file, whose fenced blocks reach the shell, C++, Less,
Swift and Julia grammars.

The stub is a rule that matches nothing, which is exactly what an include of
a missing rule already was. It carries a comment saying so. Run after adding
or bumping a vendored grammar; `DanglingIncludesGateTest` fails the build
while a shipped grammar still includes a rule that is defined nowhere. A
grammar with none is left byte-identical.
"""
import json
import os
import sys

STUB_COMMENT = ("NMOX stub: upstream includes this rule and defines it nowhere. "
                "It matches nothing, as the unresolved include did; it exists so that "
                "the engine has something to resolve "
                "(scripts/stub-dangling-grammar-includes.py).")


def includes(node, out):
    if isinstance(node, dict):
        target = node.get("include")
        if isinstance(target, str):
            out.add(target)
        for value in node.values():
            includes(value, out)
    elif isinstance(node, list):
        for value in node:
            includes(value, out)


def repository_opens_at(text):
    """Offset just past the `{` of the top-level "repository", or None."""
    depth = 0
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == '"':
            j = i + 1
            while text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            if depth == 1 and text[i + 1:j] == "repository":
                k = j + 1
                while text[k] in " \t\r\n":
                    k += 1
                if text[k] == ":":
                    k += 1
                    while text[k] in " \t\r\n":
                        k += 1
                    if text[k] == "{":
                        return k + 1
            i = j + 1
            continue
        if c in "{[":
            depth += 1
        elif c in "}]":
            depth -= 1
        i += 1
    return None


def insert(text, rules):
    """The grammar's own text with the stubs added first in its repository.

    The file is not re-serialized: a vendored grammar keeps its bytes, so
    that what changed here is what a reader of the diff sees.
    """
    at = repository_opens_at(text)
    if at is None:
        return None
    rest = text[at:].lstrip(" \t\r\n")
    if rest.startswith("}"):
        return None
    line_end = "\r\n" if "\r\n" in text else "\n"
    # the indentation of the entry that follows
    after = text[at:]
    indent = after[len(after) - len(after.lstrip("\r\n")):]
    indent = indent[:len(indent) - len(indent.lstrip(" \t"))]
    stubs = "".join(
        "%s%s%s: %s," % (line_end, indent, json.dumps(rule),
                          json.dumps({"comment": STUB_COMMENT, "patterns": []}, ensure_ascii=False))
        for rule in rules)
    return text[:at] + stubs + text[at:]


def main(directory):
    grammars = {}
    texts = {}
    by_scope = {}
    for name in sorted(os.listdir(directory)):
        if not name.endswith(".json"):
            continue
        path = os.path.join(directory, name)
        with open(path, encoding="utf-8") as f:
            texts[name] = f.read()
        grammars[name] = json.loads(texts[name])
        scope = grammars[name].get("scopeName")
        if isinstance(scope, str):
            by_scope[scope] = name

    missing = {}
    for name, grammar in grammars.items():
        found = set()
        includes(grammar, found)
        for target in found:
            if target.startswith("#"):
                owner, rule = name, target[1:]
            elif "#" in target:
                scope, rule = target.split("#", 1)
                owner = by_scope.get(scope)
                if owner is None:
                    continue  # a grammar this product does not ship
            else:
                continue
            if rule not in (grammars[owner].get("repository") or {}):
                missing.setdefault(owner, set()).add(rule)

    for name in sorted(grammars):
        rules = sorted(missing.get(name, ()))
        if not rules:
            continue
        text = insert(texts[name], rules)
        if text is None:
            # no top-level repository to insert into, or an empty one: these
            # are the product's own few-line stub grammars, rewritten whole
            repository = grammars[name].setdefault("repository", {})
            for rule in rules:
                repository[rule] = {"comment": STUB_COMMENT, "patterns": []}
            text = json.dumps(grammars[name], indent=2, ensure_ascii=False) + "\n"
        json.loads(text)  # what is written is a grammar, or nothing is written
        with open(os.path.join(directory, name), "w", encoding="utf-8") as f:
            f.write(text)
        print("%s: %d stubbed: %s" % (name, len(rules), ", ".join(rules)))
    if not missing:
        print("every included rule is defined")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
