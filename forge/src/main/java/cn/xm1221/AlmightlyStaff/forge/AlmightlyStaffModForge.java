package cn.xm1221.AlmightlyStaff.forge;

import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;

@Mod(AlmightlyStaffMod.MOD_ID)
public final class AlmightlyStaffModForge {
    public AlmightlyStaffModForge(FMLJavaModLoadingContext context) {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(AlmightlyStaffMod.MOD_ID, context.getModEventBus());
        // Run our common setup.
        context.getModEventBus().addListener((RegisterEvent event) -> {
            AlmightlyStaffMod.init();
        });
    }
}
