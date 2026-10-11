package cn.xm1221.AlmightlyStaff.fabric.client;

import at.petrak.hexcasting.api.item.VariantItem;
import at.petrak.hexcasting.xplat.IClientXplatAbstractions;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.client.AlmightlyStaffKeybinds;
import cn.xm1221.AlmightlyStaff.client.ShiftScrollListener;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientRawInputEvent;
import net.fabricmc.api.ClientModInitializer;

import static at.petrak.hexcasting.api.HexAPI.modLoc;

public final class AlmightlyStaffModFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 注册 hexcasting:variant 属性 → 驱动模型覆盖
        IClientXplatAbstractions.INSTANCE.registerItemProperty(
            AlmightlyStaffMod.ALL_IN_ONE, modLoc("variant"),
            (stack, level, holder, holderID) -> {
                if (stack.getItem() instanceof VariantItem vi) return vi.getVariant(stack);
                return 0;
            });

        // HexParse/Hex 1.21 已去掉 fabric.event.MouseScrollCallback，改用 Architectury 原始滚轮事件
        ClientRawInputEvent.MOUSE_SCROLLED.register((client, amountX, amountY) -> {
            if (ShiftScrollListener.onScrollInGameplay(amountY)) {
                return EventResult.interruptTrue();
            }
            return EventResult.pass();
        });

        // 注册按键绑定和 client tick（V 键切换模式 + 滚轮数据发送）
        AlmightlyStaffKeybinds.registerBindings();
        AlmightlyStaffKeybinds.initTick();
    }
}
