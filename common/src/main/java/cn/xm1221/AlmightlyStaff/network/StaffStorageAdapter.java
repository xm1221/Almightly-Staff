package cn.xm1221.AlmightlyStaff.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 物品页存储平台抽象接口（1.20.1 基于 ItemStack NBT，1.21.1 可实现为 DataComponents）。
 */
public interface StaffStorageAdapter {
    String ensureStaffUuid(ItemStack stack);

    List<Integer> indices(ItemStack stack);

    boolean exists(ItemStack stack, int pageIndex);

    String name(ItemStack stack, int pageIndex);

    List<CompoundTag> contentTags(ItemStack stack, int pageIndex);

    boolean isSealed(ItemStack stack, int pageIndex);

    int castPage(ItemStack stack);

    int lowestFree(ItemStack stack);

    int nearestPage(ItemStack stack, int removedIndex);

    StaffPages.Result writeContent(ItemStack stack, int pageIndex, List<CompoundTag> tags);

    StaffPages.Result create(ItemStack stack, int pageIndex);

    StaffPages.Result rename(ItemStack stack, int pageIndex, String newName);

    StaffPages.Result delete(ItemStack stack, int pageIndex);

    StaffPages.Result duplicate(ItemStack stack, int sourceIndex, int targetIndex, String newName);

    StaffPages.Reorder reorder(ItemStack stack, int fromIndex, int targetIndex, boolean after);

    void select(ItemStack stack, int pageIndex);
}
