package org.nmox.studio.infra.ops.ui;

/**
 * The HUD's one easing: a critically damped spring per value, so a node
 * glides to a new place and stops without overshooting, and a gauge
 * moves to a new reading the same way. Pure arithmetic; the canvas
 * decides when to step it and whether to step it at all (reduced motion
 * snaps instead).
 */
public final class Motion {

    /** A value on its way somewhere. */
    public static final class Spring {
        private double value;
        private double velocity;
        private double target;

        public Spring(double start) {
            this.value = start;
            this.target = start;
        }

        public double value() {
            return value;
        }

        public double target() {
            return target;
        }

        public void setTarget(double t) {
            this.target = t;
        }

        /** Arrive now: no motion. */
        public void snap() {
            value = target;
            velocity = 0;
        }

        /** True when it has arrived (within half a pixel and nearly still). */
        public boolean settled() {
            return Math.abs(value - target) < 0.5 && Math.abs(velocity) < 0.5;
        }

        /**
         * Advances by {@code dt} seconds with stiffness {@code k}: critically
         * damped, so damping is {@code 2*sqrt(k)}. Semi-implicit Euler keeps
         * it stable at the frame rates a Swing timer delivers.
         */
        public void step(double dt, double k) {
            double damping = 2 * Math.sqrt(k);
            double accel = -k * (value - target) - damping * velocity;
            velocity += accel * dt;
            value += velocity * dt;
            if (settled()) {
                value = target;
                velocity = 0;
            }
        }
    }

    /** The stiffness nodes move with: ~0.6 s to arrive. */
    public static final double LAYOUT_K = 80;
    /** Gauges follow a reading a little faster. */
    public static final double GAUGE_K = 140;
    /** Camera moves (view changes) are slower than node moves, so the eye follows. */
    public static final double CAMERA_K = 40;

    private Motion() {
    }

    /** Linear interpolation, clamped. */
    public static double lerp(double a, double b, double t) {
        double u = Math.max(0, Math.min(1, t));
        return a + (b - a) * u;
    }
}
