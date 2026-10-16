package com.wildfire.main.networking.appearance;

import net.minecraft.network.chat.Component;

public enum BreastShape {
    ROUND(0, "Округлая"),
    CONNECTED(1, "Сплошная"),
    PERKY(2, "Стоячая"),
    SOFT(3, "Мягкая"),
    SAGGING(4, "Просевшая"),
    LEGACY(5, "Legacy-треугольники"),
    BLOCKS(6, "Блоки"),
    CONNECTED_BLOCKS(7, "Сплошные блоки"),
    WIDE(8, "Широкая"),
    NARROW(9, "Узкая"),
    TEARDROP(10, "Каплевидная"),
    ASYMMETRIC(11, "Асимметричная");

    private final int id;
    private final String displayName;

    BreastShape(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public int id() { return id; }
    public Component label() { return Component.literal("Форма: " + displayName); }
    public BreastShape next() { return values()[(ordinal() + 1) % values().length]; }

    public static BreastShape fromId(int id) {
        for (BreastShape shape : values()) if (shape.id == id) return shape;
        return ROUND;
    }
}
