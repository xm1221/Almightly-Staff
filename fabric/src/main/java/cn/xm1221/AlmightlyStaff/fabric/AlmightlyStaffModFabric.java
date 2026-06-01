package cn.xm1221.AlmightlyStaff.fabric;

import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import net.fabricmc.api.ModInitializer;



public final class AlmightlyStaffModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.
        AlmightlyStaffMod.init();
    }
}
