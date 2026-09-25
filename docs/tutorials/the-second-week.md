# The Second Week

<!-- languages -->
**English** · [Español](the-second-week.es.md) · [Français](the-second-week.fr.md) · [Deutsch](the-second-week.de.md) · [Русский](the-second-week.ru.md) · [Українська](the-second-week.uk.md) · [Polski](the-second-week.pl.md) · [Português (Brasil)](the-second-week.pt.md) · [Bahasa Indonesia](the-second-week.id.md) · [Filipino](the-second-week.tl.md) · [Tiếng Việt](the-second-week.vi.md) · [简体中文](the-second-week.zh.md) · [हिन्दी](the-second-week.hi.md) · [עברית](the-second-week.he.md) · [العربية](the-second-week.ar.md)
<!-- /languages -->

*Commit, review, resolve, propose — without leaving for another tool.*

The first hour is opening a project and running it. The second week is
everything around the code: twenty commits a day, a diff to read before
each one, a conflict after a pull, a pull request after a push, a stack
trace to chase, a README to keep honest. This walkthrough is one sitting
in a git repository you already have, and every step is something you
will do again tomorrow.

## 1. Make NMOX Studio git's editor

**Do:** Team ▸ **Use NMOX Studio with Git…**

**See:** the six global git settings that make NMOX Studio git's editor,
difftool and mergetool, each beside the value it has **now**, so nothing
is replaced unseen. **Apply** sets them (**Close** is the default button,
because this writes your global git configuration); **Copy Commands**
puts the `git config` lines on the clipboard instead. When git already
uses NMOX Studio the dialog says so and offers no Apply.

The same lines, if you prefer a terminal:

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
git config --global merge.tool nmox
git config --global mergetool.nmox.cmd 'nmox -w "$MERGED"'
git config --global mergetool.nmox.trustExitCode false
```

## 2. Commit

**Do:** change a file, then in a terminal:

```bash
git commit -a
```

**See:** the commit message opens in NMOX Studio, and the status line
says a terminal is waiting for it. Git's `#` lines are comments; only
what you write is spellchecked; a summary line longer than 72
characters, where git's own tools cut it, gets a warning past the 72nd.
Save, close the tab, and the commit lands — the terminal was holding
until you did. Quitting the IDE with the message still open hands it
back too, with whatever was saved.

`git rebase -i` opens its list the same way: each command and commit
highlighted, and **Toggle Comment** drops a line without deleting it.

## 3. Know where you stand

**See:** the **⎇ chip** on the status line — `⎇ main ±3 ↑2 ↓1` is your
branch, three changed files, two commits to push and one to pull (the
arrows appear only when there is something to push or pull). Its menu
starts with **Switch Branch…** and **Commit…**.

**Do:** put the caret on any line of a tracked file.

**See:** beside the chip, who last changed that line, how long ago and
why: `Ada Lovelace, 3 days ago · Fix the parser`. A line you have not
committed says so, and a file with unsaved changes says that instead of
naming the wrong author. Click the note for the whole file's
annotations; **View ▸ Line Blame** turns it off.

## 4. Review a diff

**Do:**

```bash
git difftool
```

**See:** each changed file side by side in NMOX Studio's diff view, with
**Previous / Next Difference** and "Difference 2 of 5" above it. An added
or deleted file shows its missing side as an empty pane ("no file"); a
binary file shows as binary, and the bar says whether two binaries
differ. Close the tab and git moves to the next file.

## 5. Resolve a conflict

**Do:** merge a branch that conflicts, then:

```bash
git mergetool
```

**See:** the conflicted file in the editor with the current and
incoming sides tinted, and a warning on each `<<<<<<<` line. Put the
caret on it and press ⌘. (Alt+Enter elsewhere), or use **Source ▸ Fix
Code…**: **Accept Current Change**, **Accept Incoming Change** or
**Accept Both Changes**, each one undoable edit. A block that changed
since the offer is refused rather than guessed at. Save, close the tab,
and answer git.

## 6. Propose it

**Do:** push, then Team ▸ **New Pull Request on GitHub** (also on the
chip's menu).

**See:** GitHub's own New Pull Request page for your branch, in your own
browser, where you are signed in. From an editor, **Edit ▸ Open on
GitHub** and **Copy GitHub Link** give the line or lines you are on; on
Project Studio's tree they give a file or a folder.

## 7. Chase a failure

**Do:** run your tests in the Terminal (⌃\`) until one fails.

**See:** a location in the output — `src/app.ts:42:7`, a stack frame
`(/abs/app.js:10:5)`, `--> src/main.rs:3:5`, `File "x.py", line 12` —
opens at that line and column on ⌘-click (Ctrl-click on Windows and
Linux). A URL or `localhost:3000` is never a link, and a path that is not
there is refused by name rather than guessed.

## 8. Keep the README honest

**Do:** Tools ▸ **Check Markdown Links…**

**See:** every relative link and image in the project's Markdown checked
the way GitHub renders it — the file must exist, and a `#heading` must be
a heading of that file. A dead link is an error, a missing heading a
warning, both as squiggles and in Action Items, with one sentence on the
status line. Nothing leaves your machine: a link with a scheme is not
checked.

## 9. Hand it to an agent

**Do:** Tools ▸ **Agent Port (MCP)…**, tick **Keep this address and
token**, then **Copy for Claude Code** and run the copied line once.

**See:** an agent that can read what the IDE knows — the aimed project,
what is serving and running, what you are editing, the last failure —
and still connects tomorrow, because the token is kept in your system
keychain and the port is reused. The port stays read-only by
construction. Clearing **Keep** deletes the keychain entry.

## What you did

You wrote a commit message, read a diff, resolved a conflict, opened a
pull request, found who wrote a line, followed a stack trace and checked
a README — all in the window you were already in. None of it replaced
git: every step is git's own, opened where you work.
