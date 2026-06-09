package cn.xm1221.AlmightlyStaff.neoforge;

import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(AlmightlyStaffMod.MOD_ID)
public final class AlmightlyStaffModNeoForge {
    public AlmightlyStaffModNeoForge(ModContainer modContainer) {
        IEventBus modBus = modContainer.getEventBus();
        modBus.addListener(RegisterEvent.class, event -> {
            AlmightlyStaffMod.init();
        });
    }
}
