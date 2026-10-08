package com.linc.spectralenchantments.spectral;

/** A weapon entity that swings on its own, as the client's WeaponPoses draws it. Client state. */
public interface SwingingWeapon {
    /** Swing progress 0-1, or a negative value when not swinging. */
    float attackProgress(float partialTick);

    AttackStyle attackStyle();

    boolean attackMirrored();

    /** The fraction of the swing spent winding up before the hit lands. */
    float attackUpswing();
}
