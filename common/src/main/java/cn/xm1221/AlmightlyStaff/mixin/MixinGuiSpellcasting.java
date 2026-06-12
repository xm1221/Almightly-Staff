package cn.xm1221.AlmightlyStaff.mixin;

import at.petrak.hexcasting.api.casting.eval.ResolvedPattern;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import at.petrak.hexcasting.common.msgs.IMessage;
import at.petrak.hexcasting.common.msgs.MsgNewSpellPatternC2S;
import at.petrak.hexcasting.xplat.IClientXplatAbstractions;
import cn.xm1221.AlmightlyStaff.gui.IdeSpellcastingAccess;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

@Mixin(GuiSpellcasting.class)
public abstract class MixinGuiSpellcasting implements IdeSpellcastingAccess {

    @Shadow(remap = false) private List<ResolvedPattern> patterns;
    @Shadow(remap = false) private Set<HexCoord> usedSpots;

    @Unique @Nullable private BiConsumer<HexPattern, Integer> onDrawPattern$ide;
    @Unique private boolean ideWriteMode = true;

    @WrapWithCondition(
        method = "drawEnd",
        at = @At(value = "INVOKE",
            target = "Lat/petrak/hexcasting/xplat/IClientXplatAbstractions;sendPacketToServer(Lat/petrak/hexcasting/common/msgs/IMessage;)V",
            remap = false),
        remap = false
    )
    private boolean redirectPattern$ide(IClientXplatAbstractions inst, IMessage msg) {
        if (onDrawPattern$ide != null && msg instanceof MsgNewSpellPatternC2S s) {
            onDrawPattern$ide.accept(s.pattern(), s.resolvedPatterns().size() - 1);
            return !ideWriteMode; // write=t拦截, cast=f放行
        }
        return true;
    }

    @Override @Nullable public BiConsumer<HexPattern, Integer> getOnDrawPattern$ide() { return onDrawPattern$ide; }
    @Override public void setOnDrawPattern$ide(@Nullable BiConsumer<HexPattern, Integer> cb) { onDrawPattern$ide = cb; }
    @Override public void clearPatterns$ide() { patterns.clear(); usedSpots.clear(); }
    @Override public void setPatternType$ide(int index, ResolvedPatternType type) { if (index >= 0 && index < patterns.size()) patterns.get(index).setType(type); }
    @Override public int patternCount$ide() { return patterns.size(); }
    @Override public void setIdeWriteMode$ide(boolean w) { ideWriteMode = w; }
}
