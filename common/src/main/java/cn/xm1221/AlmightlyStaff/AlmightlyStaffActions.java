package cn.xm1221.AlmightlyStaff;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.common.lib.hex.HexActions;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

public class AlmightlyStaffActions {

        /**
         * 注册一个图案动作。
         *
         * @param id        id
         * @param angles    笔顺角度序列，如 "qaq"
         * @param startDir  起始方向，如 HexDir.NORTH_EAST
         * @param action    动作实例
         */
        private static void register(String id, String angles, HexDir startDir, Action action) {
            ResourceLocation nsid = ResourceLocation.tryBuild(AlmightlyStaffMod.MOD_ID, id);
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null) {
                throw new IllegalArgumentException("无效的动作 ID: " + id);
            }
            ActionRegistryEntry entry = new ActionRegistryEntry(HexPattern.fromAngles(angles, startDir), action);
            Registry.register(
                    HexActions.REGISTRY,
                    nsid,
                    entry
            );
            System.out.println("Registered action: " + key.toString());
        }

        public static void init(){


        }

}
