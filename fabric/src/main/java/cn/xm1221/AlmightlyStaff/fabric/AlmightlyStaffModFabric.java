package cn.xm1221.AlmightlyStaff.fabric;

import at.petrak.hexcasting.common.lib.HexCreativeTabs;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.items.AlmightlyStaffItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.registries.BuiltInRegistries;

public final class AlmightlyStaffModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        AlmightlyStaffMod.init();
        AlmightlyStaffMod.registerItems();

        if (BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(HexCreativeTabs.HEX).isPresent()) {
            ItemGroupEvents.modifyEntriesEvent(
                BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(HexCreativeTabs.HEX).get()
            ).register(entries -> {
                entries.accept(AlmightlyStaffItems.getStaff());
            });
        }
    }
}
