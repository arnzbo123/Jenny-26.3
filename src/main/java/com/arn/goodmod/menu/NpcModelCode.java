package com.arn.goodmod.menu;

import com.arn.goodmod.entity.NpcKind;
import java.util.ArrayList;
import java.util.List;

/** fx/fw/em: addon names joined by '#', followed by optional '$' and integer selectors. */
public record NpcModelCode(List<String> addons, List<Integer> selectors) {
    public static final int MAX_LENGTH = 4096;
    public NpcModelCode {
        addons = List.copyOf(addons);
        selectors = List.copyOf(selectors);
    }

    public static NpcModelCode parse(String text, NpcKind kind) {
        if (text.length() > MAX_LENGTH) throw new IllegalArgumentException("Model code is too long");
        String[] sections = text.split("\\$", -1);
        if (sections.length > 2) throw new IllegalArgumentException("Model code contains multiple '$' separators");
        List<String> addons = new ArrayList<>();
        for (String name : sections[0].split("#", -1)) {
            if (name.isEmpty() || name.equals("cross")) continue;
            if (name.length() > 128 || !name.matches("[A-Za-z0-9_ .-]+") || name.equals(".") || name.equals("..")) {
                throw new IllegalArgumentException("Invalid custom model name");
            }
            if (addons.contains(name)) throw new IllegalArgumentException("Duplicate custom model name");
            addons.add(name);
            if (addons.size() > 32) throw new IllegalArgumentException("Too many custom models");
        }
        List<Integer> selectors = new ArrayList<>();
        if (sections.length == 2 && !sections[1].isEmpty()) {
            String values = sections[1].endsWith("-") ? sections[1].substring(0, sections[1].length() - 1) : sections[1];
            int[] bounds = bounds(kind);
            String[] parts = values.split("-", -1);
            // hj copied all ten goblin fields; e3.D exposes nine editable selectors.
            // The final flag is gameplay state, so imported codes cannot change it.
            if (kind == NpcKind.GOBLIN && parts.length == 10) {
                if (!parts[9].equals("0") && !parts[9].equals("1") && !parts[9].equals("00") && !parts[9].equals("01")) {
                    throw new IllegalArgumentException("Invalid legacy goblin state flag");
                }
                parts = java.util.Arrays.copyOf(parts, 9);
            }
            if (parts.length != bounds.length) throw new IllegalArgumentException("Wrong number of appearance values for " + kind.id());
            for (int i = 0; i < parts.length; i++) {
                if (!parts[i].matches("[0-9]{1,3}")) throw new IllegalArgumentException("Appearance values must be nonnegative integers");
                int value = Integer.parseInt(parts[i]);
                if (value >= bounds[i]) throw new IllegalArgumentException("Appearance value " + (i + 1) + " is out of range");
                selectors.add(value);
            }
        }
        return new NpcModelCode(addons, selectors);
    }

    public static int[] bounds(NpcKind kind) {
        return switch (kind) {
            case GOBLIN -> new int[]{4, 3, 3, 16, 16, 6, 8, 5, 6};
            case KOBOLD -> new int[]{101, 11, 11, 8, 3, 101, 101, 3, 3, 4, 2};
            default -> new int[0];
        };
    }

    public String encode() {
        StringBuilder result = new StringBuilder();
        addons.forEach(name -> result.append(name).append('#'));
        if (!selectors.isEmpty()) {
            result.append('$');
            selectors.forEach(value -> result.append(value).append('-'));
        }
        return result.toString();
    }
}
