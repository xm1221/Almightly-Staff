package cn.xm1221.AlmightlyStaff.mixin;

import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import cn.xm1221.AlmightlyStaff.gui.AlmightlyStaffIDEScreen;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 拦截 Screen.render() 里的 renderBackground 调用。
 * GuiSpellcasting 内嵌到 IDE 时，其 render() 经 super.render() 触发 renderBackground
 * -> renderBlurredBackground()（全屏模糊后处理），这里在 IDE 打开时跳过。
 */
@Mixin(Screen.class)
public class MixinScreen {
    @WrapWithCondition(
        method = "render",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")
    )
    private boolean almightly$skipBackgroundInIde(GuiGraphics g, int mx, int my, float pt) {
        Screen self = (Screen) (Object) this;
        // 仅当：当前屏幕是 IDE，且被渲染的 Screen 是 GuiSpellcasting（内嵌网格）时跳过背景
        if (self instanceof GuiSpellcasting && Minecraft.getInstance().screen instanceof AlmightlyStaffIDEScreen) {
            return false;
        }
        return true;
    }
}
