package cn.xm1221.AlmightlyStaff;

import cn.xm1221.AlmightlyStaff.items.ItemAlmightlyStaff;
import cn.xm1221.AlmightlyStaff.network.ModNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class AlmightlyStaffMod {
    public static final String MOD_ID = "almightly_staff";

    public static final Item ALL_IN_ONE = new ItemAlmightlyStaff(new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(1));

    public static void init() {
        // Write common init code here.
        AlmightlyStaffActions.init();
        ModNetworking.init();

    }
    public static void FabricInit() {
        Registry.register(BuiltInRegistries.ITEM, ResourceLocation.tryBuild(AlmightlyStaffMod.MOD_ID,"all_in_one"),AlmightlyStaffMod.ALL_IN_ONE);
    }
}
