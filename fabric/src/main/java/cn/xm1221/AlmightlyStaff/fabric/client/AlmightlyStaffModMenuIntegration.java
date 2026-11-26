package cn.xm1221.AlmightlyStaff.fabric.client;

import cn.xm1221.AlmightlyStaff.client.StaffClientConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu 配置入口（仅在安装 ModMenu 时由其加载）。
 */
public final class AlmightlyStaffModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return StaffClientConfigScreen::new;
    }
}
