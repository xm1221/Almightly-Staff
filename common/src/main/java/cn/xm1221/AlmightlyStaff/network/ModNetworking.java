package cn.xm1221.AlmightlyStaff.network;

import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.eval.env.PackagedItemCastEnv;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.common.msgs.MsgNewSpiralPatternsS2C;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.items.ItemAlmightlyStaff;
import dev.architectury.networking.NetworkChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class ModNetworking {
    public static final NetworkChannel CHANNEL = NetworkChannel.create(
        new ResourceLocation(AlmightlyStaffMod.MOD_ID, "network"));

    public static void init() {
        CHANNEL.register(MsgShiftScrollC2S.class, MsgShiftScrollC2S::encode, MsgShiftScrollC2S::decode, MsgShiftScrollC2S::handle);
        CHANNEL.register(MsgAlmightlyStaffModeC2S.class, MsgAlmightlyStaffModeC2S::encode, MsgAlmightlyStaffModeC2S::decode, MsgAlmightlyStaffModeC2S::handle);
        CHANNEL.register(MsgStaffReadC2S.class, MsgStaffReadC2S::encode, MsgStaffReadC2S::decode, MsgStaffReadC2S::handle);
        CHANNEL.register(MsgStaffWriteC2S.class, MsgStaffWriteC2S::encode, MsgStaffWriteC2S::decode, MsgStaffWriteC2S::handle);
        CHANNEL.register(MsgStaffCastC2S.class, MsgStaffCastC2S::encode, MsgStaffCastC2S::decode, MsgStaffCastC2S::handle);
        CHANNEL.register(MsgStaffPageC2S.class, MsgStaffPageC2S::encode, MsgStaffPageC2S::decode, MsgStaffPageC2S::handle);
        CHANNEL.register(MsgStaffEscapePatternC2S.class, MsgStaffEscapePatternC2S::encode, MsgStaffEscapePatternC2S::decode, MsgStaffEscapePatternC2S::handle);
        CHANNEL.register(MsgStaffEscapeResultS2C.class, MsgStaffEscapeResultS2C::encode, MsgStaffEscapeResultS2C::decode, MsgStaffEscapeResultS2C::handle);
        CHANNEL.register(MsgStaffReadS2C.class, MsgStaffReadS2C::encode, MsgStaffReadS2C::decode, MsgStaffReadS2C::handle);
        CHANNEL.register(MsgParseToCodeC2S.class, MsgParseToCodeC2S::encode, MsgParseToCodeC2S::decode, MsgParseToCodeC2S::handle);
        CHANNEL.register(MsgParseToCodeS2C.class, MsgParseToCodeS2C::encode, MsgParseToCodeS2C::decode, MsgParseToCodeS2C::handle);
        CHANNEL.register(MsgParseToIotasC2S.class, MsgParseToIotasC2S::encode, MsgParseToIotasC2S::decode, MsgParseToIotasC2S::handle);
        CHANNEL.register(MsgParseToIotasS2C.class, MsgParseToIotasS2C::encode, MsgParseToIotasS2C::decode, MsgParseToIotasS2C::handle);
    }

    // ---- 滚轮翻页 ----
    public record MsgShiftScrollC2S(double mainHandDelta, double offHandDelta,
                                    boolean isCtrl, boolean invertSpellbook, boolean invertAbacus) {
        public static void encode(MsgShiftScrollC2S msg, FriendlyByteBuf buf) {
            buf.writeDouble(msg.mainHandDelta); buf.writeDouble(msg.offHandDelta);
            buf.writeBoolean(msg.isCtrl); buf.writeBoolean(msg.invertSpellbook); buf.writeBoolean(msg.invertAbacus);
        }
        public static MsgShiftScrollC2S decode(FriendlyByteBuf buf) { return new MsgShiftScrollC2S(buf.readDouble(), buf.readDouble(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()); }
        public static void handle(MsgShiftScrollC2S msg, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctxSupplier) {
            var s = ctxSupplier.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctxSupplier.get().queue(() -> {
                handleForHand(s, InteractionHand.MAIN_HAND, msg.mainHandDelta, msg.invertSpellbook);
                handleForHand(s, InteractionHand.OFF_HAND, msg.offHandDelta, msg.invertSpellbook);
            });
        }
        private static void handleForHand(ServerPlayer sender, InteractionHand hand, double delta, boolean inv) {
            if (delta == 0) return; var st = sender.getItemInHand(hand); if (!(st.getItem() instanceof ItemAlmightlyStaff)) return;
            if (inv) delta = -delta;
            var ni = ItemAlmightlyStaff.rotatePageIdx(st, delta < 0.0); var len = ItemAlmightlyStaff.highestPage(st); var sealed = ItemAlmightlyStaff.isSealed(st);
            MutableComponent c; var idxC = Component.literal(String.valueOf(ni)).withStyle(ChatFormatting.WHITE); var lenC = Component.literal(String.valueOf(len)).withStyle(ChatFormatting.WHITE);
            if (hand == InteractionHand.OFF_HAND && st.hasCustomHoverName()) {
                c = sealed ? Component.translatable("hexcasting.tooltip.spellbook.page_with_name.sealed", idxC, lenC, Component.literal("").withStyle(st.getRarity().color, ChatFormatting.ITALIC).append(st.getHoverName()), Component.translatable("hexcasting.tooltip.spellbook.sealed").withStyle(ChatFormatting.GOLD))
                    : Component.translatable("hexcasting.tooltip.spellbook.page_with_name", idxC, lenC, Component.literal("").withStyle(st.getRarity().color, ChatFormatting.ITALIC).append(st.getHoverName()));
            } else c = sealed ? Component.translatable("hexcasting.tooltip.spellbook.page.sealed", idxC, lenC, Component.translatable("hexcasting.tooltip.spellbook.sealed").withStyle(ChatFormatting.GOLD))
                : Component.translatable("hexcasting.tooltip.spellbook.page", idxC, lenC);
            sender.displayClientMessage(c.withStyle(ChatFormatting.GRAY), true);
        }
    }

    public record MsgAlmightlyStaffModeC2S() {
        public static void encode(MsgAlmightlyStaffModeC2S m, FriendlyByteBuf b) {} static MsgAlmightlyStaffModeC2S decode(FriendlyByteBuf b) { return new MsgAlmightlyStaffModeC2S(); }
        public static void handle(MsgAlmightlyStaffModeC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> { var st = s.getMainHandItem(); if (st.getItem() instanceof ItemAlmightlyStaff it) it.casting(s.level(), s, InteractionHand.MAIN_HAND); });
        }
    }

    // ---- IDE 消息 ----
    public record MsgStaffReadC2S() {
        public static void encode(MsgStaffReadC2S m, FriendlyByteBuf b) {} public static MsgStaffReadC2S decode(FriendlyByteBuf b) { return new MsgStaffReadC2S(); }
        public static void handle(MsgStaffReadC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> { var st = s.getMainHandItem(); if (st.getItem() instanceof ItemAlmightlyStaff it) CHANNEL.sendToPlayer(s, new MsgStaffReadS2C(it.readIota(st, s.serverLevel()))); });
        }
    }
    public record MsgStaffReadS2C(Iota iota) {
        public static void encode(MsgStaffReadS2C m, FriendlyByteBuf b) { b.writeBoolean(m.iota != null); if (m.iota != null) b.writeNbt(IotaType.serialize(m.iota)); }
        public static MsgStaffReadS2C decode(FriendlyByteBuf b) { if (!b.readBoolean()) return new MsgStaffReadS2C(null); try { return new MsgStaffReadS2C(IotaType.deserialize(b.readNbt(), null)); } catch (Exception e) { return new MsgStaffReadS2C(null); } }
        public static void handle(MsgStaffReadS2C m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) { var s = Minecraft.getInstance().screen; if (s instanceof cn.xm1221.AlmightlyStaff.gui.AlmightlyStaffIDEScreen ide) ide.onIotaReceived(m.iota); }
    }
    public record MsgStaffWriteC2S(Iota iota) {
        public static void encode(MsgStaffWriteC2S m, FriendlyByteBuf b) { b.writeBoolean(m.iota != null); if (m.iota != null) b.writeNbt(IotaType.serialize(m.iota)); }
        public static MsgStaffWriteC2S decode(FriendlyByteBuf b) { if (!b.readBoolean()) return new MsgStaffWriteC2S(null); return new MsgStaffWriteC2S(IotaType.deserialize(b.readNbt(), null)); }
        public static void handle(MsgStaffWriteC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) { var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return; ctx.get().queue(() -> { var st = s.getMainHandItem(); if (st.getItem() instanceof ItemAlmightlyStaff it) it.writeDatum(st, m.iota); }); }
    }
    public record MsgStaffCastC2S() {
        public static void encode(MsgStaffCastC2S m, FriendlyByteBuf b) {} public static MsgStaffCastC2S decode(FriendlyByteBuf b) { return new MsgStaffCastC2S(); }
        public static void handle(MsgStaffCastC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) { var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return; ctx.get().queue(() -> { var st = s.getMainHandItem(); if (st.getItem() instanceof ItemAlmightlyStaff it) it.casting(s.level(), s, InteractionHand.MAIN_HAND); }); }
    }
    public record MsgStaffPageC2S(int delta) {
        public static void encode(MsgStaffPageC2S m, FriendlyByteBuf b) { b.writeInt(m.delta); } public static MsgStaffPageC2S decode(FriendlyByteBuf b) { return new MsgStaffPageC2S(b.readInt()); }
        public static void handle(MsgStaffPageC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) { var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return; ctx.get().queue(() -> { var st = s.getMainHandItem(); if (st.getItem() instanceof ItemAlmightlyStaff) { ItemAlmightlyStaff.rotatePageIdx(st, m.delta < 0); CHANNEL.sendToPlayer(s, new MsgStaffReadS2C(((ItemAlmightlyStaff)st.getItem()).readIota(st, s.serverLevel()))); } }); }
    }
    public record MsgStaffEscapePatternC2S(HexPattern pattern) {
        public static void encode(MsgStaffEscapePatternC2S m, FriendlyByteBuf b) { b.writeNbt(m.pattern.serializeToNBT()); } public static MsgStaffEscapePatternC2S decode(FriendlyByteBuf b) { return new MsgStaffEscapePatternC2S(HexPattern.fromNBT(b.readNbt())); }
        public static void handle(MsgStaffEscapePatternC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) { var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return; ctx.get().queue(() -> { var st = s.getMainHandItem(); if (st.getItem() instanceof ItemAlmightlyStaff it) { it.writeDatum(st, null); it.writeDatum(st, new PatternIota(m.pattern)); it.casting(s.level(), s, InteractionHand.MAIN_HAND); CHANNEL.sendToPlayer(s, new MsgStaffEscapeResultS2C(it.readIota(st, s.serverLevel()))); } }); }
    }
    public record MsgStaffEscapeResultS2C(Iota iota) {
        public static void encode(MsgStaffEscapeResultS2C m, FriendlyByteBuf b) { b.writeBoolean(m.iota != null); if (m.iota != null) b.writeNbt(IotaType.serialize(m.iota)); }
        public static MsgStaffEscapeResultS2C decode(FriendlyByteBuf b) { if (!b.readBoolean()) return new MsgStaffEscapeResultS2C(null); try { return new MsgStaffEscapeResultS2C(IotaType.deserialize(b.readNbt(), null)); } catch (Exception e) { return new MsgStaffEscapeResultS2C(null); } }
        public static void handle(MsgStaffEscapeResultS2C m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) { var s = Minecraft.getInstance().screen; if (s instanceof cn.xm1221.AlmightlyStaff.gui.AlmightlyStaffIDEScreen ide) ide.onEscapeResult(m.iota); }
    }

    // ---- HexParse ----
    public record MsgParseToCodeC2S(List<Iota> iotas) {
        public static void encode(MsgParseToCodeC2S m, FriendlyByteBuf b) { b.writeNbt(IotaType.serialize(new ListIota(new ArrayList<>(m.iotas)))); }
        public static MsgParseToCodeC2S decode(FriendlyByteBuf b) { try { var i = IotaType.deserialize(b.readNbt(), null); var l = new ArrayList<Iota>(); if (i instanceof ListIota li) for (var x : li.getList()) l.add(x); else if (i != null) l.add(i); return new MsgParseToCodeC2S(l); } catch (Exception e) { return new MsgParseToCodeC2S(List.of()); } }
        public static void handle(MsgParseToCodeC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> {
                var tag = IotaType.serialize(new ListIota(new ArrayList<>(m.iotas)));
                try { String code = io.yukkuric.hexparse.parsers.ParserMain.ParseIotaNbt(tag, s, x -> x); CHANNEL.sendToPlayer(s, new MsgParseToCodeS2C(code)); }
                catch (Exception e) { CHANNEL.sendToPlayer(s, new MsgParseToCodeS2C("")); }
            });
        }
    }
    public record MsgParseToCodeS2C(String code) { public static void encode(MsgParseToCodeS2C m, FriendlyByteBuf b) { b.writeUtf(m.code); } public static MsgParseToCodeS2C decode(FriendlyByteBuf b) { return new MsgParseToCodeS2C(b.readUtf()); } public static void handle(MsgParseToCodeS2C m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) { var s = Minecraft.getInstance().screen; if (s instanceof cn.xm1221.AlmightlyStaff.gui.AlmightlyStaffIDEScreen ide) ide.onParseCode(m.code); } }
    public record MsgParseToIotasC2S(String code) { public static void encode(MsgParseToIotasC2S m, FriendlyByteBuf b) { b.writeUtf(m.code); } public static MsgParseToIotasC2S decode(FriendlyByteBuf b) { return new MsgParseToIotasC2S(b.readUtf()); } public static void handle(MsgParseToIotasC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) { var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return; ctx.get().queue(() -> { try { var tag = io.yukkuric.hexparse.parsers.ParserMain.ParseCode(m.code, s); CHANNEL.sendToPlayer(s, new MsgParseToIotasS2C(IotaType.deserialize(tag, s.serverLevel()))); } catch (Exception e) { CHANNEL.sendToPlayer(s, new MsgParseToIotasS2C(null)); } }); } }
    public record MsgParseToIotasS2C(Iota iota) { public static void encode(MsgParseToIotasS2C m, FriendlyByteBuf b) { b.writeBoolean(m.iota != null); if (m.iota != null) b.writeNbt(IotaType.serialize(m.iota)); } public static MsgParseToIotasS2C decode(FriendlyByteBuf b) { if (!b.readBoolean()) return new MsgParseToIotasS2C(null); try { return new MsgParseToIotasS2C(IotaType.deserialize(b.readNbt(), null)); } catch (Exception e) { return new MsgParseToIotasS2C(null); } } public static void handle(MsgParseToIotasS2C m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) { var s = Minecraft.getInstance().screen; if (s instanceof cn.xm1221.AlmightlyStaff.gui.AlmightlyStaffIDEScreen ide) ide.onParseResult(m.iota); } }
}
