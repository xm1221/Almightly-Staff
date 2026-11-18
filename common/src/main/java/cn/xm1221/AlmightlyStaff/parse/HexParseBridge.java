package cn.xm1221.AlmightlyStaff.parse;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import io.yukkuric.hexparse.hooks.GreatPatternUnlocker;
import io.yukkuric.hexparse.hooks.PatternMapper;
import io.yukkuric.hexparse.parsers.ParserMain;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Isolates all runtime references to {@code io.yukkuric.hexparse.*} so common classes
 * can load safely when the optional {@code hexparse} mod is absent.
 * Callers must check {@code Platform.isModLoaded("hexparse")} before referencing this class.
 */
public final class HexParseBridge {
    private HexParseBridge() {
    }

    public record GreatSpellResult(boolean usable, String signature, String startDir) {
    }

    public static String parseIotaNbt(CompoundTag listNbt, ServerPlayer player) {
        return ParserMain.ParseIotaNbt(listNbt, player, x -> x);
    }

    public static Iota parseCodeToIota(String code, ServerPlayer player) {
        CompoundTag tag = ParserMain.ParseCode(code, player);
        return IotaType.deserialize(tag, player.serverLevel());
    }

    public static GreatSpellResult checkGreatSpell(ServerLevel level, String actionId) {
        PatternMapper.init(level);
        CompoundTag tag = PatternMapper.mapPatternWorld.get(actionId);
        if (tag != null) {
            Iota iota = IotaType.deserialize(tag, level);
            if (iota instanceof PatternIota pi) {
                boolean unlocked = GreatPatternUnlocker.get(level).isUnlocked(actionId);
                if (unlocked) {
                    return new GreatSpellResult(
                        true,
                        pi.getPattern().anglesSignature(),
                        pi.getPattern().getStartDir().name()
                    );
                }
            }
        }
        return new GreatSpellResult(false, "", "");
    }
}
