# The gates

A gate is a test that holds a house law rather than a feature: it fails the
build when a change breaks a rule that was paid for by a shipped bug. This
page lists every one, grouped by theme, with the law it holds and where the
law came from. When your build fails on a test named here, read its line
first, then the test's own javadoc, which tells the whole story.

**Every test whose name ends in `GateTest`, `LedgerTest`, `ParityTest` or
`CensusTest` is listed here, and `GatesIndexGateTest` fails the build
when one is missing.** That test derives the list from the
source tree rather than from memory, so a new gate cannot ship without its
line on this page.

A few shapes recur, and knowing them makes a failure quicker to read:

- **A ledger** classifies every member of a derived population (every spawn
  site, every whole-file read, every clock) and fails on an unclassified
  one. The fix is a decision, written down with its reason, not a
  suppression.
- **A parity test** holds two homes of one fact together, such as the
  checked-in cask and the workflow that regenerates it.
- **A census** reads the assembled cluster or a generated artifact, so it
  sees what actually ships rather than what the source intended. Those run
  in the `packaged-app-gates` execution after `package`, so build from the
  root first.
- **A source gate** reads code because the behaviour lives in Swing wiring
  a headless test cannot drive. It is the weakest shape, so it usually pairs
  with a behavioural test of the seam it guards.

The house laws themselves, with the incidents behind them, are in
[CONTRIBUTING.md](../../CONTRIBUTING.md#how-this-house-works) and the
[codebase guide](codebase-guide.md).

## Security and trust

Nothing runs a stranger's code, leaks a secret, or paints a stranger's markup.

- [`SpawnSiteTrustLedgerTest`](../../application/src/test/java/org/nmox/studio/application/SpawnSiteTrustLedgerTest.java): Every `CommandExecutor.run` and `ProcessBuilder` spawn site is classified: gated by Workspace Trust before the spawn, gated by its caller, or blessed in writing because its argv is not project-controlled. Born v1.224.0, after Run Focused Test spawned project runners ungated for ~190 releases.
- [`SpawnTrustGateTest`](../../tools/src/test/java/org/nmox/studio/tools/npm/SpawnTrustGateTest.java): Run/Build/Test/Clean and the NPM Explorer ask for trust before they spawn project code (v1.103.0, an RCE found by review).
- [`DebugTrustGateTest`](../../editor/src/test/java/org/nmox/studio/editor/debug/DebugTrustGateTest.java): The debug actions consult Workspace Trust before launching anything, against the project root rather than the file's folder (v1.37.0).
- [`LspFormatTrustGateTest`](../../editor/src/test/java/org/nmox/studio/editor/lsp/LspFormatTrustGateTest.java): A project's own `node_modules/.bin` language server or Prettier is used only when the workspace is trusted, via the silent check (v1.102.0: opening a file was RCE).
- [`RackTrustGateTest`](../../rack/src/test/java/org/nmox/studio/rack/service/RackTrustGateTest.java): `TrustGate.find()` resolves to the rack's adapter, so the forge buttons in Contract Studio stay gated without a rack dependency (v1.224.0).
- [`McpReadOnlyLedgerTest`](../../rack/src/test/java/org/nmox/studio/rack/mcp/McpReadOnlyLedgerTest.java): No Agent Port class names a spawn, write or trust primitive: the port is read-only by construction (v2.54.0).
- [`RackKvasirAskGateTest`](../../rack/src/test/java/org/nmox/studio/rack/service/RackKvasirAskGateTest.java): KVASIR asks consent before its conversation window opens, and a decline opens nothing (v1.173.0, caught live).
- [`ContainmentLedgerTest`](../../application/src/test/java/org/nmox/studio/application/ContainmentLedgerTest.java): Every "which file inside this root?" decision routes through `core.util.Containment` or is classified (v2.186.0–v2.187.0: two measured write-escapes).
- [`SelfStartingLedgerTest`](../../rack/src/test/java/org/nmox/studio/rack/devices/SelfStartingLedgerTest.java): Every persisted switch in the fleet is classified as a setting or self-starting, so an imported rack really arrives at rest (v2.179.0: TAIL started polling a sender-chosen path).
- [`BundleHeadGateTest`](../../application/src/test/java/org/nmox/studio/application/BundleHeadGateTest.java): A bundle value that begins with a placeholder is never painted straight into a Swing sink, since the argument could begin with `<html>` (v2.97.0).
- [`PlainLabelGateTest`](../../application/src/test/java/org/nmox/studio/application/PlainLabelGateTest.java): A label built from non-literal text guards it with `PlainText.plain`, because Swing renders a leading `<html>` (v2.86.0).
- [`PlainButtonGateTest`](../../application/src/test/java/org/nmox/studio/application/PlainButtonGateTest.java): The same law for buttons and menu items (v2.86.0; the live walk found the button sink a label-only census missed).
- [`PlainTooltipGateTest`](../../application/src/test/java/org/nmox/studio/application/PlainTooltipGateTest.java): The same law for tooltips, which Swing builds fresh per hover so the component property never reaches them (v2.86.0).
- [`PlainMessageGateTest`](../../application/src/test/java/org/nmox/studio/application/PlainMessageGateTest.java): External text reaches a dialog only through `PlainDialogs` (v2.86.0).
- [`PlainStatusGateTest`](../../application/src/test/java/org/nmox/studio/application/PlainStatusGateTest.java): Every status-line text begins with our own literal or rides `PlainStatus.text` (v2.86.0).
- [`PlainTableGateTest`](../../application/src/test/java/org/nmox/studio/application/PlainTableGateTest.java): Every production table and tree references `PlainTables`, so a cell starting `<html><img src=…>` cannot make the IDE fetch a URL (v1.306.0).
- [`LazyHydrationGateTest`](../../apiclient/src/test/java/org/nmox/studio/apiclient/ui/LazyHydrationGateTest.java): API Studio never bulk-reads the keychain at tab open; each request hydrates its own token on first use (v1.201.0, after a macOS prompt storm).
- [`DeleteHygieneGateTest`](../../apiclient/src/test/java/org/nmox/studio/apiclient/ui/DeleteHygieneGateTest.java): Deleting an API Studio request forgets its keychain secret, and clearing the response refreshes the find bar (v1.200.0).

## Bounded, off the EDT, and never orphaned

Every read has a ceiling, the paint thread does no disk or process work, and every thread and process can be accounted for.

- [`BoundedReadLedgerTest`](../../application/src/test/java/org/nmox/studio/application/BoundedReadLedgerTest.java): Every whole-file read goes through `BoundedReads` or is classified (v2.180.0: 73 unguarded reads, most on files a `git clone` brings).
- [`BoundedCompletionReadGateTest`](../../editor/src/test/java/org/nmox/studio/editor/ghost/BoundedCompletionReadGateTest.java): Complete with KVASIR reads a capped window around the caret, never the whole document (v2.61.1).
- [`HeavyDirsLedgerTest`](../../application/src/test/java/org/nmox/studio/application/HeavyDirsLedgerTest.java): Every walk's skip set is `HeavyDirs` or is classified (v2.186.0: twelve skip sets holding nine different answers).
- [`DaemonThreadGateTest`](../../application/src/test/java/org/nmox/studio/application/DaemonThreadGateTest.java): `new Thread(` appears only in `core.util.Threads` or on a named shutdown-hook line (v2.63.0).
- [`LiveRunsLedgerTest`](../../application/src/test/java/org/nmox/studio/application/LiveRunsLedgerTest.java): Every spawn registers with `LiveRuns` so the toolbar ■ can stop it, or carries a written exemption (v2.71.0).
- [`SpawnThreadGateTest`](../../tools/src/test/java/org/nmox/studio/tools/npm/SpawnThreadGateTest.java): The ▶ asks for trust on the EDT and then forks on a named lane, never on the menu thread (v2.70.0).
- [`SendLaneAndCloseGateTest`](../../apiclient/src/test/java/org/nmox/studio/apiclient/api/SendLaneAndCloseGateTest.java): API Studio sends ride their own interruptible lane, the pretty-print runs off the EDT, and close saves only when dirty (v1.99.0).
- [`ReloadOffEdtGateTest`](../../dbstudio/src/test/java/org/nmox/studio/dbstudio/ui/ReloadOffEdtGateTest.java): DB Studio's workspace reload and `.env` offer read disk on the RequestProcessor, never the EDT (v1.119.0).
- [`StudioSafetyGateTest`](../../web3/src/test/java/org/nmox/studio/web3/engine/StudioSafetyGateTest.java): Contract Studio confirms before broadcasting off-loopback with the safe button default, reloads off the EDT, and a stopped watch revokes its cursors (v1.100.0).
- [`Ledger55GateTest`](../../editor/src/test/java/org/nmox/studio/editor/debug/Ledger55GateTest.java): Prettier's timeout kills the whole process tree, the debug port probe binds loopback, the completion harvest is windowed, and interrupted Chrome profiles are reaped (v1.123.0).
- [`McpTransportGateTest`](../../rack/src/test/java/org/nmox/studio/rack/mcp/McpTransportGateTest.java): The Agent Port answers JSON-RPC -32603 on a throw instead of dropping the connection (v2.56.1).
- [`StaleRunGateTest`](../../rack/src/test/java/org/nmox/studio/rack/model/StaleRunGateTest.java): A replaced run's exit cannot drop the SERVING gate of the run that replaced it: press DEV twice (v2.186.0).

## Boot costs nothing

A window open at startup takes a note; the work waits until someone can see it (the v1.38.0 startup measurement).

- [`BootGateTest`](../../rack/src/test/java/org/nmox/studio/rack/BootGateTest.java): Project Studio syncs once per boot and the Docker panel generates its previews on first show, not at boot (v1.38.0).
- [`NpmExplorerBootGateTest`](../../tools/src/test/java/org/nmox/studio/tools/npm/NpmExplorerBootGateTest.java): The NPM Explorer never spawns `npm ls -g` at boot; it was the only boot-time process in the IDE (v1.38.0).
- [`ProjectExplorerBootGateTest`](../../project/src/test/java/org/nmox/studio/project/ProjectExplorerBootGateTest.java): The toolchain-detect walk runs once per boot, owned by `componentOpened` (v1.38.0).
- [`Web3BootGateTest`](../../web3/src/test/java/org/nmox/studio/web3/ui/Web3BootGateTest.java): A hidden Contract Studio tab never walks the artifact tree (v1.38.0).
- [`ApiStudioLifecycleGateTest`](../../apiclient/src/test/java/org/nmox/studio/apiclient/ui/ApiStudioLifecycleGateTest.java): API Studio's constructor never loads the workspace, background work rides the module RequestProcessor, and a re-aim clears what Explain could disclose (v1.39.0, v1.172.0).
- [`DbStudioLifecycleGateTest`](../../dbstudio/src/test/java/org/nmox/studio/dbstudio/ui/DbStudioLifecycleGateTest.java): DB Studio's constructor never loads the workspace; `componentOpened` loads it exactly once (v1.39.0).
- [`DiscoveryTabsGateTest`](../../ui/src/test/java/org/nmox/studio/ui/DiscoveryTabsGateTest.java): The Browser opens on first launch and IRC does not; neither builds in `componentOpened`, and IRC never auto-connects (v2.118.0).
- [`FirstLaunchWindowsLedgerTest`](../../application/src/test/java/org/nmox/studio/application/FirstLaunchWindowsLedgerTest.java): Every window states whether it opens on a first launch, and a newcomer meets three editor tabs rather than ten (v2.118.0).

## Platform idioms

The NetBeans Platform's own mechanisms, used the way the platform reads them.

- [`SoftDependencyGateTest`](../../core/src/test/java/org/nmox/studio/core/spi/SoftDependencyGateTest.java): Soft dependencies on the rack ride the `core.spi` lookups, never a caught `LinkageError`; apiclient, web3 and infra name no rack package (v1.46.0).
- [`LayerPositionCensusTest`](../../application/src/test/java/org/nmox/studio/application/LayerPositionCensusTest.java): No layer folder mixes positioned and unpositioned rows and no two rows share a position, read across the assembled cluster (v2.69.7).
- [`KeymapProfileParityTest`](../../ui/src/test/java/org/nmox/studio/ui/KeymapProfileParityTest.java): Every NMOX chord is registered in all five keymap profiles (v2.3.0).
- [`CompletionAllQueryGateTest`](../../editor/src/test/java/org/nmox/studio/editor/completion/CompletionAllQueryGateTest.java): Every completion provider answers the second Ctrl+Space (`COMPLETION_ALL`) like the first (v2.58.1: the second press wiped every NMOX item).
- [`LexerIdiomGateTest`](../../editor/src/test/java/org/nmox/studio/editor/languages/LexerIdiomGateTest.java): Every CSL language config finds its lexer through the recursion-guarded `Lexers.find` (v1.110.0: a stack overflow that poisoned the platform logger).
- [`MultiMimeSingletonGateTest`](../../editor/src/test/java/org/nmox/studio/editor/lsp/MultiMimeSingletonGateTest.java): A language server registered under two or more mimes is a single multi-mime instance, derived from the generated layer (v2.19.0–v2.19.1: two servers per project).
- [`OutlineNavigatorGateTest`](../../editor/src/test/java/org/nmox/studio/editor/outline/OutlineNavigatorGateTest.java): Every language the outline model understands is registered on the Navigator panel (v1.76.0: ten outlines were built and unreachable).
- [`MarkupFamilyParityTest`](../../editor/src/test/java/org/nmox/studio/editor/design/MarkupFamilyParityTest.java): Every design surface (swatches, picker, `var(` completion and jump) registers every markup-family mime (v2.25.0).
- [`SuffixlessAngularGateTest`](../../editor/src/test/java/org/nmox/studio/editor/angular/SuffixlessAngularGateTest.java): The dead `.instance` MIME-resolver channel stays dead, and the four-file Angular switcher speaks suffixless names (v2.37.8).
- [`ProductVersionGateTest`](../../application/src/test/java/org/nmox/studio/application/ProductVersionGateTest.java): The product version is read in one place, `ProductVersion`; a module classloader cannot see the branded bundle (v2.67.0).
- [`PopupTargetGateTest`](../../application/src/test/java/org/nmox/studio/application/PopupTargetGateTest.java): Every context menu acts on the clicked item, not the selected one (v1.270.0: Delete removed the wrong request).
- [`InputClearGateTest`](../../ui/src/test/java/org/nmox/studio/ui/irc/InputClearGateTest.java): IRC's clear-line chord is Ctrl+U because Escape never reaches a docked window, and window-level bindings die there too (v1.205.0, v2.2.1).
- [`CssTokenWiringGateTest`](../../editor/src/test/java/org/nmox/studio/editor/design/CssTokenWiringGateTest.java): The design-token seams are called where they are used: the two-proof seam law (v1.321.0).
- [`EmmetWiringGateTest`](../../editor/src/test/java/org/nmox/studio/editor/emmet/EmmetWiringGateTest.java): The Emmet action is registered and bound under every mime that claims it (v1.321.0 law, v1.329.0 feature).
- [`MotionGuardWiringGateTest`](../../ui/src/test/java/org/nmox/studio/ui/browser/fx/MotionGuardWiringGateTest.java): Play, Scrub and Stop in the Motion pane all consult the target guard (v2.16.0).
- [`ProbedPortWiringGateTest`](../../rack/src/test/java/org/nmox/studio/rack/devices/ProbedPortWiringGateTest.java): The static lanes probe a free port at the spawn and announce the port the server's banner names (v1.320.0–v1.321.0).
- [`SeamRestoreGateTest`](../../rack/src/test/java/org/nmox/studio/rack/model/SeamRestoreGateTest.java): A test that swaps a production seam restores it through the seam's own reset (v2.184.0: a test invented its own "production" lane).

## Accessibility

Every control speaks its name; a screen reader hears the thing, not the role.

- [`WindowNameLawCensusTest`](../../application/src/test/java/org/nmox/studio/application/WindowNameLawCensusTest.java): Every registered window is under an accessibility name contract or exempt in writing (v2.77.0).
- [`InputsNamedGateTest`](../../application/src/test/java/org/nmox/studio/application/InputsNamedGateTest.java): Every text field, password field, combo and spinner is named or labelled (v2.85.0: 46 were not).
- [`TextAreasNamedGateTest`](../../application/src/test/java/org/nmox/studio/application/TextAreasNamedGateTest.java): Every text area carries an accessible name (v2.85.0).
- [`CollectionsNamedGateTest`](../../application/src/test/java/org/nmox/studio/application/CollectionsNamedGateTest.java): Every table, list and tree is named or labelled (v2.85.0).
- [`A11yInputNamesGateTest`](../../application/src/test/java/org/nmox/studio/application/A11yInputNamesGateTest.java): No studio adds an input without also naming one (v2.38.0).
- [`LabelNamesAreTheirTextGateTest`](../../ui/src/test/java/org/nmox/studio/ui/actions/LabelNamesAreTheirTextGateTest.java): A label with text is named by its text, not by a constant a screen reader would read instead (v2.85.0).

## Localization

Fifteen languages, each written in its own conventions, with the English entering nowhere below the gates. The long record is [l10n-completion.md](l10n-completion.md).

- [`LocaleBundleParityTest`](../../application/src/test/java/org/nmox/studio/application/LocaleBundleParityTest.java): Every localized package carries every shipped language with the English key set and the same placeholders, read from the assembled cluster (v2.97.0).
- [`NativeTypographyGateTest`](../../application/src/test/java/org/nmox/studio/application/NativeTypographyGateTest.java): Each translation follows its own typography: quotation marks, ellipsis, apostrophe, form of address, right-to-left marks (v2.150.0).
- [`RtlPlaceholderIsolationGateTest`](../../application/src/test/java/org/nmox/studio/application/RtlPlaceholderIsolationGateTest.java): An argument landing in a right-to-left sentence is isolated so a dotfile keeps its dot (v2.181.0).
- [`BundleEncodingGateTest`](../../application/src/test/java/org/nmox/studio/application/BundleEncodingGateTest.java): No shipped bundle value is UTF-8 decoded twice (v2.150.1).
- [`CaseFoldLocaleGateTest`](../../application/src/test/java/org/nmox/studio/application/CaseFoldLocaleGateTest.java): Every case-fold names its locale: the Turkish-I law (v2.37.5).
- [`CatalogueProseLedgerTest`](../../application/src/test/java/org/nmox/studio/application/CatalogueProseLedgerTest.java): Every enum that carries words is classified as translated or as machine text (v2.134.0).
- [`ClockSiteLedgerTest`](../../application/src/test/java/org/nmox/studio/application/ClockSiteLedgerTest.java): Every date pattern is `Clocks` (read by a person) or a named stable site (read by a machine) (v2.104.0).
- [`SortSiteLedgerTest`](../../application/src/test/java/org/nmox/studio/application/SortSiteLedgerTest.java): Every string sort is `Collate` or a named stable site (v2.104.0).
- [`NumberSiteLedgerTest`](../../application/src/test/java/org/nmox/studio/application/NumberSiteLedgerTest.java): Every locale-sensitive number format goes through `Numbers` or names its locale (v2.105.0).
- [`GroupSeparatorLedgerTest`](../../core/src/test/java/org/nmox/studio/core/util/GroupSeparatorLedgerTest.java): Each language's digit-group separator is the one that was measured on this JDK (ledger 107).
- [`PaintedNumberSeparatorGateTest`](../../application/src/test/java/org/nmox/studio/application/PaintedNumberSeparatorGateTest.java): No surface built on a zero-advance logical font paints a grouped number (ledger 107).
- [`WordBoundaryLedgerTest`](../../application/src/test/java/org/nmox/studio/application/WordBoundaryLedgerTest.java): Every place the product decides where a word ends says whose word it is (v2.115.0: Devanagari search was cut into letters).
- [`SearchSurfaceLedgerTest`](../../application/src/test/java/org/nmox/studio/application/SearchSurfaceLedgerTest.java): Every search surface goes through the one matcher, `SearchTerms` (v2.106.0).
- [`OptionalKeyLookupGateTest`](../../application/src/test/java/org/nmox/studio/application/OptionalKeyLookupGateTest.java): A key allowed to be missing is looked up, never caught as an exception (v2.135.0: twelve times slower for English readers).
- [`HalfTranslatedRowGateTest`](../../application/src/test/java/org/nmox/studio/application/HalfTranslatedRowGateTest.java): No row is translated on one line and English on the next (v2.137.0).
- [`OwnScriptGateTest`](../../application/src/test/java/org/nmox/studio/application/OwnScriptGateTest.java): In Russian, Ukrainian, Chinese and Hindi an English word is a written decision (v2.138.0).
- [`PluralCopyGateTest`](../../application/src/test/java/org/nmox/studio/application/PluralCopyGateTest.java): A counted sentence branches its plural (v2.85.0, widened v2.180.0).
- [`DialogChromeOverlayGateTest`](../../application/src/test/java/org/nmox/studio/application/DialogChromeOverlayGateTest.java): The platform's dialog and wizard buttons speak every language, choice branches and mnemonics included (v2.127.0).
- [`ToolbarOverlayGateTest`](../../application/src/test/java/org/nmox/studio/application/ToolbarOverlayGateTest.java): The platform toolbar's overlays exist in every language and every choice pattern renders (v2.102.0).
- [`PlatformDialogLedgerTest`](../../application/src/test/java/org/nmox/studio/application/PlatformDialogLedgerTest.java): Every platform dialog a walk found is overlaid or recorded with a reason; this population cannot be derived (v2.142.0).
- [`PaintedSurfaceLedgerTest`](../../application/src/test/java/org/nmox/studio/application/PaintedSurfaceLedgerTest.java): Every self-painting component is classified as geometry or as mirrored for right-to-left (v2.148.0).
- [`TranslatedNewcomerDocsTest`](../../application/src/test/java/org/nmox/studio/application/TranslatedNewcomerDocsTest.java): The quickstart, the glossary and Coming from VS Code exist in every language the chrome speaks, each with the full language bar, the English sections in order under the English anchors, and every code block byte for byte the English one (3.1.0).
- [`TranslatedGuideGateTest`](../../application/src/test/java/org/nmox/studio/application/TranslatedGuideGateTest.java): The user guide exists in every language, every topic of every chapter included (v2.104.0, v3.0.1).
- [`TranslatedTutorialsGateTest`](../../application/src/test/java/org/nmox/studio/application/TranslatedTutorialsGateTest.java): Every tutorial exists in every language with the English commands, headings and screenshots (v2.153.0).
- [`TranslatedShotsGateTest`](../../application/src/test/java/org/nmox/studio/application/TranslatedShotsGateTest.java): A translated document is illustrated in its own language (v2.161.0).
- [`ForgeFixturesGateTest`](../../application/src/test/java/org/nmox/studio/application/ForgeFixturesGateTest.java): Every translated guide's language has its own scene content for the screenshot forge (v2.163.0).

## Docs tell the truth

A number, a link or a picture in the docs is a claim, so it has a test.

- [`DocsCountGateTest`](../../application/src/test/java/org/nmox/studio/application/DocsCountGateTest.java): Every count a live doc quotes (devices, spaces, manifests, grammars, languages) equals the product's (v1.362.0).
- [`UiCountLiteralGateTest`](../../application/src/test/java/org/nmox/studio/application/UiCountLiteralGateTest.java): No user-visible string literal carries a hand-typed count (v2.85.0: a button promised 92 spaces while 93 shipped).
- [`DocsIndexGateTest`](../../application/src/test/java/org/nmox/studio/application/DocsIndexGateTest.java): Every document beside an index is linked from it, including every live engineering document (v2.90.0).
- [`DocsContentsGateTest`](../../application/src/test/java/org/nmox/studio/application/DocsContentsGateTest.java): The long documents' contents blocks are derived from their own headings (v2.89.0).
- [`DocsDockerViewGateTest`](../../application/src/test/java/org/nmox/studio/application/DocsDockerViewGateTest.java): The screenshot forge's Docker view shows only the docs container and forwards no write (v2.164.0).
- [`JuniorDocsGateTest`](../../application/src/test/java/org/nmox/studio/application/JuniorDocsGateTest.java): Every public type has a class javadoc and the onboarding packages keep their `package-info` maps (v2.7.1).
- [`ConflictMarkerGateTest`](../../application/src/test/java/org/nmox/studio/application/ConflictMarkerGateTest.java): No tracked file carries a VCS conflict marker (v1.314.0: one rode CHANGELOG.md for six releases).
- [`CheckpointParityTest`](../../rack/src/test/java/org/nmox/studio/rack/projectstudio/CheckpointParityTest.java): A learning space's Check My Work claims name files and tools the space really ships, and a task fails on the untouched seed (v2.39.1, v2.85.0).
- [`ExperimentGuideParityTest`](../../rack/src/test/java/org/nmox/studio/rack/projectstudio/ExperimentGuideParityTest.java): An experiment's walkthrough names a file its template really writes (v2.36.0).
- [`ExampleDevicesGateTest`](../../rack/src/test/java/org/nmox/studio/rack/devices/ExampleDevicesGateTest.java): Every example JSON device parses and mounts through the real load path (v2.0.1).
- [`GatesIndexGateTest`](../../application/src/test/java/org/nmox/studio/application/GatesIndexGateTest.java): This page lists every gate, ledger, parity and census test in the tree, and names nothing that is not there (v3.1.0).

## Release, packaging and dependencies

What ships is what was built, and every version has one home.

- [`ContributorFloorGateTest`](../../application/src/test/java/org/nmox/studio/application/ContributorFloorGateTest.java): The root pom enforces the build JDK at `validate`, every CI `setup-java` installs at least that JDK under a name that says so, and `build.sh` refuses below the same floor (v3.1.0: four places held four different answers).
- [`SpecVersionGateTest`](../../application/src/test/java/org/nmox/studio/application/SpecVersionGateTest.java): Every module's OpenIDE spec version is the release train's, never the frozen 1.0 (v1.47.0).
- [`OrgJsonVersionGateTest`](../../application/src/test/java/org/nmox/studio/application/OrgJsonVersionGateTest.java): org.json's version lives in one root property across every module copy (v1.50.0).
- [`CaskGeneratorParityTest`](../../application/src/test/java/org/nmox/studio/application/CaskGeneratorParityTest.java): The checked-in Homebrew cask is byte-identical to what the release workflow regenerates, with no deprecated stanza (v2.149.0).
- [`BundledRuntimeGateTest`](../../application/src/test/java/org/nmox/studio/application/BundledRuntimeGateTest.java): Every jlink site pins OpenJFX by hash and gates `javafx.web` into the image (v1.199.0).
- [`PackagedConfGateTest`](../../application/src/test/java/org/nmox/studio/application/PackagedConfGateTest.java): The assembled app's conf opens every module the platform reflects into and grants native access, on one `default_options` line (v1.195.1, v1.256.0).
- [`PackagedLicenseGateTest`](../../application/src/test/java/org/nmox/studio/application/PackagedLicenseGateTest.java): The assembled app and the portable zip carry LICENSE and NOTICE (v1.208.0).
- [`PackagedSiteGateTest`](../../application/src/test/java/org/nmox/studio/application/PackagedSiteGateTest.java): The assembled cluster carries the bundled website byte for byte (v2.40.0).
- [`LicenseConsistencyGateTest`](../../application/src/test/java/org/nmox/studio/application/LicenseConsistencyGateTest.java): LICENSE, README, the NBM metadata and the SBOM all say Apache-2.0 (v1.207.0).
- [`OpenFolderFromOsGateTest`](../../application/src/test/java/org/nmox/studio/application/OpenFolderFromOsGateTest.java): A folder handed over by the operating system is aimed on Linux and Windows, and macOS offers no door that cannot open: the bundle declares no document types and the launcher sets no `CFProcessPath` (the hardened runtime ignores it, measured on the notarized dry run); the Linux entry claims `inode/directory` and runs `nmox`, and the Windows folder verbs run `--aim "%V\."`, speak every installer language and leave on uninstall (v3.1.0).
- [`ReleaseSigningLanesGateTest`](../../application/src/test/java/org/nmox/studio/application/ReleaseSigningLanesGateTest.java): Every signing step waits for its own secret, and the macOS lane signs, notarizes and staples app and DMG, jars' natives included (v2.186.0–v3.0.0).
- [`TerminalCommandGateTest`](../../application/src/test/java/org/nmox/studio/application/TerminalCommandGateTest.java): `nmox` works on all three OSes: the macOS launcher and the Linux command run for real through chains of links, aim folders, open files and return at once, the two Unix rules stay identical, and the Windows PATH task has a message for every installer language (v3.1.0).
- [`ShipScriptsGateTest`](../../application/src/test/java/org/nmox/studio/application/ShipScriptsGateTest.java): Every script in `scripts/` parses, the pipeline scripts set `pipefail`, and CI runs the update gauntlet (v2.68.1–v2.69.5).
- [`AngularSpacePinParityTest`](../../rack/src/test/java/org/nmox/studio/rack/projectstudio/AngularSpacePinParityTest.java): The Angular template and the Angular learning space pin the same Angular line (v1.241.0).
- [`KitCatalogParityTest`](../../rack/src/test/java/org/nmox/studio/rack/projectstudio/KitCatalogParityTest.java): The Contract Kit's starters and the learning catalogue pin the same dependency versions (v1.141.0).
- [`PreflightLaneParityTest`](../../tools/src/test/java/org/nmox/studio/tools/npm/PreflightLaneParityTest.java): Every toolchain the IDE lanes speak keeps a same-tool check in the PREFLIGHT ship gate (v1.163.0).

## The rack and the studios

Wiring laws for specific surfaces, each pinned because it would fail silently if unwired.

- [`KindVocabularyGateTest`](../../rack/src/test/java/org/nmox/studio/rack/devices/KindVocabularyGateTest.java): One vocabulary for which toolchain runs and tests how, and a kind with no run target greys instead of running node (v2.186.0).
- [`ServingAnnounceOrderGateTest`](../../rack/src/test/java/org/nmox/studio/rack/devices/ServingAnnounceOrderGateTest.java): Every serving device announces URL before READY through one method (v2.176.0).
- [`CommunityRacksGateTest`](../../rack/src/test/java/org/nmox/studio/rack/gallery/CommunityRacksGateTest.java): Every community rack file is indexed, passes the judge and mounts at rest (v2.179.0).
- [`BlockParityTest`](../../rack/src/test/java/org/nmox/studio/rack/blockstudio/BlockParityTest.java): Block Studio honours the external-edit pulse and ⌘I laws like every other studio (v1.79.0).
- [`MultiCloudParityTest`](../../infra/src/test/java/org/nmox/studio/infra/api/MultiCloudParityTest.java): Hetzner and Cloudflare nodes get the drift, destroy and cloud-init treatment DigitalOcean nodes have (v1.23.0).
- [`DbStudioSafetyGateTest`](../../dbstudio/src/test/java/org/nmox/studio/dbstudio/engine/DbStudioSafetyGateTest.java): DB Studio's destructive dialogs default to Cancel, and a 0-row UPDATE is a failure (v1.101.0).
- [`ConsoleBarLayoutGateTest`](../../dbstudio/src/test/java/org/nmox/studio/dbstudio/ui/ConsoleBarLayoutGateTest.java): DB Studio's console toolbar wraps instead of clipping (v1.273.0).
- [`OrganizeGesturesGateTest`](../../web3/src/test/java/org/nmox/studio/web3/ui/OrganizeGesturesGateTest.java): Contract Studio's Remove Network and Forget Deployment refuse the built-in, default to No, and drop the keychain entry off the EDT (v1.269.0).
- [`TasksLawsGateTest`](../../ui/src/test/java/org/nmox/studio/ui/tasks/TasksLawsGateTest.java): The Task Board renders plain text, targets the clicked card and defaults its confirms to No (v1.323.0).
- [`ShelfButtonsGateTest`](../../ui/src/test/java/org/nmox/studio/ui/actions/ShelfButtonsGateTest.java): While a shelf worker moves or deletes a tree, every button on the shelf greys (v2.184.0).
- [`FilterWiringGateTest`](../../ui/src/test/java/org/nmox/studio/ui/irc/FilterWiringGateTest.java): Every place an IRC line reaches a transcript consults the user's filters, local echo included (v2.10.2).
- [`NgSchematicParityGateTest`](../../ui/src/test/java/org/nmox/studio/ui/actions/NgSchematicParityGateTest.java): File ▸ New Angular Schematic offers exactly HALO's schematics, in order (v1.240.0).

## Named like a gate, testing a feature

These match the naming pattern this page is held to, but the "gate" in their name is a product feature, not a build law. They are listed so the index stays complete.

- [`EditGateTest`](../../dbstudio/src/test/java/org/nmox/studio/dbstudio/engine/EditGateTest.java): DB Studio's grid-editability gate: which result sets may be edited, and why not.
- [`TempoGateTest`](../../rack/src/test/java/org/nmox/studio/rack/devices/TempoGateTest.java): TEMPO's ENABLE jack runs the clock exactly while the gate is high.
- [`AutoUrlGateTest`](../../rack/src/test/java/org/nmox/studio/rack/devices/AutoUrlGateTest.java): VITALS and BEACON aim at the live web server when their URL is blank, and an explicit URL wins.

## Laws under other names

House laws whose class names do not end in one of the four suffixes. This section is curated rather than derived, but every name in it must still exist.

- [`DeviceContractTest`](../../rack/src/test/java/org/nmox/studio/rack/devices/DeviceContractTest.java): Every device in the catalogue has unique labelled ports, round-trips its state, fits its faceplate without overlap, and names every control (v1.41.0, v2.163.0).
- [`A11yContractTest`](../../rack/src/test/java/org/nmox/studio/rack/ui/controls/A11yContractTest.java): Every rack widget reports its role, name and value and works from the keyboard (v1.41.0).
- [`ConsoleJackContractTest`](../../rack/src/test/java/org/nmox/studio/rack/devices/ConsoleJackContractTest.java): Every device declaring a STOP or ENABLE jack handles it, and rear jacks never collide (v1.90.0).
- [`DeviceDocsTest`](../../rack/src/test/java/org/nmox/studio/rack/devices/DeviceDocsTest.java): `docs/devices.md` is generated from the catalogue and fails on drift.
- [`PluginVersionSingleHomeTest`](../../application/src/test/java/org/nmox/studio/application/PluginVersionSingleHomeTest.java): A build-plugin version lives only in the root pom's `pluginManagement` (v2.131.0).
- [`FxPinLockstepTest`](../../application/src/test/java/org/nmox/studio/application/FxPinLockstepTest.java): The OpenJFX version's four homes move together (v1.249.0).
- [`CiExportPinCurrencyTest`](../../rack/src/test/java/org/nmox/studio/rack/projectstudio/CiExportPinCurrencyTest.java): Export CI's action pins equal our own workflows' pins (v1.236.0).
- [`BrandingVersionStampTest`](../../application/src/test/java/org/nmox/studio/application/BrandingVersionStampTest.java): The committed branding version is the dev sentinel and every release job stamps it (v2.143.0).
- [`UpdateCenterTest`](../../ui/src/test/java/org/nmox/studio/ui/UpdateCenterTest.java): The update center's registration, catalog URL and release assets agree (v1.51.0).
- [`WindowShortcutsTest`](../../ui/src/test/java/org/nmox/studio/ui/WindowShortcutsTest.java): No window claims a chord the platform already owns, and every window chord shows in the menu (v1.38.1).
- [`ActionIdsResolveTest`](../../application/src/test/java/org/nmox/studio/application/ActionIdsResolveTest.java): Every `Actions.forID` the product calls names an action the assembled cluster registers (v3.1.0: the Terminal button asked for an id that never existed, so its "in the project" branch never ran from 1.212.0).
- [`CloneRepositoryReflectionTest`](../../application/src/test/java/org/nmox/studio/application/CloneRepositoryReflectionTest.java): The git classes the Welcome's Clone link names by reflection exist in the cluster with the constructor it calls (v3.1.0).
- [`DeadDoorsTest`](../../application/src/test/java/org/nmox/studio/application/DeadDoorsTest.java): Every menu row, chord and toolbar button of the connector-less bug-tracking module is hidden, derived from the assembled cluster (v3.1.0: "Find Tasks..." and "Report Task..." sat disabled on the Team menu).
- [`OpenedProjectsGroupTest`](../../application/src/test/java/org/nmox/studio/application/OpenedProjectsGroupTest.java): Opening a project opens the Navigator and no other platform explorer; the group's members are derived from the assembled cluster (v3.1.0: four duplicate file trees crowded the left dock).
- [`WindowMenuIsUnambiguousTest`](../../application/src/test/java/org/nmox/studio/application/WindowMenuIsUnambiguousTest.java): No two Window-menu rows share a name in any language, and every window is reachable there (v2.118.0).
- [`WayfindingVocabularyTest`](../../application/src/test/java/org/nmox/studio/application/WayfindingVocabularyTest.java): A menu path the product tells the user names a door that exists in the reader's language (v2.118.0).
- [`DocsMenuDoorsTest`](../../application/src/test/java/org/nmox/studio/application/DocsMenuDoorsTest.java): A menu path in the docs names a door that exists, in that document's language (v2.153.0).
- [`DocsLinksResolveTest`](../../application/src/test/java/org/nmox/studio/application/DocsLinksResolveTest.java): Every relative link and `#anchor` in the live docs lands, anchors computed by GitHub's own rule (v3.1.0: two anchors into the user guide had died when its chapters were renumbered).
- [`TimelessGuideTest`](../../application/src/test/java/org/nmox/studio/application/TimelessGuideTest.java): The user guide and the newcomer pages name no `vX.Y.Z` release tag; history lives in CHANGELOG.md (v3.1.0: forty had grown into the English guide).
- [`ImageRefsTest`](../../application/src/test/java/org/nmox/studio/application/ImageRefsTest.java): Every image a live doc references exists and every image is referenced (v2.32.0).
- [`MarkdownTableShapeTest`](../../application/src/test/java/org/nmox/studio/application/MarkdownTableShapeTest.java): No live doc glues a paragraph to a table or strands a row (v2.26.1).
- [`ChangelogLinkBlockTest`](../../application/src/test/java/org/nmox/studio/application/ChangelogLinkBlockTest.java): Every released version in the changelog has its compare link (v2.104.0).
- [`MenuRowsSpeakTest`](../../application/src/test/java/org/nmox/studio/application/MenuRowsSpeakTest.java): Every platform menu row is translated, with no mnemonic collision (v2.143.0).
- [`ModuleDescriptorsSpeakTest`](../../application/src/test/java/org/nmox/studio/application/ModuleDescriptorsSpeakTest.java): Every module names itself through a localizing bundle, in every language (v2.140.0).
- [`SpiHoldsNoProseTest`](../../core/src/test/java/org/nmox/studio/core/spi/SpiHoldsNoProseTest.java): No sentence lives in `core.spi`, which has no bundle (v2.101.0).
- [`SeedNamesAreNotLiteralsTest`](../../application/src/test/java/org/nmox/studio/application/SeedNamesAreNotLiteralsTest.java): Seed data a user first sees comes from a bundle (v2.130.0).
- [`ChromeLiteralRatchetTest`](../../application/src/test/java/org/nmox/studio/application/ChromeLiteralRatchetTest.java): No module grows its count of Swing sinks fed a bare English literal (v2.97.0).
- [`NarrowPaneHintBudgetTest`](../../application/src/test/java/org/nmox/studio/application/NarrowPaneHintBudgetTest.java): A hint in a narrow pane fits its budget in every language (v2.123.0).
- [`QuickSearchLabelsEscapedTest`](../../application/src/test/java/org/nmox/studio/application/QuickSearchLabelsEscapedTest.java): Every Quick Search result label is escaped where it reaches the platform's HTML renderer (v3.1.0: Block Studio's `<my-card>` was eaten as a tag).
- [`AuthoredMarkupIsNotGuardedTest`](../../application/src/test/java/org/nmox/studio/application/AuthoredMarkupIsNotGuardedTest.java): Markup the product authors is rendered, not guarded into literal tags (v2.128.0).
- [`InjectedJsPurityTest`](../../ui/src/test/java/org/nmox/studio/ui/browser/devtools/InjectedJsPurityTest.java): JavaScript injected into a page carries no Java-ism (v2.38.3).
- [`GeneratedJsPurityTest`](../../rack/src/test/java/org/nmox/studio/rack/projectstudio/GeneratedJsPurityTest.java): JavaScript the kits generate carries no Java-ism (v2.38.4).
- [`IdeWorkspaceFilesTest`](../../core/src/test/java/org/nmox/studio/core/util/IdeWorkspaceFilesTest.java): Every studio workspace file follows the naming convention REFLEX ignores (v1.281.0).
- [`LoadingGuardShapeTest`](../../apiclient/src/test/java/org/nmox/studio/apiclient/LoadingGuardShapeTest.java): API Studio's loading flag is raised only above a `try` and dropped only in a `finally` (v1.265.0).
- [`DialogSafetyTest`](../../infra/src/test/java/org/nmox/studio/infra/DialogSafetyTest.java): The Infra Designer's destroy and deploy dialogs default to the safe button (v1.98.0).
- [`LayerFolderDuplicateTest`](../../editor/src/test/java/org/nmox/studio/editor/LayerFolderDuplicateTest.java): No folder is declared twice in the editor layer (v2.18.0).
- [`PositionedRegistrationsTest`](../../editor/src/test/java/org/nmox/studio/editor/PositionedRegistrationsTest.java): Every completion and hyperlink registration carries a position (v2.28.0).
- [`PopupPositionUniquenessTest`](../../editor/src/test/java/org/nmox/studio/editor/PopupPositionUniquenessTest.java): No two popup entries share a position in one folder (v2.36.4).
