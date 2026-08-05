package cn.xm1221.AlmightlyStaff.neoforge;

import at.petrak.hexcasting.api.item.VariantItem;
import at.petrak.hexcasting.xplat.IClientXplatAbstractions;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.client.AlmightlyStaffKeybinds;
import cn.xm1221.AlmightlyStaff.client.ShiftScrollListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

import static at.petrak.hexcasting.api.HexAPI.modLoc;

/**
 * NeoForge 客户端初始化：模型属性注册 + 鼠标滚轮翻页 + client tick + 按键注册。
 * 参照 HexMod 的 ForgeHexClientInitializer。
 */
public class AlmightlyStaffModNeoForgeClient {

    /** 按键必须在这里注册（RegisterKeyMappingsEvent 早于 FMLClientSetupEvent）。 */
    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(AlmightlyStaffKeybinds.MODE_KEY);
        event.register(AlmightlyStaffKeybinds.IDE_KEY);
    }

    @SubscribeEvent
    public static void clientInit(FMLClientSetupEvent evt) {
        evt.enqueueWork(() -> {
            // 注册 hexcasting:variant 属性 → 驱动模型覆盖
            IClientXplatAbstractions.INSTANCE.registerItemProperty(
                AlmightlyStaffMod.ALL_IN_ONE, modLoc("variant"),
                (stack, level, holder, holderID) -> {
                    if (stack.getItem() instanceof VariantItem vi) return vi.getVariant(stack);
                    return 0;
                });
        });

        var evBus = NeoForge.EVENT_BUS;

        // Client tick — 发送累积的滚轮数据
        evBus.addListener(ClientTickEvent.Post.class, e ->
            ShiftScrollListener.clientTickEnd());

        // 鼠标滚轮 — 捕获 Shift+滚轮 翻页
        evBus.addListener(InputEvent.MouseScrollingEvent.class, e -> {
            var cancel = ShiftScrollListener.onScrollInGameplay(e.getScrollDeltaY());
            e.setCanceled(cancel);
        });

        // 按键点击处理（按键映射已在上方 RegisterKeyMappingsEvent 注册）
        AlmightlyStaffKeybinds.initTick();
    }
}
