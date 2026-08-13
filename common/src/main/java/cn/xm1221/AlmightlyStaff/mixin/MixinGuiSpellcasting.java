package cn.xm1221.AlmightlyStaff.mixin;

import at.petrak.hexcasting.api.casting.eval.ResolvedPattern;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import at.petrak.hexcasting.common.msgs.MsgNewSpellPatternC2S;
import at.petrak.hexcasting.xplat.IClientXplatAbstractions;
import cn.xm1221.AlmightlyStaff.gui.IdeSpellcastingAccess;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
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
    @Shadow(remap = false) private List<? extends Iota> cachedStack;

    @Unique @Nullable private BiConsumer<HexPattern, Integer> onDrawPattern$ide;
    @Unique private boolean ideWriteMode = true;
    @Unique private boolean castCollectMode$ide = false;

    @WrapWithCondition(
        method = "drawEnd",
        at = @At(value = "INVOKE",
            target = "Lat/petrak/hexcasting/xplat/IClientXplatAbstractions;sendPacketToServer(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            remap = false),
        remap = false
    )
    private boolean redirectPattern$ide(IClientXplatAbstractions inst, CustomPacketPayload msg) {
        if (onDrawPattern$ide != null && msg instanceof MsgNewSpellPatternC2S s) {
            onDrawPattern$ide.accept(s.pattern(), s.resolvedPatterns().size() - 1);
            return !ideWriteMode; // write=true 拦截, cast=false 放行
        }
        return true;
    }

    @Override @Nullable public BiConsumer<HexPattern, Integer> getOnDrawPattern$ide() { return onDrawPattern$ide; }
    @Override public void setOnDrawPattern$ide(@Nullable BiConsumer<HexPattern, Integer> cb) { onDrawPattern$ide = cb; }
    @Override public void clearPatterns$ide() { patterns.clear(); usedSpots.clear(); }
    @Override public void setPatternType$ide(int index, ResolvedPatternType type) { if (index >= 0 && index < patterns.size()) patterns.get(index).setType(type); }
    @Override public int patternCount$ide() { return patterns.size(); }
    @Override public void setIdeWriteMode$ide(boolean w) { ideWriteMode = w; }
    @Override public void setCastCollectMode$ide(boolean m) { castCollectMode$ide = m; }
    @Override public void setStackClear$ide() { if (cachedStack != null) cachedStack.clear(); }
    /** 1.21.1 的 cachedStack 是 List<? extends Iota>；序列化为 NBT 标签返回（客户端不解反序列化）。 */
    @Override public List<CompoundTag> getStack$ide() {
        List<CompoundTag> out = new ArrayList<>();
        if (cachedStack == null) return out;
        for (Iota iota : cachedStack) {
            if (iota == null) continue;
            try {
                var tag = IotaType.TYPED_CODEC.encodeStart(NbtOps.INSTANCE, iota).result().orElse(null);
                if (tag instanceof CompoundTag ct) out.add(ct);
            } catch (Exception ignored) { }
        }
        return out;
    }
}
