package cn.xm1221.AlmightlyStaff.forge;


import cn.xm1221.AlmightlyStaff.client.AlmightlyStaffKeybinds;
import cn.xm1221.AlmightlyStaff.client.ShiftScrollListener;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class AlmightlyStaffModForgeClient {
    @SubscribeEvent
    public static void clientInit(FMLClientSetupEvent evt) {
        evt.enqueueWork(() -> {
            // 注册按键绑定和 client tick（V 键切换模式 + 滚轮数据发送）
            AlmightlyStaffKeybinds.init();
        });

        var evBus = MinecraftForge.EVENT_BUS;

        // 注册鼠标滚轮事件（捕获 Shift+滚轮 翻页）
        evBus.addListener((InputEvent.MouseScrollingEvent e) -> {
            var cancel = ShiftScrollListener.onScrollInGameplay(e.getScrollDelta());
            e.setCanceled(cancel);
        });
    }
}
