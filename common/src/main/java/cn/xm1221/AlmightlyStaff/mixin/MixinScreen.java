package cn.xm1221.AlmightlyStaff.mixin;

import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import cn.xm1221.AlmightlyStaff.gui.AlmightlyStaffIDEScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 模糊根因：IDE 内嵌渲染时调用 spellcasting.render()
 * -> GuiSpellcasting.render -> super.render()(Screen.render)
 * -> this.renderBackground()（虚调用，this 是 GuiSpellcasting 实例，
 *    未覆写 -> Screen 原始实现）-> renderBlurredBackground()
 * -> 全屏模糊后处理，覆盖整个 IDE。
 *
 * renderBackground 声明在 Screen（可注入），IDE 打开且渲染内嵌网格时跳过。
 */
@Mixin(Screen.class)
public class MixinScreen {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, remap = false)
    private void almightly$skipBackgroundInIde(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (self instanceof GuiSpellcasting && Minecraft.getInstance().screen instanceof AlmightlyStaffIDEScreen) {
            ci.cancel(); // IDE 内嵌网格：跳过背景（含 renderBlurredBackground 全屏模糊）
        }
    }
}
