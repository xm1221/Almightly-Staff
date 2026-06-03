package cn.xm1221.AlmightlyStaff.client;

import at.petrak.hexcasting.api.mod.HexConfig;
import at.petrak.hexcasting.common.lib.HexItems;

import cn.xm1221.AlmightlyStaff.items.ItemAlmightlyStaff;
import cn.xm1221.AlmightlyStaff.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;

/**
 * 监听客户端滚轮事件，将 Shift+滚轮 操作转为滚轮翻页请求发送至服务端。
 * 支持 Spellbook、Abacus 以及 AlmightlyStaff。
 */
//https://github.com/FallingColors/HexMod
public class ShiftScrollListener {
    private static double mainHandDelta = 0;
    private static double offHandDelta = 0;

    public static boolean onScrollInGameplay(double delta) {
        if (Minecraft.getInstance().screen != null) {
            return false;
        }

        return onScroll(delta, true);
    }

    public static boolean onScroll(double delta, boolean needsSneaking) {
        LocalPlayer player = Minecraft.getInstance().player;
        // not .isCrouching! that fails for players who are not on the ground
        // yes, this does work if you remap your sneak key
        if (player != null && (player.isShiftKeyDown() || !needsSneaking)) {
            // Spectators shouldn't interact with items!
            if (player.isSpectator()) {
                return false;
            }

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
            var msg = new ModNetworking.MsgShiftScrollC2S(
                mainHandDelta, offHandDelta,
                Minecraft.getInstance().options.keySprint.isDown(),
                HexConfig.client().invertSpellbookScrollDirection(),
                HexConfig.client().invertAbacusScrollDirection()
            );
            ModNetworking.CHANNEL.sendToServer(msg);
            mainHandDelta = 0;
            offHandDelta = 0;
        }
    }

    private static boolean IsScrollableItem(Item item) {
        return item == HexItems.SPELLBOOK || item == HexItems.ABACUS
            || item instanceof ItemAlmightlyStaff;
    }
}
