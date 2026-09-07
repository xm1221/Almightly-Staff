package cn.xm1221.AlmightlyStaff.items

import at.petrak.hexcasting.api.HexAPI
import at.petrak.hexcasting.api.casting.asActionResult
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.IotaType
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import at.petrak.hexcasting.api.item.IotaHolderItem
import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.api.pigment.FrozenPigment
import at.petrak.hexcasting.api.utils.getCompound
import at.petrak.hexcasting.api.utils.getTag
import at.petrak.hexcasting.api.utils.hasInt
import at.petrak.hexcasting.api.utils.putInt
import at.petrak.hexcasting.api.utils.putLong
import at.petrak.hexcasting.api.utils.putTag
import at.petrak.hexcasting.client.ClientTickCounter
import at.petrak.hexcasting.common.items.magic.ItemMediaHolder
import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.min

class ItemHomelessBottle(pProperties: Properties) : ItemMediaHolder(pProperties), IotaHolderItem {
    val COLOR = "color"
    val HOME = "home"

    val MAX = MediaConstants.QUENCHED_BLOCK_UNIT*64

    override fun canProvideMedia(stack: ItemStack?): Boolean {
        return true
    }

    override fun canRecharge(stack: ItemStack?): Boolean {
        return false
    }

    override fun getBarColor(pStack: ItemStack?): Int {
        if (pStack == null) return super.getBarColor(pStack)
        val tag = pStack.getCompound(COLOR)
        if (tag == null || tag.isEmpty) return super.getBarColor(pStack)
        val pigment = FrozenPigment.fromNBT(tag)
        // 固定位置 + 连续时间：颜色只随玩家颜色器平滑变化，不闪烁
        return pigment.colorProvider.getColor(
            ClientTickCounter.getTotal(),
            Vec3.ZERO,
        )
    }

    override fun onCraftedBy(itemStack: ItemStack?, level: Level?, player: Player?) {
        if(player == null || itemStack==null || level == null) return super.onCraftedBy(itemStack, level, player)
        val pigment = HexAPI.INSTANCE.get().getColorizer(player)
        val tag = pigment.serializeToNBT()
        itemStack.putTag(COLOR, tag)
        val veciota =  player.position().asActionResult[0]
        writeDatum(itemStack, veciota)
        // 空瓶起步：0 媒质、10 万容量（=10 个紫水晶粉），离家后开始恢复并成长
        setMaxMedia(itemStack, 100_000)
        setMedia(itemStack, 0)
    }

    override fun readIotaTag(stack: ItemStack?): CompoundTag? {
        if (stack == null || stack.isEmpty) return null
       return stack.getCompound(HOME)
    }

    override fun writeable(stack: ItemStack?): Boolean {
        return true
    }


    override fun canWrite(
        stack: ItemStack?,
        iota: Iota?
    ): Boolean {
        if(iota is Vec3Iota) return true
        else return false
    }

    override fun writeDatum(
        stack: ItemStack?,
        iota: Iota?
    ) {
        val tag = IotaType.serialize(iota)
        stack?.putTag(HOME, tag)
    }

    override fun inventoryTick(itemStack: ItemStack?, level: Level?, entity: Entity?, i: Int, bl: Boolean) {
        if(itemStack == null || level == null || entity == null) return super.inventoryTick(itemStack, level, entity, i, bl)
        if(level.isClientSide) {
            return super.inventoryTick(itemStack, level, entity, i, bl)
        }
        val serverlevel = level as ServerLevel
        val iota = readIota(itemStack,serverlevel)
        if(iota !is Vec3Iota) return super.inventoryTick(itemStack, level, entity, i, bl)
        val home = iota.vec3
        val pos = entity.position()
         val distance = pos.distanceTo(home).toLong()
        if(level.gameTime % 4 == 0.toLong() && distance > 1000) {
            addMediaWithNoLimit(distance*10,itemStack)
        }
    }

    fun addMediaWithNoLimit(media: Long, stack: ItemStack) {
        val cmedia = getMedia(stack)
        val nmedia = cmedia+ media
        val maxmedia = getMaxMedia(stack)
        if(nmedia>maxmedia)setMaxMedia(stack,nmedia)
        setMedia(stack, nmedia)
    }

    fun addMedia(media:Long,stack: ItemStack){
        val cmedia = getMedia(stack)
        val nmedia = cmedia+ media
        val maxmedia = getMaxMedia(stack)
        setMedia(stack, min(nmedia, maxmedia))
    }

    fun setMaxMedia(stack: ItemStack, maxmedia:Long){
       val tag = stack.tag
        val  media = min(maxmedia, MAX)
        if(tag != null) {
            if(tag.hasInt(TAG_MAX_MEDIA)){
                stack.putInt(TAG_MAX_MEDIA, media.toInt())
            }
            else stack.putLong(TAG_MAX_MEDIA, media)
        }
    }

    override fun getName(itemStack: ItemStack?): Component? {
        val style=Style.EMPTY.withColor(getBarColor(itemStack))
        val name =super.getName(itemStack)
        return Component.literal("").append(name).withStyle(style)
    }


}