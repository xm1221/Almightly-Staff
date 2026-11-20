package cn.xm1221.AlmightlyStaff.network;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.gui.StaffLibScreen;
import cn.xm1221.AlmightlyStaff.gui.StaffParseScreen;
import cn.xm1221.AlmightlyStaff.items.ItemAlmightlyStaff;
import cn.xm1221.AlmightlyStaff.parse.HexParseBridge;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ModNetworking {

    /** 页操作消息的 op 取值。 */
    public static final int OP_NEW = 0, OP_DUPLICATE = 1, OP_DELETE = 2, OP_REORDER = 3, OP_RENAME = 4, OP_SELECT = 5;

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
        // ---- 法术库（统一页操作 + 分块/增量同步） ----
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffLibReadC2S.TYPE, MsgStaffLibReadC2S.STREAM_CODEC, MsgStaffLibReadC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), MsgStaffLibSyncS2C.TYPE, MsgStaffLibSyncS2C.STREAM_CODEC, MsgStaffLibSyncS2C::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffPageOpC2S.TYPE, MsgStaffPageOpC2S.STREAM_CODEC, MsgStaffPageOpC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffPageWriteC2S.TYPE, MsgStaffPageWriteC2S.STREAM_CODEC, MsgStaffPageWriteC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffGreatSpellCheckC2S.TYPE, MsgStaffGreatSpellCheckC2S.STREAM_CODEC, MsgStaffGreatSpellCheckC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), MsgStaffGreatSpellCheckS2C.TYPE, MsgStaffGreatSpellCheckS2C.STREAM_CODEC, MsgStaffGreatSpellCheckS2C::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffCastClearStackC2S.TYPE, MsgStaffCastClearStackC2S.STREAM_CODEC, MsgStaffCastClearStackC2S::handle);
        // ---- IDE 异步 HexParse ----
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgIdeParseToCodeC2S.TYPE, MsgIdeParseToCodeC2S.STREAM_CODEC, MsgIdeParseToCodeC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), MsgIdeParseToCodeS2C.TYPE, MsgIdeParseToCodeS2C.STREAM_CODEC, MsgIdeParseToCodeS2C::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgIdeParseToIotasC2S.TYPE, MsgIdeParseToIotasC2S.STREAM_CODEC, MsgIdeParseToIotasC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), MsgIdeParseToIotasS2C.TYPE, MsgIdeParseToIotasS2C.STREAM_CODEC, MsgIdeParseToIotasS2C::handle);
    }

    public static void sendToServer(CustomPacketPayload p) { NetworkManager.sendToServer(p); }
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload p) { NetworkManager.sendToPlayer(player, p); }

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
            var ni = ItemAlmightlyStaff.rotatePageIdx(stack, delta < 0.0, sender.level());
            StaffPages.select(stack, ni);
            var len = ItemAlmightlyStaff.highestPage(stack); var sealed = ItemAlmightlyStaff.isSealed(stack);
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
        static void handle(MsgStaffReadS2C msg, NetworkManager.PacketContext ctx) { }
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
            ctx.queue(() -> {
                var st = sp.getMainHandItem();
                if (st.getItem() instanceof ItemAlmightlyStaff it) {
                    int ni = ItemAlmightlyStaff.rotatePageIdx(st, msg.delta < 0, sp.level());
                    StaffPages.select(st, ni);
                    sendToPlayer(sp, new MsgStaffReadS2C(it.readIota(st)));
                }
            });
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
            ctx.queue(() -> {
                var st = sp.getMainHandItem();
                if (!(st.getItem() instanceof ItemAlmightlyStaff it)) return;
                if (it.isOnCooldown(sp)) return;
                it.writeDatum(st, null); it.writeDatum(st, new PatternIota(msg.pattern)); it.casting(sp.level(), sp, InteractionHand.MAIN_HAND); sendToPlayer(sp, new MsgStaffEscapeResultS2C(it.readIota(st)));
            });
        }
    }
    public static class MsgStaffEscapeResultS2C implements CustomPacketPayload {
        public static final Type<MsgStaffEscapeResultS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "staff_esc_res"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffEscapeResultS2C> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> ModNetworking.writeIota(buf, msg.iota), buf -> new MsgStaffEscapeResultS2C(ModNetworking.readIota(buf)));
        final Iota iota; public MsgStaffEscapeResultS2C(Iota i) { this.iota = i; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffEscapeResultS2C msg, NetworkManager.PacketContext ctx) { }
    }

    // ==================== HexParse Iotas→Code（传统 StaffLibScreen 用） ====================
    public static class MsgParseToCodeC2S implements CustomPacketPayload {
        public static final Type<MsgParseToCodeC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "parse_to_code"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgParseToCodeC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> {
                    buf.writeBoolean(msg.listNbt != null);
                    if (msg.listNbt != null) buf.writeNbt(msg.listNbt);
                },
                buf -> new MsgParseToCodeC2S(buf.readBoolean() ? buf.readNbt() : null));
        final CompoundTag listNbt;
        public MsgParseToCodeC2S(CompoundTag listNbt) { this.listNbt = listNbt; }
        public MsgParseToCodeC2S(List<CompoundTag> list) { this.listNbt = buildListTag(list); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgParseToCodeC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> {
                if (!Platform.isModLoaded("hexparse")) {
                    sendToPlayer(sp, new MsgParseToCodeS2C(""));
                    return;
                }
                try {
                    String code = HexParseBridge.parseIotaNbt(msg.listNbt, sp);
                    sendToPlayer(sp, new MsgParseToCodeS2C(code != null ? code : ""));
                } catch (Throwable e) {
                    sendToPlayer(sp, new MsgParseToCodeS2C(""));
                }
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

    // ==================== HexParse Code→Iotas（传统 StaffParseScreen 用） ====================
    public static class MsgParseToIotasC2S implements CustomPacketPayload {
        public static final Type<MsgParseToIotasC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "parse_to_iotas"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgParseToIotasC2S> STREAM_CODEC = StreamCodec.of((buf, msg) -> buf.writeUtf(msg.code), buf -> new MsgParseToIotasC2S(buf.readUtf()));
        final String code; public MsgParseToIotasC2S(String c) { code = c; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgParseToIotasC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> {
                if (!Platform.isModLoaded("hexparse")) {
                    sendToPlayer(sp, new MsgParseToIotasS2C(null));
                    return;
                }
                try {
                    sendToPlayer(sp, new MsgParseToIotasS2C(HexParseBridge.parseCodeToIota(msg.code, sp)));
                } catch (Throwable e) {
                    sendToPlayer(sp, new MsgParseToIotasS2C(null));
                }
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

    // ==================== 法术库（页同步） ====================
    public record PageData(int pageIndex, String name, List<CompoundTag> iotas, boolean sealed) {
        public PageData { iotas = iotas == null ? new ArrayList<>() : new ArrayList<>(iotas); }
    }

    /** 客户端由 iota 原始 NBT 标签组装 ListIota 的序列化标签（反序列化在服务端）。 */
    public static CompoundTag buildListTag(List<CompoundTag> tags) {
        return StaffPages.listTagOf(tags, StaffPages.emptyContent());
    }

    private static String statusKey(StaffPages.Result result) {
        return switch (result) {
            case OK -> "";
            case SEALED -> "page_sealed";
            case FULL -> "page_full";
            default -> "page_invalid";
        };
    }

    private static PageData buildPage(ItemStack stack, int pageIndex) {
        return new PageData(pageIndex, StaffPages.name(stack, pageIndex),
            StaffPages.contentTags(stack, pageIndex), StaffPages.isSealed(stack, pageIndex));
    }

    private static List<PageData> buildPages(ItemStack stack) {
        List<PageData> pages = new ArrayList<>();
        for (int pageIndex : StaffPages.indices(stack)) pages.add(buildPage(stack, pageIndex));
        return pages;
    }

    private static int pageBytes(PageData p) {
        FriendlyByteBuf tmp = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer(256));
        try {
            tmp.writeInt(p.pageIndex());
            tmp.writeUtf(p.name());
            tmp.writeBoolean(p.sealed());
            tmp.writeInt(p.iotas().size());
            for (CompoundTag t : p.iotas()) if (t != null) tmp.writeNbt(t);
            return tmp.readableBytes();
        } catch (Exception e) {
            return 4096;
        } finally {
            tmp.release();
        }
    }

    private static void sendSync(ServerPlayer s, ItemStack stack, int focusPage) {
        String staffUuid = StaffPages.ensureStaffUuid(stack);
        List<PageData> all = buildPages(stack);
        int castPage = StaffPages.castPage(stack);
        final int MAX_BYTES = 20000;
        List<PageData> batch = new ArrayList<>();
        int batchBytes = 16;
        for (PageData p : all) {
            int pb = pageBytes(p);
            if (!batch.isEmpty() && batchBytes + pb > MAX_BYTES) {
                sendToPlayer(s, new MsgStaffLibSyncS2C(new ArrayList<>(batch), castPage, focusPage, List.of(), false, false, "", staffUuid));
                batch.clear();
                batchBytes = 16;
            }
            batch.add(p);
            batchBytes += pb;
        }
        if (!batch.isEmpty() || all.isEmpty()) {
            sendToPlayer(s, new MsgStaffLibSyncS2C(new ArrayList<>(batch), castPage, focusPage, List.of(), true, false, "", staffUuid));
        }
    }

    private static void sendIncremental(ServerPlayer s, ItemStack stack, String status, int focusPage,
                                        List<Integer> removed, int... pageIndexes) {
        String staffUuid = StaffPages.ensureStaffUuid(stack);
        List<PageData> pages = new ArrayList<>();
        for (int idx : pageIndexes) if (StaffPages.exists(stack, idx)) pages.add(buildPage(stack, idx));
        sendToPlayer(s, new MsgStaffLibSyncS2C(pages, StaffPages.castPage(stack), focusPage,
            removed == null ? List.of() : removed, true, true, status == null ? "" : status, staffUuid));
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
            ctx.queue(() -> {
                var st = staffInMainHand(sp);
                if (st == null) return;
                StaffPages.select(st, StaffPages.castPage(st));
                sendSync(sp, st, 0);
            });
        }
    }

    public static class MsgStaffLibSyncS2C implements CustomPacketPayload {
        public static final Type<MsgStaffLibSyncS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "lib_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffLibSyncS2C> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> {
                    buf.writeInt(msg.pages.size());
                    for (PageData p : msg.pages) {
                        buf.writeInt(p.pageIndex);
                        buf.writeUtf(p.name);
                        buf.writeBoolean(p.sealed);
                        buf.writeInt(p.iotas.size());
                        for (CompoundTag t : p.iotas) if (t != null) buf.writeNbt(t);
                    }
                    buf.writeInt(msg.castPage);
                    buf.writeInt(msg.focusPage);
                    List<Integer> rem = msg.removed == null ? List.of() : msg.removed;
                    buf.writeInt(rem.size());
                    for (int idx : rem) buf.writeInt(idx);
                    buf.writeBoolean(msg.lastChunk);
                    buf.writeBoolean(msg.incremental);
                    buf.writeUtf(msg.status == null ? "" : msg.status);
                    buf.writeUtf(msg.staffUuid == null ? "" : msg.staffUuid);
                },
                buf -> {
                    int n = buf.readInt(); List<PageData> pages = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        int idx = buf.readInt(); String name = buf.readUtf(); boolean sealed = buf.readBoolean();
                        int c = buf.readInt(); List<CompoundTag> iotas = new ArrayList<>();
                        for (int j = 0; j < c; j++) { CompoundTag tag = buf.readNbt(); if (tag != null) iotas.add(tag); }
                        pages.add(new PageData(idx, name, iotas, sealed));
                    }
                    int castPage = buf.readInt();
                    int focusPage = buf.readInt();
                    int removedCount = buf.readInt(); List<Integer> removed = new ArrayList<>();
                    for (int i = 0; i < removedCount; i++) removed.add(buf.readInt());
                    return new MsgStaffLibSyncS2C(pages, castPage, focusPage, removed,
                        buf.readBoolean(), buf.readBoolean(), buf.readUtf(), buf.readUtf());
                });
        final List<PageData> pages;
        final int castPage, focusPage;
        final List<Integer> removed;
        final boolean lastChunk, incremental;
        final String status, staffUuid;

        public MsgStaffLibSyncS2C(List<PageData> pages, int castPage, int focusPage, List<Integer> removed,
                                  boolean lastChunk, boolean incremental, String status, String staffUuid) {
            this.pages = pages;
            this.castPage = castPage;
            this.focusPage = focusPage;
            this.removed = removed;
            this.lastChunk = lastChunk;
            this.incremental = incremental;
            this.status = status;
            this.staffUuid = staffUuid;
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffLibSyncS2C msg, NetworkManager.PacketContext ctx) {
            Minecraft.getInstance().execute(() -> {
                var s = Minecraft.getInstance().screen;
                if (s instanceof StaffSyncReceiver scr) {
                    scr.onSyncChunk(msg.pages, msg.castPage, msg.focusPage, msg.removed,
                        msg.lastChunk, msg.incremental, msg.status, msg.staffUuid);
                }
            });
        }
    }

    public static class MsgStaffPageOpC2S implements CustomPacketPayload {
        public static final Type<MsgStaffPageOpC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "page_op"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffPageOpC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> {
                    buf.writeInt(msg.op); buf.writeInt(msg.a); buf.writeInt(msg.b);
                    buf.writeBoolean(msg.flag);
                    buf.writeUtf(msg.name == null ? "" : msg.name);
                },
                buf -> new MsgStaffPageOpC2S(buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readUtf()));
        final int op, a, b;
        final boolean flag;
        final String name;
        public MsgStaffPageOpC2S(int op, int a, int b, boolean flag, String name) {
            this.op = op; this.a = a; this.b = b; this.flag = flag; this.name = name;
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffPageOpC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> apply(sp, msg));
        }

        private static void apply(ServerPlayer s, MsgStaffPageOpC2S m) {
            var st = staffInMainHand(s);
            if (st == null) return;
            switch (m.op) {
                case OP_NEW -> {
                    int idx = StaffPages.lowestFree(st);
                    if (idx < 0) { sendIncremental(s, st, "page_full", 0, List.of()); return; }
                    if (StaffPages.create(st, idx) != StaffPages.Result.OK) {
                        sendIncremental(s, st, "page_invalid", 0, List.of()); return;
                    }
                    StaffPages.select(st, idx);
                    sendIncremental(s, st, "", idx, List.of(), idx);
                }
                case OP_DUPLICATE -> {
                    int idx = StaffPages.lowestFree(st);
                    if (idx < 0) { sendIncremental(s, st, "page_full", 0, List.of()); return; }
                    var result = StaffPages.duplicate(st, m.a, idx, m.name);
                    if (result != StaffPages.Result.OK) {
                        sendIncremental(s, st, statusKey(result), 0, List.of(), m.a); return;
                    }
                    StaffPages.select(st, idx);
                    sendIncremental(s, st, "", idx, List.of(), idx);
                }
                case OP_DELETE -> {
                    int idx = m.a;
                    var result = StaffPages.delete(st, idx);
                    if (result != StaffPages.Result.OK) {
                        sendIncremental(s, st, statusKey(result), 0, List.of(), idx); return;
                    }
                    int next = StaffPages.nearestPage(st, idx);
                    StaffPages.select(st, next);
                    sendIncremental(s, st, "", next, List.of(idx), next);
                }
                case OP_REORDER -> {
                    var result = StaffPages.reorder(st, m.a, m.b, m.flag);
                    if (result.result() != StaffPages.Result.OK) {
                        sendSync(s, st, 0);
                        sendIncremental(s, st, statusKey(result.result()), 0, List.of());
                        return;
                    }
                    sendSync(s, st, result.newIndex());
                }
                case OP_RENAME -> {
                    var result = StaffPages.rename(st, m.a, m.name);
                    if (result != StaffPages.Result.OK) {
                        sendIncremental(s, st, statusKey(result), 0, List.of(), m.a); return;
                    }
                    sendIncremental(s, st, "", 0, List.of(), m.a);
                }
                case OP_SELECT -> {
                    if (!StaffPages.validIndex(m.a) || !StaffPages.exists(st, m.a)) return;
                    StaffPages.select(st, m.a);
                    sendIncremental(s, st, "", 0, List.of());
                }
                default -> { }
            }
        }
    }

    public static class MsgStaffPageWriteC2S implements CustomPacketPayload {
        public static final Type<MsgStaffPageWriteC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "page_write"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffPageWriteC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> {
                    buf.writeInt(msg.pageIndex);
                    buf.writeInt(msg.iotaTags == null ? 0 : msg.iotaTags.size());
                    if (msg.iotaTags != null) for (CompoundTag t : msg.iotaTags) if (t != null) buf.writeNbt(t);
                },
                buf -> {
                    int p = buf.readInt();
                    int c = buf.readInt(); List<CompoundTag> tags = new ArrayList<>();
                    for (int j = 0; j < c; j++) { CompoundTag t = buf.readNbt(); if (t != null) tags.add(t); }
                    return new MsgStaffPageWriteC2S(p, tags);
                });
        final int pageIndex;
        final List<CompoundTag> iotaTags;
        public MsgStaffPageWriteC2S(int p, List<CompoundTag> tags) { pageIndex = p; iotaTags = tags; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffPageWriteC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> {
                var st = staffInMainHand(sp); if (st == null) return;
                var result = StaffPages.writeContent(st, msg.pageIndex, msg.iotaTags == null ? List.of() : msg.iotaTags);
                if (result != StaffPages.Result.OK) {
                    sendIncremental(sp, st, statusKey(result), 0, List.of(), msg.pageIndex);
                    return;
                }
                sendIncremental(sp, st, "", 0, List.of());
            });
        }
    }

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
                if (!Platform.isModLoaded("hexparse")) {
                    sendToPlayer(sp, new MsgStaffGreatSpellCheckS2C(msg.actionId, false, "", ""));
                    return;
                }
                try {
                    var res = HexParseBridge.checkGreatSpell(sp.serverLevel(), msg.actionId);
                    sendToPlayer(sp, new MsgStaffGreatSpellCheckS2C(msg.actionId, res.usable(), res.signature(), res.startDir()));
                } catch (Throwable ignored) {
                    sendToPlayer(sp, new MsgStaffGreatSpellCheckS2C(msg.actionId, false, "", ""));
                }
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
                if (s instanceof StaffSyncReceiver scr) scr.onGreatSpellCheck(msg.actionId, msg.usable, msg.signature, msg.startDir);
            });
        }
    }

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

    // ==================== IDE 双向 HexParse 消息（带 requestId + 原始 NBT 传输） ====================
    public static class MsgIdeParseToCodeC2S implements CustomPacketPayload {
        public static final Type<MsgIdeParseToCodeC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "ide_parse_to_code"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgIdeParseToCodeC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> {
                    buf.writeInt(msg.requestId); buf.writeInt(msg.pageIndex);
                    buf.writeBoolean(msg.listNbt != null);
                    if (msg.listNbt != null) buf.writeNbt(msg.listNbt);
                },
                buf -> {
                    int req = buf.readInt(), page = buf.readInt();
                    CompoundTag nbt = buf.readBoolean() ? buf.readNbt() : null;
                    return new MsgIdeParseToCodeC2S(req, page, nbt);
                });
        final int requestId, pageIndex;
        final CompoundTag listNbt;
        public MsgIdeParseToCodeC2S(int requestId, int pageIndex, CompoundTag listNbt) {
            this.requestId = requestId; this.pageIndex = pageIndex; this.listNbt = listNbt;
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgIdeParseToCodeC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> {
                if (!Platform.isModLoaded("hexparse")) {
                    sendToPlayer(sp, new MsgIdeParseToCodeS2C(msg.requestId, msg.pageIndex, "", "hexparse_missing"));
                    return;
                }
                try {
                    String code = HexParseBridge.parseIotaNbt(msg.listNbt, sp);
                    sendToPlayer(sp, new MsgIdeParseToCodeS2C(msg.requestId, msg.pageIndex, code == null ? "" : code, ""));
                } catch (Throwable e) {
                    sendToPlayer(sp, new MsgIdeParseToCodeS2C(msg.requestId, msg.pageIndex, "", e.getMessage() == null ? "parse_error" : e.getMessage()));
                }
            });
        }
    }

    public static class MsgIdeParseToCodeS2C implements CustomPacketPayload {
        public static final Type<MsgIdeParseToCodeS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "ide_parse_to_code_s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgIdeParseToCodeS2C> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> {
                    buf.writeInt(msg.requestId); buf.writeInt(msg.pageIndex);
                    buf.writeUtf(msg.code == null ? "" : msg.code);
                    buf.writeUtf(msg.error == null ? "" : msg.error);
                },
                buf -> new MsgIdeParseToCodeS2C(buf.readInt(), buf.readInt(), buf.readUtf(), buf.readUtf()));
        final int requestId, pageIndex;
        final String code, error;
        public MsgIdeParseToCodeS2C(int requestId, int pageIndex, String code, String error) {
            this.requestId = requestId; this.pageIndex = pageIndex; this.code = code; this.error = error;
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgIdeParseToCodeS2C msg, NetworkManager.PacketContext ctx) {
            Minecraft.getInstance().execute(() -> {
                var s = Minecraft.getInstance().screen;
                if (s instanceof StaffSyncReceiver scr) scr.onIdeParseToCode(msg.requestId, msg.pageIndex, msg.code, msg.error);
            });
        }
    }

    public static class MsgIdeParseToIotasC2S implements CustomPacketPayload {
        public static final Type<MsgIdeParseToIotasC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "ide_parse_to_iotas"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgIdeParseToIotasC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> {
                    buf.writeInt(msg.requestId); buf.writeInt(msg.pageIndex);
                    buf.writeUtf(msg.code == null ? "" : msg.code);
                },
                buf -> new MsgIdeParseToIotasC2S(buf.readInt(), buf.readInt(), buf.readUtf()));
        final int requestId, pageIndex;
        final String code;
        public MsgIdeParseToIotasC2S(int requestId, int pageIndex, String code) {
            this.requestId = requestId; this.pageIndex = pageIndex; this.code = code;
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgIdeParseToIotasC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> {
                if (!Platform.isModLoaded("hexparse")) {
                    sendToPlayer(sp, new MsgIdeParseToIotasS2C(msg.requestId, msg.pageIndex, false, List.of(), "hexparse_missing", -1));
                    return;
                }
                var res = HexParseBridge.parseCodeToRawTags(msg.code, sp);
                sendToPlayer(sp, new MsgIdeParseToIotasS2C(msg.requestId, msg.pageIndex, res.ok(), res.tags(), res.error(), res.errorLine()));
            });
        }
    }

    public static class MsgIdeParseToIotasS2C implements CustomPacketPayload {
        public static final Type<MsgIdeParseToIotasS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "ide_parse_to_iotas_s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgIdeParseToIotasS2C> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> {
                    buf.writeInt(msg.requestId); buf.writeInt(msg.pageIndex); buf.writeBoolean(msg.ok);
                    List<CompoundTag> tags = msg.iotaTags == null ? List.of() : msg.iotaTags;
                    buf.writeInt(tags.size());
                    for (CompoundTag t : tags) if (t != null) buf.writeNbt(t);
                    buf.writeUtf(msg.error == null ? "" : msg.error);
                    buf.writeInt(msg.errorLine);
                },
                buf -> {
                    int req = buf.readInt(), page = buf.readInt();
                    boolean ok = buf.readBoolean();
                    int n = buf.readInt();
                    List<CompoundTag> tags = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        CompoundTag t = buf.readNbt();
                        if (t != null) tags.add(t);
                    }
                    return new MsgIdeParseToIotasS2C(req, page, ok, tags, buf.readUtf(), buf.readInt());
                });
        final int requestId, pageIndex;
        final boolean ok;
        final List<CompoundTag> iotaTags;
        final String error;
        final int errorLine;
        public MsgIdeParseToIotasS2C(int requestId, int pageIndex, boolean ok, List<CompoundTag> iotaTags, String error, int errorLine) {
            this.requestId = requestId; this.pageIndex = pageIndex; this.ok = ok;
            this.iotaTags = iotaTags; this.error = error; this.errorLine = errorLine;
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgIdeParseToIotasS2C msg, NetworkManager.PacketContext ctx) {
            Minecraft.getInstance().execute(() -> {
                var s = Minecraft.getInstance().screen;
                if (s instanceof StaffSyncReceiver scr) scr.onIdeParseToIotas(msg.requestId, msg.pageIndex, msg.ok, msg.iotaTags, msg.error, msg.errorLine);
            });
        }
    }
}
