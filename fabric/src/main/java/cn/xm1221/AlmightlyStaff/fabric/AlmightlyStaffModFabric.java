package cn.xm1221.AlmightlyStaff.fabric;



import at.petrak.hexcasting.common.lib.HexCreativeTabs;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.items.AlmightlyStaffItems;
import cn.xm1221.AlmightlyStaff.recipe.AlmightlyStaffRecipes;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;


public final class AlmightlyStaffModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        AlmightlyStaffMod.init();
        AlmightlyStaffMod.FabricInit();
        // 封印万法之杖的合成配方（无序：未封存法杖 + 封存材料）
        AlmightlyStaffRecipes.register((serializer, id) ->
                Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id, serializer));
        // 与 hexcasting 自身相同的方式：遍历所有创造标签页，向 HEX 标签页加入本模组物品
        ItemGroupEvents.MODIFY_ENTRIES_ALL.register((tab, entries) -> {
            if (tab == HexCreativeTabs.HEX) {
                entries.accept(AlmightlyStaffItems.getStaff());
                entries.accept(AlmightlyStaffItems.getHomelessBottle());
            }
        });
    }
}
