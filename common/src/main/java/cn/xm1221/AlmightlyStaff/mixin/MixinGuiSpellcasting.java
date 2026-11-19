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
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 让 GuiSpellcasting 可被法术库绘制流程复用：
 * 拦截 drawEnd 的封包发送，改为本地回调捕获已绘制图案。
 */
@Mixin(GuiSpellcasting.class)
public abstract class MixinGuiSpellcasting implements IdeSpellcastingAccess {

    @Shadow(remap = false) private List<ResolvedPattern> patterns;
    @Shadow(remap = false) private Set<HexCoord> usedSpots;
    @Shadow(remap = false) private List<net.minecraft.nbt.CompoundTag> cachedStack;
    @Shadow(remap = false) private void calculateIotaDisplays() { }

    @Unique @Nullable private BiConsumer<HexPattern, Integer> onDrawPattern$ide;
    @Unique private boolean ideWriteMode = true;
    @Unique private boolean castCollectMode$ide = false;
    @Unique private float hexSizeOverride$ide = Float.NaN;
    @Unique @Nullable private Vec2 coordsOffsetOverride$ide;

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
            return !ideWriteMode; // write=true 拦截, cast=false 放行
        }
        return true;
    }

    @ModifyReturnValue(method = "hexSize", at = @At("RETURN"), remap = false)
    private float overrideHexSize$ide(float original) {
        return Float.isNaN(hexSizeOverride$ide) ? original : hexSizeOverride$ide;
    }

    @ModifyReturnValue(method = "coordsOffset", at = @At("RETURN"), remap = false)
    private Vec2 overrideCoordsOffset$ide(Vec2 original) {
        return coordsOffsetOverride$ide != null ? coordsOffsetOverride$ide : original;
    }

    @Override @Nullable public BiConsumer<HexPattern, Integer> getOnDrawPattern$ide() { return onDrawPattern$ide; }
    @Override public void setOnDrawPattern$ide(@Nullable BiConsumer<HexPattern, Integer> cb) { onDrawPattern$ide = cb; }
    @Override public void clearPatterns$ide() { patterns.clear(); usedSpots.clear(); }
    @Override public void setPatternType$ide(int index, ResolvedPatternType type) { if (index >= 0 && index < patterns.size()) patterns.get(index).setType(type); }
    @Override public int patternCount$ide() { return patterns.size(); }
    @Override public void setIdeWriteMode$ide(boolean w) { ideWriteMode = w; }
    @Override public void setCastCollectMode$ide(boolean m) { castCollectMode$ide = m; }
    @Override public void setStackClear$ide() { cachedStack = new java.util.ArrayList<>(); calculateIotaDisplays(); }
    @Override public List<net.minecraft.nbt.CompoundTag> getStack$ide() { return cachedStack == null ? List.of() : cachedStack; }
    @Override public void setHexSizeOverride$ide(float hexSize) { hexSizeOverride$ide = hexSize; }
    @Override public void setCoordsOffsetOverride$ide(@Nullable Vec2 offset) { coordsOffsetOverride$ide = offset; }
    @Override public void recalcDisplays$ide() { calculateIotaDisplays(); }
}
