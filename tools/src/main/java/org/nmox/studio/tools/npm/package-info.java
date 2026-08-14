/**
 * Project recognition and the NPM surface. {@code WebProjectFactory}
 * (registered {@code @ServiceProvider(ProjectFactory.class)}) is the
 * front door of the whole product: it recognizes 58 manifest types —
 * package.json, Cargo.toml, go.mod, foundry.toml … — and hands the
 * platform a {@code WebProject} whose <b>Lookup</b> carries everything
 * the IDE asks a project for: an {@code ActionProvider} (the real
 * Run/Build/Test/Clean, per toolchain), {@code Sources}, and the
 * open-hook that aims the rack.
 *
 * <p>If Lookup is still fuzzy, this is the package to fix that in:
 * "the project's Lookup" is literally the bag of capabilities other
 * code discovers with {@code getLookup().lookup(SomeInterface.class)}.
 * Package-manager truth (npm vs pnpm vs yarn, corepack pin first) is
 * resolved from the project's own contract in {@code ProjectInspector}
 * and honored by every lane (v1.60.0).
 */
package org.nmox.studio.tools.npm;
