package cn.xm1221.AlmightlyStaff.recipe;

import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

import java.util.function.BiConsumer;

public final class AlmightlyStaffRecipes {
    /** "almightly_staff:seal_staff"，数据文件在 data/almightly_staff/recipes/seal_staff.json。 */
    public static final SimpleCraftingRecipeSerializer<SealStaffRecipe> SEAL_STAFF =
        new SimpleCraftingRecipeSerializer<>(SealStaffRecipe::new);

    public static final ResourceLocation SEAL_STAFF_ID =
        ResourceLocation.tryBuild(AlmightlyStaffMod.MOD_ID, "seal_staff");

    private AlmightlyStaffRecipes() {
    }

    /** 由各平台在各自的注册时机调用（Fabric: BuiltInRegistries.RECIPE_SERIALIZER；Forge: RegisterEvent）。 */
    public static void register(BiConsumer<RecipeSerializer<?>, ResourceLocation> registrar) {
        registrar.accept(SEAL_STAFF, SEAL_STAFF_ID);
    }
}
