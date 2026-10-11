package cn.xm1221.AlmightlyStaff.parse

import at.petrak.hexcasting.api.casting.iota.Iota
import cn.xm1221.AlmightlyStaff.items.ItemAlmightlyStaff
import io.yukkuric.hexparse.api.HexParseAPI
import net.minecraft.world.item.ItemStack

class AlmightlyStaffParse {

    companion object {
        @JvmStatic
        fun initParse() {
            // HexParse 1.21：CreateItemIOMethod 的 writer/reader 直接收 Iota，
            // 不再走 CompoundTag / ItemStack NBT。法杖页数据在 DataComponents 上，
            // 交给 ItemSpellbook.writeDatum / readIota 处理当前页。
            HexParseAPI.CreateItemIOMethod(
                ItemAlmightlyStaff::class.java,
                writer = { stack: ItemStack, iota: Iota ->
                    val item = stack.item
                    if (item is ItemAlmightlyStaff) {
                        item.writeDatum(stack, iota)
                    }
                },
                reader = { stack: ItemStack ->
                    val item = stack.item
                    if (item is ItemAlmightlyStaff) item.readIota(stack) else null
                }
            )
        }
    }
}
