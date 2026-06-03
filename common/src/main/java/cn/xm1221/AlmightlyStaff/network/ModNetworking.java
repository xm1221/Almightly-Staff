package cn.xm1221.AlmightlyStaff.network;

import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import cn.xm1221.AlmightlyStaff.items.ItemAlmightlyStaff;
import dev.architectury.networking.NetworkChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * 统一管理 Almightly Staff mod 的所有网络消息。
 * 使用 Architectury NetworkChannel 实现跨平台（Fabric/Forge）兼容。
 */
public class ModNetworking {
    public static final NetworkChannel CHANNEL = NetworkChannel.create(
        new ResourceLocation(AlmightlyStaffMod.MOD_ID, "network"));

    public static void init() {
        // 注册滚轮翻页消息
        CHANNEL.register(
            MsgShiftScrollC2S.class,
            MsgShiftScrollC2S::encode,
            MsgShiftScrollC2S::decode,
            MsgShiftScrollC2S::handle
        );

        // 注册模式切换消息
        CHANNEL.register(
            MsgAlmightlyStaffModeC2S.class,
            MsgAlmightlyStaffModeC2S::encode,
            MsgAlmightlyStaffModeC2S::decode,
            MsgAlmightlyStaffModeC2S::handle
        );
    }

    // ==================== 滚轮翻页消息 ====================

    public record MsgShiftScrollC2S(double mainHandDelta, double offHandDelta,
                                    boolean isCtrl, boolean invertSpellbook,
                                    boolean invertAbacus) {
        public static void encode(MsgShiftScrollC2S msg, FriendlyByteBuf buf) {
            buf.writeDouble(msg.mainHandDelta);
            buf.writeDouble(msg.offHandDelta);
            buf.writeBoolean(msg.isCtrl);
            buf.writeBoolean(msg.invertSpellbook);
            buf.writeBoolean(msg.invertAbacus);
        }

        public static MsgShiftScrollC2S decode(FriendlyByteBuf buf) {
            return new MsgShiftScrollC2S(
                buf.readDouble(), buf.readDouble(),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean()
            );
        }

        public static void handle(MsgShiftScrollC2S msg,
                                  java.util.function.Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctxSupplier) {
            var ctx = ctxSupplier.get();
            var sender = ctx.getPlayer() instanceof ServerPlayer sp ? sp : null;
            if (sender == null) return;

            ctx.queue(() -> {
                handleForHand(sender, InteractionHand.MAIN_HAND, msg.mainHandDelta, msg.invertSpellbook);
                handleForHand(sender, InteractionHand.OFF_HAND, msg.offHandDelta, msg.invertSpellbook);
            });
        }

        private static void handleForHand(ServerPlayer sender, InteractionHand hand,
                                           double delta, boolean invertSpellbook) {
            if (delta == 0) return;
            var stack = sender.getItemInHand(hand);
            if (!(stack.getItem() instanceof ItemAlmightlyStaff)) return;

            if (invertSpellbook) delta = -delta;

            var newIdx = ItemAlmightlyStaff.rotatePageIdx(stack, delta < 0.0);
            var len = ItemAlmightlyStaff.highestPage(stack);
            var sealed = ItemAlmightlyStaff.isSealed(stack);

            MutableComponent component;
            if (hand == InteractionHand.OFF_HAND && stack.hasCustomHoverName()) {
                if (sealed) {
                    component = Component.translatable("hexcasting.tooltip.spellbook.page_with_name.sealed",
                        Component.literal(String.valueOf(newIdx)).withStyle(ChatFormatting.WHITE),
                        Component.literal(String.valueOf(len)).withStyle(ChatFormatting.WHITE),
                        Component.literal("").withStyle(stack.getRarity().color, ChatFormatting.ITALIC)
                            .append(stack.getHoverName()),
                        Component.translatable("hexcasting.tooltip.spellbook.sealed").withStyle(ChatFormatting.GOLD));
                } else {
                    component = Component.translatable("hexcasting.tooltip.spellbook.page_with_name",
                        Component.literal(String.valueOf(newIdx)).withStyle(ChatFormatting.WHITE),
                        Component.literal(String.valueOf(len)).withStyle(ChatFormatting.WHITE),
                        Component.literal("").withStyle(stack.getRarity().color, ChatFormatting.ITALIC)
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

    public record MsgAlmightlyStaffModeC2S() {
        public static void encode(MsgAlmightlyStaffModeC2S msg, FriendlyByteBuf buf) {
            // 空消息，无需额外数据
        }

        public static MsgAlmightlyStaffModeC2S decode(FriendlyByteBuf buf) {
            return new MsgAlmightlyStaffModeC2S();
        }

        public static void handle(MsgAlmightlyStaffModeC2S msg,
                                  java.util.function.Supplier<dev.architectury.networking.NetworkManager.PacketContext> ctxSupplier) {
            var ctx = ctxSupplier.get();
            var sender = ctx.getPlayer() instanceof ServerPlayer sp ? sp : null;
            if (sender == null) return;

            ctx.queue(() -> {
                var stack = sender.getMainHandItem();
                if (stack.getItem() instanceof ItemAlmightlyStaff staff) {
                    staff.changesMode(stack);
                    var mode = ItemAlmightlyStaff.isModeActive(stack);
                    sender.displayClientMessage(
                        Component.translatable("almightly_staff.mode." + (mode ? "on" : "off"))
                            .withStyle(mode ? ChatFormatting.GREEN : ChatFormatting.RED),
                        true);
                }
            });
        }
    }
}
