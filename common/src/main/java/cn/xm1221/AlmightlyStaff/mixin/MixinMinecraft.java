package cn.xm1221.AlmightlyStaff.mixin;

import cn.xm1221.AlmightlyStaff.gui.ide.host.EmbeddedScreenHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@link EmbeddedScreenHost} 分发事件期间接管子屏的 {@code setScreen} 调用，
 * 防止点击外部界面（如 Patchouli 书页超链接或按 ESC）直接把整个 IDE 替换关闭。
 * 目标为 MC 原版 Minecraft，1.20.1 严禁写 remap = false。
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void almightly$interceptEmbeddedSetScreen(@Nullable Screen next, CallbackInfo ci) {
        if (EmbeddedScreenHost.onInterceptSetScreen(next)) {
            ci.cancel();
        }
    }
}
