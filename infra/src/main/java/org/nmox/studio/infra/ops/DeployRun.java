package org.nmox.studio.infra.ops;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.nmox.studio.infra.ops.remote.DeployPlan;
import org.nmox.studio.infra.ops.remote.Deployer;

/**
 * A deploy as the HUD watches it: which step is running, which have
 * passed or failed, the last lines of output. One per host at a time;
 * the Hub refuses a second while one runs. EDT-confined like the
 * topology.
 *
 * <p>A rollback is a run too — the same tracker, the same refusal while
 * one is in flight — with only the steps it has: it starts an image the
 * host already holds, so there is nothing to bundle, upload or build.
 */
public final class DeployRun {

    public enum StepState { PENDING, RUNNING, OK, FAILED }

    /** The output kept for the panel; the full log goes to the feed as it happens. */
    public static final int TAIL = 40;

    public final String hostId;
    public final DeployPlan plan;
    public final long startedAt;
    /** The steps this run has, in order: all six for a deploy, RUN and VERIFY for a rollback. */
    public final Map<Deployer.Step, StepState> steps = new EnumMap<>(Deployer.Step.class);
    /** True when this run starts an earlier image instead of building a new one. */
    public final boolean rollback;
    private final Deque<String> tail = new ArrayDeque<>();
    public Deployer.Outcome outcome;
    public boolean finished;

    public DeployRun(String hostId, DeployPlan plan, long startedAt) {
        this(hostId, plan, startedAt, false);
    }

    public DeployRun(String hostId, DeployPlan plan, long startedAt, boolean rollback) {
        this.hostId = hostId;
        this.plan = plan;
        this.startedAt = startedAt;
        this.rollback = rollback;
        for (Deployer.Step s : Deployer.Step.values()) {
            if (!rollback || s == Deployer.Step.RUN || s == Deployer.Step.VERIFY) {
                steps.put(s, StepState.PENDING);
            }
        }
    }

    public void line(String text) {
        tail.addLast(text);
        while (tail.size() > TAIL) {
            tail.removeFirst();
        }
    }

    public List<String> tail() {
        return new ArrayList<>(tail);
    }

    public Deployer.Step current() {
        for (Map.Entry<Deployer.Step, StepState> e : steps.entrySet()) {
            if (e.getValue() == StepState.RUNNING) {
                return e.getKey();
            }
        }
        return null;
    }

    public boolean ok() {
        return finished && outcome != null && outcome.ok();
    }
}
