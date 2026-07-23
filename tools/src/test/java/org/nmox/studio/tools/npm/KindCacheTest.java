package org.nmox.studio.tools.npm;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The enablement kind-cache (v1.114.0 HIGH): isActionEnabled runs on the
 * EDT at every menu/toolbar/selection refresh, and detectKind walks the
 * project directory — the cache turns a storm of enablement checks into
 * one scan per TTL window, with staleness bounded at the TTL.
 */
class KindCacheTest {

    private static final File DIR = new File("/tmp/any");

    @Test
    @DisplayName("Repeated checks within the TTL cost one directory scan, not N")
    void cachesWithinTtl() {
        AtomicInteger scans = new AtomicInteger();
        var cache = new WebProjectActionProvider.KindCache(3_000, d -> {
            scans.incrementAndGet();
            return ProjectKind.NODE;
        });
        for (int i = 0; i < 50; i++) {
            assertThat(cache.get(DIR, 1_000 + i)).isEqualTo(ProjectKind.NODE);
        }
        assertThat(scans.get()).as("a menu-paint storm = one scan").isEqualTo(1);
    }

    @Test
    @DisplayName("After the TTL the kind re-detects — staleness is bounded")
    void redetectsAfterTtl() {
        AtomicInteger scans = new AtomicInteger();
        var cache = new WebProjectActionProvider.KindCache(3_000, d -> {
            scans.incrementAndGet();
            return scans.get() == 1 ? ProjectKind.NODE : ProjectKind.RUST;
        });
        assertThat(cache.get(DIR, 0)).isEqualTo(ProjectKind.NODE);
        assertThat(cache.get(DIR, 3_001)).as("TTL elapsed → fresh detect")
                .isEqualTo(ProjectKind.RUST);
        assertThat(scans.get()).isEqualTo(2);
    }
}
