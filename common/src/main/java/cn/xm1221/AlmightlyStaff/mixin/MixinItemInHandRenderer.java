package cn.xm1221.AlmightlyStaff.mixin;

import cn.xm1221.AlmightlyStaff.items.ItemAlmightlyStaff;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 取消「手持法杖时媒质自然恢复导致的缩手（换手）动画」。
 *
 * <p>根因链路：
 * <ol>
 *   <li>{@link ItemAlmightlyStaff#inventoryTick} 每 13 tick 给法杖补 1 dust 媒质，
 *       于是物品栈的 CUSTOM_DATA 变了；</li>
 *   <li>服务端 AbstractContainerMenu#broadcastChanges 发现槽位值变了，
 *       把该槽位重新同步给客户端，客户端手里就换成了一个新的 ItemStack 实例；</li>
 *   <li>ItemInHandRenderer#tick 判定「手里的东西换了」
 *       （NeoForge 走 ItemExtension#shouldCauseReequipAnimation，默认实现是
 *       !oldStack.equals(newStack)，而 ItemStack 没有覆写 equals ⇒ 引用比较；
 *       Fabric/原版则是 this.mainHandItem != itemstack），
 *       于是把 mainHandHeight 压回 0，第一人称手部就会往下缩一下再抬回来——
 *       每 13 tick 抖一次，就是看到的「缩手动画」。</li>
 * </ol>
 *
 * <p>处理：在 tick 开头，如果「记住的那份」和当前手里的是同一把法杖
 * （同一个物品，只是媒质数变了），就先把记住的那份换成当前实例，
 * 后面那套比较自然判定为「没换手」，动画不再触发。
 * 换成别的物品时物品类型不同，换手动画照常播放，不影响正常手感。
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer {

    @Shadow private ItemStack mainHandItem;
    @Shadow private ItemStack offHandItem;

    @Inject(method = "tick", at = @At("HEAD"))
    private void almightly$ignoreStaffMediaRecharge(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        this.mainHandItem = almightly$sameStaff(this.mainHandItem, player.getMainHandItem());
        this.offHandItem = almightly$sameStaff(this.offHandItem, player.getOffhandItem());
    }

    /** 还是同一把法杖时返回当前实例（等价于「没换手」），否则原样返回。 */
    @Unique
    private static ItemStack almightly$sameStaff(ItemStack remembered, ItemStack current) {
        if (remembered.getItem() instanceof ItemAlmightlyStaff
                && current.getItem() == remembered.getItem()) {
            return current;
        }
        return remembered;
    }
}
