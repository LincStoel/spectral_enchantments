package com.linc.spectralenchantments.boots;

/**
 * Vertical motion in vanilla's units (blocks, blocks per tick): each tick an entity moves by its speed, then gravity
 * takes {@value #GRAVITY} off and air drag keeps {@value #DRAG} of what is left.
 */
public final class JumpPhysics {
    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;
    /** Upward speeds searched for a height; past 16 blocks (High Jump's and any sane bounce's limit) with room to spare. */
    private static final double MAX_SPEED = 4.0;
    /** How high a plain jump (0.42 blocks a tick) rises: about 1.25 blocks. */
    public static final double VANILLA_JUMP_HEIGHT = heightFor(0.42);

    private JumpPhysics() {}

    /** How high an entity launched upward at {@code speed} rises. */
    public static double heightFor(double speed) {
        double height = 0;
        while (speed > 0) {
            height += speed;
            speed = (speed - GRAVITY) * DRAG;
        }
        return height;
    }

    /** The upward speed that rises {@code height} blocks; a few dozen steps, so only once per jump or bounce. */
    public static double speedFor(double height) {
        if (!(height > 0)) {
            return 0;
        }
        double low = 0;
        double high = MAX_SPEED;
        for (int i = 0; i < 20; i++) {
            double mid = (low + high) / 2;
            if (heightFor(mid) < height) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return high;
    }
}
