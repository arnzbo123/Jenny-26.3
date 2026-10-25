package com.arn.goodmod.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.loading.math.MathParser;
import com.geckolib.renderer.base.GeoRenderState;

/** Per-render-state inputs for expressions migrated from the legacy animation data. */
public final class GoodcraftMolang {
    public enum Input {
        SIZE, PITCH, ARMPITCH, ARMYAW, YAW, SCALE;

        private final String query = "query.sexmod." + name().toLowerCase(java.util.Locale.ROOT);
        private final DataTicket<Double> ticket = DataTicket.create(
                "sexmod:molang_" + name().toLowerCase(java.util.Locale.ROOT), Double.class);

        public String query() { return query; }
        public DataTicket<Double> ticket() { return ticket; }
    }

    private GoodcraftMolang() {}

    /** Call before GeckoLib loads animations. Renderers must bind their actual values. */
    public static void register() {
        for (Input input : Input.values()) {
            MathParser.setVariable(input.query(), state -> require(state.renderState(), input));
        }
    }

    public static void bind(GeoRenderState state, Input input, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Non-finite animation input: " + input.query());
        }
        state.addGeckolibData(input.ticket(), value);
    }

    /** Fails on an unbound input; no shared mutable values or default-zero bindings. */
    public static double require(GeoRenderState state, Input input) {
        if (state == null || !state.hasGeckolibData(input.ticket())) {
            throw new IllegalStateException("Renderer did not bind animation input " + input.query());
        }
        return state.getGeckolibData(input.ticket());
    }
}
