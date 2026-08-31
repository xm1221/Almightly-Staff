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
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

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
        // ---- 法术库 ----
        CHANNEL.register(MsgStaffLibReadC2S.class, MsgStaffLibReadC2S::encode, MsgStaffLibReadC2S::decode, MsgStaffLibReadC2S::handle);
        CHANNEL.register(MsgStaffLibSyncS2C.class, MsgStaffLibSyncS2C::encode, MsgStaffLibSyncS2C::decode, MsgStaffLibSyncS2C::handle);
        CHANNEL.register(MsgStaffPageRenameC2S.class, MsgStaffPageRenameC2S::encode, MsgStaffPageRenameC2S::decode, MsgStaffPageRenameC2S::handle);
        CHANNEL.register(MsgStaffPageSetIotaC2S.class, MsgStaffPageSetIotaC2S::encode, MsgStaffPageSetIotaC2S::decode, MsgStaffPageSetIotaC2S::handle);
        CHANNEL.register(MsgStaffPageAppendIotaC2S.class, MsgStaffPageAppendIotaC2S::encode, MsgStaffPageAppendIotaC2S::decode, MsgStaffPageAppendIotaC2S::handle);
        CHANNEL.register(MsgStaffPageSwapC2S.class, MsgStaffPageSwapC2S::encode, MsgStaffPageSwapC2S::decode, MsgStaffPageSwapC2S::handle);
        CHANNEL.register(MsgStaffPageSelectC2S.class, MsgStaffPageSelectC2S::encode, MsgStaffPageSelectC2S::decode, MsgStaffPageSelectC2S::handle);
        CHANNEL.register(MsgStaffPageWriteC2S.class, MsgStaffPageWriteC2S::encode, MsgStaffPageWriteC2S::decode, MsgStaffPageWriteC2S::handle);
        CHANNEL.register(MsgStaffGreatSpellCheckC2S.class, MsgStaffGreatSpellCheckC2S::encode, MsgStaffGreatSpellCheckC2S::decode, MsgStaffGreatSpellCheckC2S::handle);
        CHANNEL.register(MsgStaffGreatSpellCheckS2C.class, MsgStaffGreatSpellCheckS2C::encode, MsgStaffGreatSpellCheckS2C::decode, MsgStaffGreatSpellCheckS2C::handle);
        CHANNEL.register(MsgStaffCastClearStackC2S.class, MsgStaffCastClearStackC2S::encode, MsgStaffCastClearStackC2S::decode, MsgStaffCastClearStackC2S::handle);
        CHANNEL.register(MsgStaffShareSpellC2S.class, MsgStaffShareSpellC2S::encode, MsgStaffShareSpellC2S::decode, MsgStaffShareSpellC2S::handle);
        CHANNEL.register(MsgStaffShareSpellS2C.class, MsgStaffShareSpellS2C::encode, MsgStaffShareSpellS2C::decode, MsgStaffShareSpellS2C::handle);
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
        public static void handle(MsgStaffReadS2C m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) { var s = Minecraft.getInstance().screen;  }
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
        public static void handle(MsgStaffEscapeResultS2C m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) { var s = Minecraft.getInstance().screen;  }
    }

    // ---- HexParse ----
    /** 代码请求：携带客户端组装好的 ListIota 原始 NBT，服务端直接解析。 */
    public record MsgParseToCodeC2S(CompoundTag listNbt) {
        public static void encode(MsgParseToCodeC2S m, FriendlyByteBuf b) { if (m.listNbt != null) b.writeNbt(m.listNbt); }
        public static MsgParseToCodeC2S decode(FriendlyByteBuf b) { return new MsgParseToCodeC2S(b.readNbt()); }
        public static void handle(MsgParseToCodeC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> {
                try { String code = io.yukkuric.hexparse.parsers.ParserMain.ParseIotaNbt(m.listNbt, s, x -> x); CHANNEL.sendToPlayer(s, new MsgParseToCodeS2C(code)); }
                catch (Exception e) { CHANNEL.sendToPlayer(s, new MsgParseToCodeS2C("")); }
            });
        }
    }
    public record MsgParseToCodeS2C(String code) { public static void encode(MsgParseToCodeS2C m, FriendlyByteBuf b) { b.writeUtf(m.code); } public static MsgParseToCodeS2C decode(FriendlyByteBuf b) { return new MsgParseToCodeS2C(b.readUtf()); } public static void handle(MsgParseToCodeS2C m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) { Minecraft.getInstance().execute(() -> { var s = Minecraft.getInstance().screen; if (s instanceof cn.xm1221.AlmightlyStaff.gui.StaffLibScreen scr) scr.onParseCode(m.code); }); } }
    public record MsgParseToIotasC2S(String code) { public static void encode(MsgParseToIotasC2S m, FriendlyByteBuf b) { b.writeUtf(m.code); } public static MsgParseToIotasC2S decode(FriendlyByteBuf b) { return new MsgParseToIotasC2S(b.readUtf()); } public static void handle(MsgParseToIotasC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) { var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return; ctx.get().queue(() -> { try { var tag = io.yukkuric.hexparse.parsers.ParserMain.ParseCode(m.code, s); CHANNEL.sendToPlayer(s, new MsgParseToIotasS2C(IotaType.deserialize(tag, s.serverLevel()))); } catch (Exception e) { CHANNEL.sendToPlayer(s, new MsgParseToIotasS2C(null)); } }); } }
    public record MsgParseToIotasS2C(Iota iota) { public static void encode(MsgParseToIotasS2C m, FriendlyByteBuf b) { b.writeBoolean(m.iota != null); if (m.iota != null) b.writeNbt(IotaType.serialize(m.iota)); } public static MsgParseToIotasS2C decode(FriendlyByteBuf b) { if (!b.readBoolean()) return new MsgParseToIotasS2C(null); try { return new MsgParseToIotasS2C(IotaType.deserialize(b.readNbt(), null)); } catch (Exception e) { return new MsgParseToIotasS2C(null); } } public static void handle(MsgParseToIotasS2C m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) { Minecraft.getInstance().execute(() -> { var s = Minecraft.getInstance().screen; if (s instanceof cn.xm1221.AlmightlyStaff.gui.StaffParseScreen scr) scr.onParseResult(m.iota); }); } }

    // ==================== 法术库（页同步） ====================
    public record PageData(int pageIndex, String name, List<CompoundTag> iotas) {
        public PageData { iotas = iotas == null ? new ArrayList<>() : new ArrayList<>(iotas); }
    }

    /** 客户端由 iota 原始 NBT 标签组装 ListIota 的序列化标签（反序列化在服务端）。 */
    public static CompoundTag buildListTag(List<CompoundTag> tags) {
        CompoundTag out = new CompoundTag();
        out.putString(HexIotaTypes.KEY_TYPE, HexIotaTypes.REGISTRY.getKey(HexIotaTypes.LIST).toString());
        ListTag items = new ListTag();
        for (CompoundTag t : tags) if (t != null) items.add(t);
        out.put(HexIotaTypes.KEY_DATA, items);
        return out;
    }

    /** 服务端反序列化页内 iota（可解析实体），返回原始序列化 NBT 标签；客户端不做反序列化。 */
    private static List<CompoundTag> readPageIotaTags(ItemStack stack, int pageIndex, net.minecraft.server.level.ServerLevel level) {
        var pages = at.petrak.hexcasting.api.utils.NBTHelper.getCompound(stack, at.petrak.hexcasting.common.items.storage.ItemSpellbook.TAG_PAGES);
        List<CompoundTag> list = new ArrayList<>();
        if (pages != null && pages.contains(String.valueOf(pageIndex), net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            try {
                Iota i = IotaType.deserialize(pages.getCompound(String.valueOf(pageIndex)), level);
                if (i instanceof ListIota li) { for (var x : li.getList()) list.add(IotaType.serialize(x)); }
                else if (i != null) list.add(IotaType.serialize(i));
            } catch (Exception ignored) { }
        }
        return list;
    }

    private static String pageName(ItemStack stack, int pageIndex) {
        var names = at.petrak.hexcasting.api.utils.NBTHelper.getCompound(stack, at.petrak.hexcasting.common.items.storage.ItemSpellbook.TAG_PAGE_NAMES);
        if (names == null) return "";
        String json = names.getString(String.valueOf(pageIndex));
        if (json.isEmpty()) return "";
        try { return Component.Serializer.fromJson(json).getString(); } catch (Exception e) { return ""; }
    }

    private static void setPageName(ItemStack stack, int pageIndex, String name) {
        var names = at.petrak.hexcasting.api.utils.NBTHelper.getOrCreateCompound(stack, at.petrak.hexcasting.common.items.storage.ItemSpellbook.TAG_PAGE_NAMES);
        String key = String.valueOf(pageIndex);
        if (name == null || name.isBlank()) names.remove(key);
        else names.putString(key, Component.Serializer.toJson(Component.literal(name)));
        if (names.isEmpty()) at.petrak.hexcasting.api.utils.NBTHelper.remove(stack, at.petrak.hexcasting.common.items.storage.ItemSpellbook.TAG_PAGE_NAMES);
    }

    private static void selectPage(ItemStack stack, int pageIndex) {
        at.petrak.hexcasting.api.utils.NBTHelper.putInt(stack, at.petrak.hexcasting.common.items.storage.ItemSpellbook.TAG_SELECTED_PAGE, Math.max(1, pageIndex));
        String name = pageName(stack, pageIndex);
        if (!name.isEmpty()) stack.setHoverName(Component.literal(name));
        else stack.resetHoverName();
    }

    /** 服务端反序列化原始标签后写入页面。 */
    private static void writePageIotaList(ItemStack stack, int pageIndex, List<CompoundTag> tags, net.minecraft.server.level.ServerLevel level) {
        selectPage(stack, pageIndex);
        var item = (ItemAlmightlyStaff) stack.getItem();
        if (tags == null || tags.isEmpty()) { item.writeDatum(stack, null); return; }
        List<Iota> iotas = new ArrayList<>();
        for (CompoundTag t : tags) {
            try { Iota i = IotaType.deserialize(t, level); if (i != null) iotas.add(i); } catch (Exception ignored) { }
        }
        item.writeDatum(stack, new ListIota(iotas));
    }

    private static List<PageData> buildPages(ItemStack stack, net.minecraft.server.level.ServerLevel level) {
        List<PageData> pages = new ArrayList<>();
        for (int i = 1; i <= 64; i++) {
            List<CompoundTag> iotas = readPageIotaTags(stack, i, level);
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

    /** 分块同步：按字节分批，单包 ≤ 20000 字节（1.20.1 custom payload 上限 32767）。 */
    private static void sendSync(ServerPlayer s, ItemStack stack) {
        List<PageData> all = buildPages(stack, s.serverLevel());
        int selected = at.petrak.hexcasting.common.items.storage.ItemSpellbook.getPage(stack, 1);
        final int MAX_BYTES = 20000;
        List<PageData> batch = new ArrayList<>();
        int batchBytes = 4; // selectedPage int
        for (PageData p : all) {
            int pb = pageBytes(p);
            if (!batch.isEmpty() && batchBytes + pb > MAX_BYTES) {
                CHANNEL.sendToPlayer(s, new MsgStaffLibSyncS2C(new ArrayList<>(batch), selected, false, false));
                batch.clear();
                batchBytes = 4;
            }
            batch.add(p);
            batchBytes += pb;
        }
        if (!batch.isEmpty() || all.isEmpty()) {
            CHANNEL.sendToPlayer(s, new MsgStaffLibSyncS2C(new ArrayList<>(batch), selected, true, false));
        }
    }

    /** 构造单个页的 PageData。iota 与名字都为空表示该页已删除（客户端据此合并时移除）。 */
    private static PageData buildPage(ItemStack stack, int pageIndex, net.minecraft.server.level.ServerLevel level) {
        return new PageData(pageIndex, pageName(stack, pageIndex), readPageIotaTags(stack, pageIndex, level));
    }

    /** 增量同步：只回发受影响页（无入参=仅选中变化），并携带当前选中页；客户端按 pageIndex 合并。 */
    private static void sendPageSync(ServerPlayer s, ItemStack stack, int... pageIndexes) {
        int selected = at.petrak.hexcasting.common.items.storage.ItemSpellbook.getPage(stack, 1);
        List<PageData> pages = new ArrayList<>();
        for (int idx : pageIndexes) pages.add(buildPage(stack, idx, s.serverLevel()));
        CHANNEL.sendToPlayer(s, new MsgStaffLibSyncS2C(pages, selected, true, true));
    }

    private static ItemStack staffInMainHand(ServerPlayer s) {
        var st = s.getMainHandItem();
        return st.getItem() instanceof ItemAlmightlyStaff ? st : null;
    }

    public record MsgStaffLibReadC2S() {
        public static void encode(MsgStaffLibReadC2S m, FriendlyByteBuf b) {}
        public static MsgStaffLibReadC2S decode(FriendlyByteBuf b) { return new MsgStaffLibReadC2S(); }
        public static void handle(MsgStaffLibReadC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> { var st = staffInMainHand(s); if (st != null) sendSync(s, st); });
        }
    }
    /**
     * 页同步回包。lastChunk 只对全量同步有意义（首开分块累计到最后一包再应用）；
     * incremental=true 表示这是编辑后的增量回发：只携带受影响页（可能为空=仅选中变化），
     * 客户端按 pageIndex 合并到现有列表，而不是整表替换。
     */
    public record MsgStaffLibSyncS2C(List<PageData> pages, int selectedPage, boolean lastChunk, boolean incremental) {
        public static void encode(MsgStaffLibSyncS2C m, FriendlyByteBuf b) {
            b.writeInt(m.pages.size());
            for (PageData p : m.pages) {
                b.writeInt(p.pageIndex); b.writeUtf(p.name);
                b.writeInt(p.iotas.size());
                for (CompoundTag t : p.iotas) if (t != null) b.writeNbt(t);
            }
            b.writeInt(m.selectedPage);
            b.writeBoolean(m.lastChunk);
            b.writeBoolean(m.incremental);
        }
        public static MsgStaffLibSyncS2C decode(FriendlyByteBuf b) {
            int n = b.readInt(); List<PageData> pages = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int idx = b.readInt(); String name = b.readUtf();
                int c = b.readInt(); List<CompoundTag> iotas = new ArrayList<>();
                for (int j = 0; j < c; j++) {
                    CompoundTag tag = b.readNbt();
                    if (tag != null) iotas.add(tag);
                }
                pages.add(new PageData(idx, name, iotas));
            }
            return new MsgStaffLibSyncS2C(pages, b.readInt(), b.readBoolean(), b.readBoolean());
        }
        public static void handle(MsgStaffLibSyncS2C m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            Minecraft.getInstance().execute(() -> {
                var s = Minecraft.getInstance().screen;
                if (s instanceof cn.xm1221.AlmightlyStaff.gui.StaffLibScreen scr)
                    scr.onSyncChunk(m.pages, m.selectedPage, m.lastChunk, m.incremental);
            });
        }
    }
    public record MsgStaffPageRenameC2S(int pageIndex, String name) {
        public static void encode(MsgStaffPageRenameC2S m, FriendlyByteBuf b) { b.writeInt(m.pageIndex); b.writeUtf(m.name); }
        public static MsgStaffPageRenameC2S decode(FriendlyByteBuf b) { return new MsgStaffPageRenameC2S(b.readInt(), b.readUtf()); }
        public static void handle(MsgStaffPageRenameC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> { var st = staffInMainHand(s); if (st != null) { setPageName(st, m.pageIndex, m.name); sendPageSync(s, st, m.pageIndex); } });
        }
    }
    public record MsgStaffPageSetIotaC2S(int pageIndex, int slotIndex, CompoundTag iotaTag) {
        public static void encode(MsgStaffPageSetIotaC2S m, FriendlyByteBuf b) { b.writeInt(m.pageIndex); b.writeInt(m.slotIndex); b.writeBoolean(m.iotaTag != null); if (m.iotaTag != null) b.writeNbt(m.iotaTag); }
        public static MsgStaffPageSetIotaC2S decode(FriendlyByteBuf b) { int p = b.readInt(); int s = b.readInt(); CompoundTag t = null; if (b.readBoolean()) t = b.readNbt(); return new MsgStaffPageSetIotaC2S(p, s, t); }
        public static void handle(MsgStaffPageSetIotaC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> {
                var st = staffInMainHand(s); if (st == null) return;
                List<CompoundTag> list = readPageIotaTags(st, m.pageIndex, s.serverLevel());
                if (m.iotaTag == null) {
                    if (m.slotIndex >= 0 && m.slotIndex < list.size()) list.remove(m.slotIndex);
                } else {
                    if (m.slotIndex >= 0 && m.slotIndex < list.size()) list.set(m.slotIndex, m.iotaTag);
                    else list.add(m.iotaTag);
                }
                writePageIotaList(st, m.pageIndex, list, s.serverLevel());
                sendPageSync(s, st, m.pageIndex);
            });
        }
    }
    public record MsgStaffPageAppendIotaC2S(int pageIndex, CompoundTag iotaTag) {
        public static void encode(MsgStaffPageAppendIotaC2S m, FriendlyByteBuf b) { b.writeInt(m.pageIndex); if (m.iotaTag != null) b.writeNbt(m.iotaTag); }
        public static MsgStaffPageAppendIotaC2S decode(FriendlyByteBuf b) { return new MsgStaffPageAppendIotaC2S(b.readInt(), b.readNbt()); }
        public static void handle(MsgStaffPageAppendIotaC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> {
                var st = staffInMainHand(s); if (st == null || m.iotaTag == null) return;
                List<CompoundTag> list = readPageIotaTags(st, m.pageIndex, s.serverLevel());
                list.add(m.iotaTag);
                writePageIotaList(st, m.pageIndex, list, s.serverLevel());
                sendPageSync(s, st, m.pageIndex);
            });
        }
    }
    public record MsgStaffPageSwapC2S(int a, int b) {
        public static void encode(MsgStaffPageSwapC2S m, FriendlyByteBuf buf) { buf.writeInt(m.a); buf.writeInt(m.b); }
        public static MsgStaffPageSwapC2S decode(FriendlyByteBuf buf) { return new MsgStaffPageSwapC2S(buf.readInt(), buf.readInt()); }
        public static void handle(MsgStaffPageSwapC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> {
                var st = staffInMainHand(s); if (st == null || m.a == m.b) return;
                List<CompoundTag> la = readPageIotaTags(st, m.a, s.serverLevel());
                List<CompoundTag> lb = readPageIotaTags(st, m.b, s.serverLevel());
                String na = pageName(st, m.a), nb = pageName(st, m.b);
                writePageIotaList(st, m.a, lb, s.serverLevel()); writePageIotaList(st, m.b, la, s.serverLevel());
                setPageName(st, m.a, nb); setPageName(st, m.b, na);
                int sel = at.petrak.hexcasting.common.items.storage.ItemSpellbook.getPage(st, 1);
                if (sel == m.a) selectPage(st, m.b); else if (sel == m.b) selectPage(st, m.a);
                sendPageSync(s, st, m.a, m.b);
            });
        }
    }
    /** 整页写入：改名 + 写完整 iota 原始标签（空列表=清空页面）；反序列化在服务端。 */
    public record MsgStaffPageWriteC2S(int pageIndex, String name, List<CompoundTag> iotaTags) {
        public static void encode(MsgStaffPageWriteC2S m, FriendlyByteBuf b) {
            b.writeInt(m.pageIndex); b.writeUtf(m.name);
            b.writeInt(m.iotaTags == null ? 0 : m.iotaTags.size());
            if (m.iotaTags != null) for (CompoundTag t : m.iotaTags) if (t != null) b.writeNbt(t);
        }
        public static MsgStaffPageWriteC2S decode(FriendlyByteBuf b) {
            int p = b.readInt(); String name = b.readUtf();
            int c = b.readInt(); List<CompoundTag> tags = new ArrayList<>();
            for (int j = 0; j < c; j++) { CompoundTag t = b.readNbt(); if (t != null) tags.add(t); }
            return new MsgStaffPageWriteC2S(p, name, tags);
        }
        public static void handle(MsgStaffPageWriteC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> {
                var st = staffInMainHand(s); if (st == null) return;
                writePageIotaList(st, m.pageIndex, m.iotaTags == null ? List.of() : m.iotaTags, s.serverLevel());
                setPageName(st, m.pageIndex, m.name);
                sendPageSync(s, st, m.pageIndex);
            });
        }
    }
    public record MsgStaffPageSelectC2S(int pageIndex) {
        public static void encode(MsgStaffPageSelectC2S m, FriendlyByteBuf b) { b.writeInt(m.pageIndex); }
        public static MsgStaffPageSelectC2S decode(FriendlyByteBuf b) { return new MsgStaffPageSelectC2S(b.readInt()); }
        public static void handle(MsgStaffPageSelectC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> { var st = staffInMainHand(s); if (st != null) { selectPage(st, m.pageIndex); sendPageSync(s, st); } });
        }
    }
    /**
     * 卓越法术检查：服务端直接调 HexParse 的检查（GreatPatternUnlocker.isUnlocked）与图案获取（PatternMapper.mapPatternWorld），不经 ParseCode 转换。
     */
    public record MsgStaffGreatSpellCheckC2S(String actionId, int targetSlot) {
        public static void encode(MsgStaffGreatSpellCheckC2S m, FriendlyByteBuf b) { b.writeUtf(m.actionId); b.writeInt(m.targetSlot); }
        public static MsgStaffGreatSpellCheckC2S decode(FriendlyByteBuf b) { return new MsgStaffGreatSpellCheckC2S(b.readUtf(), b.readInt()); }
        public static void handle(MsgStaffGreatSpellCheckC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> {
                boolean usable = false; String sig = ""; String dir = "";
                try {
                    var level = s.serverLevel();
                    io.yukkuric.hexparse.hooks.PatternMapper.init(level);
                    // jar 版本 mapPatternWorld 值为序列化后的 NBT 标签，需反序列化得到图案
                    var tag = io.yukkuric.hexparse.hooks.PatternMapper.mapPatternWorld.get(m.actionId);
                    if (tag != null) {
                        Iota i = IotaType.deserialize(tag, level);
                        if (i instanceof at.petrak.hexcasting.api.casting.iota.PatternIota pi) {
                            boolean unlocked = io.yukkuric.hexparse.hooks.GreatPatternUnlocker.get(level).isUnlocked(m.actionId);
                            if (unlocked) {
                                usable = true;
                                sig = pi.getPattern().anglesSignature();
                                dir = pi.getPattern().getStartDir().name();
                            }
                        }
                    }
                } catch (Exception ignored) { }
                CHANNEL.sendToPlayer(s, new MsgStaffGreatSpellCheckS2C(m.actionId, usable, sig, dir));
            });
        }
    }
    public record MsgStaffGreatSpellCheckS2C(String actionId, boolean usable, String signature, String startDir) {
        public static void encode(MsgStaffGreatSpellCheckS2C m, FriendlyByteBuf b) { b.writeUtf(m.actionId); b.writeBoolean(m.usable); b.writeUtf(m.signature); b.writeUtf(m.startDir); }
        public static MsgStaffGreatSpellCheckS2C decode(FriendlyByteBuf b) { return new MsgStaffGreatSpellCheckS2C(b.readUtf(), b.readBoolean(), b.readUtf(), b.readUtf()); }
        public static void handle(MsgStaffGreatSpellCheckS2C m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            Minecraft.getInstance().execute(() -> {
                var s = Minecraft.getInstance().screen;
                if (s instanceof cn.xm1221.AlmightlyStaff.gui.StaffLibScreen scr) scr.onGreatSpellCheck(m.actionId, m.usable, m.signature, m.startDir);
            });
        }
    }
    /** 打开施法采集界面时清空玩家施法栈，保证采集到的都是本次施放产生的结果。 */
    public record MsgStaffCastClearStackC2S() {
        public static void encode(MsgStaffCastClearStackC2S m, FriendlyByteBuf b) { }
        public static MsgStaffCastClearStackC2S decode(FriendlyByteBuf b) { return new MsgStaffCastClearStackC2S(); }
        public static void handle(MsgStaffCastClearStackC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> {
                try {
                    // setStaffcastImage(null) → CCStaffcastImage 置空 tag → 下次 getStaffcastVM 返回全新空 image
                    at.petrak.hexcasting.xplat.IXplatAbstractions.INSTANCE.setStaffcastImage(s, null);
                } catch (Exception ignored) { }
            });
        }
    }
    /** 分享到聊天：客户端提交整页 ListIota 原始 NBT，服务端用 HexParse 公开 API 转成代码回传，
     *  客户端把代码发到聊天（hexparse 的 inline 联动会把图案渲染成图标，数字等非图案 iota 直接以代码形式显示）。 */
    public record MsgStaffShareSpellC2S(CompoundTag listNbt) {
        public static void encode(MsgStaffShareSpellC2S m, FriendlyByteBuf b) { if (m.listNbt != null) b.writeNbt(m.listNbt); }
        public static MsgStaffShareSpellC2S decode(FriendlyByteBuf b) { return new MsgStaffShareSpellC2S(b.readNbt()); }
        public static void handle(MsgStaffShareSpellC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> {
                String code = "";
                try {
                    code = io.yukkuric.hexparse.parsers.ParserMain.ParseIotaNbt(m.listNbt, s, x -> x);
                } catch (Exception ignored) { }
                CHANNEL.sendToPlayer(s, new MsgStaffShareSpellS2C(code == null ? "" : code));
            });
        }
    }
    public record MsgStaffShareSpellS2C(String code) {
        public static void encode(MsgStaffShareSpellS2C m, FriendlyByteBuf b) { b.writeUtf(m.code); }
        public static MsgStaffShareSpellS2C decode(FriendlyByteBuf b) { return new MsgStaffShareSpellS2C(b.readUtf()); }
        public static void handle(MsgStaffShareSpellS2C m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            Minecraft.getInstance().execute(() -> {
                var s = Minecraft.getInstance().screen;
                if (s instanceof cn.xm1221.AlmightlyStaff.gui.StaffLibScreen scr) scr.onShareCode(m.code);
            });
        }
    }
}