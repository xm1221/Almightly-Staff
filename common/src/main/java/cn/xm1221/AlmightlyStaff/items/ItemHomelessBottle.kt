package cn.xm1221.AlmightlyStaff.items

import at.petrak.hexcasting.api.HexAPI
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

/**
 * 游子之瓶（1.21.1 / Hex Casting 0.12）。
 *
 * 与 1.20.1 的差异（0.12 把 iota 与媒质都搬到了 DataComponents）：
 * - `IotaHolderItem` 不再有 `readIotaTag`；读写统一走 [HexDataComponents.IOTA_HOLDER_IOTA]。
 * - `MediaHolderItem` 不再有 `setMaxMedia`；容量用 [HexDataComponents.MEDIA_MAX] 表达。
 * - `NBTHelper` 是 Kotlin 顶层函数（`@file:JvmName("NBTHelper")`），没有 `INSTANCE`，
 *   从 Kotlin 调用要 `import` 扩展函数，从 Java 调用才是静态方法。
 */
class ItemHomelessBottle(pProperties: Properties) : ItemMediaHolder(pProperties), IotaHolderItem {
    companion object {
        const val COLOR = "color"
        const val HOME = "home"

        /** 上限：64 个淬炼块的媒质。 */
        val MAX: Long = MediaConstants.QUENCHED_BLOCK_UNIT * 64
    }

    private fun customTag(stack: ItemStack): CompoundTag =
        stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()

    private fun setCustomTag(stack: ItemStack, tag: CompoundTag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
    }

    override fun canProvideMedia(stack: ItemStack): Boolean = true

    override fun canRecharge(stack: ItemStack): Boolean = false

    override fun getBarColor(pStack: ItemStack): Int {
        val tag = NBTHelper.getCompound(customTag(pStack), COLOR)
        if (tag == null || tag.isEmpty) return super.getBarColor(pStack)
        val pigment = FrozenPigment.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null)
            ?: return super.getBarColor(pStack)
        // 固定位置 + 连续时间：颜色只随玩家颜色器平滑变化，不闪烁
        return pigment.colorProvider.getColor(ClientTickCounter.getTotal(), Vec3.ZERO)
    }

    override fun onCraftedBy(itemStack: ItemStack, level: Level, player: Player) {
        super.onCraftedBy(itemStack, level, player)
        val pigment = HexAPI.instance().getColorizer(player)
        val encoded = FrozenPigment.CODEC.encodeStart(NbtOps.INSTANCE, pigment).result().orElse(null)
        if (encoded is CompoundTag) {
            val root = customTag(itemStack)
            root.put(COLOR, encoded)
            setCustomTag(itemStack, root)
        }
        // 记录合成时的位置作为"家"
        writeDatum(itemStack, Vec3Iota(player.position()))
        // 空瓶起步：0 媒质、10 万容量（=10 个紫水晶粉），离家后开始恢复并成长
        itemStack.set(HexDataComponents.MEDIA_MAX.get(), 100_000L)
        setMedia(itemStack, 0L)
    }

    override fun readIota(stack: ItemStack): Iota? {
        if (stack.isEmpty) return null
        stack.get(HexDataComponents.IOTA_HOLDER_IOTA.get())?.let { return it }
        // 兼容旧存档：回落到自定义 NBT 里的 home
        return NBTHelper.getCompound(customTag(stack), HOME)?.let { StaffHex.deserializeIota(it) }
    }

    override fun writeable(stack: ItemStack): Boolean = true

    override fun canWrite(stack: ItemStack, iota: Iota?): Boolean = iota is Vec3Iota

    override fun writeDatum(stack: ItemStack, iota: Iota?) {
        if (iota == null) {
            stack.remove(HexDataComponents.IOTA_HOLDER_IOTA.get())
            val root = customTag(stack)
            root.remove(HOME)
            setCustomTag(stack, root)
        } else {
            stack.set(HexDataComponents.IOTA_HOLDER_IOTA.get(), iota)
            val root = customTag(stack)
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
        val nmedia = getMedia(stack) + media
        val maxmedia = getMaxMedia(stack)
        if (nmedia > maxmedia) {
            // 0.12 没有 setMaxMedia，直接写容量组件
            stack.set(HexDataComponents.MEDIA_MAX.get(), min(nmedia, MAX))
        }
        setMedia(stack, min(nmedia, getMaxMedia(stack)))
    }

    fun addMedia(media: Long, stack: ItemStack) {
        val nmedia = getMedia(stack) + media
        setMedia(stack, min(nmedia, getMaxMedia(stack)))
    }

    override fun getName(itemStack: ItemStack): Component {
        val style = Style.EMPTY.withColor(getBarColor(itemStack))
        return Component.literal("").append(super.getName(itemStack)).withStyle(style)
    }
}
