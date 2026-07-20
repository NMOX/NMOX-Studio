# Tutorial: Polyglot editing & debugging

NMOX Studio edits 70+ languages with real syntax highlighting, a
Navigator outline, and language-server intelligence — and it debugs
JavaScript/TypeScript (and the browser) out of the box with breakpoints
that actually stop. This tutorial hits a breakpoint in a Node app.

<!-- screenshot: the editor stopped at a breakpoint, variables and call stack visible -->

## Before you start

Open (or scaffold) a small Node project with a script you can run, e.g.
an Express route or a plain `node server.js`.

## Steps

1. **Open a source file.** Highlighting, bracket matching, code folding,
   and mark-occurrences all come up automatically. The **Navigator**
   shows the file's outline; language servers (installed via
   `Tools ▸ Environment Doctor` hints) add completion and diagnostics.

2. **Set a breakpoint.** Click the editor gutter on a line inside your
   handler — a breakpoint dot appears.

3. **Debug the file.** Run **Debug File** (or "Debug in Chrome
   (breakpoints)" for an HTML/JS page). A one-time Workspace Trust prompt
   guards the spawn; then the vendored `js-debug` adapter launches your
   program.

4. **Hit the breakpoint.** Trigger the code path (make the request, or
   let the script reach the line). Execution **stops** at your
   breakpoint — inspect variables, walk the call stack, step over/into.
   For browser debugging, a throwaway-profile Chrome opens at your live
   dev-server URL and page breakpoints map back into the IDE.

## What you just learned

- The editor treats 70+ languages as first-class (TextMate grammars +
  CSL + LSP); config files (YAML, TOML, Dockerfile, nginx…) are covered
  too.
- JS/TS debugging is built in — a session multiplexer flattens
  js-debug's child sessions so the platform's single-session debugger
  can drive it.
- Every debug spawn is trust-gated and killed as a whole process tree on
  stop (no orphans).

## Next

- **Run Focused Test** debugs a single test method per language.
- Diagnostics from rack tools (eslint/tsc/phpstan) land in the platform
  Action Items window.
