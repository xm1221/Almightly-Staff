package cn.xm1221.AlmightlyStaff.client;

import at.petrak.hexcasting.api.mod.HexConfig;
import cn.xm1221.AlmightlyStaff.items.ItemAlmightlyStaff;
import cn.xm1221.AlmightlyStaff.network.ModNetworking;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;

/**
 * 监听客户端滚轮事件，将 Shift+滚轮 转为翻页请求发服务端。
 */
// https://github.com/FallingColors/HexMod
public class ShiftScrollListener {
    private static double mainHandDelta = 0;
    private static double offHandDelta = 0;

    public static boolean onScrollInGameplay(double delta) {
        if (Minecraft.getInstance().screen != null) return false;
        return onScroll(delta, true);
    }

    public static boolean onScroll(double delta, boolean needsSneaking) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && (player.isShiftKeyDown() || !needsSneaking)) {
            if (player.isSpectator()) return false;
            if (IsScrollableItem(player.getMainHandItem().getItem())) {
                mainHandDelta += delta;
                return true;
            } else if (IsScrollableItem(player.getOffhandItem().getItem())) {
                offHandDelta += delta;
                return true;
            }
        }
        return false;
    }

    public static void clientTickEnd() {
        if (mainHandDelta != 0 || offHandDelta != 0) {
            boolean invert = HexConfig.client().invertSpellbookScrollDirection();
            NetworkManager.sendToServer(new ModNetworking.MsgShiftScrollC2S(
                mainHandDelta, offHandDelta, false, invert, invert));
            mainHandDelta = 0;
            offHandDelta = 0;
        }
    }

    private static boolean IsScrollableItem(Item item) {
        return item instanceof ItemAlmightlyStaff;
    }
}
