package cn.xm1221.AlmightlyStaff.mixin;

import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import cn.xm1221.AlmightlyStaff.gui.StaffCastScreen;
import cn.xm1221.AlmightlyStaff.gui.StaffDrawScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 模糊根因（1.21.1 特有）：包装屏内嵌渲染时调用 spellcasting.render()
 * -> GuiSpellcasting.render -> super.render()(Screen.render)
 * -> this.renderBackground()（虚调用，this 是 GuiSpellcasting 实例，
 *    未覆写 -> Screen 原始实现）-> renderBlurredBackground()
 * -> 全屏模糊后处理，覆盖整个界面。
 *
 * renderBackground 声明在 Screen（可注入），当被渲染的 Screen 是内嵌的
 * GuiSpellcasting 且当前主屏幕是我们的包装屏（StaffCastScreen/StaffDrawScreen）
 * 时跳过背景（含模糊），避免与包装屏自身的背景重复叠加。
 */
@Mixin(Screen.class)
public class MixinScreen {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, remap = false)
    private void almightly$skipBackgroundInIde(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        Screen current = Minecraft.getInstance().screen;
        if (self instanceof GuiSpellcasting
            && (current instanceof StaffCastScreen || current instanceof StaffDrawScreen)) {
            ci.cancel(); // 内嵌网格：跳过背景（含 renderBlurredBackground 全屏模糊）
        }
    }
}
