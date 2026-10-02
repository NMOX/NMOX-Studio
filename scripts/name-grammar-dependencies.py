#!/usr/bin/env python3
"""Make a grammar name the grammars it needs loaded, where the engine looks.

    scripts/name-grammar-dependencies.py <grammar.json> <scope> [<scope> ...]

Before TM4E, the engine the editor runs, tokenizes with a grammar, it walks
the grammar's rules and loads every grammar they include. The walk has two
holes (3.5.4). It does not look inside `captures`, so a grammar included only
from a capture is never loaded: the shell command of an `exec` line in a git
rebase todo, the JavaScript between backticks in CoffeeScript. And it keeps
the rules it has visited in a HashSet, where a rule is a HashMap, so a rule
that reads the same as one visited earlier in ANOTHER grammar counts as
visited: `{"include": "#comments"}` is such a rule, and what it leads to is
different in every grammar that has one. An include of a grammar that was not
loaded resolves to nothing; the engine logs `CANNOT find grammar for
scopeName` and drops the rule that asked.

This adds one rule, first in the grammar's top-level patterns, that the walk
cannot miss and the tokenizer can never match: it begins with `(?!)`, which
matches nowhere, and its patterns include the named scopes. Its comment names
the grammar, so no other grammar has a rule that reads the same. Run again
with more scopes and they are added to the rule already there.

`GrammarDependenciesLoadGateTest` loads every registered grammar through the
real engine and fails, with the command to run, while one of them reaches a
grammar the engine did not load. A vendored grammar is not re-serialized:
the diff is the one line.
"""
import json
import sys

MARK = "NMOX loader for "


def comment(scope_name):
    return (MARK + scope_name + ": this rule never matches. It names grammars this one "
            "includes, or the ones it includes do, where the engine's dependency walk does "
            "not find them, so that they are loaded (scripts/name-grammar-dependencies.py).")


def loader(scope_name, scopes):
    return {"comment": comment(scope_name), "begin": "(?!)", "end": "(?!)",
            "patterns": [{"include": s} for s in scopes]}


def patterns_open_at(text):
    """Offset just past the `[` of the top-level "patterns", or None."""
    depth = 0
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == '"':
            j = i + 1
            while text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            if depth == 1 and text[i + 1:j] == "patterns":
                k = j + 1
                while text[k] in " \t\r\n":
                    k += 1
                if text[k] == ":":
                    k += 1
                    while text[k] in " \t\r\n":
                        k += 1
                    if text[k] == "[":
                        return k + 1
            i = j + 1
            continue
        if c in "{[":
            depth += 1
        elif c in "}]":
            depth -= 1
        i += 1
    return None


def main(path, scopes):
    with open(path, encoding="utf-8", newline="") as f:
        text = f.read()
    grammar = json.loads(text)
    scope_name = grammar.get("scopeName")
    if not isinstance(scope_name, str):
        sys.exit("%s: no scopeName; not a grammar" % path)
    top = grammar.get("patterns")
    if not isinstance(top, list):
        sys.exit("%s: no top-level patterns to put the rule in" % path)

    named = []
    existing = None
    if top and isinstance(top[0], dict) and str(top[0].get("comment", "")).startswith(MARK):
        existing = top[0]
        named = [p["include"] for p in existing.get("patterns", [])]
    wanted = sorted(set(named) | set(scopes))
    if wanted == named:
        print("%s: already names %s" % (path, ", ".join(named)))
        return
    line = json.dumps(loader(scope_name, wanted), ensure_ascii=False)

    if existing is not None:
        # the rule this script wrote is one line; replace that line's rule
        old = json.dumps(loader(scope_name, named), ensure_ascii=False)
        if text.count(old) != 1:
            sys.exit("%s: its loader rule was edited by hand; restore it or remove it first" % path)
        text = text.replace(old, line)
    else:
        at = patterns_open_at(text)
        if at is None:
            sys.exit("%s: could not find the top-level patterns in the text" % path)
        line_end = "\r\n" if "\r\n" in text else "\n"
        after = text[at:]
        if after.lstrip(" \t\r\n").startswith("]"):
            text = text[:at] + line + text[at:]
        else:
            lead = after[:len(after) - len(after.lstrip("\r\n"))]
            rest = after[len(lead):]
            indent = rest[:len(rest) - len(rest.lstrip(" \t"))]
            if lead:
                text = text[:at] + line_end + indent + line + "," + text[at:]
            else:
                # a one-line array: [{"include": …}, …]
                text = text[:at] + line + ", " + text[at:]

    after_edit = json.loads(text)  # what is written is a grammar, or nothing is written
    first = after_edit["patterns"][0]
    assert [p["include"] for p in first["patterns"]] == wanted
    assert after_edit["patterns"][1:] == (top[1:] if existing is not None else top)
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(text)
    print("%s: names %s" % (path, ", ".join(wanted)))


if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2:])
