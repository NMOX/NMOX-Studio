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
 */
public final class DeployRun {

    public enum StepState { PENDING, RUNNING, OK, FAILED }

    /** The output kept for the panel; the full log goes to the feed as it happens. */
    public static final int TAIL = 40;

    public final String hostId;
    public final DeployPlan plan;
    public final long startedAt;
    public final Map<Deployer.Step, StepState> steps = new EnumMap<>(Deployer.Step.class);
    private final Deque<String> tail = new ArrayDeque<>();
    public Deployer.Outcome outcome;
    public boolean finished;

    public DeployRun(String hostId, DeployPlan plan, long startedAt) {
        this.hostId = hostId;
        this.plan = plan;
        this.startedAt = startedAt;
        for (Deployer.Step s : Deployer.Step.values()) {
            steps.put(s, StepState.PENDING);
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
        for (Deployer.Step s : Deployer.Step.values()) {
            if (steps.get(s) == StepState.RUNNING) {
                return s;
            }
        }
        return null;
    }

    public boolean ok() {
        return finished && outcome != null && outcome.ok();
    }
}
