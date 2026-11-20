package cn.xm1221.AlmightlyStaff.mixin;

import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import cn.xm1221.AlmightlyStaff.gui.StaffCastScreen;
import cn.xm1221.AlmightlyStaff.gui.StaffDrawScreen;
import cn.xm1221.AlmightlyStaff.gui.ide.StaffIdeScreen;
import cn.xm1221.AlmightlyStaff.gui.ide.host.EmbeddedScreenHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1 专用背景与全屏模糊拦截：
 * 1. {@link EmbeddedScreenHost#isRendering()} 期间嵌入渲染外部 Screen（如 Patchouli）时跳过其自带背景与模糊；
 * 2. 当被渲染的 Screen 是内嵌的 {@link GuiSpellcasting} 且当前主屏幕是我们的包装屏或 IDE 时跳过背景（含模糊）。
 */
@Mixin(Screen.class)
public class MixinScreen {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, remap = false)
    private void almightly$skipBackgroundInIde(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        if (EmbeddedScreenHost.isRendering()) {
            ci.cancel();
            return;
        }
        Screen self = (Screen) (Object) this;
        Screen current = Minecraft.getInstance().screen;
        if (self instanceof GuiSpellcasting
            && (current instanceof StaffCastScreen || current instanceof StaffDrawScreen || current instanceof StaffIdeScreen)) {
            ci.cancel(); // 内嵌网格：跳过背景（含 renderBlurredBackground 全屏模糊）
        }
    }
}
