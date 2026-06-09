package cn.xm1221.AlmightlyStaff.fabric;



import at.petrak.hexcasting.common.lib.HexCreativeTabs;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.items.AlmightlyStaffItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;


public final class AlmightlyStaffModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        AlmightlyStaffMod.init();
        AlmightlyStaffMod.FabricInit();
        if(BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(HexCreativeTabs.HEX).isPresent()) {
            ItemGroupEvents.modifyEntriesEvent(BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(HexCreativeTabs.HEX).get()).register(entries -> {
                entries.accept(AlmightlyStaffItems.getStaff());
                // ... 添加更多物品
            });
        }
    }
}
