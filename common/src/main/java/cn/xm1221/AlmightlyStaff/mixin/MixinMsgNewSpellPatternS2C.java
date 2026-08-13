package cn.xm1221.AlmightlyStaff.mixin;

import at.petrak.hexcasting.common.msgs.MsgNewSpellPatternS2C;
import cn.xm1221.AlmightlyStaff.gui.StaffCastScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把服务端施法结果回包转发给施法采集包装屏：
 * 原版 MsgNewSpellPatternS2C.handle 只处理 screen instanceof GuiSpellcasting 的情况，
 * 而我们的 StaffCastScreen 是包装屏（当前 screen 不是 GuiSpellcasting），需要手动转发以更新栈显示。
 */
@Mixin(MsgNewSpellPatternS2C.class)
public class MixinMsgNewSpellPatternS2C {
    @Inject(method = "handle", at = @At("HEAD"), remap = false)
    private void forwardToCastScreen$ide(CallbackInfo ci) {
        var self = (MsgNewSpellPatternS2C) (Object) this;
        Minecraft.getInstance().execute(() -> {
            var s = Minecraft.getInstance().screen;
            if (s instanceof StaffCastScreen scs) {
                scs.onServerUpdate(self.info(), self.index());
            }
        });
    }
}
