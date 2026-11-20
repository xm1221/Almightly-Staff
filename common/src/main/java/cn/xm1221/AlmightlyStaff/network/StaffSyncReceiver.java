package cn.xm1221.AlmightlyStaff.network;

import net.minecraft.nbt.CompoundTag;

import java.util.List;

/**
 * 客户端同步回包接收接口，供 {@link cn.xm1221.AlmightlyStaff.gui.StaffLibScreen} 与
 * {@link cn.xm1221.AlmightlyStaff.gui.ide.StaffIdeScreen} 共用。
 */
public interface StaffSyncReceiver {
    void onSyncChunk(List<ModNetworking.PageData> chunkPages, int castPage, int focusPage,
                     List<Integer> removed, boolean lastChunk, boolean incremental,
                     String status, String staffUuid);

    void onGreatSpellCheck(String actionId, boolean usable, String signature, String startDir);

    default void onIdeParseToCode(int requestId, int pageIndex, String code, String error) {
    }

    default void onIdeParseToIotas(int requestId, int pageIndex, boolean ok,
                                   List<CompoundTag> iotaTags, String error, int errorLine) {
    }
}
