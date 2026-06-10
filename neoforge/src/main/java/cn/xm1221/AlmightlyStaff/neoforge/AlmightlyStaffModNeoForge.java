package cn.xm1221.AlmightlyStaff.neoforge;

import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.items.AlmightlyStaffItems;
import at.petrak.hexcasting.common.lib.HexCreativeTabs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

// Pattern: https://github.com/VazkiiMods/Botania/blob/1.21/Forge/src/main/java/vazkii/botania/forge/ForgeCommonInitializer.java
@Mod(AlmightlyStaffMod.MOD_ID)
public final class AlmightlyStaffModNeoForge {
    private static IEventBus MOD_BUS;

    public AlmightlyStaffModNeoForge(ModContainer modContainer) {
        MOD_BUS = modContainer.getEventBus();

        // Item registration (HexMod bind pattern)
        bind(Registries.ITEM, AlmightlyStaffMod::registerItems);

        // Client-side init (handles its own side-check via event types)
        MOD_BUS.register(AlmightlyStaffModNeoForgeClient.class);

        // Add to Hex Casting creative tab
        MOD_BUS.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey().equals(HexCreativeTabs.HEX_KEY)) {
                event.accept(AlmightlyStaffItems.getStaff());
            }
        });

        AlmightlyStaffMod.init();
    }

    @SuppressWarnings("unchecked")
    private static <T> void bind(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> registry,
                                  Consumer<BiConsumer<T, ResourceLocation>> source) {
        MOD_BUS.addListener((RegisterEvent event) -> {
            if (registry.equals(event.getRegistryKey())) {
                source.accept((t, rl) -> event.register((net.minecraft.resources.ResourceKey) registry, rl, () -> t));
            }
        });
    }
}
