package cn.xm1221.AlmightlyStaff.items;


import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.api.utils.NBTHelper;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;


public class AlmightlyStaffItems {
     public static ItemStack getStaff(){
         var stack = new ItemStack(AlmightlyStaffMod.ALL_IN_ONE, 1);
         CompoundTag tag = new CompoundTag();
         NBTHelper.putLong(tag, "max_media", 64 * MediaConstants.CRYSTAL_UNIT);
         NBTHelper.putLong(tag, "media", 64 * MediaConstants.CRYSTAL_UNIT);
         stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
         return stack;
     }
}
