package cn.xm1221.AlmightlyStaff.client;

import cn.xm1221.AlmightlyStaff.AlmightlyStaffConfig;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.gui.StaffCastScreen;
import cn.xm1221.AlmightlyStaff.gui.ide.StaffIdeScreen;
import cn.xm1221.AlmightlyStaff.gui.ide.host.EmbeddedScreenHost;
import cn.xm1221.AlmightlyStaff.items.ItemAlmightlyStaff;
import cn.xm1221.AlmightlyStaff.network.ModNetworking;
import cn.xm1221.AlmightlyStaff.gui.StaffLibScreen;
import cn.xm1221.MieLib.registry.SpellcastingClientEvents;
import net.minecraft.resources.ResourceLocation;
import at.petrak.hexcasting.api.item.MediaHolderItem;
import at.petrak.hexcasting.common.items.magic.ItemMediaBattery;
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

    public static final KeyMapping IDE_KEY = new KeyMapping(
        "key." + AlmightlyStaffMod.MOD_ID + ".ide",
        InputConstants.Type.KEYSYM,
        org.lwjgl.glfw.GLFW.GLFW_KEY_B,
        CATEGORY
    );

    public static void init() {
        KeyMappingRegistry.register(MODE_KEY);
        KeyMappingRegistry.register(IDE_KEY);

        SpellcastingClientEvents.registerServerUpdatePost(
            new ResourceLocation(AlmightlyStaffMod.MOD_ID, "cast_screen_forward"),
            (info, index, screen) -> {
                if (screen instanceof StaffCastScreen scs) {
                    scs.onServerUpdate(info, index);
                } else if (screen instanceof StaffIdeScreen ide) {
                    ide.onSpellcastingServerUpdate(info, index);
                }
            }
        );

        // 注册 hexcasting:variant 属性，用于模型变体切换
        IClientXplatAbstractions.INSTANCE.registerItemProperty(
            AlmightlyStaffMod.ALL_IN_ONE,
            ItemFocus.VARIANT_PRED,
            (stack, level, holder, holderID) -> ((ItemAlmightlyStaff) stack.getItem()).getVariant(stack)
        );

        // 注册 hexcasting:media 属性（与 ItemMediaBattery 相同），用于游子之瓶按媒质满度切换贴图
        IClientXplatAbstractions.INSTANCE.registerItemProperty(
            AlmightlyStaffMod.HOME_BOTTLE,
            ItemMediaBattery.MEDIA_PREDICATE,
            (stack, level, holder, holderID) -> ((MediaHolderItem) stack.getItem()).getMediaFullness(stack)
        );

        ClientTickEvent.CLIENT_POST.register(instance -> {
            Minecraft mc = Minecraft.getInstance();
            EmbeddedScreenHost.tickRestoreIfNeeded(mc);
            if(mc.player == null) return;
            if (MODE_KEY.consumeClick()){
                ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgAlmightlyStaffModeC2S());
            }
            if (IDE_KEY.consumeClick() && !(mc.screen instanceof StaffLibScreen) && !(mc.screen instanceof StaffIdeScreen)){
                var stack = mc.player.getMainHandItem();
                if (stack.getItem() instanceof ItemAlmightlyStaff) {
                    mc.setScreen(AlmightlyStaffConfig.get().useIdeScreen ? new StaffIdeScreen() : new StaffLibScreen());
                }
            }
            // 处理滚轮累积
            ShiftScrollListener.clientTickEnd();
        });
    }
}
