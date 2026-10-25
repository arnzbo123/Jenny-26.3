package com.arn.goodmod.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;

/** e4.b, ff.L/ff.a and e3.a; model selector order matches the legacy serialized code. */
public final class NpcAppearance {
    public static final List<String> KOBOLD_COLORS = List.of("GREEN", "YELLOW", "RED", "PURPLE", "LIGHT_GREEN", "OLD_BLUE", "DARK_GREY", "BROWN", "DARK_BLUE", "LIGHT_BLUE", "SILVER");
    private NpcAppearance() {}
    /** ff:336/2413 and e7:316 invert the 0..100 body-size slider for Molang. */
    public static double koboldAnimationSize(int selector) { return 0.25 - Math.clamp(selector, 0, 100) / 400.0; }
    public static String randomCode(NpcKind kind, RandomSource random) {
        List<Integer> values = new ArrayList<>();
        if (kind == NpcKind.KOBOLD) {
            values.add(0); values.add(3); values.add(random.nextInt(11));
            values.add(random.nextInt(8)); values.add(random.nextInt(3)); values.add(bell(random)); values.add(bell(random));
            values.add(random.nextInt(3)); values.add(random.nextInt(3)); values.add(random.nextInt(2)); values.add(random.nextInt(2));
        } else if (kind == NpcKind.GOBLIN) for (int bound : new int[]{4, 3, 3, 9, 9, 6, 8, 5, 6}) values.add(random.nextInt(bound));
        else return "";
        StringBuilder encoded = new StringBuilder("$"); values.forEach(value -> encoded.append(value).append('-')); return encoded.toString();
    }
    private static int bell(RandomSource random) { double x = -2.5 + 5 * random.nextDouble(); return ((int)Math.round(Math.exp(-x * x) * 100)) % 100; }
    public static String replaceSelector(String code, int index, int value) {
        int boundary = code.indexOf('$'); if (boundary < 0) return code;
        String[] parts = code.substring(boundary + 1).split("-"); if (index >= parts.length) return code;
        parts[index] = Integer.toString(value); return code.substring(0, boundary + 1) + String.join("-", parts) + "-";
    }
}
