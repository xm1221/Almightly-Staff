package cn.xm1221.AlmightlyStaff.recipe;

import at.petrak.hexcasting.api.item.IotaHolderItem;
import at.petrak.hexcasting.api.mod.HexTags;
import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.network.StaffPages;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 密封万法之杖的当前页（与咒术书的蜜脾封印行为一致，适配 Minecraft 1.21.1 CraftingInput API）。
 * <p>
 * 无序合成，一份未封存的万法之杖 + 一份封存材料（{@code hexcasting:seal_materials} 标签，默认只有蜜脾），
 * 其它格子必须为空；生产物直接沿用原料的组件与 NBT（页码、图案、媒质、颜色），只把当前页标记为已封存。
 */
public class SealStaffRecipe extends CustomRecipe {
    public SealStaffRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public boolean matches(CraftingInput container, Level level) {
        boolean foundSealee = false;
        boolean foundMaterial = false;

        for (int i = 0; i < container.size(); i++) {
            var stack = container.getItem(i);
            if (isSealableStaff(stack)) {
                if (foundSealee) {
                    return false;
                }
                foundSealee = true;
            } else if (stack.is(HexTags.Items.SEAL_MATERIALS)) {
                if (foundMaterial) {
                    return false;
                }
                foundMaterial = true;
            } else if (!stack.isEmpty()) {
                return false;
            }
        }

        return foundSealee && foundMaterial;
    }

    @Override
    public @NotNull ItemStack assemble(CraftingInput inv, HolderLookup.Provider access) {
        ItemStack out = ItemStack.EMPTY;

        for (int i = 0; i < inv.size(); i++) {
            var stack = inv.getItem(i);
            if (isSealableStaff(stack)) {
                out = stack.copy();
                break;
            }
        }

        if (!out.isEmpty()) {
            ItemSpellbook.setSealed(out, true);
            StaffPages.setSealed(out, StaffPages.castPage(out), true);
            out.setCount(1);
        }

        return out;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return AlmightlyStaffRecipes.SEAL_STAFF;
    }

    /** 是本模组的法杖、当前页有 Iota、且当前页尚未封存。 */
    private static boolean isSealableStaff(ItemStack stack) {
        if (!stack.is(AlmightlyStaffMod.ALL_IN_ONE)) {
            return false;
        }
        int cp = StaffPages.castPage(stack);
        if (ItemSpellbook.isSealed(stack) || StaffPages.isSealed(stack, cp)) {
            return false;
        }
        if (!StaffPages.contentTags(stack, cp).isEmpty()) {
            return true;
        }
        return stack.getItem() instanceof IotaHolderItem holder && holder.readIota(stack) != null;
    }
}
