package cn.xm1221.AlmightlyStaff.fabric;

import at.petrak.hexcasting.common.lib.HexCreativeTabs;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.items.AlmightlyStaffItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class AlmightlyStaffModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        AlmightlyStaffMod.init();
        AlmightlyStaffMod.registerItems((item, id) -> Registry.register(BuiltInRegistries.ITEM, id, item));

        // Add to Hex Casting creative tab
        ItemGroupEvents.modifyEntriesEvent(HexCreativeTabs.HEX_KEY).register(entries ->
            entries.accept(AlmightlyStaffItems.getStaff()));
    }
}
