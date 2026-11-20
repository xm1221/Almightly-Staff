package cn.xm1221.AlmightlyStaff.gui;

import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * 由 {@link cn.xm1221.AlmightlyStaff.mixin.MixinGuiSpellcasting} 实现，
 * 供绘制/施法/IDE 子界面向 GuiSpellcasting 注入图案回调与写模式开关。
 */
public interface IdeSpellcastingAccess {
    @Nullable BiConsumer<HexPattern, Integer> getOnDrawPattern$ide();
    void setOnDrawPattern$ide(@Nullable BiConsumer<HexPattern, Integer> callback);
    void clearPatterns$ide();
    void setPatternType$ide(int index, ResolvedPatternType type);
    int patternCount$ide();
    void setIdeWriteMode$ide(boolean writeMode);
    /** 施法采集模式：服务端回包 isStackClear 时不自动关屏（参考 HexGuide 书页内嵌）。 */
    void setCastCollectMode$ide(boolean castCollectMode);
    /** 清空本地栈显示（不关屏），isStackClear 时由包装屏调用。 */
    void setStackClear$ide();
    /** 读取本地施法栈（GuiSpellcasting 的 cachedStack，显示在侧栏的那份）。 */
    List<CompoundTag> getStack$ide();
    void setHexSizeOverride$ide(float hexSize);
    void setCoordsOffsetOverride$ide(@Nullable Vec2 offset);
    void recalcDisplays$ide();
    boolean isDrawing$ide();
    void cancelStroke$ide();
}
