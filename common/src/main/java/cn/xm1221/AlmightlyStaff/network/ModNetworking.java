package cn.xm1221.AlmightlyStaff.network;

import at.petrak.hexcasting.api.casting.iota.*;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.gui.AlmightlyStaffIDEScreen;
import cn.xm1221.AlmightlyStaff.items.ItemAlmightlyStaff;
import dev.architectury.networking.NetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;

public class ModNetworking {

    public static void init() {
        // C2S
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgShiftScrollC2S.TYPE, MsgShiftScrollC2S.STREAM_CODEC, MsgShiftScrollC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgAlmightlyStaffModeC2S.TYPE, MsgAlmightlyStaffModeC2S.STREAM_CODEC, MsgAlmightlyStaffModeC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffReadC2S.TYPE, MsgStaffReadC2S.STREAM_CODEC, MsgStaffReadC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffWriteC2S.TYPE, MsgStaffWriteC2S.STREAM_CODEC, MsgStaffWriteC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffCastC2S.TYPE, MsgStaffCastC2S.STREAM_CODEC, MsgStaffCastC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffPageC2S.TYPE, MsgStaffPageC2S.STREAM_CODEC, MsgStaffPageC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgStaffEscapePatternC2S.TYPE, MsgStaffEscapePatternC2S.STREAM_CODEC, MsgStaffEscapePatternC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgParseToCodeC2S.TYPE, MsgParseToCodeC2S.STREAM_CODEC, MsgParseToCodeC2S::handle);
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgParseToIotasC2S.TYPE, MsgParseToIotasC2S.STREAM_CODEC, MsgParseToIotasC2S::handle);
        // S2C
        NetworkManager.registerReceiver(NetworkManager.s2c(), MsgStaffReadS2C.TYPE, MsgStaffReadS2C.STREAM_CODEC, MsgStaffReadS2C::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), MsgStaffEscapeResultS2C.TYPE, MsgStaffEscapeResultS2C.STREAM_CODEC, MsgStaffEscapeResultS2C::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), MsgParseToCodeS2C.TYPE, MsgParseToCodeS2C.STREAM_CODEC, MsgParseToCodeS2C::handle);
        NetworkManager.registerReceiver(NetworkManager.s2c(), MsgParseToIotasS2C.TYPE, MsgParseToIotasS2C.STREAM_CODEC, MsgParseToIotasS2C::handle);
    }

    public static void sendToServer(CustomPacketPayload p) { NetworkManager.sendToServer(p); }
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload p) { NetworkManager.sendToPlayer(player, p); }

    // ==================== 滚轮翻页 ====================
    public static class MsgShiftScrollC2S implements CustomPacketPayload {
        public static final Type<MsgShiftScrollC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "scroll"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgShiftScrollC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeDouble(msg.mainHandDelta); buf.writeDouble(msg.offHandDelta); buf.writeBoolean(msg.invertSpellbook); buf.writeBoolean(msg.invertAbacus); },
                buf -> new MsgShiftScrollC2S(buf.readDouble(), buf.readDouble(), buf.readBoolean(), buf.readBoolean()));
        final double mainHandDelta, offHandDelta;
        final boolean invertSpellbook, invertAbacus;
        public MsgShiftScrollC2S(double m, double o, boolean is, boolean ia) { mainHandDelta = m; offHandDelta = o; invertSpellbook = is; invertAbacus = ia; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgShiftScrollC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> { hfh(sp, InteractionHand.MAIN_HAND, msg.mainHandDelta, msg.invertSpellbook); hfh(sp, InteractionHand.OFF_HAND, msg.offHandDelta, msg.invertAbacus); });
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

    // ==================== IDE Read ====================
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
                (buf, msg) -> { boolean h = msg.iota != null; buf.writeBoolean(h); if (h) buf.writeNbt(IotaType.TYPED_STREAM_CODEC.encode(buf, msg.iota)); },
                buf -> { if (!buf.readBoolean()) return new MsgStaffReadS2C(null); try { return new MsgStaffReadS2C(IotaType.TYPED_STREAM_CODEC.decode(buf)); } catch (Exception e) { return new MsgStaffReadS2C(null); } });
        final Iota iota; public MsgStaffReadS2C(Iota i) { iota = i; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffReadS2C msg, NetworkManager.PacketContext ctx) { var s = Minecraft.getInstance().screen; if (s instanceof AlmightlyStaffIDEScreen ide) ide.onIotaReceived(msg.iota); }
    }

    // ==================== IDE Write ====================
    public static class MsgStaffWriteC2S implements CustomPacketPayload {
        public static final Type<MsgStaffWriteC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "staff_write"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffWriteC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { boolean h = msg.iota != null; buf.writeBoolean(h); if (h) buf.writeNbt(IotaType.TYPED_STREAM_CODEC.encode(buf, msg.iota)); },
                buf -> { if (!buf.readBoolean()) return new MsgStaffWriteC2S(null); return new MsgStaffWriteC2S(IotaType.TYPED_STREAM_CODEC.decode(buf)); });
        final Iota iota; public MsgStaffWriteC2S(Iota i) { iota = i; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffWriteC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> { var st = sp.getMainHandItem(); if (st.getItem() instanceof ItemAlmightlyStaff it) it.writeDatum(st, msg.iota); });
        }
    }

    // ==================== IDE Cast ====================
    public static class MsgStaffCastC2S implements CustomPacketPayload {
        public static final Type<MsgStaffCastC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "staff_cast"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffCastC2S> STREAM_CODEC = StreamCodec.of((buf, msg) -> {}, buf -> new MsgStaffCastC2S());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffCastC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> { var st = sp.getMainHandItem(); if (st.getItem() instanceof ItemAlmightlyStaff it) it.casting(sp.level(), sp, InteractionHand.MAIN_HAND); });
        }
    }

    // ==================== IDE Page ====================
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

    // ==================== IDE Escape ====================
    public static class MsgStaffEscapePatternC2S implements CustomPacketPayload {
        public static final Type<MsgStaffEscapePatternC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "staff_esc_pat"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgStaffEscapePatternC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> buf.writeNbt(msg.pattern.serializeToNBT()), buf -> new MsgStaffEscapePatternC2S(HexPattern.fromNBT(buf.readNbt())));
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
                (buf, msg) -> { boolean h = msg.iota != null; buf.writeBoolean(h); if (h) buf.writeNbt(IotaType.TYPED_STREAM_CODEC.encode(buf, msg.iota)); },
                buf -> { if (!buf.readBoolean()) return new MsgStaffEscapeResultS2C(null); try { return new MsgStaffEscapeResultS2C(IotaType.TYPED_STREAM_CODEC.decode(buf)); } catch (Exception e) { return new MsgStaffEscapeResultS2C(null); } });
        final Iota iota; public MsgStaffEscapeResultS2C(Iota i) { iota = i; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgStaffEscapeResultS2C msg, NetworkManager.PacketContext ctx) { var s = Minecraft.getInstance().screen; if (s instanceof AlmightlyStaffIDEScreen ide) ide.onEscapeResult(msg.iota); }
    }

    // ==================== HexParse Iotas→Code ====================
    public static class MsgParseToCodeC2S implements CustomPacketPayload {
        public static final Type<MsgParseToCodeC2S> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "parse_to_code"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgParseToCodeC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> buf.writeNbt(IotaType.serialize(new ListIota(new ArrayList<>(msg.iotas)))),
                buf -> { var l = new ArrayList<Iota>(); try { var i = IotaType.TYPED_STREAM_CODEC.decode(buf); if (i instanceof ListIota li) for (var x : li.getList()) l.add(x); else if (i != null) l.add(i); } catch (Exception ignored) {} return new MsgParseToCodeC2S(l); });
        final List<Iota> iotas; public MsgParseToCodeC2S(List<Iota> list) { iotas = list; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgParseToCodeC2S msg, NetworkManager.PacketContext ctx) {
            var s = ctx.getPlayer(); if (!(s instanceof ServerPlayer sp)) return;
            ctx.queue(() -> {
                try {
                    var pm = Class.forName("io.yukkuric.hexparse.parsers.ParserMain");
                    Object code = pm.getMethod("ParseIotaNbt", Iota.class, ServerPlayer.class, java.util.function.Function.class)
                        .invoke(null, new ListIota(new ArrayList<>(msg.iotas)), sp, (java.util.function.Function<String,String>) x -> x);
                    sendToPlayer(sp, new MsgParseToCodeS2C(code != null ? (String) code : ""));
                } catch (Exception e) { sendToPlayer(sp, new MsgParseToCodeS2C("")); }
            });
        }
    }
    public static class MsgParseToCodeS2C implements CustomPacketPayload {
        public static final Type<MsgParseToCodeS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "parse_to_code_s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgParseToCodeS2C> STREAM_CODEC = StreamCodec.of((buf, msg) -> buf.writeUtf(msg.code), buf -> new MsgParseToCodeS2C(buf.readUtf()));
        final String code; public MsgParseToCodeS2C(String c) { code = c; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgParseToCodeS2C msg, NetworkManager.PacketContext ctx) { var s = Minecraft.getInstance().screen; if (s instanceof AlmightlyStaffIDEScreen ide) ide.onParseCode(msg.code); }
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
                try {
                    var pm = Class.forName("io.yukkuric.hexparse.parsers.ParserMain");
                    Object iota = pm.getMethod("ParseCode", String.class, ServerPlayer.class).invoke(null, msg.code, sp);
                    sendToPlayer(sp, new MsgParseToIotasS2C((Iota) iota));
                } catch (Exception e) { sendToPlayer(sp, new MsgParseToIotasS2C(null)); }
            });
        }
    }
    public static class MsgParseToIotasS2C implements CustomPacketPayload {
        public static final Type<MsgParseToIotasS2C> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "parse_to_iotas_s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgParseToIotasS2C> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { boolean h = msg.iota != null; buf.writeBoolean(h); if (h) buf.writeNbt(IotaType.TYPED_STREAM_CODEC.encode(buf, msg.iota)); },
                buf -> { if (!buf.readBoolean()) return new MsgParseToIotasS2C(null); try { return new MsgParseToIotasS2C(IotaType.TYPED_STREAM_CODEC.decode(buf)); } catch (Exception e) { return new MsgParseToIotasS2C(null); } });
        final Iota iota; public MsgParseToIotasS2C(Iota i) { iota = i; }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        static void handle(MsgParseToIotasS2C msg, NetworkManager.PacketContext ctx) { var s = Minecraft.getInstance().screen; if (s instanceof AlmightlyStaffIDEScreen ide) ide.onParseResult(msg.iota); }
    }
}
