package cn.xm1221.AlmightlyStaff.mixin;

import cn.xm1221.AlmightlyStaff.gui.ide.host.EmbeddedScreenHost;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 嵌入渲染外部 Screen 时跳过其自带的暗色全屏背景遮罩，避免重影。
 * 目标为 MC 原版 Screen，1.20.1 严禁写 remap = false。
 */
@Mixin(Screen.class)
public abstract class MixinScreen {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void almightly$skipEmbeddedBackground(GuiGraphics g, CallbackInfo ci) {
        if (EmbeddedScreenHost.isRendering()) {
            ci.cancel();
        }
    }
}
