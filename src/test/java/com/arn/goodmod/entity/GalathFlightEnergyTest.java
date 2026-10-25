package com.arn.goodmod.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GalathFlightEnergyTest {
    @Test void boostReuseRequiresMoreThanThreeSecondsAndExhaustsThreeCharges() {
        var energy = new GalathFlightEnergy();
        assertTrue(energy.tryBoost());
        for (int tick = 0; tick < 60; tick++) energy.tick();
        assertFalse(energy.tryBoost()); energy.tick(); assertTrue(energy.tryBoost());
        for (int tick = 0; tick < 61; tick++) energy.tick();
        assertTrue(energy.tryBoost()); assertEquals(0, energy.charges());
        for (int tick = 0; tick < 61; tick++) energy.tick();
        assertFalse(energy.tryBoost());
    }
    @Test void boostRestartsFiveSecondRechargeAndReloadKeepsPartialProgress() {
        var energy = new GalathFlightEnergy(); energy.tryBoost();
        for (int tick = 0; tick < 90; tick++) energy.tick();
        assertTrue(energy.tryBoost());
        for (int tick = 0; tick < 99; tick++) energy.tick();
        var loaded = new GalathFlightEnergy(); loaded.restore(energy.charges(), energy.cooldown(), energy.rechargeTicks());
        assertEquals(1, loaded.charges()); loaded.tick(); assertEquals(2, loaded.charges());
        for (int tick = 0; tick < 100; tick++) loaded.tick();
        assertEquals(3, loaded.charges());
    }
}
