package cn.xm1221.AlmightlyStaff.network;

import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.items.ItemAlmightlyStaff;
import dev.architectury.networking.NetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * 使用 MC 1.21.1 CustomPacketPayload + Architectury NetworkManager 的跨平台网络。
 * 不使用任何弃用 API。
 */
public class ModNetworking {

    public static void init() {
        // C2S: scroll wheel
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgShiftScrollC2S.TYPE, MsgShiftScrollC2S.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
        // C2S: mode key (V)
        NetworkManager.registerReceiver(NetworkManager.c2s(), MsgAlmightlyStaffModeC2S.TYPE, MsgAlmightlyStaffModeC2S.STREAM_CODEC,
                (msg, ctx) -> msg.handle(ctx));
    }

    // ==================== 滚轮翻页消息 ====================

    public static class MsgShiftScrollC2S implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MsgShiftScrollC2S> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "scroll"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgShiftScrollC2S> STREAM_CODEC =
                StreamCodec.of((buf, msg) -> msg.write(buf), MsgShiftScrollC2S::new);

        private final double mainHandDelta;
        private final double offHandDelta;
        private final boolean invertSpellbook;
        private final boolean invertAbacus;

        public MsgShiftScrollC2S(double mainHandDelta, double offHandDelta,
                                  boolean invertSpellbook, boolean invertAbacus) {
            this.mainHandDelta = mainHandDelta;
            this.offHandDelta = offHandDelta;
            this.invertSpellbook = invertSpellbook;
            this.invertAbacus = invertAbacus;
        }

        private MsgShiftScrollC2S(RegistryFriendlyByteBuf buf) {
            this(buf.readDouble(), buf.readDouble(), buf.readBoolean(), buf.readBoolean());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeDouble(mainHandDelta);
            buf.writeDouble(offHandDelta);
            buf.writeBoolean(invertSpellbook);
            buf.writeBoolean(invertAbacus);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private void handle(NetworkManager.PacketContext context) {
            var sender = context.getPlayer();
            if (!(sender instanceof ServerPlayer sp)) return;
            context.queue(() -> {
                handleForHand(sp, InteractionHand.MAIN_HAND, mainHandDelta, invertSpellbook);
                handleForHand(sp, InteractionHand.OFF_HAND, offHandDelta, invertAbacus);
            });
        }

        private static void handleForHand(ServerPlayer sender, InteractionHand hand,
                                           double delta, boolean invert) {
            if (delta == 0) return;
            var stack = sender.getItemInHand(hand);
            if (!(stack.getItem() instanceof ItemAlmightlyStaff)) return;

            if (invert) delta = -delta;

            var newIdx = ItemAlmightlyStaff.rotatePageIdx(stack, delta < 0.0, sender.level());
            var len = ItemAlmightlyStaff.highestPage(stack);
            var sealed = ItemAlmightlyStaff.isSealed(stack);

            MutableComponent component;
            if (hand == InteractionHand.OFF_HAND && stack.has(DataComponents.CUSTOM_NAME)) {
                var rarityColor = stack.getHoverName().getStyle().getColor();
                if (sealed) {
                    component = Component.translatable("hexcasting.tooltip.spellbook.page_with_name.sealed",
                        Component.literal(String.valueOf(newIdx)).withStyle(ChatFormatting.WHITE),
                        Component.literal(String.valueOf(len)).withStyle(ChatFormatting.WHITE),
                        Component.literal("").withStyle(style -> style.withItalic(true).withColor(rarityColor))
                            .append(stack.getHoverName()),
                        Component.translatable("hexcasting.tooltip.spellbook.sealed").withStyle(ChatFormatting.GOLD));
                } else {
                    component = Component.translatable("hexcasting.tooltip.spellbook.page_with_name",
                        Component.literal(String.valueOf(newIdx)).withStyle(ChatFormatting.WHITE),
                        Component.literal(String.valueOf(len)).withStyle(ChatFormatting.WHITE),
                        Component.literal("").withStyle(style -> style.withItalic(true).withColor(rarityColor))
                            .append(stack.getHoverName()));
                }
            } else {
                if (sealed) {
                    component = Component.translatable("hexcasting.tooltip.spellbook.page.sealed",
                        Component.literal(String.valueOf(newIdx)).withStyle(ChatFormatting.WHITE),
                        Component.literal(String.valueOf(len)).withStyle(ChatFormatting.WHITE),
                        Component.translatable("hexcasting.tooltip.spellbook.sealed").withStyle(ChatFormatting.GOLD));
                } else {
                    component = Component.translatable("hexcasting.tooltip.spellbook.page",
                        Component.literal(String.valueOf(newIdx)).withStyle(ChatFormatting.WHITE),
                        Component.literal(String.valueOf(len)).withStyle(ChatFormatting.WHITE));
                }
            }
            sender.displayClientMessage(component.withStyle(ChatFormatting.GRAY), true);
        }
    }

    // ==================== 模式切换消息 ====================

    public static class MsgAlmightlyStaffModeC2S implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MsgAlmightlyStaffModeC2S> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AlmightlyStaffMod.MOD_ID, "mode"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MsgAlmightlyStaffModeC2S> STREAM_CODEC =
                StreamCodec.of((buf, msg) -> {}, buf -> new MsgAlmightlyStaffModeC2S());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private void handle(NetworkManager.PacketContext context) {
            var sender = context.getPlayer();
            if (!(sender instanceof ServerPlayer sp)) return;
            context.queue(() -> {
                var item = sp.getMainHandItem().getItem();
                if (item instanceof ItemAlmightlyStaff staff) {
                    staff.casting(sp.level(), sp, InteractionHand.MAIN_HAND);
                }
            });
        }
    }
}
