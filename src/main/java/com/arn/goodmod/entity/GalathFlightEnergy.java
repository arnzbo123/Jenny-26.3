package com.arn.goodmod.entity;

/** hf: three boosts, strictly over three seconds between uses, one recharge per five seconds. */
public final class GalathFlightEnergy {
    private int charges = 3, cooldown, recharge;
    public int charges() { return charges; }
    public int cooldown() { return cooldown; }
    public int rechargeTicks() { return recharge; }
    public boolean tryBoost() {
        if (charges == 0 || cooldown > 0) return false;
        charges--; cooldown = 61; recharge = 0; return true;
    }
    public void tick() {
        if (cooldown > 0) cooldown--;
        if (charges < 3 && ++recharge >= 100) { charges++; recharge = 0; }
    }
    public void restore(int charges, int cooldown, int recharge) {
        this.charges = Math.clamp(charges, 0, 3); this.cooldown = Math.clamp(cooldown, 0, 61); this.recharge = Math.clamp(recharge, 0, 99);
    }
}
