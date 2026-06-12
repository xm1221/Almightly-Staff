package cn.xm1221.AlmightlyStaff.gui;

import at.petrak.hexcasting.api.casting.iota.*;
import net.minecraft.network.chat.Component;

public class IotaTextRenderer {

    public static String toText(Iota iota) {
        if (iota == null) return "§7null";
        if (iota instanceof NullIota) return "§7Null";
        if (iota instanceof GarbageIota) return "§7Garbage";
        if (iota instanceof BooleanIota b) return b.getBool() ? "§aTrue" : "§cFalse";
        if (iota instanceof DoubleIota d) {
            double v = d.getDouble();
            if (v == Math.floor(v) && !Double.isInfinite(v)) return "§b" + (long) v;
            return "§b" + v;
        }
        if (iota instanceof Vec3Iota v) return String.format("§e(%.1f, %.1f, %.1f)", v.getVec3().x, v.getVec3().y, v.getVec3().z);
        if (iota instanceof PatternIota p) return "§d" + p.getPattern().toString();
        if (iota instanceof ListIota l) return "§6[...] §7(" + l.getList().size() + " iotas)";
        if (iota instanceof EntityIota e) return "§5Entity:\"" + e.getEntity().getName().getString() + "\"";
        if (iota instanceof ContinuationIota) return "§9Continuation";
        return iota.display().getString();
    }

    public static Component toComponent(Iota iota) {
        return Component.literal(toText(iota));
    }
}
