package cn.xm1221.AlmightlyStaff;

import cn.xm1221.AlmightlyStaff.items.ItemAlmightlyStaff;
import cn.xm1221.AlmightlyStaff.items.ItemHomelessBottle;
import cn.xm1221.AlmightlyStaff.network.ModNetworking;
import cn.xm1221.AlmightlyStaff.parse.AlmightlyStaffParse;
import dev.architectury.platform.Platform;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class AlmightlyStaffMod {
    public static final String MOD_ID = "almightly_staff";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(MOD_ID, Registries.ITEM);

    public static Item ALL_IN_ONE;
    public static Item HOME_BOTTLE;

    public static final RegistrySupplier<Item> ALL_IN_ONE_SUPPLIER = ITEMS.register("all_in_one", () -> {
        ALL_IN_ONE = new ItemAlmightlyStaff(new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(1));
        return ALL_IN_ONE;
    });

    public static final RegistrySupplier<Item> HOME_BOTTLE_SUPPLIER = ITEMS.register("homeless_bottle", () -> {
        HOME_BOTTLE = new ItemHomelessBottle(new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(1));
        return HOME_BOTTLE;
    });

    public static void init() {
        // 物品在 Forge 上通过 DeferredRegister 延迟到 RegisterEvent 再实例化，避免构造阶段触发 Registry is already frozen
        ITEMS.register();
        AlmightlyStaffConfig.load();
        AlmightlyStaffActions.init();
        ModNetworking.init();
        if (Platform.isModLoaded("hexparse")) {
            AlmightlyStaffParse.initParse();
        }
    }

    public static void FabricInit() {
    }
}
