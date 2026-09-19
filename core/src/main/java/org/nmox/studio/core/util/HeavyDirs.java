package org.nmox.studio.core.util;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The directories no walk descends into: generated or vendored trees
 * whose thousands of files would swamp a tree, a search or a click. One
 * home (promoted from Project Studio's file tree in v2.87.0 the moment a
 * second reader — the Markdown tree — arrived) so the file tree and every
 * later walk elide the same names.
 *
 * <p><b>Ledger 110, closed in v2.186.0.</b> For eleven releases that
 * javadoc was a claim rather than a fact: twelve declarations across four
 * modules held NINE distinct answers, only two of them reached this class,
 * and the five names here were the only five all twelve agreed on. The
 * sharpest instance was this file itself — {@code target} and {@code out}
 * sat in eleven of the twelve sets and not in the one that calls itself the
 * one home, so the Project Studio tree happily expanded a Rust
 * {@code target/} while every other walk in the product refused it.
 *
 * <p><b>What belongs here.</b> A name is in {@link #NAMES} when it is
 * unambiguously a build product or a package manager's installed tree —
 * something a {@code .gitignore} carries and a person never opens to read.
 * A name that is merely USUALLY generated belongs to the caller that skips
 * it, through {@link #plus}: the merge kept every legitimate difference
 * rather than flattening it.
 *
 * <p>Three names stayed out on purpose, each with a reason:
 *
 * <ul>
 *   <li>{@code vendor} — six walks skip it and they are right to, but this
 *       product's own Classic Kit WRITES {@code vendor/jquery-3.7.1.min.js}
 *       and wires a script tag at it, so a file tree that refuses to expand
 *       {@code vendor/} would hide files the IDE itself just put there.
 *       Those six callers ask for it by name.</li>
 *   <li>{@code .nmox} — the IDE's own state under the user's home. The two
 *       walks that must not descend into it (the failure timeline and the
 *       learning-space exporter) ask for it; the file tree, which is how a
 *       person LOOKS at a learning space, must not.</li>
 *   <li>{@code .cache} and {@code .idea} — another tool's churn, relevant
 *       to the save-watcher deciding whether a change is a SOURCE change
 *       and to nobody else.</li>
 * </ul>
 *
 * <p>The population is gated: {@code HeavyDirsLedgerTest} derives every
 * skip-set literal in the product's main sources and fails the build when a
 * thirteenth copy lands unclassified. That gate is the half of this that is
 * not deferrable — the sets did not disagree because anyone decided they
 * should, they disagreed because nothing was watching.
 */
public final class HeavyDirs {

    /**
     * The names every walk skips. Ordered by how often a project has one,
     * which is also roughly how much each costs to walk.
     *
     * <ul>
     *   <li>{@code node_modules}, {@code .venv} — a package manager's
     *       installed tree, the two heaviest directories in the two
     *       largest ecosystems this IDE serves;</li>
     *   <li>{@code .git} — the repository's own object store;</li>
     *   <li>{@code dist}, {@code build}, {@code out}, {@code target},
     *       {@code coverage} — build and report output, in the five
     *       spellings the toolchains here use;</li>
     *   <li>{@code .next}, {@code .nuxt}, {@code .svelte-kit},
     *       {@code .angular} — the framework caches, each named by its own
     *       CLI and never written by hand;</li>
     *   <li>{@code __pycache__} — compiled bytecode beside its source.</li>
     * </ul>
     */
    public static final Set<String> NAMES = Set.of(
            "node_modules", ".venv", ".git",
            "dist", "build", "out", "target", "coverage",
            ".next", ".nuxt", ".svelte-kit", ".angular",
            "__pycache__");

    private HeavyDirs() {
    }

    public static boolean isHeavy(String dirName) {
        return NAMES.contains(dirName);
    }

    /**
     * {@link #NAMES} plus this caller's own extras — the seam that lets a
     * walk differ from every other walk WITHOUT re-declaring the thirteen
     * names they all agree on. A caller that needs {@code vendor} or
     * {@code .nmox} says so here, where the reason can sit beside it.
     *
     * @return an unmodifiable set; the argument order is preserved so a
     *         caller's extras read last, where they were written
     */
    public static Set<String> plus(String... extras) {
        Set<String> all = new LinkedHashSet<>(NAMES);
        all.addAll(Arrays.asList(extras));
        return Set.copyOf(all);
    }
}
