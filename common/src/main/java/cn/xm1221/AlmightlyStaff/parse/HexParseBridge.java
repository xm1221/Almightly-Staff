package cn.xm1221.AlmightlyStaff.parse;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import io.yukkuric.hexparse.hooks.GreatPatternUnlocker;
import io.yukkuric.hexparse.hooks.PatternMapper;
import io.yukkuric.hexparse.misc.StringProcessors;
import io.yukkuric.hexparse.parsers.CodeCutter;
import io.yukkuric.hexparse.parsers.ParserMain;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
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

    private static CompoundTag serializeIota(Iota iota) {
        if (iota == null) return null;
        try {
            var t = IotaType.TYPED_CODEC.encodeStart(NbtOps.INSTANCE, iota).result().orElse(null);
            return t instanceof CompoundTag ct ? ct : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Iota deserializeIota(CompoundTag tag) {
        if (tag == null) return null;
        try {
            return IotaType.TYPED_CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Converts a serialized ListIota CompoundTag (or a list of element tags under "hexcasting:data")
     * into HexParse code string using 1.21 HexParse API.
     */
    public static String parseIotaNbt(CompoundTag listNbt, ServerPlayer player) {
        if (listNbt == null) return "";
        Iota iota = deserializeIota(listNbt);
        if (iota == null && listNbt.contains("hexcasting:data", Tag.TAG_LIST)) {
            ListTag items = listNbt.getList("hexcasting:data", Tag.TAG_COMPOUND);
            List<Iota> list = new ArrayList<>(items.size());
            for (Tag el : items) {
                if (el instanceof CompoundTag ct) {
                    Iota parsed = deserializeIota(ct);
                    if (parsed != null) list.add(parsed);
                }
            }
            iota = new ListIota(list);
        }
        if (iota == null) return "";
        String code = ParserMain.ParseIotaNbt(iota, player, StringProcessors.READ_DEFAULT);
        return code != null ? code : "";
    }

    public static String parseIotaTags(List<CompoundTag> tags, ServerPlayer player) {
        List<Iota> list = new ArrayList<>();
        if (tags != null) {
            for (CompoundTag ct : tags) {
                Iota parsed = deserializeIota(ct);
                if (parsed != null) list.add(parsed);
            }
        }
        String code = ParserMain.ParseIotaNbt(new ListIota(list), player, StringProcessors.READ_DEFAULT);
        return code != null ? code : "";
    }

    public static Iota parseCodeToIota(String code, ServerPlayer player) {
        return ParserMain.ParseCode(code == null ? "" : code, player);
    }

    /**
     * Parses HexParse source code on the server and returns raw element CompoundTags
     * (never deserializing entities on the client).
     */
    public static RawParseResult parseCodeToRawTags(String code, ServerPlayer player) {
        try {
            List<String> tokens = CodeCutter.splitCode(code == null ? "" : code);
            Iota parsed = ParserMain.ParseCode(tokens, player);
            if (parsed == null) {
                return new RawParseResult(false, List.of(), "Empty parse result", 1);
            }
            List<CompoundTag> out = new ArrayList<>();
            if (parsed instanceof ListIota li) {
                for (Iota el : li.getList()) {
                    CompoundTag ct = serializeIota(el);
                    if (ct != null) {
                        out.add(ct);
                    }
                }
            } else {
                CompoundTag ct = serializeIota(parsed);
                if (ct != null) {
                    out.add(ct);
                }
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
        Iota iota = PatternMapper.mapPatternWorld.get(actionId);
        if (iota instanceof PatternIota pi) {
            boolean unlocked = GreatPatternUnlocker.get(level).isUnlocked(actionId);
            if (unlocked) {
                return new GreatSpellResult(
                    true,
                    pi.getPattern().getSignature().toAnglesString(),
                    pi.getPattern().getOrientation().name()
                );
            }
        }
        return new GreatSpellResult(false, "", "");
    }
}
