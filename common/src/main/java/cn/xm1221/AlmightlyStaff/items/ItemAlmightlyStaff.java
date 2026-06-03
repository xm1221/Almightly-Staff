package cn.xm1221.AlmightlyStaff.items;


import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.eval.env.PackagedItemCastEnv;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.item.HexHolderItem;
import at.petrak.hexcasting.api.mod.HexConfig;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.api.utils.NBTHelper;
import at.petrak.hexcasting.common.items.ItemStaff;
import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import at.petrak.hexcasting.common.lib.HexAttributes;
import at.petrak.hexcasting.common.lib.HexItems;
import at.petrak.hexcasting.common.lib.HexSounds;
import at.petrak.hexcasting.common.msgs.MsgClearSpiralPatternsS2C;
import at.petrak.hexcasting.common.msgs.MsgNewSpiralPatternsS2C;
import at.petrak.hexcasting.common.msgs.MsgOpenSpellGuiS2C;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static at.petrak.hexcasting.api.utils.NBTHelper.getList;


public class ItemAlmightlyStaff extends ItemSpellbook implements HexHolderItem {


    public ItemAlmightlyStaff(Properties properties) {
        super(properties);
    }

    public static final String MODE = "mode";

    public static boolean isModeActive(ItemStack stack) {
        return NBTHelper.getBoolean(stack, MODE);
    }

    @Override
    public @NotNull UseAnim getUseAnimation(ItemStack pStack) {
        return UseAnim.BLOCK;
    }

    @Override
    public  InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand usedHand) {
        if (isModeActive(player.getItemInHand(usedHand))) {
            var stack = player.getItemInHand(usedHand);
            if (!hasHex(stack)) {
                return InteractionResultHolder.fail(stack);
            }

            if (world.isClientSide) {
                return InteractionResultHolder.success(stack);
            }

            List<Iota> instrs = getHex(stack, (ServerLevel) world);
            if (instrs == null) {
                return InteractionResultHolder.fail(stack);
            }
            var sPlayer = (ServerPlayer) player;
            var ctx = new PackagedItemCastEnv(sPlayer, usedHand);
            var vm = CastingVM.empty(ctx);
            var clientView = vm.queueExecuteAndWrapIotas(instrs, sPlayer.serverLevel());

            var patterns = instrs.stream()
                    .filter(i -> i instanceof PatternIota)
                    .map(i -> ((PatternIota) i).getPattern())
                    .toList();
            /*var packet = new MsgNewSpiralPatternsS2C(sPlayer.getUUID(), patterns, 140);
            IXplatAbstractions.INSTANCE.sendPacketToPlayer(sPlayer, packet);
            IXplatAbstractions.INSTANCE.sendPacketTracking(sPlayer, packet);*/

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
                // Somehow we lost spraying particles on each new pattern, so do it here
                // this also nicely prevents particle spam on trinkets
                new ParticleSpray(player.position(), new Vec3(0.0, 1.5, 0.0), 0.4, Math.PI / 3, 30)
                        .sprayParticles(sPlayer.serverLevel(), ctx.getPigment());
            }

            var sound = ctx.getSound().sound();
            if (sound != null) {
                var soundPos = sPlayer.position();
                sPlayer.level().playSound(null, soundPos.x, soundPos.y, soundPos.z,
                        sound, SoundSource.PLAYERS, 1f, 1f);
            }

            if (broken) {
                stack.shrink(1);
                player.broadcastBreakEvent(usedHand);
                return InteractionResultHolder.consume(stack);
            } else {
                return InteractionResultHolder.success(stack);
            }
        } else {
            return HexItems.STAFF_ACACIA.use(world, player, usedHand);
        }
    };

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

    public void changesMode(ItemStack stack) {
        NBTHelper.putBoolean(stack, MODE, !NBTHelper.getBoolean(stack, MODE));
    }
}