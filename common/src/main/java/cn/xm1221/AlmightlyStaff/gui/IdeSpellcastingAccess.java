package cn.xm1221.AlmightlyStaff.gui;

import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import org.jetbrains.annotations.Nullable;
import java.util.function.BiConsumer;

public interface IdeSpellcastingAccess {
    @Nullable BiConsumer<HexPattern, Integer> getOnDrawPattern$ide();
    void setOnDrawPattern$ide(@Nullable BiConsumer<HexPattern, Integer> callback);
    void clearPatterns$ide();
    void setPatternType$ide(int index, ResolvedPatternType type);
    int patternCount$ide();
    void setIdeWriteMode$ide(boolean writeMode);
}
