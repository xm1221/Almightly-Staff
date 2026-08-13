package cn.xm1221.AlmightlyStaff.network;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.common.lib.HexDataComponents;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.gui.StaffLibScreen;
import cn.xm1221.AlmightlyStaff.gui.StaffParseScreen;
import cn.xm1221.AlmightlyStaff.items.ItemAlmightlyStaff;
import dev.architectury.networking.NetworkManager;
import io.yukkuric.hexparse.hooks.GreatPatternUnlocker;
import io.yukkuric.hexparse.hooks.PatternMapper;
import io.yukkuric.hexparse.misc.StringProcessors;
import io.yukkuric.hexparse.parsers.ParserMain;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModNetworking {

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgShiftScrollC2S.TYPE, MsgShiftScrollC2S.STREAM_CODEC, MsgShiftScrollC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgAlmightlyStaffModeC2S.TYPE, MsgAlmightlyStaffModeC2S.STREAM_CODEC, MsgAlmightlyStaffModeC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffReadC2S.TYPE, MsgStaffReadC2S.STREAM_CODEC, MsgStaffReadC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffWriteC2S.TYPE, MsgStaffWriteC2S.STREAM_CODEC, MsgStaffWriteC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffCastC2S.TYPE, MsgStaffCastC2S.STREAM_CODEC, MsgStaffCastC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffPageC2S.TYPE, MsgStaffPageC2S.STREAM_CODEC, MsgStaffPageC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffEscapePatternC2S.TYPE, MsgStaffEscapePatternC2S.STREAM_CODEC, MsgStaffEscapePatternC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgParseToCodeC2S.TYPE, MsgParseToCodeC2S.STREAM_CODEC, MsgParseToCodeC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgParseToIotasC2S.TYPE, MsgParseToIotasC2S.STREAM_CODEC, MsgParseToIotasC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), MsgStaffReadS2C.TYPE, MsgStaffReadS2C.STREAM_CODEC, MsgStaffReadS2C::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), MsgStaffEscapeResultS2C.TYPE, MsgStaffEscapeResultS2C.STREAM_CODEC, MsgStaffEscapeResultS2C::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), MsgParseToCodeS2C.TYPE, MsgParseToCodeS2C.STREAM_CODEC, MsgParseToCodeS2C::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), MsgParseToIotasS2C.TYPE, MsgParseToIotasS2C.STREAM_CODEC, MsgParseToIotasS2C::handle);
        // ---- 法术库 ----
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffLibReadC2S.TYPE, MsgStaffLibReadC2S.STREAM_CODEC, MsgStaffLibReadC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), MsgStaffLibSyncS2C.TYPE, MsgStaffLibSyncS2C.STREAM_CODEC, MsgStaffLibSyncS2C::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffPageRenameC2S.TYPE, MsgStaffPageRenameC2S.STREAM_CODEC, MsgStaffPageRenameC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffPageSetIotaC2S.TYPE, MsgStaffPageSetIotaC2S.STREAM_CODEC, MsgStaffPageSetIotaC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffPageAppendIotaC2S.TYPE, MsgStaffPageAppendIotaC2S.STREAM_CODEC, MsgStaffPageAppendIotaC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffPageSwapC2S.TYPE, MsgStaffPageSwapC2S.STREAM_CODEC, MsgStaffPageSwapC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffPageSelectC2S.TYPE, MsgStaffPageSelectC2S.STREAM_CODEC, MsgStaffPageSelectC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffPageWriteC2S.TYPE, MsgStaffPageWriteC2S.STREAM_CODEC, MsgStaffPageWriteC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffGreatSpellCheckC2S.TYPE, MsgStaffGreatSpellCheckC2S.STREAM_CODEC, MsgStaffGreatSpellCheckC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), MsgStaffGreatSpellCheckS2C.TYPE, MsgStaffGreatSpellCheckS2C.STREAM_CODEC, MsgStaffGreatSpellCheckS2C::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffCastClearStackC2S.TYPE, MsgStaffCastClearStackC2S.STREAM_CODEC, MsgStaffCastClearStackC2S::handle);
    }

    public static void sendToServer(CustomPacketPayload p) { NetworkManager.sendToServer(p); }
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload p) { NetworkManager.sendToPlayer(player, p); }

    // ---------- Iota <-> NBT helpers ----------
    private static CompoundTag serializeIota(Iota i) {
        try {
            var t = IotaType.TYPED_CODEC.encodeStart(NbtOps.INSTANCE, i).result().orElse(null);
            return t instanceof CompoundTag ct ? ct : null;
        } catch (Exception e) { return null; }
    }
    private static Iota deserializeIota(CompoundTag tag, ServerLevel level) {
        try { return IotaType.TYPED_CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null); }
        catch (Exception e) { return null; }
    }

    // nullable Iota stream codec
    private static void writeIota(RegistryFriendlyByteBuf buf, Iota iota) {
        buf.writeBoolean(iota != null);
        if (iota != null) IotaType.TYPED_STREAM_CODEC.encode(buf, iota);
    }
    private static Iota readIota(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        try { return IotaType.TYPED_STREAM_CODEC.decode(buf); } catch (Exception e) { return null; }
    }

    // ==================== 滚轮翻页 ====================
    public static class MsgShiftScrollC2S implements CustomPacketPayload {
        public static final Type<MsgShiftScrollC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "scroll"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgShiftScrollC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeDouble(msg.mainHandDelta); buf.writeDouble(msg.offHandDelta); buf.writeBoolean(msg.isCtrl); buf.writeBoolean(msg.invertSpellbook); buf.writeBoolean(msg.invertAbacus); },
                buf -> new MsgShiftScrollC2S(buf.readDouble(), buf.readDouble(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
        final double mainHandDelta, offHandDelta;
        final boolean isCtrl, invertSpellbook, invertAbacus;
        public MsgShiftScrollC2S(double m, double o, boolean c, boolean is, boolean ia) { mainHandDelta = m; offHandDelta = o; isCtrl = c; invertSpellbook = is; invertAbacus = ia; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgShiftScrollC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> { hfh(sp, InteractionHand.MAIN_HAND, msg.mainHandDelta, msg.invertSpellbook); hfh(sp, InteractionHand.OFF_HAND, msg.offHandDelta, msg.invertSpellbook); });
        }
        private static void hfh(ServerPlayer sender, InteractionHand hand, double delta, boolean invert) {
            if (delta == 0) return; var stack = sender.getItemInHand(hand); if (!(stack.getItem() instanceof ItemAlmightlyStaff)) return;
            if (invert) delta = -delta;
            var ni = ItemAlmightlyStaff.rotatePageIdx(stack, delta < 0.0, sender.level()); var len = ItemAlmightlyStaff.highestPage(stack); var sealed = ItemAlmightlyStaff.isSealed(stack);
            MutableComponent c; var iC = Component.literal(String.valueOf(ni)).withStyle(ChatFormatting.WHITE); var lC = Component.literal(String.valueOf(len)).withStyle(ChatFormatting.WHITE);
            if (hand == InteractionHand.OFF_HAND && stack.has(DataComponents.CUSTOM_NAME)) {
                var rc = stack.getHoverName().getStyle().getColor();
                c = sealed ? Component.translatable("hexcasting.tooltip.spellbook.page_with_name.sealed", iC, lC, Component.literal("").withStyle(st -> st.withItalic(true).withColor(rc)).append(stack.getHoverName()), Component.translatable("hexcasting.tooltip.spellbook.sealed").withStyle(ChatFormatting.GOLD))
                    : Component.translatable("hexcasting.tooltip.spellbook.page_with_name", iC, lC, Component.literal("").withStyle(st -> st.withItalic(true).withColor(rc)).append(stack.getHoverName()));
            } else c = sealed ? Component.translatable("hexcasting.tooltip.spellbook.page.sealed", iC, lC, Component.translatable("hexcasting.tooltip.spellbook.sealed").withStyle(ChatFormatting.GOLD))
                : Component.translatable("hexcasting.tooltip.spellbook.page", iC, lC);
            sender.displayClientMessage(c.withStyle(ChatFormatting.GRAY), true);
        }
    }

    // ==================== 模式切换 ====================
    public static class MsgAlmightlyStaffModeC2S implements CustomPacketPayload {
        public static final Type<MsgAlmightlyStaffModeC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "mode"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgAlmightlyStaffModeC2S> STREAM_CODEC = StreamCodec.of((buf, msg) -> {}, buf -> new MsgAlmightlyStaffModeC2S());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgAlmightlyStaffModeC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> { var item = sp.getMainHandItem().getItem(); if (item instanceof ItemAlmightlyStaff stf) stf.casting(sp.level(), sp, InteractionHand.MAIN_HAND); });
        }
    }

    // ==================== 页读取 ====================
    public static class MsgStaffReadC2S implements CustomPacketPayload {
        public static final Type<MsgStaffReadC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "staff_read"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffReadC2S> STREAM_CODEC = StreamCodec.of((buf, msg) -> {}, buf -> new MsgStaffReadC2S());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffReadC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> { var st = sp.getMainHandItem(); if (st.getItem() instanceof ItemAlmightlyStaff it) sendToPlayer(sp, new MsgStaffReadS2C(it.readIota(st))); });
        }
    }
    public static class MsgStaffReadS2C implements CustomPacketPayload {
        public static final Type<MsgStaffReadS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "staff_read_s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffReadS2C> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> ModNetworking.writeIota(buf, msg.iota), buf -> new MsgStaffReadS2C(ModNetworking.readIota(buf)));
        final Iota iota; public MsgStaffReadS2C(Iota i) { this.iota = i; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffReadS2C msg, NetworkManager.PacketContext ctx) { } // 法术库流程不使用该旧消息
    }

    // ==================== 页写入 ====================
    public static class MsgStaffWriteC2S implements CustomPacketPayload {
        public static final Type<MsgStaffWriteC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "staff_write"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffWriteC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> ModNetworking.writeIota(buf, msg.iota), buf -> new MsgStaffWriteC2S(ModNetworking.readIota(buf)));
        final Iota iota; public MsgStaffWriteC2S(Iota i) { this.iota = i; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffWriteC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> { var st = sp.getMainHandItem(); if (st.getItem() instanceof ItemAlmightlyStaff it) it.writeDatum(st, msg.iota); });
        }
    }

    // ==================== 立即施放 ====================
    public static class MsgStaffCastC2S implements CustomPacketPayload {
        public static final Type<MsgStaffCastC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "staff_cast"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffCastC2S> STREAM_CODEC = StreamCodec.of((buf, msg) -> {}, buf -> new MsgStaffCastC2S());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffCastC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> { var st = sp.getMainHandItem(); if (st.getItem() instanceof ItemAlmightlyStaff it) it.casting(sp.level(), sp, InteractionHand.MAIN_HAND); });
        }
    }

    // ==================== 翻页 ====================
    public static class MsgStaffPageC2S implements CustomPacketPayload {
        public static final Type<MsgStaffPageC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "staff_page"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffPageC2S> STREAM_CODEC = StreamCodec.of((buf, msg) -> buf.writeInt(msg.delta), buf -> new MsgStaffPageC2S(buf.readInt()));
        final int delta; public MsgStaffPageC2S(int d) { delta = d; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffPageC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> { var st = sp.getMainHandItem(); if (st.getItem() instanceof ItemAlmightlyStaff) { ItemAlmightlyStaff.rotatePageIdx(st, msg.delta < 0, sp.level()); sendToPlayer(sp, new MsgStaffReadS2C(((ItemAlmightlyStaff) st.getItem()).readIota(st))); } });
        }
    }

    // ==================== Escape ====================
    public static class MsgStaffEscapePatternC2S implements CustomPacketPayload {
        public static final Type<MsgStaffEscapePatternC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "staff_esc_pat"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffEscapePatternC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { HexPattern.STREAM_CODEC.encode(buf, msg.pattern); }, buf -> new MsgStaffEscapePatternC2S(HexPattern.STREAM_CODEC.decode(buf)));
        final HexPattern pattern; public MsgStaffEscapePatternC2S(HexPattern p) { pattern = p; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffEscapePatternC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> { var st = sp.getMainHandItem(); if (st.getItem() instanceof ItemAlmightlyStaff it) { it.writeDatum(st, null); it.writeDatum(st, new PatternIota(msg.pattern)); it.casting(sp.level(), sp, InteractionHand.MAIN_HAND); sendToPlayer(sp, new MsgStaffEscapeResultS2C(it.readIota(st))); } });
        }
    }
    public static class MsgStaffEscapeResultS2C implements CustomPacketPayload {
        public static final Type<MsgStaffEscapeResultS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "staff_esc_res"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffEscapeResultS2C> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> ModNetworking.writeIota(buf, msg.iota), buf -> new MsgStaffEscapeResultS2C(ModNetworking.readIota(buf)));
        final Iota iota; public MsgStaffEscapeResultS2C(Iota i) { this.iota = i; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffEscapeResultS2C msg, NetworkManager.PacketContext ctx) { } // 法术库流程不使用该旧消息
    }

    // ==================== HexParse Iotas→Code ====================
    /** 代码请求：携带页内 iota 的原始 NBT 标签列表，服务端反序列化后由 HexParse 解析。 */
    public static class MsgParseToCodeC2S implements CustomPacketPayload {
        public static final Type<MsgParseToCodeC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "parse_to_code"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgParseToCodeC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeInt(msg.iotas.size()); for (CompoundTag t : msg.iotas) buf.writeNbt(t); },
                buf -> { int n = buf.readInt(); List<CompoundTag> l = new ArrayList<>(); for (int i = 0; i < n; i++) { CompoundTag t = buf.readNbt(); if (t != null) l.add(t); } return new MsgParseToCodeC2S(l); });
        final List<CompoundTag> iotas; public MsgParseToCodeC2S(List<CompoundTag> list) { iotas = list == null ? List.of() : list; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgParseToCodeC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> {
                try {
                    List<Iota> iotas = new ArrayList<>();
                    for (CompoundTag t : msg.iotas) { Iota i = deserializeIota(t, sp.serverLevel()); if (i != null) iotas.add(i); }
                    String code = ParserMain.ParseIotaNbt(new ListIota(iotas), sp, StringProcessors.READ_DEFAULT);
                    sendToPlayer(sp, new MsgParseToCodeS2C(code != null ? code : ""));
                } catch (Exception e) { sendToPlayer(sp, new MsgParseToCodeS2C("")); }
            });
        }
    }
    public static class MsgParseToCodeS2C implements CustomPacketPayload {
        public static final Type<MsgParseToCodeS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "parse_to_code_s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgParseToCodeS2C> STREAM_CODEC = StreamCodec.of((buf, msg) -> buf.writeUtf(msg.code), buf -> new MsgParseToCodeS2C(buf.readUtf()));
        final String code; public MsgParseToCodeS2C(String c) { code = c; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgParseToCodeS2C msg, NetworkManager.PacketContext ctx) {
            Minecraft.getInstance().execute(() -> { var s = Minecraft.getInstance().screen; if (s instanceof StaffLibScreen scr) scr.onParseCode(msg.code); });
        }
    }

    // ==================== HexParse Code→Iotas ====================
    public static class MsgParseToIotasC2S implements CustomPacketPayload {
        public static final Type<MsgParseToIotasC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "parse_to_iotas"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgParseToIotasC2S> STREAM_CODEC = StreamCodec.of((buf, msg) -> buf.writeUtf(msg.code), buf -> new MsgParseToIotasC2S(buf.readUtf()));
        final String code; public MsgParseToIotasC2S(String c) { code = c; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgParseToIotasC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> {
                try { Iota iota = ParserMain.ParseCode(msg.code, sp); sendToPlayer(sp, new MsgParseToIotasS2C(iota)); }
                catch (Exception e) { sendToPlayer(sp, new MsgParseToIotasS2C(null)); }
            });
        }
    }
    public static class MsgParseToIotasS2C implements CustomPacketPayload {
        public static final Type<MsgParseToIotasS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "parse_to_iotas_s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgParseToIotasS2C> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> ModNetworking.writeIota(buf, msg.iota), buf -> new MsgParseToIotasS2C(ModNetworking.readIota(buf)));
        final Iota iota; public MsgParseToIotasS2C(Iota i) { this.iota = i; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgParseToIotasS2C msg, NetworkManager.PacketContext ctx) {
            Minecraft.getInstance().execute(() -> { var s = Minecraft.getInstance().screen; if (s instanceof StaffParseScreen scr) scr.onParseResult(msg.iota); });
        }
    }

    // ==================== 法术库（页同步，DataComponents） ====================
    public record PageData(int pageIndex, String name, List<CompoundTag> iotas) {
        public PageData { iotas = iotas == null ? new ArrayList<>() : new ArrayList<>(iotas); }
    }

    /** 客户端由 iota 原始 NBT 标签组装 ListIota 的序列化标签（反序列化在服务端）。 */
    public static CompoundTag buildListTag(List<CompoundTag> tags) {
        CompoundTag out = new CompoundTag();
        try {
            List<Iota> iotas = new ArrayList<>();
            for (CompoundTag t : tags) { Iota i = deserializeIota(t, null); if (i != null) iotas.add(i); }
            var tag = IotaType.TYPED_CODEC.encodeStart(NbtOps.INSTANCE, new ListIota(iotas)).result().orElse(null);
            if (tag instanceof CompoundTag ct) return ct;
        } catch (Exception ignored) { }
        return out;
    }

    // ---- DataComponents 页操作（1.21.1 的 ItemSpellbook 用 DataComponents 而非 NBT） ----

    /** 服务端反序列化页内 iota，返回原始序列化 NBT 标签；客户端不做反序列化。 */
    private static List<CompoundTag> readPageIotaTags(ItemStack stack, int pageIndex) {
        Map<String, Iota> pages = stack.get(HexDataComponents.SPELLBOOK_PAGES.get());
        Iota i = pages == null ? null : pages.get(String.valueOf(pageIndex));
        List<CompoundTag> list = new ArrayList<>();
        if (i instanceof ListIota li) { for (var x : li.getList()) { CompoundTag t = serializeIota(x); if (t != null) list.add(t); } }
        else if (i != null) { CompoundTag t = serializeIota(i); if (t != null) list.add(t); }
        return list;
    }

    private static String pageName(ItemStack stack, int pageIndex) {
        Map<String, Component> names = stack.get(HexDataComponents.SPELLBOOK_PAGE_NAMES.get());
        if (names == null) return "";
        var c = names.get(String.valueOf(pageIndex));
        return c == null ? "" : c.getString();
    }

    private static void setPageName(ItemStack stack, int pageIndex, String name) {
        Map<String, Component> names = new HashMap<>(stack.getOrDefault(HexDataComponents.SPELLBOOK_PAGE_NAMES.get(), Map.of()));
        String key = String.valueOf(pageIndex);
        if (name == null || name.isBlank()) names.remove(key);
        else names.put(key, Component.literal(name));
        if (names.isEmpty()) stack.remove(HexDataComponents.SPELLBOOK_PAGE_NAMES.get());
        else stack.set(HexDataComponents.SPELLBOOK_PAGE_NAMES.get(), names);
    }

    private static void selectPage(ItemStack stack, int pageIndex) {
        stack.set(HexDataComponents.SELECTED_SPELLBOOK_PAGE.get(), Math.max(1, pageIndex));
        String name = pageName(stack, pageIndex);
        if (!name.isEmpty()) stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        else stack.remove(DataComponents.CUSTOM_NAME);
    }

    /** 服务端反序列化原始标签后写入页面（DataComponents）。 */
    private static void writePageIotaList(ItemStack stack, int pageIndex, List<CompoundTag> tags, ServerLevel level) {
        selectPage(stack, pageIndex);
        Map<String, Iota> pages = new HashMap<>(stack.getOrDefault(HexDataComponents.SPELLBOOK_PAGES.get(), Map.of()));
        String key = String.valueOf(pageIndex);
        if (tags == null || tags.isEmpty()) {
            pages.remove(key);
        } else {
            List<Iota> iotas = new ArrayList<>();
            for (CompoundTag t : tags) { Iota i = deserializeIota(t, level); if (i != null) iotas.add(i); }
            pages.put(key, new ListIota(iotas));
        }
        if (pages.isEmpty()) stack.remove(HexDataComponents.SPELLBOOK_PAGES.get());
        else stack.set(HexDataComponents.SPELLBOOK_PAGES.get(), pages);
    }

    private static List<PageData> buildPages(ItemStack stack, ServerLevel level) {
        List<PageData> pages = new ArrayList<>();
        for (int i = 1; i <= 64; i++) {
            List<CompoundTag> iotas = readPageIotaTags(stack, i);
            String name = pageName(stack, i);
            if (iotas.isEmpty() && name.isEmpty()) continue;
            pages.add(new PageData(i, name, iotas));
        }
        return pages;
    }

    /** 单页序列化后的字节数（用与真实编码一致的写法精确测量）。 */
    private static int pageBytes(PageData p) {
        FriendlyByteBuf tmp = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer(256));
        try {
            tmp.writeInt(p.pageIndex());
            tmp.writeUtf(p.name());
            tmp.writeInt(p.iotas().size());
            for (CompoundTag t : p.iotas()) if (t != null) tmp.writeNbt(t);
            return tmp.readableBytes();
        } catch (Exception e) {
            return 4096;
        } finally {
            tmp.release();
        }
    }

    /** 分块同步：按字节分批，单包 ≤ 20000 字节。 */
    private static void sendSync(ServerPlayer s, ItemStack stack) {
        List<PageData> all = buildPages(stack, s.serverLevel());
        int selected = ItemAlmightlyStaff.getPage(stack, 1);
        final int MAX_BYTES = 20000;
        List<PageData> batch = new ArrayList<>();
        int batchBytes = 4; // selectedPage int
        for (PageData p : all) {
            int pb = pageBytes(p);
            if (!batch.isEmpty() && batchBytes + pb > MAX_BYTES) {
                sendToPlayer(s, new MsgStaffLibSyncS2C(new ArrayList<>(batch), selected, false));
                batch.clear();
                batchBytes = 4;
            }
            batch.add(p);
            batchBytes += pb;
        }
        if (!batch.isEmpty() || all.isEmpty()) {
            sendToPlayer(s, new MsgStaffLibSyncS2C(new ArrayList<>(batch), selected, true));
        }
    }

    private static ItemStack staffInMainHand(ServerPlayer s) {
        var st = s.getMainHandItem();
        return st.getItem() instanceof ItemAlmightlyStaff ? st : null;
    }

    public static class MsgStaffLibReadC2S implements CustomPacketPayload {
        public static final Type<MsgStaffLibReadC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "lib_read"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffLibReadC2S> STREAM_CODEC = StreamCodec.of((buf, msg) -> {}, buf -> new MsgStaffLibReadC2S());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffLibReadC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> { var st = staffInMainHand(sp); if (st != null) sendSync(sp, st); });
        }
    }
    public static class MsgStaffLibSyncS2C implements CustomPacketPayload {
        public static final Type<MsgStaffLibSyncS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "lib_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffLibSyncS2C> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> {
                    buf.writeInt(msg.pages.size());
                    for (PageData p : msg.pages) {
                        buf.writeInt(p.pageIndex); buf.writeUtf(p.name);
                        buf.writeInt(p.iotas.size());
                        for (CompoundTag t : p.iotas) if (t != null) buf.writeNbt(t);
                    }
                    buf.writeInt(msg.selectedPage);
                    buf.writeBoolean(msg.lastChunk);
                },
                buf -> {
                    int n = buf.readInt(); List<PageData> pages = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        int idx = buf.readInt(); String name = buf.readUtf();
                        int c = buf.readInt(); List<CompoundTag> iotas = new ArrayList<>();
                        for (int j = 0; j < c; j++) { CompoundTag tag = buf.readNbt(); if (tag != null) iotas.add(tag); }
                        pages.add(new PageData(idx, name, iotas));
                    }
                    return new MsgStaffLibSyncS2C(pages, buf.readInt(), buf.readBoolean());
                });
        final List<PageData> pages; final int selectedPage; final boolean lastChunk;
        public MsgStaffLibSyncS2C(List<PageData> p, int sel, boolean last) { pages = p; selectedPage = sel; lastChunk = last; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffLibSyncS2C msg, NetworkManager.PacketContext ctx) {
            Minecraft.getInstance().execute(() -> {
                var s = Minecraft.getInstance().screen;
                if (s instanceof StaffLibScreen scr) scr.onSyncChunk(msg.pages, msg.selectedPage, msg.lastChunk);
            });
        }
    }
    public static class MsgStaffPageRenameC2S implements CustomPacketPayload {
        public static final Type<MsgStaffPageRenameC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "page_rename"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffPageRenameC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeInt(msg.pageIndex); buf.writeUtf(msg.name); },
                buf -> new MsgStaffPageRenameC2S(buf.readInt(), buf.readUtf()));
        final int pageIndex; final String name;
        public MsgStaffPageRenameC2S(int p, String n) { pageIndex = p; name = n; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffPageRenameC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> { var st = staffInMainHand(sp); if (st != null) { setPageName(st, msg.pageIndex, msg.name); sendSync(sp, st); } });
        }
    }
    public static class MsgStaffPageSetIotaC2S implements CustomPacketPayload {
        public static final Type<MsgStaffPageSetIotaC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "page_set_iota"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffPageSetIotaC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeInt(msg.pageIndex); buf.writeInt(msg.slotIndex); buf.writeBoolean(msg.iotaTag != null); if (msg.iotaTag != null) buf.writeNbt(msg.iotaTag); },
                buf -> { int p = buf.readInt(); int s = buf.readInt(); CompoundTag t = null; if (buf.readBoolean()) t = buf.readNbt(); return new MsgStaffPageSetIotaC2S(p, s, t); });
        final int pageIndex, slotIndex; final CompoundTag iotaTag;
        public MsgStaffPageSetIotaC2S(int p, int s, CompoundTag t) { pageIndex = p; slotIndex = s; iotaTag = t; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffPageSetIotaC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> {
                var st = staffInMainHand(sp); if (st == null) return;
                List<CompoundTag> list = readPageIotaTags(st, msg.pageIndex);
                if (msg.iotaTag == null) { if (msg.slotIndex >= 0 && msg.slotIndex < list.size()) list.remove(msg.slotIndex); }
                else { if (msg.slotIndex >= 0 && msg.slotIndex < list.size()) list.set(msg.slotIndex, msg.iotaTag); else list.add(msg.iotaTag); }
                writePageIotaList(st, msg.pageIndex, list, sp.serverLevel());
                sendSync(sp, st);
            });
        }
    }
    public static class MsgStaffPageAppendIotaC2S implements CustomPacketPayload {
        public static final Type<MsgStaffPageAppendIotaC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "page_append_iota"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffPageAppendIotaC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeInt(msg.pageIndex); if (msg.iotaTag != null) buf.writeNbt(msg.iotaTag); },
                buf -> new MsgStaffPageAppendIotaC2S(buf.readInt(), buf.readNbt()));
        final int pageIndex; final CompoundTag iotaTag;
        public MsgStaffPageAppendIotaC2S(int p, CompoundTag t) { pageIndex = p; iotaTag = t; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffPageAppendIotaC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> {
                var st = staffInMainHand(sp); if (st == null || msg.iotaTag == null) return;
                List<CompoundTag> list = readPageIotaTags(st, msg.pageIndex);
                list.add(msg.iotaTag);
                writePageIotaList(st, msg.pageIndex, list, sp.serverLevel());
                sendSync(sp, st);
            });
        }
    }
    public static class MsgStaffPageSwapC2S implements CustomPacketPayload {
        public static final Type<MsgStaffPageSwapC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "page_swap"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffPageSwapC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeInt(msg.a); buf.writeInt(msg.b); },
                buf -> new MsgStaffPageSwapC2S(buf.readInt(), buf.readInt()));
        final int a, b;
        public MsgStaffPageSwapC2S(int a, int b) { this.a = a; this.b = b; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffPageSwapC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> {
                var st = staffInMainHand(sp); if (st == null || msg.a == msg.b) return;
                List<CompoundTag> la = readPageIotaTags(st, msg.a);
                List<CompoundTag> lb = readPageIotaTags(st, msg.b);
                String na = pageName(st, msg.a), nb = pageName(st, msg.b);
                writePageIotaList(st, msg.a, lb, sp.serverLevel()); writePageIotaList(st, msg.b, la, sp.serverLevel());
                setPageName(st, msg.a, nb); setPageName(st, msg.b, na);
                int sel = ItemAlmightlyStaff.getPage(st, 1);
                if (sel == msg.a) selectPage(st, msg.b); else if (sel == msg.b) selectPage(st, msg.a);
                sendSync(sp, st);
            });
        }
    }
    /** 整页写入：改名 + 写完整 iota 原始标签（空列表=清空页面）；反序列化在服务端。 */
    public static class MsgStaffPageWriteC2S implements CustomPacketPayload {
        public static final Type<MsgStaffPageWriteC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "page_write"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffPageWriteC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> {
                    buf.writeInt(msg.pageIndex); buf.writeUtf(msg.name);
                    buf.writeInt(msg.iotaTags == null ? 0 : msg.iotaTags.size());
                    if (msg.iotaTags != null) for (CompoundTag t : msg.iotaTags) if (t != null) buf.writeNbt(t);
                },
                buf -> {
                    int p = buf.readInt(); String name = buf.readUtf();
                    int c = buf.readInt(); List<CompoundTag> tags = new ArrayList<>();
                    for (int j = 0; j < c; j++) { CompoundTag t = buf.readNbt(); if (t != null) tags.add(t); }
                    return new MsgStaffPageWriteC2S(p, name, tags);
                });
        final int pageIndex; final String name; final List<CompoundTag> iotaTags;
        public MsgStaffPageWriteC2S(int p, String n, List<CompoundTag> tags) { pageIndex = p; name = n; iotaTags = tags; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffPageWriteC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> {
                var st = staffInMainHand(sp); if (st == null) return;
                writePageIotaList(st, msg.pageIndex, msg.iotaTags == null ? List.of() : msg.iotaTags, sp.serverLevel());
                setPageName(st, msg.pageIndex, msg.name);
                sendSync(sp, st);
            });
        }
    }
    public static class MsgStaffPageSelectC2S implements CustomPacketPayload {
        public static final Type<MsgStaffPageSelectC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "page_select"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffPageSelectC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> buf.writeInt(msg.pageIndex), buf -> new MsgStaffPageSelectC2S(buf.readInt()));
        final int pageIndex; public MsgStaffPageSelectC2S(int p) { pageIndex = p; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffPageSelectC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> { var st = staffInMainHand(sp); if (st != null) { selectPage(st, msg.pageIndex); sendSync(sp, st); } });
        }
    }
    /**
     * 卓越法术检查：服务端直接调 HexParse 的检查（GreatPatternUnlocker.isUnlocked）与图案获取（PatternMapper.mapPatternWorld）。
     * 1.21.1 的 mapPatternWorld 值为 Iota（非 NBT 标签）。
     */
    public static class MsgStaffGreatSpellCheckC2S implements CustomPacketPayload {
        public static final Type<MsgStaffGreatSpellCheckC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "great_check"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffGreatSpellCheckC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeUtf(msg.actionId); buf.writeInt(msg.targetSlot); },
                buf -> new MsgStaffGreatSpellCheckC2S(buf.readUtf(), buf.readInt()));
        final String actionId; final int targetSlot;
        public MsgStaffGreatSpellCheckC2S(String a, int t) { actionId = a; targetSlot = t; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffGreatSpellCheckC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> {
                boolean usable = false; String sig = ""; String dir = "";
                try {
                    var level = sp.serverLevel();
                    PatternMapper.init(level);
                    Iota i = PatternMapper.mapPatternWorld.get(msg.actionId);
                    if (i instanceof PatternIota pi) {
                        boolean unlocked = GreatPatternUnlocker.get(level).isUnlocked(msg.actionId);
                        if (unlocked) {
                            usable = true;
                            sig = pi.getPattern().anglesSignature();
                            dir = pi.getPattern().getStartDir().name();
                        }
                    }
                } catch (Exception ignored) { }
                sendToPlayer(sp, new MsgStaffGreatSpellCheckS2C(msg.actionId, usable, sig, dir));
            });
        }
    }
    public static class MsgStaffGreatSpellCheckS2C implements CustomPacketPayload {
        public static final Type<MsgStaffGreatSpellCheckS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "great_check_s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffGreatSpellCheckS2C> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeUtf(msg.actionId); buf.writeBoolean(msg.usable); buf.writeUtf(msg.signature); buf.writeUtf(msg.startDir); },
                buf -> new MsgStaffGreatSpellCheckS2C(buf.readUtf(), buf.readBoolean(), buf.readUtf(), buf.readUtf()));
        final String actionId; final boolean usable; final String signature, startDir;
        public MsgStaffGreatSpellCheckS2C(String a, boolean u, String s, String d) { actionId = a; usable = u; signature = s; startDir = d; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffGreatSpellCheckS2C msg, NetworkManager.PacketContext ctx) {
            Minecraft.getInstance().execute(() -> {
                var s = Minecraft.getInstance().screen;
                if (s instanceof StaffLibScreen scr) scr.onGreatSpellCheck(msg.actionId, msg.usable, msg.signature, msg.startDir);
            });
        }
    }
    /** 打开施法采集界面时清空玩家施法栈，保证采集到的都是本次施放产生的结果。 */
    public static class MsgStaffCastClearStackC2S implements CustomPacketPayload {
        public static final Type<MsgStaffCastClearStackC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "cast_clear_stack"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffCastClearStackC2S> STREAM_CODEC = StreamCodec.of((buf, msg) -> {}, buf -> new MsgStaffCastClearStackC2S());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffCastClearStackC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> {
                try { IXplatAbstractions.INSTANCE.setStaffcastImage(sp, null); } catch (Exception ignored) { }
            });
        }
    }
}
