# The 3.4 developer-experience plan (draft)

*Started 2026-09-26. 3.1 asked how long the first hour takes; 3.2 asked
what a developer who stayed does all day; 3.3 asked what happens when the
project is big. Every one of them imagined one person, at a keyboard and a
screen, on a day where nothing broke. 3.4 asks three questions that drop
one of those assumptions each.*

## The questions

1. **What happens when a second developer joins?** The studios commit six
   files beside the code (`.nmoxrack.json`, `.nmoxapi.json`,
   `.nmoxtasks.json`, `.nmoxdb.json`, `.nmoxweb3.json`, `.nmoxblocks.json`),
   and teammates merge them. Secrets live in each person's own keychain, so
   a colleague's clone has none of them. Does the second developer's first
   hour work, does the IDE say plainly what they are missing, and when two
   people edit the same studio file and git merges it, does each studio
   open it, refuse it by name, or silently lose someone's work?
2. **What happens when something goes wrong?** The IDE killed mid-save, a
   full disk, the network dropped mid-request, a language server that
   crashes, a dev server that ignores SIGTERM, a repository mid-rebase, a
   corrupt workspace file. "Refusals speak" is a house law tested case by
   case; this is the sweep. Does every failure say what happened, and does
   the IDE recover without the user repairing its files by hand?
3. **Can someone use it without a mouse, or without seeing it?** The name
   laws guarantee every control has an accessible name. Nobody has done
   the daily loop keyboard-only, or read it the way a screen reader does.
   Can every window be reached, every control operated and every result
   read without a pointer, and does what a screen reader hears match what
   the screen shows?

## How each is measured

- **A second developer:** two clones of one repository on this machine,
  two throwaway userdirs and two throwaway HOMEs (so neither shares a
  keychain entry, a trust grant or a preference). Alice sets the project up
  in every studio and pushes; Bob clones and opens it; both edit the same
  studio files and merge, producing git's own conflicts and keep-both
  merges. Each studio's parser is fed the merged bytes headlessly first,
  then the walk opens them in the assembled app.
- **When things go wrong:** fault injection, one fault per run, each
  against the assembled app or the code path it names: `kill -9` during a
  save, a full disk image mounted as the project, a proxy that drops the
  connection mid-response, a language server killed under the editor, a
  dev server that traps SIGTERM, a repository stopped mid-rebase and
  mid-merge, each workspace file truncated and garbled.
- **Without a mouse or a screen:** the accessibility tree the walk tools
  read is the one a screen reader reads. Every window reached by keyboard
  alone (its chord, then Tab), every control's role, name and state read
  and compared with what the screen paints, and a static census of the
  painted surfaces that take no keyboard at all.

## What the surveys found

*(to be written from the surveys)*

## The plan

| # | Question | Unit | Proof |
|---|----------|------|-------|

## Already on the branch

- The 3.3 big-project fixture is a script (`scripts/big-fixture.sh`).
