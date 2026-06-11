package cn.xm1221.AlmightlyStaff.client;

import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.items.ItemAlmightlyStaff;
import cn.xm1221.AlmightlyStaff.network.ModNetworking;
import at.petrak.hexcasting.common.items.storage.ItemFocus;
import at.petrak.hexcasting.xplat.IClientXplatAbstractions;
import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * 注册并处理 Almightly Staff 的按键绑定。
 * 默认 V 键切换）。
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

        // 注册 hexcasting:variant 属性，用于模型变体切换
        IClientXplatAbstractions.INSTANCE.registerItemProperty(
            AlmightlyStaffMod.ALL_IN_ONE,
            ItemFocus.VARIANT_PRED,
            (stack, level, holder, holderID) -> ((ItemAlmightlyStaff) stack.getItem()).getVariant(stack)
        );

        ClientTickEvent.CLIENT_POST.register(instance -> {
            if(Minecraft.getInstance().player == null) return;
            if (MODE_KEY.consumeClick()){
                ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgAlmightlyStaffModeC2S());
            }
            // 处理滚轮累积
            ShiftScrollListener.clientTickEnd();
        });
    }
}
