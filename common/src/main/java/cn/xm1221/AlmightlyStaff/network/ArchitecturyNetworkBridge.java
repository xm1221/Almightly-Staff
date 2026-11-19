package cn.xm1221.AlmightlyStaff.network;

import net.minecraft.nbt.CompoundTag;

import java.util.List;

/**
 * 1.20.1 客户端网络桥实现，委托 {@link ModNetworking#CHANNEL}。
 */
public final class ArchitecturyNetworkBridge implements StaffNetworkBridge {
    public static final ArchitecturyNetworkBridge INSTANCE = new ArchitecturyNetworkBridge();

    private ArchitecturyNetworkBridge() {
    }

    @Override
    public void requestFullSync() {
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffLibReadC2S());
    }

    @Override
    public void sendPageOp(int op, int a, int b, boolean flag, String name) {
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageOpC2S(op, a, b, flag, name));
    }

    @Override
    public void sendPageWrite(int pageIndex, List<CompoundTag> iotaTags) {
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageWriteC2S(pageIndex, iotaTags));
    }

    @Override
    public void requestIdeParseToCode(int requestId, int pageIndex, List<CompoundTag> iotaTags) {
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgIdeParseToCodeC2S(
            requestId, pageIndex, ModNetworking.buildListTag(iotaTags)));
    }

    @Override
    public void requestIdeParseToIotas(int requestId, int pageIndex, String code) {
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgIdeParseToIotasC2S(requestId, pageIndex, code));
    }

    @Override
    public void checkGreatSpell(String actionId, int targetSlot) {
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffGreatSpellCheckC2S(actionId, targetSlot));
    }

    @Override
    public void clearCastStack() {
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffCastClearStackC2S());
    }
}
