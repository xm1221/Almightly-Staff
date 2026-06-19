package cn.xm1221.AlmightlyStaff.gui;

import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;

import java.util.function.BiConsumer;

/**
 * 给 MixinGuiSpellcasting 实现，IDE Screen 通过 Interface 调用 spellcasting 的方法。
 */
public interface IdeSpellcastingAccess {
    BiConsumer<HexPattern, Integer> getOnDrawPattern$ide();
    void setOnDrawPattern$ide(BiConsumer<HexPattern, Integer> cb);
    void clearPatterns$ide();
    int patternCount$ide();
    void setPatternType$ide(int index, ResolvedPatternType type);
    boolean isIdeWriteMode$ide();
    void setIdeWriteMode$ide(boolean write);
}
