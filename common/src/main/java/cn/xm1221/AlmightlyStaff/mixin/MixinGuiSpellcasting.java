package cn.xm1221.AlmightlyStaff.mixin;

import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import cn.xm1221.AlmightlyStaff.gui.IdeSpellcastingAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.WrapWithCondition;

import java.util.function.BiConsumer;

/**
 * 拦截 GuiSpellcasting.sendPacketToServer()，IDE Write 模式时阻止发包。
 * 图案绘制回调由 IdeSpellcastingAccess 接口提供给 IDE Screen。
 */
@Mixin(GuiSpellcasting.class)
public abstract class MixinGuiSpellcasting implements IdeSpellcastingAccess {

    @Unique private BiConsumer<HexPattern, Integer> onDrawPattern$ide;
    @Unique private boolean ideWriteMode$ide = true;

    @Override public BiConsumer<HexPattern, Integer> getOnDrawPattern$ide() { return onDrawPattern$ide; }
    @Override public void setOnDrawPattern$ide(BiConsumer<HexPattern, Integer> cb) { onDrawPattern$ide = cb; }
    @Override public void clearPatterns$ide() {}
    @Override public int patternCount$ide() { return 0; }
    @Override public void setPatternType$ide(int index, ResolvedPatternType type) {}
    @Override public boolean isIdeWriteMode$ide() { return ideWriteMode$ide; }
    @Override public void setIdeWriteMode$ide(boolean write) { ideWriteMode$ide = write; }

    @WrapWithCondition(method = "drawEnd", at = @At(value = "INVOKE", target = "at/petrak/hexcasting/client/gui/GuiSpellcasting.sendPacketToServer (Z)V"))
    private boolean ideCancelSend(GuiSpellcasting self, boolean b) {
        return !ideWriteMode$ide;
    }
}
