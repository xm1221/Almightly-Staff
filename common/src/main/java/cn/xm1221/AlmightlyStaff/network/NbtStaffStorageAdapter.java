package cn.xm1221.AlmightlyStaff.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 1.20.1 存储适配器实现：委托给纯 NBT 页存储 {@link StaffPages}。
 */
public final class NbtStaffStorageAdapter implements StaffStorageAdapter {
    public static final NbtStaffStorageAdapter INSTANCE = new NbtStaffStorageAdapter();

    private NbtStaffStorageAdapter() {
    }

    @Override
    public String ensureStaffUuid(ItemStack stack) {
        return StaffPages.ensureStaffUuid(stack);
    }

    @Override
    public List<Integer> indices(ItemStack stack) {
        return StaffPages.indices(stack);
    }

    @Override
    public boolean exists(ItemStack stack, int pageIndex) {
        return StaffPages.exists(stack, pageIndex);
    }

    @Override
    public String name(ItemStack stack, int pageIndex) {
        return StaffPages.name(stack, pageIndex);
    }

    @Override
    public List<CompoundTag> contentTags(ItemStack stack, int pageIndex) {
        return StaffPages.contentTags(stack, pageIndex);
    }

    @Override
    public boolean isSealed(ItemStack stack, int pageIndex) {
        return StaffPages.isSealed(stack, pageIndex);
    }

    @Override
    public int castPage(ItemStack stack) {
        return StaffPages.castPage(stack);
    }

    @Override
    public int lowestFree(ItemStack stack) {
        return StaffPages.lowestFree(stack);
    }

    @Override
    public int nearestPage(ItemStack stack, int removedIndex) {
        return StaffPages.nearestPage(stack, removedIndex);
    }

    @Override
    public StaffPages.Result writeContent(ItemStack stack, int pageIndex, List<CompoundTag> tags) {
        return StaffPages.writeContent(stack, pageIndex, tags);
    }

    @Override
    public StaffPages.Result create(ItemStack stack, int pageIndex) {
        return StaffPages.create(stack, pageIndex);
    }

    @Override
    public StaffPages.Result rename(ItemStack stack, int pageIndex, String newName) {
        return StaffPages.rename(stack, pageIndex, newName);
    }

    @Override
    public StaffPages.Result delete(ItemStack stack, int pageIndex) {
        return StaffPages.delete(stack, pageIndex);
    }

    @Override
    public StaffPages.Result duplicate(ItemStack stack, int sourceIndex, int targetIndex, String newName) {
        return StaffPages.duplicate(stack, sourceIndex, targetIndex, newName);
    }

    @Override
    public StaffPages.Reorder reorder(ItemStack stack, int fromIndex, int targetIndex, boolean after) {
        return StaffPages.reorder(stack, fromIndex, targetIndex, after);
    }

    @Override
    public void select(ItemStack stack, int pageIndex) {
        StaffPages.select(stack, pageIndex);
    }
}
