package com.linc.spectralenchantments.spectral;

/** How an Animated weapon swings; picked per attack from Better Combat data or the item's type. */
public enum AttackStyle {
    /** Horizontal sweep across the target. */
    SLASH_H,
    /** Overhead chop. */
    SLASH_V,
    /** Pull back, then thrust. */
    STAB,
    /** Lift high, then slam down. */
    SMASH,
    /** Not a weapon: tumble into the target. */
    BONK;

    private static final AttackStyle[] VALUES = values();

    public static AttackStyle byId(int id) {
        return VALUES[Math.floorMod(id, VALUES.length)];
    }
}
