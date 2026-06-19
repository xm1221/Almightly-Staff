package cn.xm1221.AlmightlyStaff.client;

import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.network.ModNetworking;
import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

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
            if (Minecraft.getInstance().player == null) return;
            if (MODE_KEY.consumeClick()) {
                NetworkManager.sendToServer(new ModNetworking.MsgAlmightlyStaffModeC2S());
            }
            // 处理滚轮累积
            ShiftScrollListener.clientTickEnd();
        });
    }
}
