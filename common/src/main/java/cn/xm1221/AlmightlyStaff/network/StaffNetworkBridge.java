package cn.xm1221.AlmightlyStaff.network;

import net.minecraft.nbt.CompoundTag;

import java.util.List;

/**
 * 客户端网络发送抽象接口（1.20.1 走 Architectury NetworkChannel，1.21.1 接 CustomPacketPayload）。
 */
public interface StaffNetworkBridge {
    void requestFullSync();

    void sendPageOp(int op, int a, int b, boolean flag, String name);

    void sendPageWrite(int pageIndex, List<CompoundTag> iotaTags);

    void requestIdeParseToCode(int requestId, int pageIndex, List<CompoundTag> iotaTags);

    void requestIdeParseToIotas(int requestId, int pageIndex, String code);

    void checkGreatSpell(String actionId, int targetSlot);

    void clearCastStack();
}
