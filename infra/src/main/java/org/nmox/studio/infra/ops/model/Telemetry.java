package org.nmox.studio.infra.ops.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A host's last measurements, as a bounded ring so a host polled for a
 * week costs the same memory as one polled once. Every value is a number
 * the probe read off the host; absent means the host did not say.
 */
public final class Telemetry {

    /** One probe's reading. {@code NaN} where the host offered no number. */
    public record Sample(long atMillis, double load1, int cpus, long memTotalKb, long memUsedKb,
            long diskTotalKb, long diskUsedKb, long uptimeSeconds) {

        /** Load per core, 0..1+; NaN without a load or a core count. */
        public double loadShare() {
            return cpus > 0 && !Double.isNaN(load1) ? load1 / cpus : Double.NaN;
        }

        /** Used memory as a share, NaN when the host did not report memory. */
        public double memShare() {
            return memTotalKb > 0 ? (double) memUsedKb / memTotalKb : Double.NaN;
        }

        /** Used disk as a share, NaN when unknown. */
        public double diskShare() {
            return diskTotalKb > 0 ? (double) diskUsedKb / diskTotalKb : Double.NaN;
        }
    }

    /** How many samples a host keeps: enough for a sparkline, bounded. */
    public static final int CAPACITY = 60;

    private final Deque<Sample> samples = new ArrayDeque<>();

    public void add(Sample sample) {
        samples.addLast(sample);
        while (samples.size() > CAPACITY) {
            samples.removeFirst();
        }
    }

    /** The newest sample, or null before the first probe. */
    public Sample latest() {
        return samples.peekLast();
    }

    /** Oldest first. */
    public List<Sample> history() {
        return new ArrayList<>(samples);
    }

    public int size() {
        return samples.size();
    }
}
