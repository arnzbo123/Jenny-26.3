package com.arn.goodmod.entity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Registry identities and attributes traced to bi/fy and the original subclasses. */
public enum NpcKind {
    JENNY("jenny", "jenny", 0.49F, 1.95F, 20, 0.5, 30, true, false),
    ELLIE("ellie", "ellie", 0.6F, 1.8F, 20, 0.5, 30, true, false),
    SLIME("slime", "slime", 0.6F, 1.8F, 20, 0.5, 30, false, false),
    BIA("bia", "bia", 0.49F, 1.65F, 20, 0.5, 30, true, false),
    BEE("bee", "bee", 0.3F, 1.5F, 12, 0.2, 16, false, true),
    LUNA("luna", "cat", 0.6F, 1.8F, 20, 1.0, 30, true, false),
    ALLIE("allie", "allie", 0.5F, 1.0F, 20, 0.5, 30, false, false),
    KOBOLD("kobold", "kobold", 0.5F, 0.99F, 69, 0.5, 30, false, true),
    GOBLIN("goblin", "goblin", 0.5F, 0.99F, 20, 0.5, 30, false, false),
    GALATH("galath", "galath", 0.6F, 1.8F, 110, 0.6, 50, false, false),
    MANGLELIE("manglelie", "manglelie", 0.6F, 1.8F, 20, 0.5, 30, false, false);

    private final String id, animationPrefix;
    private final float width, height;
    private final double health, speed, range;
    private final boolean equipment, storage;

    NpcKind(String id, String prefix, float width, float height, double health, double speed,
            double range, boolean equipment, boolean storage) {
        this.id = id;
        this.animationPrefix = prefix;
        this.width = width;
        this.height = height;
        this.health = health;
        this.speed = speed;
        this.range = range;
        this.equipment = equipment;
        this.storage = storage;
    }

    public String id() { return id; }
    public String animationPrefix() { return animationPrefix; }
    public float width() { return width; }
    public float height() { return height; }
    public double maxHealth() { return health; }
    public double movementSpeed() { return speed; }
    public double followRange() { return range; }
    public boolean supportsEquipment() { return equipment; }
    public boolean supportsStorage() { return storage; }
    public boolean hasPlayerForm() { return this != MANGLELIE; }
    public static Optional<NpcKind> byId(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(kind -> kind.id.equals(key)).findFirst();
    }
}
