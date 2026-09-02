package cn.xm1221.AlmightlyStaff.parse

import at.petrak.hexcasting.api.utils.getOrCreateCompound
import at.petrak.hexcasting.common.items.storage.ItemSpellbook
import cn.xm1221.AlmightlyStaff.items.ItemAlmightlyStaff
import io.yukkuric.hexparse.api.HexParseAPI
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

class AlmightlyStaffParse() {

     companion object{
         @JvmStatic
         fun initParse(){
             HexParseAPI.CreateItemIOMethod(

                 ItemAlmightlyStaff::class.java,
                 writer = { stack: ItemStack?, nbt: CompoundTag? ->
                     val idx = ItemAlmightlyStaff.getPage(stack, 1)
                     val pageKey = idx.toString()
                     stack!!.getOrCreateCompound(ItemAlmightlyStaff.TAG_PAGES).put(pageKey, nbt)
                 }
             )
         }
     }
}