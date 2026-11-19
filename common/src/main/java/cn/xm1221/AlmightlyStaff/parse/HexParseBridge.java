package cn.xm1221.AlmightlyStaff.parse;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import io.yukkuric.hexparse.hooks.GreatPatternUnlocker;
import io.yukkuric.hexparse.hooks.PatternMapper;
import io.yukkuric.hexparse.parsers.CodeCutter;
import io.yukkuric.hexparse.parsers.ParserMain;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

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

    public record RawParseResult(boolean ok, List<CompoundTag> tags, String error, int errorLine) {
    }

    public static String parseIotaNbt(CompoundTag listNbt, ServerPlayer player) {
        return ParserMain.ParseIotaNbt(listNbt, player, x -> x);
    }

    public static Iota parseCodeToIota(String code, ServerPlayer player) {
        CompoundTag tag = ParserMain.ParseCode(code, player);
        return IotaType.deserialize(tag, player.serverLevel());
    }

    /**
     * Parses HexParse source code on the server and returns raw element CompoundTags
     * (never deserializing entities on the client).
     */
    public static RawParseResult parseCodeToRawTags(String code, ServerPlayer player) {
        try {
            List<String> tokens = CodeCutter.splitCode(code == null ? "" : code);
            CompoundTag listTag = ParserMain.ParseCode(tokens, player);
            if (listTag == null) {
                return new RawParseResult(false, List.of(), "Empty parse result", 1);
            }
            IotaType.deserialize(listTag, player.serverLevel());
            List<CompoundTag> out = new ArrayList<>();
            Tag data = listTag.get("hexcasting:data");
            if (data instanceof ListTag items) {
                for (Tag el : items) {
                    if (el instanceof CompoundTag ct) {
                        out.add(ct.copy());
                    }
                }
            } else {
                out.add(listTag.copy());
            }
            return new RawParseResult(true, out, "", -1);
        } catch (Throwable t) {
            String msg = t.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = t.getClass().getSimpleName();
            }
            return new RawParseResult(false, List.of(), msg, 1);
        }
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
