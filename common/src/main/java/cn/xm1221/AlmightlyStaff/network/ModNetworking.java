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
import cn.xm1221.AlmightlyStaff.parse.HexParseBridge;
import dev.architectury.networking.NetworkChannel;
import dev.architectury.platform.Platform;
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
        CHANNEL.register(MsgStaffPageOpC2S.class, MsgStaffPageOpC2S::encode, MsgStaffPageOpC2S::decode, MsgStaffPageOpC2S::handle);
        CHANNEL.register(MsgStaffPageWriteC2S.class, MsgStaffPageWriteC2S::encode, MsgStaffPageWriteC2S::decode, MsgStaffPageWriteC2S::handle);
        CHANNEL.register(MsgStaffGreatSpellCheckC2S.class, MsgStaffGreatSpellCheckC2S::encode, MsgStaffGreatSpellCheckC2S::decode, MsgStaffGreatSpellCheckC2S::handle);
        CHANNEL.register(MsgStaffGreatSpellCheckS2C.class, MsgStaffGreatSpellCheckS2C::encode, MsgStaffGreatSpellCheckS2C::decode, MsgStaffGreatSpellCheckS2C::handle);
        CHANNEL.register(MsgStaffCastClearStackC2S.class, MsgStaffCastClearStackC2S::encode, MsgStaffCastClearStackC2S::decode, MsgStaffCastClearStackC2S::handle);
        // ---- IDE 双向 HexParse ----
        CHANNEL.register(MsgIdeParseToCodeC2S.class, MsgIdeParseToCodeC2S::encode, MsgIdeParseToCodeC2S::decode, MsgIdeParseToCodeC2S::handle);
        CHANNEL.register(MsgIdeParseToCodeS2C.class, MsgIdeParseToCodeS2C::encode, MsgIdeParseToCodeS2C::decode, MsgIdeParseToCodeS2C::handle);
        CHANNEL.register(MsgIdeParseToIotasC2S.class, MsgIdeParseToIotasC2S::encode, MsgIdeParseToIotasC2S::decode, MsgIdeParseToIotasC2S::handle);
        CHANNEL.register(MsgIdeParseToIotasS2C.class, MsgIdeParseToIotasS2C::encode, MsgIdeParseToIotasS2C::decode, MsgIdeParseToIotasS2C::handle);
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
        public static void handle(MsgStaffEscapePatternC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null;
            if (s == null) return;
            ctx.get().queue(() -> {
                var st = s.getMainHandItem();
                if (!(st.getItem() instanceof ItemAlmightlyStaff it)) return;
                // 冷却中：先不改写当前页也不施放，否则会出现「页面被改成 escape 图案但法术没放出去」
                if (it.isOnCooldown(s)) return;
                it.writeDatum(st, null);
                it.writeDatum(st, new PatternIota(m.pattern));
                it.casting(s.level(), s, InteractionHand.MAIN_HAND);
                CHANNEL.sendToPlayer(s, new MsgStaffEscapeResultS2C(it.readIota(st, s.serverLevel())));
            });
        }
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
                if (!Platform.isModLoaded("hexparse")) {
                    CHANNEL.sendToPlayer(s, new MsgParseToCodeS2C(""));
                    return;
                }
                try {
                    String code = HexParseBridge.parseIotaNbt(m.listNbt, s);
                    CHANNEL.sendToPlayer(s, new MsgParseToCodeS2C(code));
                } catch (Throwable e) {
                    CHANNEL.sendToPlayer(s, new MsgParseToCodeS2C(""));
                }
            });
        }
    }
    public record MsgParseToCodeS2C(String code) { public static void encode(MsgParseToCodeS2C m, FriendlyByteBuf b) { b.writeUtf(m.code); } public static MsgParseToCodeS2C decode(FriendlyByteBuf b) { return new MsgParseToCodeS2C(b.readUtf()); } public static void handle(MsgParseToCodeS2C m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) { Minecraft.getInstance().execute(() -> { var s = Minecraft.getInstance().screen; if (s instanceof cn.xm1221.AlmightlyStaff.gui.StaffLibScreen scr) scr.onParseCode(m.code); }); } }
    public record MsgParseToIotasC2S(String code) {
        public static void encode(MsgParseToIotasC2S m, FriendlyByteBuf b) { b.writeUtf(m.code); }
        public static MsgParseToIotasC2S decode(FriendlyByteBuf b) { return new MsgParseToIotasC2S(b.readUtf()); }
        public static void handle(MsgParseToIotasC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> {
                if (!Platform.isModLoaded("hexparse")) {
                    CHANNEL.sendToPlayer(s, new MsgParseToIotasS2C(null));
                    return;
                }
                try {
                    CHANNEL.sendToPlayer(s, new MsgParseToIotasS2C(HexParseBridge.parseCodeToIota(m.code, s)));
                } catch (Throwable e) {
                    CHANNEL.sendToPlayer(s, new MsgParseToIotasS2C(null));
                }
            });
        }
    }
    public record MsgParseToIotasS2C(Iota iota) { public static void encode(MsgParseToIotasS2C m, FriendlyByteBuf b) { b.writeBoolean(m.iota != null); if (m.iota != null) b.writeNbt(IotaType.serialize(m.iota)); } public static MsgParseToIotasS2C decode(FriendlyByteBuf b) { if (!b.readBoolean()) return new MsgParseToIotasS2C(null); try { return new MsgParseToIotasS2C(IotaType.deserialize(b.readNbt(), null)); } catch (Exception e) { return new MsgParseToIotasS2C(null); } } public static void handle(MsgParseToIotasS2C m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) { Minecraft.getInstance().execute(() -> { var s = Minecraft.getInstance().screen; if (s instanceof cn.xm1221.AlmightlyStaff.gui.StaffParseScreen scr) scr.onParseResult(m.iota); }); } }

    // ==================== 法术库（页同步） ====================
    /** 页操作消息的 op 取值。 */
    public static final int OP_NEW = 0, OP_DUPLICATE = 1, OP_DELETE = 2, OP_REORDER = 3, OP_RENAME = 4, OP_SELECT = 5;

    /**
     * 单页数据。空页（名字与内容都空）也是合法的一页：页的存在由 {@code pages} 里的 key 决定，
     * 不再用"空页 = 已删除"的隐式约定。
     */
    public record PageData(int pageIndex, String name, List<CompoundTag> iotas, boolean sealed) {
        public PageData { iotas = iotas == null ? new ArrayList<>() : new ArrayList<>(iotas); }
    }

    /** 客户端由 iota 原始 NBT 标签组装 ListIota 的序列化标签（反序列化在服务端）。 */
    public static CompoundTag buildListTag(List<CompoundTag> tags) {
        return StaffPages.listTagOf(tags, StaffPages.emptyContent());
    }

    /** 拒绝原因 → 语言键（客户端拼成 almightly_staff.gui.*）；空串表示成功。 */
    private static String statusKey(StaffPages.Result result) {
        return switch (result) {
            case OK -> "";
            case SEALED -> "page_sealed";
            case FULL -> "page_full";
            default -> "page_invalid";
        };
    }

    /** 构造单个页的 PageData（空页也返回，客户端按页号合并）。 */
    private static PageData buildPage(ItemStack stack, int pageIndex) {
        return new PageData(pageIndex, StaffPages.name(stack, pageIndex),
            StaffPages.contentTags(stack, pageIndex), StaffPages.isSealed(stack, pageIndex));
    }

    /** 全量构造：按 {@link StaffPages#indices} 枚举，空页也在内。 */
    private static List<PageData> buildPages(ItemStack stack) {
        List<PageData> pages = new ArrayList<>();
        for (int pageIndex : StaffPages.indices(stack)) pages.add(buildPage(stack, pageIndex));
        return pages;
    }

    /** 单页序列化后的字节数（用与真实编码一致的写法精确测量）。 */
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

    /** 分块同步：按字节分批，单包 ≤ 20000 字节（1.20.1 custom payload 上限 32767）。 */
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
                CHANNEL.sendToPlayer(s, new MsgStaffLibSyncS2C(new ArrayList<>(batch), castPage, focusPage, List.of(), false, false, "", staffUuid));
                batch.clear();
                batchBytes = 16;
            }
            batch.add(p);
            batchBytes += pb;
        }
        if (!batch.isEmpty() || all.isEmpty()) {
            CHANNEL.sendToPlayer(s, new MsgStaffLibSyncS2C(new ArrayList<>(batch), castPage, focusPage, List.of(), true, false, "", staffUuid));
        }
    }

    /**
     * 增量同步：只回发受影响页（或仅施放页），客户端按 pageIndex 合并。
     *
     * @param status   拒绝原因语言键，空串 = 成功
     * @param focusPage &gt;0 时客户端应切到该页（新建/复制/删除后的落点），0 = 不变
     * @param removed  被删除的页号，客户端据此移除（不再用"空页 = 删除"）
     */
    private static void sendIncremental(ServerPlayer s, ItemStack stack, String status, int focusPage,
                                        List<Integer> removed, int... pageIndexes) {
        String staffUuid = StaffPages.ensureStaffUuid(stack);
        List<PageData> pages = new ArrayList<>();
        for (int idx : pageIndexes) if (StaffPages.exists(stack, idx)) pages.add(buildPage(stack, idx));
        CHANNEL.sendToPlayer(s, new MsgStaffLibSyncS2C(pages, StaffPages.castPage(stack), focusPage,
            removed == null ? List.of() : removed, true, true, status == null ? "" : status, staffUuid));
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
            ctx.get().queue(() -> {
                var st = staffInMainHand(s);
                if (st == null) return;
                // 打开界面时先把"悬停名"与当前施放页对齐：Hex 的 inventoryTick 每 tick 会把悬停名
                // 写回当前页的页名，两者一旦不一致就会出现"改名回滚 / 删除的页复活"（B1/B2/B3）
                StaffPages.select(st, StaffPages.castPage(st));
                sendSync(s, st, 0);
            });
        }
    }
    /**
     * 页同步回包。lastChunk 只对全量同步有意义（首开分块累计到最后一包再应用）；
     * incremental=true 表示这是操作后的增量回发：只携带受影响页（可能为空=仅施放页变化），
     * 客户端按 pageIndex 合并到现有列表，而不是整表替换。
     *
     * @param castPage  V 键施放页（▶）
     * @param focusPage &gt;0 时客户端应切到该页；0 = 不改
     * @param removed   本次被删除的页号
     * @param status    拒绝原因语言键，空串 = 成功
     */
    public record MsgStaffLibSyncS2C(List<PageData> pages, int castPage, int focusPage, List<Integer> removed,
                                     boolean lastChunk, boolean incremental, String status, String staffUuid) {
        public static void encode(MsgStaffLibSyncS2C m, FriendlyByteBuf b) {
            b.writeInt(m.pages.size());
            for (PageData p : m.pages) {
                b.writeInt(p.pageIndex); b.writeUtf(p.name); b.writeBoolean(p.sealed);
                b.writeInt(p.iotas.size());
                for (CompoundTag t : p.iotas) if (t != null) b.writeNbt(t);
            }
            b.writeInt(m.castPage);
            b.writeInt(m.focusPage);
            List<Integer> removed = m.removed == null ? List.of() : m.removed;
            b.writeInt(removed.size());
            for (int idx : removed) b.writeInt(idx);
            b.writeBoolean(m.lastChunk);
            b.writeBoolean(m.incremental);
            b.writeUtf(m.status == null ? "" : m.status);
            b.writeUtf(m.staffUuid == null ? "" : m.staffUuid);
        }
        public static MsgStaffLibSyncS2C decode(FriendlyByteBuf b) {
            int n = b.readInt(); List<PageData> pages = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int idx = b.readInt(); String name = b.readUtf(); boolean sealed = b.readBoolean();
                int c = b.readInt(); List<CompoundTag> iotas = new ArrayList<>();
                for (int j = 0; j < c; j++) {
                    CompoundTag tag = b.readNbt();
                    if (tag != null) iotas.add(tag);
                }
                pages.add(new PageData(idx, name, iotas, sealed));
            }
            int castPage = b.readInt();
            int focusPage = b.readInt();
            int removedCount = b.readInt(); List<Integer> removed = new ArrayList<>();
            for (int i = 0; i < removedCount; i++) removed.add(b.readInt());
            return new MsgStaffLibSyncS2C(pages, castPage, focusPage, removed,
                b.readBoolean(), b.readBoolean(), b.readUtf(), b.readUtf());
        }
        public static void handle(MsgStaffLibSyncS2C m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            Minecraft.getInstance().execute(() -> {
                var s = Minecraft.getInstance().screen;
                if (s instanceof StaffSyncReceiver scr)
                    scr.onSyncChunk(m.pages, m.castPage, m.focusPage, m.removed, m.lastChunk, m.incremental, m.status, m.staffUuid);
            });
        }
    }

    /**
     * 页操作：新建/复制/删除/拖拽排序/改名/选中。
     *
     * <p>服务端在这里做全部校验（页满、封存），不依赖客户端把按钮灰掉。
     */
    public record MsgStaffPageOpC2S(int op, int a, int b, boolean flag, String name) {
        public static void encode(MsgStaffPageOpC2S m, FriendlyByteBuf b) {
            b.writeInt(m.op); b.writeInt(m.a); b.writeInt(m.b);
            b.writeBoolean(m.flag);
            b.writeUtf(m.name == null ? "" : m.name);
        }
        public static MsgStaffPageOpC2S decode(FriendlyByteBuf b) {
            return new MsgStaffPageOpC2S(b.readInt(), b.readInt(), b.readInt(), b.readBoolean(), b.readUtf());
        }
        public static void handle(MsgStaffPageOpC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> apply(s, m));
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
                        // 排序被拒时整表重发，客户端不会因为半个排序而与服务端不一致
                        sendSync(s, st, 0);
                        sendIncremental(s, st, statusKey(result.result()), 0, List.of());
                        return;
                    }
                    // 排序可能影响多页 → 走分块全量同步
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

    /** 整页内容写入（空列表 = 清空页面，页仍然存在）。改名走 {@link MsgStaffPageOpC2S}。 */
    public record MsgStaffPageWriteC2S(int pageIndex, List<CompoundTag> iotaTags) {
        public static void encode(MsgStaffPageWriteC2S m, FriendlyByteBuf b) {
            b.writeInt(m.pageIndex);
            b.writeInt(m.iotaTags == null ? 0 : m.iotaTags.size());
            if (m.iotaTags != null) for (CompoundTag t : m.iotaTags) if (t != null) b.writeNbt(t);
        }
        public static MsgStaffPageWriteC2S decode(FriendlyByteBuf b) {
            int p = b.readInt();
            int c = b.readInt(); List<CompoundTag> tags = new ArrayList<>();
            for (int j = 0; j < c; j++) { CompoundTag t = b.readNbt(); if (t != null) tags.add(t); }
            return new MsgStaffPageWriteC2S(p, tags);
        }
        public static void handle(MsgStaffPageWriteC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> {
                var st = staffInMainHand(s); if (st == null) return;
                var result = StaffPages.writeContent(st, m.pageIndex, m.iotaTags == null ? List.of() : m.iotaTags);
                if (result != StaffPages.Result.OK) {
                    // 被拒（封存/页号无效）：把该页真实内容发回去，客户端据此回滚
                    sendIncremental(s, st, statusKey(result), 0, List.of(), m.pageIndex);
                    return;
                }
                // 成功不回页内容：客户端自己保存了草稿，回包只用于确认施放页
                sendIncremental(s, st, "", 0, List.of());
            });
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
                if (!Platform.isModLoaded("hexparse")) {
                    CHANNEL.sendToPlayer(s, new MsgStaffGreatSpellCheckS2C(m.actionId, false, "", ""));
                    return;
                }
                try {
                    var res = HexParseBridge.checkGreatSpell(s.serverLevel(), m.actionId);
                    CHANNEL.sendToPlayer(s, new MsgStaffGreatSpellCheckS2C(m.actionId, res.usable(), res.signature(), res.startDir()));
                } catch (Throwable ignored) {
                    CHANNEL.sendToPlayer(s, new MsgStaffGreatSpellCheckS2C(m.actionId, false, "", ""));
                }
            });
        }
    }
    public record MsgStaffGreatSpellCheckS2C(String actionId, boolean usable, String signature, String startDir) {
        public static void encode(MsgStaffGreatSpellCheckS2C m, FriendlyByteBuf b) { b.writeUtf(m.actionId); b.writeBoolean(m.usable); b.writeUtf(m.signature); b.writeUtf(m.startDir); }
        public static MsgStaffGreatSpellCheckS2C decode(FriendlyByteBuf b) { return new MsgStaffGreatSpellCheckS2C(b.readUtf(), b.readBoolean(), b.readUtf(), b.readUtf()); }
        public static void handle(MsgStaffGreatSpellCheckS2C m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            Minecraft.getInstance().execute(() -> {
                var s = Minecraft.getInstance().screen;
                if (s instanceof StaffSyncReceiver scr) scr.onGreatSpellCheck(m.actionId, m.usable, m.signature, m.startDir);
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

    // ==================== IDE 双向 HexParse 消息（带 requestId + 原始 NBT 传输） ====================
    public record MsgIdeParseToCodeC2S(int requestId, int pageIndex, CompoundTag listNbt) {
        public static void encode(MsgIdeParseToCodeC2S m, FriendlyByteBuf b) {
            b.writeInt(m.requestId); b.writeInt(m.pageIndex);
            b.writeBoolean(m.listNbt != null);
            if (m.listNbt != null) b.writeNbt(m.listNbt);
        }
        public static MsgIdeParseToCodeC2S decode(FriendlyByteBuf b) {
            int req = b.readInt(), page = b.readInt();
            CompoundTag nbt = b.readBoolean() ? b.readNbt() : null;
            return new MsgIdeParseToCodeC2S(req, page, nbt);
        }
        public static void handle(MsgIdeParseToCodeC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> {
                if (!Platform.isModLoaded("hexparse")) {
                    CHANNEL.sendToPlayer(s, new MsgIdeParseToCodeS2C(m.requestId, m.pageIndex, "", "hexparse_missing"));
                    return;
                }
                try {
                    String code = HexParseBridge.parseIotaNbt(m.listNbt, s);
                    CHANNEL.sendToPlayer(s, new MsgIdeParseToCodeS2C(m.requestId, m.pageIndex, code == null ? "" : code, ""));
                } catch (Throwable e) {
                    CHANNEL.sendToPlayer(s, new MsgIdeParseToCodeS2C(m.requestId, m.pageIndex, "", e.getMessage() == null ? "parse_error" : e.getMessage()));
                }
            });
        }
    }

    public record MsgIdeParseToCodeS2C(int requestId, int pageIndex, String code, String error) {
        public static void encode(MsgIdeParseToCodeS2C m, FriendlyByteBuf b) {
            b.writeInt(m.requestId); b.writeInt(m.pageIndex);
            b.writeUtf(m.code == null ? "" : m.code); b.writeUtf(m.error == null ? "" : m.error);
        }
        public static MsgIdeParseToCodeS2C decode(FriendlyByteBuf b) {
            return new MsgIdeParseToCodeS2C(b.readInt(), b.readInt(), b.readUtf(), b.readUtf());
        }
        public static void handle(MsgIdeParseToCodeS2C m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            Minecraft.getInstance().execute(() -> {
                var s = Minecraft.getInstance().screen;
                if (s instanceof StaffSyncReceiver scr) scr.onIdeParseToCode(m.requestId, m.pageIndex, m.code, m.error);
            });
        }
    }

    public record MsgIdeParseToIotasC2S(int requestId, int pageIndex, String code) {
        public static void encode(MsgIdeParseToIotasC2S m, FriendlyByteBuf b) {
            b.writeInt(m.requestId); b.writeInt(m.pageIndex); b.writeUtf(m.code == null ? "" : m.code);
        }
        public static MsgIdeParseToIotasC2S decode(FriendlyByteBuf b) {
            return new MsgIdeParseToIotasC2S(b.readInt(), b.readInt(), b.readUtf());
        }
        public static void handle(MsgIdeParseToIotasC2S m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            var s = ctx.get().getPlayer() instanceof ServerPlayer sp ? sp : null; if (s == null) return;
            ctx.get().queue(() -> {
                if (!Platform.isModLoaded("hexparse")) {
                    CHANNEL.sendToPlayer(s, new MsgIdeParseToIotasS2C(m.requestId, m.pageIndex, false, List.of(), "hexparse_missing", -1));
                    return;
                }
                var res = HexParseBridge.parseCodeToRawTags(m.code, s);
                CHANNEL.sendToPlayer(s, new MsgIdeParseToIotasS2C(m.requestId, m.pageIndex, res.ok(), res.tags(), res.error(), res.errorLine()));
            });
        }
    }

    public record MsgIdeParseToIotasS2C(int requestId, int pageIndex, boolean ok, List<CompoundTag> iotaTags, String error, int errorLine) {
        public static void encode(MsgIdeParseToIotasS2C m, FriendlyByteBuf b) {
            b.writeInt(m.requestId); b.writeInt(m.pageIndex); b.writeBoolean(m.ok);
            List<CompoundTag> tags = m.iotaTags == null ? List.of() : m.iotaTags;
            b.writeInt(tags.size());
            for (CompoundTag t : tags) if (t != null) b.writeNbt(t);
            b.writeUtf(m.error == null ? "" : m.error);
            b.writeInt(m.errorLine);
        }
        public static MsgIdeParseToIotasS2C decode(FriendlyByteBuf b) {
            int req = b.readInt(), page = b.readInt();
            boolean ok = b.readBoolean();
            int n = b.readInt();
            List<CompoundTag> tags = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                CompoundTag t = b.readNbt();
                if (t != null) tags.add(t);
            }
            return new MsgIdeParseToIotasS2C(req, page, ok, tags, b.readUtf(), b.readInt());
        }
        public static void handle(MsgIdeParseToIotasS2C m, Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctx) {
            Minecraft.getInstance().execute(() -> {
                var s = Minecraft.getInstance().screen;
                if (s instanceof StaffSyncReceiver scr) scr.onIdeParseToIotas(m.requestId, m.pageIndex, m.ok, m.iotaTags, m.error, m.errorLine);
            });
        }
    }
}