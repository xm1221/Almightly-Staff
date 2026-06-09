package cn.xm1221.AlmightlyStaff.items;


import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.api.utils.NBTHelper;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffMod;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;


public class AlmightlyStaffItems {
     public static ItemStack getStaff(){
         var stack = new ItemStack(AlmightlyStaffMod.ALL_IN_ONE,1);
         NBTHelper.putLong(stack, "max_media", 64* MediaConstants.CRYSTAL_UNIT);
         NBTHelper.putLong(stack, "media", 64* MediaConstants.CRYSTAL_UNIT);
        return stack;
     }





}
