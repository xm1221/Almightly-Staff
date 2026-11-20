package cn.xm1221.AlmightlyStaff.recipe;

import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public final class AlmightlyStaffRecipes {
    private static final Map<ResourceLocation, RecipeSerializer<?>> SERIALIZERS = new LinkedHashMap<>();

    public static RecipeSerializer<SealStaffRecipe> SEAL_STAFF;

    public static void register(BiConsumer<RecipeSerializer<?>, ResourceLocation> r) {
        if (SEAL_STAFF == null) {
            SEAL_STAFF = make("seal_staff", new SimpleCraftingRecipeSerializer<>(SealStaffRecipe::new));
        }
        for (var e : SERIALIZERS.entrySet()) {
            r.accept(e.getValue(), e.getKey());
        }
    }

    private static <T extends RecipeSerializer<?>> T make(String id, T serializer) {
        var old = SERIALIZERS.put(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, id), serializer);
        if (old != null) {
            throw new IllegalArgumentException("Duplicate recipe serializer id " + id);
        }
        return serializer;
    }
}
