## What changed

<!-- For a teammate who was not watching: what the user or contributor
     will notice, and why it was worth doing. -->

## How it was proven

- [ ] Tests ship in this PR. Verdict line(s): `Tests run: N, Failures: 0, Errors: 0`
- [ ] Interesting tests are mutation-proven: I broke the code the way the test exists to catch, watched the NAMED test fail, and restored it. Mutations and the tests they killed:
- [ ] `mvn clean verify` is green locally (tests, SpotBugs, find-sec-bugs, JaCoCo floors)
- [ ] Walked in the assembled app (`application/target/nmoxstudio/bin/nmoxstudio`, throwaway `--userdir`), rebuilt from the root first. What I did and saw:
- [ ] Not walked, and why:

## Docs truth

- [ ] CHANGELOG.md
- [ ] README.md
- [ ] docs/user-guide.md (and its translations, if a user-facing string or path changed)
- [ ] Nothing user-visible changed

<!-- A new gate, ledger, parity or census test needs its line in
     docs/engineering/gates.md; GatesIndexGateTest will say so. -->
