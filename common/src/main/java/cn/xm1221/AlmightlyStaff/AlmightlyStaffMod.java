package cn.xm1221.AlmightlyStaff;

import cn.xm1221.AlmightlyStaff.items.ItemAlmightlyStaff;
import cn.xm1221.AlmightlyStaff.network.ModNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

// Pattern: https://github.com/VazkiiMods/Botania/blob/1.21/Common/src/main/java/vazkii/botania/common/item/ModItems.java
public final class AlmightlyStaffMod {
    public static final String MOD_ID = "almightly_staff";

    public static void registerItems(BiConsumer<Item, ResourceLocation> r) {
        for (var e : ITEMS.entrySet()) {
            r.accept(e.getValue(), e.getKey());
        }
    }

    private static final Map<ResourceLocation, Item> ITEMS = new LinkedHashMap<>();

    public static final Item ALL_IN_ONE = make("all_in_one",
        new ItemAlmightlyStaff(new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(1)));

    public static void init() {
        AlmightlyStaffActions.init();
        ModNetworking.init();
    }

    private static <T extends Item> T make(String id, T item) {
        var old = ITEMS.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, id), item);
        if (old != null) {
            throw new IllegalArgumentException("Typo? Duplicate id " + id);
        }
        return item;
    }
}
