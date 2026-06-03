package cn.xm1221.AlmightlyStaff.client;

import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.items.ItemAlmightlyStaff;
import cn.xm1221.AlmightlyStaff.network.ModNetworking;
import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * 注册并处理 Almightly Staff 的按键绑定。
 * 默认 V 键切换法杖的模式（mode）。
 */
@Environment(EnvType.CLIENT)
public class AlmightlyStaffKeybinds {
    public static final String CATEGORY = "key.categories." + AlmightlyStaffMod.MOD_ID;

    public static final KeyMapping MODE_KEY = new KeyMapping(
        "key." + AlmightlyStaffMod.MOD_ID + ".mode",
        InputConstants.Type.KEYSYM,
        org.lwjgl.glfw.GLFW.GLFW_KEY_V,
        CATEGORY
    );

    public static void init() {
        KeyMappingRegistry.register(MODE_KEY);

        ClientTickEvent.CLIENT_POST.register(instance -> {
            // 处理按键：按下 V 切换模式
            while (MODE_KEY.consumeClick()) {
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    var stack = player.getMainHandItem();
                    if (stack.getItem() instanceof ItemAlmightlyStaff) {
                        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgAlmightlyStaffModeC2S());
                    }
                }
            }

            // 处理滚轮累积
            ShiftScrollListener.clientTickEnd();
        });
    }
}
