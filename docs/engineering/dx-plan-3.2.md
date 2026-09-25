# The 3.2 developer-experience plan

*Written 2026-09-24, the night after 3.1.0 shipped. 3.1 asked how long the
first hour takes. 3.2 asks about the second week.*

## The question

The developer who got through the first hour on Tuesday is still here the
following Monday. They commit twenty times a day, review diffs, hand work to
a coding agent and write a README. Where does NMOX Studio make them leave for
another tool, and where does it make them repeat themselves?

## What the first survey found

1. **Git cannot use NMOX Studio as its editor.** `code --wait` is how a VS
   Code user writes commit messages, rebase plans and merge messages;
   `nmox -w` is refused by name ("has no counterpart here"), and so is
   `nmox -d` for `git difftool`. The platform's command-line handshake
   already keeps a caller waiting while a handler works (the server writes a
   keep-alive byte every second, read from the RELEASE310 `CLIHandler`
   bytecode), and every connection gets its own server thread, so a waiting
   commit does not block a second `nmox .`.
2. **The Agent Port forgets its address and token on every restart.** An
   agent configured once is disconnected the next morning; the dialog offers
   a `.mcp.json` snippet but not the one-line `claude mcp add` a Claude Code
   user types.
3. **CONTRIBUTING's everyday rebuild fails.** `mvn -o install -DskipTests`,
   the command it says to type "every time after", fails on the JaCoCo floor
   after any focused test run has left a partial `jacoco.exec` behind: the
   check runs with the tests skipped and measures the leftovers.

## The plan

| # | Unit | Answers | Proof |
|---|------|---------|-------|
| 1 | `nmox --wait` and `nmox --diff`: NMOX Studio as git's editor and difftool, on all three OSes | 1 | a real `git commit` whose message was typed in the IDE; `git difftool` with the diff view |
| 2 | The Agent Port keeps its address and token when asked to, starts with the IDE when asked to, and offers the `claude mcp add` line | 2 | a Claude Code configured once reconnects after a restart |
| 3 | The JaCoCo floor stands down when the tests do | 3 | a focused run, then the everyday rebuild, green |

Every row the walks add is written below as it is found.

## What the walks added, in the order they found it

| # | Found by | What was wrong | What 3.2.0 does | Proof |
|---|----------|----------------|-----------------|-------|
