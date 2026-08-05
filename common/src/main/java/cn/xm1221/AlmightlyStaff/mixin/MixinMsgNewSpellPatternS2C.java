package cn.xm1221.AlmightlyStaff.mixin;

import at.petrak.hexcasting.common.msgs.MsgNewSpellPatternS2C;
import cn.xm1221.AlmightlyStaff.gui.AlmightlyStaffIDEScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 让 IDE Screen 也能接收 MsgNewSpellPatternS2C 更新图案颜色 */
@Mixin(MsgNewSpellPatternS2C.class)
public class MixinMsgNewSpellPatternS2C {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private void almightly$handle(CallbackInfo ci) {
        var self = (MsgNewSpellPatternS2C) (Object) this;
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof AlmightlyStaffIDEScreen ide) {
            ide.onPatternEvaluated(self.info().getResolutionType(), self.index());
            ci.cancel(); // 原始 handle 只认 GuiSpellcasting，我们接管
        }
    }
}
