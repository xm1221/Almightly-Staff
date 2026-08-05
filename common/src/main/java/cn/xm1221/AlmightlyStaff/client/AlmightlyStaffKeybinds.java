package cn.xm1221.AlmightlyStaff.client;

import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.gui.AlmightlyStaffIDEScreen;
import cn.xm1221.AlmightlyStaff.items.ItemAlmightlyStaff;
import cn.xm1221.AlmightlyStaff.network.ModNetworking;
import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientTickEvent;
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

    public static final KeyMapping IDE_KEY = new KeyMapping(
        "key." + AlmightlyStaffMod.MOD_ID + ".ide",
        InputConstants.Type.KEYSYM,
        org.lwjgl.glfw.GLFW.GLFW_KEY_B,
        CATEGORY
    );

    /** 仅注册按键映射本身（Fabric 用 KeyMappingRegistry；NeoForge 用 RegisterKeyMappingsEvent）。 */
    public static void registerBindings() {
        KeyMappingRegistry.register(MODE_KEY);
        KeyMappingRegistry.register(IDE_KEY);
    }

    /** 注册 tick 监听（按键点击处理 + 滚轮累积）。跨平台共用。 */
    public static void initTick() {
        ClientTickEvent.CLIENT_POST.register(instance -> {
            if (Minecraft.getInstance().player == null) return;
            if (MODE_KEY.consumeClick()) {
                ModNetworking.sendToServer(new ModNetworking.MsgAlmightlyStaffModeC2S());
            }
            if (IDE_KEY.consumeClick() && !(Minecraft.getInstance().screen instanceof AlmightlyStaffIDEScreen)) {
                var stack = Minecraft.getInstance().player.getMainHandItem();
                if (stack.getItem() instanceof ItemAlmightlyStaff) {
                    Minecraft.getInstance().setScreen(new AlmightlyStaffIDEScreen());
                }
            }
            // 处理滚轮累积
            ShiftScrollListener.clientTickEnd();
        });
    }
}
