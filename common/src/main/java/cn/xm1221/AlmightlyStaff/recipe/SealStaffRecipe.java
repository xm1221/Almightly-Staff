package cn.xm1221.AlmightlyStaff.recipe;

import at.petrak.hexcasting.api.item.IotaHolderItem;
import at.petrak.hexcasting.api.mod.HexTags;
import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 封印万法之杖。写法照搬 hexcasting 的 SealThingsRecipe（就是 "hexcasting:seal_spellbook" 实际用的那个）：
 * 无序合成，一份未封存的万法之杖 + 一份封存材料（{@code hexcasting:seal_materials} 标签，默认只有蜜脾），
 * 其它格子必须为空；生产物直接沿用原料的 NBT（页码、图案、媒质、颜色），只把当前页标记为已封存。
 * <p>
 * 没有沿用旧版 SealSpellbookRecipe 的 ShapelessRecipe + getUnsealedIngredient 写法：
 * 那是 0.11.4 里的遗留文件，且 Fabric 端的 FabricUnsealedIngredient#test 恒返回 false，
 * 在 Fabric 上匹配不到任何物品。
 */
public class SealStaffRecipe extends CustomRecipe {
    public SealStaffRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        boolean foundSealee = false;
        boolean foundMaterial = false;

        for (int i = 0; i < container.getContainerSize(); i++) {
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
                // 除了法杖和封存材料，不允许有别的物品
                return false;
            }
        }

        return foundSealee && foundMaterial;
    }

    @Override
    public @NotNull ItemStack assemble(CraftingContainer inv, RegistryAccess access) {
        ItemStack out = ItemStack.EMPTY;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (isSealableStaff(stack)) {
                out = stack.copy();
                break;
            }
        }

        if (!out.isEmpty()) {
            ItemSpellbook.setSealed(out, true);
            out.setCount(1);
        }

        return out;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return AlmightlyStaffRecipes.SEAL_STAFF;
    }

    /** 与 SealThingsRecipe.Sealee#isCorrectSealee 同判定：是本模组的法杖、当前页有 Iota、且当前页尚未封存。 */
    private static boolean isSealableStaff(ItemStack stack) {
        if (!stack.is(AlmightlyStaffMod.ALL_IN_ONE)) {
            return false;
        }
        if (ItemSpellbook.isSealed(stack)) {
            return false;
        }
        return stack.getItem() instanceof IotaHolderItem holder && holder.readIotaTag(stack) != null;
    }
}
