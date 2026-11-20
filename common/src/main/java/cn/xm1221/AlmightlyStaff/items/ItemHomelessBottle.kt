package cn.xm1221.AlmightlyStaff.items

import at.petrak.hexcasting.api.HexAPI
import at.petrak.hexcasting.api.casting.asActionResult
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import at.petrak.hexcasting.api.item.IotaHolderItem
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.api.pigment.FrozenPigment
import at.petrak.hexcasting.api.utils.NBTHelper
import at.petrak.hexcasting.client.ClientTickCounter
import at.petrak.hexcasting.common.items.magic.ItemMediaHolder
import at.petrak.hexcasting.common.lib.HexDataComponents
import cn.xm1221.AlmightlyStaff.spell.StaffHex
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.min

class ItemHomelessBottle(pProperties: Properties) : ItemMediaHolder(pProperties), IotaHolderItem {
    val COLOR = "color"
    val HOME = "home"

    val MAX = MediaConstants.QUENCHED_BLOCK_UNIT * 64

    private fun getCustomTag(stack: ItemStack): CompoundTag {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
    }

    private fun setCustomTag(stack: ItemStack, tag: CompoundTag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }

    override fun canProvideMedia(stack: ItemStack): Boolean {
        return true
    }

    override fun canRecharge(stack: ItemStack): Boolean {
        return false
    }

    override fun getBarColor(pStack: ItemStack): Int {
        val root = getCustomTag(pStack)
        val tag = NBTHelper.getCompound(root, COLOR)
        if (tag == null || tag.isEmpty) return super.getBarColor(pStack)
        val pigment = FrozenPigment.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null)
            ?: return super.getBarColor(pStack)
        // 固定位置 + 连续时间：颜色只随玩家颜色器平滑变化，不闪烁
        return pigment.colorProvider.getColor(
            ClientTickCounter.getTotal(),
            Vec3.ZERO,
        )
    }

    override fun onCraftedBy(itemStack: ItemStack, level: Level, player: Player) {
        super.onCraftedBy(itemStack, level, player)
        val pigment = HexAPI.instance().getColorizer(player)
        val encoded = FrozenPigment.CODEC.encodeStart(NbtOps.INSTANCE, pigment).result().orElse(null)
        if (encoded is CompoundTag) {
            val root = getCustomTag(itemStack)
            root.put(COLOR, encoded)
            setCustomTag(itemStack, root)
        }
        val veciota = player.position().asActionResult[0]
        writeDatum(itemStack, veciota)
        // 空瓶起步：0 媒质、10 万容量（=10 个紫水晶粉），离家后开始恢复并成长
        setMaxMedia(itemStack, 100_000)
        setMedia(itemStack, 0)
    }

    override fun readIotaTag(stack: ItemStack): CompoundTag? {
        if (stack.isEmpty) return null
        val iotaComp = stack.get(HexDataComponents.IOTA.get())
        if (iotaComp != null) {
            return StaffHex.serializeIota(iotaComp)
        }
        val root = getCustomTag(stack)
        return NBTHelper.getCompound(root, HOME)
    }

    override fun readIota(stack: ItemStack): Iota? {
        if (stack.isEmpty) return null
        val iotaComp = stack.get(HexDataComponents.IOTA.get())
        if (iotaComp != null) return iotaComp
        val tag = NBTHelper.getCompound(getCustomTag(stack), HOME) ?: return null
        return StaffHex.deserializeIota(tag)
    }

    override fun writeable(stack: ItemStack): Boolean {
        return true
    }

    override fun canWrite(stack: ItemStack, iota: Iota?): Boolean {
        return iota is Vec3Iota
    }

    override fun writeDatum(stack: ItemStack, iota: Iota?) {
        if (iota == null) {
            stack.remove(HexDataComponents.IOTA.get())
            val root = getCustomTag(stack)
            root.remove(HOME)
            setCustomTag(stack, root)
        } else {
            stack.set(HexDataComponents.IOTA.get(), iota)
            val root = getCustomTag(stack)
            root.put(HOME, StaffHex.serializeIota(iota))
            setCustomTag(stack, root)
        }
    }

    override fun inventoryTick(itemStack: ItemStack, level: Level, entity: Entity, i: Int, bl: Boolean) {
        if (level.isClientSide) {
            return super.inventoryTick(itemStack, level, entity, i, bl)
        }
        val iota = readIota(itemStack)
        if (iota !is Vec3Iota) return super.inventoryTick(itemStack, level, entity, i, bl)
        val home = iota.vec3
        val pos = entity.position()
        val distance = pos.distanceTo(home).toLong()
        if (level.gameTime % 26 == 0L && distance > 1000) {
            addMediaWithNoLimit(distance * 100, itemStack)
        }
    }

    fun addMediaWithNoLimit(media: Long, stack: ItemStack) {
        val cmedia = getMedia(stack)
        val nmedia = cmedia + media
        val maxmedia = getMaxMedia(stack)
        if (nmedia > maxmedia) setMaxMedia(stack, nmedia)
        setMedia(stack, nmedia)
    }

    fun addMedia(media: Long, stack: ItemStack) {
        val cmedia = getMedia(stack)
        val nmedia = cmedia + media
        val maxmedia = getMaxMedia(stack)
        setMedia(stack, min(nmedia, maxmedia))
    }

    override fun setMaxMedia(stack: ItemStack, maxMedia: Long) {
        val capped = min(maxMedia, MAX)
        super.setMaxMedia(stack, capped)
    }

    override fun getName(itemStack: ItemStack): Component {
        val style = Style.EMPTY.withColor(getBarColor(itemStack))
        val name = super.getName(itemStack)
        return Component.literal("").append(name).withStyle(style)
    }
}
