package cn.xm1221.AlmightlyStaff.forge;

import at.petrak.hexcasting.forge.ForgeHexClientInitializer;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;

import static dev.architectury.platform.forge.EventBuses.getModEventBus;

@Mod(AlmightlyStaffMod.MOD_ID)
public final class AlmightlyStaffModForge {
    public AlmightlyStaffModForge(FMLJavaModLoadingContext context) {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(AlmightlyStaffMod.MOD_ID, context.getModEventBus());
        // Run our common setup.
        context.getModEventBus().addListener((RegisterEvent event) -> {
            AlmightlyStaffMod.init();
        });

        var modBus = getModEventBus(AlmightlyStaffMod.MOD_ID);
        var evBus = MinecraftForge.EVENT_BUS;

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> modBus.orElseThrow().register(AlmightlyStaffModForgeClient.class));

      modBus.orElseThrow().addListener((FMLCommonSetupEvent evt) ->
                evt.enqueueWork(() -> {
                    // Forge does not strictly require TreeGrowers to initialize during early game stages, unlike Fabric
                    // and Quilt.
                    // However, all launcher panic if the same resource is registered twice.  But do need blocks and
                    // items to be completely initialized.
                    // Explicitly calling here avoids potential confusion, or reliance on tricks that may fail under
                    // compiler optimization.

                }));

    }
}
