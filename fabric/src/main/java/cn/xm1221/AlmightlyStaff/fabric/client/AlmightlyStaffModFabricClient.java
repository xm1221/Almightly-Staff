package cn.xm1221.AlmightlyStaff.fabric.client;

import at.petrak.hexcasting.fabric.event.MouseScrollCallback;
import at.petrak.hexcasting.fabric.network.FabricPacketHandler;
import cn.xm1221.AlmightlyStaff.client.AlmightlyStaffKeybinds;
import cn.xm1221.AlmightlyStaff.client.ShiftScrollListener;
import net.fabricmc.api.ClientModInitializer;

public final class AlmightlyStaffModFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // This entrypoint is suitable for setting up client-specific logic, such as rendering.
        FabricPacketHandler.initClient();

        // 注册鼠标滚轮事件（捕获 Shift+滚轮 翻页）
        MouseScrollCallback.EVENT.register(ShiftScrollListener::onScrollInGameplay);

        // 注册按键绑定和 client tick（V 键切换模式 + 滚轮数据发送）
        AlmightlyStaffKeybinds.init();
    }
}
