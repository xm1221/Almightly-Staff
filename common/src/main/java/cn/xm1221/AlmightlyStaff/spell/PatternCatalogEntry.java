package cn.xm1221.AlmightlyStaff.spell;

import net.minecraft.network.chat.Component;

/**
 * 图案目录条目。移植自 Hex CyberStaff。
 */
public record PatternCatalogEntry(PatternRef pattern, Component displayName, Kind kind) {
    public PatternCatalogEntry(PatternRef pattern, Component displayName) {
        this(pattern, displayName, Kind.NORMAL);
    }

    public enum Kind {
        NORMAL,
        NUMBER,
        META,
        PER_WORLD,
        CUSTOM,
        UNKNOWN
    }
}
