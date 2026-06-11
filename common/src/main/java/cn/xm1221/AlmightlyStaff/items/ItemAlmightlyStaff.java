package cn.xm1221.AlmightlyStaff.items;


import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.eval.env.PackagedItemCastEnv;
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.item.HexHolderItem;
import at.petrak.hexcasting.api.item.MediaHolderItem;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.api.mod.HexConfig;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.api.utils.MediaHelper;
import at.petrak.hexcasting.api.utils.NBTHelper;
import at.petrak.hexcasting.common.items.HexBaubleItem;
import at.petrak.hexcasting.common.items.magic.ItemMediaBattery;
import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import at.petrak.hexcasting.common.lib.HexAttributes;
import at.petrak.hexcasting.common.lib.HexItems;
import at.petrak.hexcasting.common.lib.HexSounds;
import at.petrak.hexcasting.common.msgs.MsgClearSpiralPatternsS2C;
import at.petrak.hexcasting.common.msgs.MsgNewSpiralPatternsS2C;
import at.petrak.hexcasting.common.msgs.MsgOpenSpellGuiS2C;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static at.petrak.hexcasting.api.utils.NBTHelper.getList;
import static at.petrak.hexcasting.common.items.ItemLens.GRID_ZOOM;
import static at.petrak.hexcasting.common.items.ItemLens.SCRY_SIGHT;
import static at.petrak.hexcasting.common.items.magic.ItemMediaHolder.HEX_COLOR;


public class ItemAlmightlyStaff extends ItemSpellbook implements HexHolderItem, HexBaubleItem, MediaHolderItem {


    public ItemAlmightlyStaff(Properties properties) {
        super(properties);
    }


    private static final DecimalFormat PERCENTAGE = new DecimalFormat("####");

    static {
        PERCENTAGE.setRoundingMode(RoundingMode.DOWN);
    }

    private static final DecimalFormat DUST_AMOUNT = new DecimalFormat("###,###.##");

    public static final String BAR_COLOR="bar_color";


    @Override
    public @NotNull UseAnim getUseAnimation(ItemStack pStack) {
        return UseAnim.BLOCK;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);

        // 右键永远弹编辑 GUI
        if (player.getAttributeValue(HexAttributes.FEEBLE_MIND) > 0) {
            return InteractionResultHolder.fail(stack);
        }
        if (player.isShiftKeyDown()) {
            if (world.isClientSide()) {
                player.playSound(HexSounds.STAFF_RESET, 1f, 1f);
            } else if (player instanceof ServerPlayer serverPlayer) {
                IXplatAbstractions.INSTANCE.clearCastingData(serverPlayer);
                var packet = new MsgClearSpiralPatternsS2C(player.getUUID());
                IXplatAbstractions.INSTANCE.sendPacketToPlayer(serverPlayer, packet);
                IXplatAbstractions.INSTANCE.sendPacketTracking(serverPlayer, packet);
            }
        }

        if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            var vm = IXplatAbstractions.INSTANCE.getStaffcastVM(serverPlayer, usedHand);
            var patterns = IXplatAbstractions.INSTANCE.getPatternsSavedInUi(serverPlayer);
            var descs = vm.generateDescs();
            IXplatAbstractions.INSTANCE.sendPacketToPlayer(serverPlayer,
                    new MsgOpenSpellGuiS2C(usedHand, patterns, descs.getFirst(), descs.getSecond(), 0));
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.success(stack);
    }

    /** 按 V 键时由服务端调用，直接施放当前页法术 */
    public void casting(Level world, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!hasHex(stack)) {
            return;
        }

        List<Iota> instrs = getHex(stack, (ServerLevel) world);
        if (instrs == null) {
            return;
        }
        var sPlayer = (ServerPlayer) player;
        var ctx = new StaffCastEnv(sPlayer, usedHand);
        var vm = CastingVM.empty(ctx);
        var clientView = vm.queueExecuteAndWrapIotas(instrs, sPlayer.serverLevel());

        var patterns = instrs.stream()
                .filter(i -> i instanceof PatternIota)
                .map(i -> ((PatternIota) i).getPattern())
                .toList();
        var packet = new MsgNewSpiralPatternsS2C(sPlayer.getUUID(), patterns, 140);
        IXplatAbstractions.INSTANCE.sendPacketToPlayer(sPlayer, packet);
        IXplatAbstractions.INSTANCE.sendPacketTracking(sPlayer, packet);

        boolean broken = breakAfterDepletion() && getMedia(stack) == 0;

        Stat<?> stat;
        if (broken) {
            stat = Stats.ITEM_BROKEN.get(this);
        } else {
            stat = Stats.ITEM_USED.get(this);
        }
        player.awardStat(stat);

        sPlayer.getCooldowns().addCooldown(this, this.cooldown());

        if (clientView.getResolutionType().getSuccess()) {
            new ParticleSpray(player.position(), new Vec3(0.0, 1.5, 0.0), 0.4, Math.PI / 3, 30)
                    .sprayParticles(sPlayer.serverLevel(), ctx.getPigment());
        }
        
    }




    private boolean breakAfterDepletion() {
        return false;
    }

    private int cooldown() {
        return HexConfig.common().artifactCooldown();
    }

    @Override
    public boolean canDrawMediaFromInventory(ItemStack stack) {
        return false;
    }

    @Override
    public boolean hasHex(ItemStack stack) {
        return !arePagesEmpty(stack);
    }

    @Override
    public @Nullable List<Iota> getHex(ItemStack stack, ServerLevel level) {
        Iota hex = readIota(stack, level);
        List<Iota> list = new ArrayList<>();
        if(hex instanceof ListIota){
           for (Iota iota:((ListIota)hex).getList()){
               list.add(iota);
           }
           return list;
        }
        list.add(hex);
        return list;
    }

    @Override
    public void writeHex(ItemStack stack, List<Iota> program, @Nullable FrozenPigment pigment, long media) {
        writeDatum(stack,new ListIota(program));
        setMedia(stack, media);
    }

    @Override
    public void clearHex(ItemStack stack) {
        writeDatum(stack,null);
    }

    @Override
    public @Nullable FrozenPigment getPigment(ItemStack stack) {
        return null;
    }

    @Override
    public long getMedia(ItemStack stack) {
        return NBTHelper.getLong(stack, "media");
    }

    @Override
    public long getMaxMedia(ItemStack stack) {
        return NBTHelper.getLong(stack, "max_media");
    }

    @Override
    public void setMedia(ItemStack stack, long media) {
        NBTHelper.putLong(stack, "max_media", media);
        NBTHelper.putLong(stack, "media", media);
    }

    @Override
    public boolean canProvideMedia(ItemStack stack) {
        return true;
    }

    @Override
    public boolean canRecharge(ItemStack stack) {
        return true;
    }

    @Override
    public int numVariants() {
        return 5; // 0~4，对应 cad/0~4_all_in_one.png
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        var out = HashMultimap.create(super.getDefaultAttributeModifiers(slot));
        if ( slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
            out.put(HexAttributes.GRID_ZOOM, GRID_ZOOM);
            out.put(HexAttributes.SCRY_SIGHT, SCRY_SIGHT);
        }
        return out;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getHexBaubleAttrs(ItemStack stack) {
        HashMultimap<Attribute, AttributeModifier> out = HashMultimap.create();
        out.put(HexAttributes.GRID_ZOOM, GRID_ZOOM);
        out.put(HexAttributes.SCRY_SIGHT, SCRY_SIGHT);
        return out;
    }

    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltipComponents,
                                TooltipFlag pIsAdvanced) {
        var maxMedia = getMaxMedia(pStack);
        if (maxMedia > 0) {
            var media = getMedia(pStack);
            var fullness = getMediaFullness(pStack);

            var color = TextColor.fromRgb(MediaHelper.mediaBarColor(media, maxMedia));

            var mediamount = Component.literal(DUST_AMOUNT.format(media / (float) MediaConstants.DUST_UNIT));
            var percentFull = Component.literal(PERCENTAGE.format(100f * fullness) + "%");
            var maxCapacity = Component.translatable("hexcasting.tooltip.media", DUST_AMOUNT.format(maxMedia / (float) MediaConstants.DUST_UNIT));

            mediamount.withStyle(style -> style.withColor(HEX_COLOR));
            maxCapacity.withStyle(style -> style.withColor(HEX_COLOR));
            percentFull.withStyle(style -> style.withColor(color));

            pTooltipComponents.add(
                    Component.translatable("hexcasting.tooltip.media_amount.advanced",
                            mediamount, maxCapacity, percentFull));
        }
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);
    }

    @Override
    public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int i, boolean bl) {
        if(entity instanceof Allay ||entity instanceof ServerPlayer) {
            var media=getMedia(itemStack);
            if(media<=getMaxMedia(itemStack) && level.getGameTime()%13==0) {
                addMedia(itemStack, (long) (media+MediaConstants.DUST_UNIT));
            }
        }
    }

    public void addMedia(ItemStack itemStack, long media) {
        if(media<=getMaxMedia(itemStack)) {
            NBTHelper.putLong(itemStack, "media", media);
        }
    }

    @Override
    public boolean isBarVisible(ItemStack itemStack) {
        return getMaxMedia(itemStack) > 0;
    }

    @Override
    public int getBarColor(ItemStack itemStack) {
        if (itemStack.getTag() != null && (!itemStack.hasTag() || !itemStack.getTag().contains(BAR_COLOR))) {
            return 0xb38ef3;
        }
        return NBTHelper.getInt(itemStack, BAR_COLOR);
    }

    public void setBarColor(ItemStack itemStack, int color) {
        NBTHelper.putInt(itemStack, BAR_COLOR, color);
    }

    @Override
    public int getBarWidth(ItemStack pStack) {
        var media = getMedia(pStack);
        var maxMedia = getMaxMedia(pStack);
        return MediaHelper.mediaBarWidth(media, maxMedia);
    }

    @Override
    public void onCraftedBy(ItemStack itemStack, Level level, Player player) {
        if(level.isClientSide) {
            return;
        }
        var color=new StaffCastEnv((ServerPlayer) player,InteractionHand.MAIN_HAND).getPigment().getColorProvider().getColor(level.getGameTime(),player.position());
        setBarColor(itemStack, color);
        NBTHelper.putLong(itemStack, "max_media", 64*MediaConstants.CRYSTAL_UNIT);
    }


}