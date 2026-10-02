#!/usr/bin/env python3
"""Rewrite the look-behinds the editor's regex engine cannot compile.

    scripts/rewrite-grammar-lookbehinds.py <grammar-directory>

TextMate grammars are written for Oniguruma, the C library VS Code runs.
The editor runs TM4E over joni, a Java port, and joni compiles a look-behind
only when every alternative at its top level has a fixed length. Recent
Oniguruma takes more, upstream grammars use it, and joni answers "invalid
pattern in look-behind" at the moment the rule is first needed. The platform's
lexer does not catch that. Until 3.5.4 every Elixir file and every Haxe file
threw on its first line, a Svelte file on its first `{#if}`, a PureScript file
on its first `data` declaration, and JavaScript or TypeScript read through
these grammars (a fenced block in Markdown, a Vue or Svelte script) on its
first `using` declaration: an exception in the log for each repaint and no
colour after it.

Each rewrite below is the nearest pattern joni compiles. Where the original
looked back over "any whitespace", the rewrite looks back over none to four
characters of it, which is every line a formatter writes. Where the original
looked back over "anything" from a point that is always directly behind the
keyword (Svelte's block modes begin at \\G, the end of the keyword's own
match), the rewrite looks at the keyword. One rule cannot be bounded: Haxe's
fallback return-type hint asks whether `function` appears anywhere earlier on
the line. It is switched off; the method rule's own return-type rule, which
begins with the fixed `(?<=\\))`, is the one that colours a return type.

Idempotent: a grammar already rewritten is left byte-identical. After bumping
one of these grammars run it again; `GrammarRegexesCompileGateTest` compiles
every pattern of every shipped grammar with the editor's own engine and fails,
naming the pattern, while one does not compile.
"""
import json
import os
import sys

WS = "|".join(["%s", "%s\\s", "%s\\s{2}", "%s\\s{3}", "%s\\s{4}"])

# file -> [(the pattern upstream wrote, the pattern joni compiles)]
REWRITES = {
    "elixir.tmLanguage.json": [
        ("(?<=\\|\\>\\s*)([_\\p{Ll}\\p{Lo}][\\p{L}\\p{N}_]*[?!]?)",
         "(?<=" + WS % (("\\|\\>",) * 5) + ")([_\\p{Ll}\\p{Lo}][\\p{L}\\p{N}_]*[?!]?)"),
    ],
    "haxe.tmLanguage.json": [
        ("(?<=\\bfunction\\b.+\\))\\s*(:)", "(?!)"),
    ],
    "purescript.tmLanguage.json": [
        # the first alternative keeps the group, so the constructor stays capture 2
        ("(?<=(\\||=)\\s*)([\\p{Lu}\\p{Lt}][\\p{Ll}_\\p{Lu}\\p{Lt}\\p{Nd}']*)",
         "(?<=([|=])|[|=]\\s|[|=]\\s{2}|[|=]\\s{3}|[|=]\\s{4})([\\p{Lu}\\p{Lt}][\\p{Ll}_\\p{Lu}\\p{Lt}\\p{Nd}']*)"),
    ],
    "svelte.tmLanguage.json": [
        ("(?<=(if|key|then|catch|html|render).*?)\\G", "(?<=if|key|then|catch|html|render)\\G"),
        ("(?<=snippet.*?)\\G", "(?<=snippet)\\G"),
        ("(?<=>\\s*\\()", "(?<=>\\(|>\\s\\()"),
        ("(?<=const.*?)\\G", "(?<=const)\\G"),
        ("(?<=each.*?)\\G", "(?<=each)\\G"),
        ("(?<=await.*?)\\G", "(?<=await)\\G"),
        ("(?<=debug.*?)\\G", "(?<=debug)\\G"),
        ("(?<=(use|transition|in|out|animate):).*$", "(?<=use:|transition:|in:|out:|animate:).*$"),
        ("(?<=(let|class|style):).*$", "(?<=let:|class:|style:).*$"),
    ],
}
# `await using`: one whitespace character between the words, as it is written
USING = ("^await\\s+using|[^\\._$[:alnum:]]await\\s+using)",
         "^await\\susing|[^\\._$[:alnum:]]await\\susing)")
for name in ("javascript", "javascriptreact", "typescript", "typescriptreact"):
    REWRITES[name + ".tmLanguage.json"] = [USING]


def in_json(pattern):
    """The pattern as it stands inside a JSON string."""
    return json.dumps(pattern, ensure_ascii=False)[1:-1]


def main(directory):
    changed = 0
    for name in sorted(REWRITES):
        path = os.path.join(directory, name)
        with open(path, encoding="utf-8", newline="") as f:
            text = f.read()
        before = text
        for old, new in REWRITES[name]:
            old_text, new_text = in_json(old), in_json(new)
            if old_text in text:
                text = text.replace(old_text, new_text)
            elif new_text not in text:
                sys.exit("%s: neither the pattern upstream wrote nor its rewrite is here: %s\n"
                         "The grammar changed; decide the rewrite again and update this table." % (name, old))
        if text != before:
            json.loads(text)  # what is written is a grammar, or nothing is written
            with open(path, "w", encoding="utf-8", newline="") as f:
                f.write(text)
            changed += 1
            print("%s: rewritten" % name)
    if not changed:
        print("every look-behind in the table is already rewritten")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
