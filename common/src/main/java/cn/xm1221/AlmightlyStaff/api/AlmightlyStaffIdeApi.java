package cn.xm1221.AlmightlyStaff.api;

import cn.xm1221.AlmightlyStaff.gui.ide.host.EmbeddedScreenHost;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * ASCode 客户端公开扩展 API：允许其他附属模组将自定义 {@link Screen}
 * 注册为万法之杖 IDE 中的可停靠/浮动外部窗口（自动接入书页树与 {@code Ctrl+K} 搜索）。
 */
public final class AlmightlyStaffIdeApi {
    private AlmightlyStaffIdeApi() {
    }

    public record ExternalScreenEntry(
        ResourceLocation id,
        Component title,
        BooleanSupplier available,
        Supplier<@Nullable Screen> screenFactory,
        boolean preferFloating,
        int defaultWidth,
        int defaultHeight
    ) {
        public ExternalScreenEntry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            available = available == null ? () -> true : available;
            Objects.requireNonNull(screenFactory, "screenFactory");
            defaultWidth = Math.max(240, defaultWidth);
            defaultHeight = Math.max(180, defaultHeight);
        }

        @Nullable
        public Screen createScreen() {
            return screenFactory.get();
        }
    }

    private static final Map<ResourceLocation, ExternalScreenEntry> EXTERNAL_SCREENS = new LinkedHashMap<>();

    /**
     * 注册一个返回 {@link Screen} 实例的外部界面入口。
     */
    public static synchronized void registerExternalScreen(ResourceLocation id, Component title,
                                                           Supplier<@Nullable Screen> screenFactory) {
        registerExternalScreen(new ExternalScreenEntry(id, title, () -> true, screenFactory, true, 340, 250));
    }

    /**
     * 注册带可见性守卫的外部界面入口。
     */
    public static synchronized void registerExternalScreen(ResourceLocation id, Component title,
                                                           BooleanSupplier available,
                                                           Supplier<@Nullable Screen> screenFactory) {
        registerExternalScreen(new ExternalScreenEntry(id, title, available, screenFactory, true, 340, 250));
    }

    /**
     * 注册通过调用第三方 API（内部调用 {@code Minecraft.setScreen}）打开的外部界面；
     * 宿主会自动在捕获模式下拦截目标 {@link Screen}。
     */
    public static synchronized void registerCapturedExternalScreen(ResourceLocation id, Component title,
                                                                   BooleanSupplier available,
                                                                   Runnable opener) {
        Objects.requireNonNull(opener, "opener");
        registerExternalScreen(new ExternalScreenEntry(
            id, title, available,
            () -> EmbeddedScreenHost.captureScreen(opener),
            true, 340, 250
        ));
    }

    /**
     * 注册完整配置的外部界面条目（相同 {@code id} 后注册覆盖前注册）。
     */
    public static synchronized void registerExternalScreen(ExternalScreenEntry entry) {
        Objects.requireNonNull(entry, "entry");
        EXTERNAL_SCREENS.put(entry.id(), entry);
    }

    public static synchronized boolean unregisterExternalScreen(ResourceLocation id) {
        return id != null && EXTERNAL_SCREENS.remove(id) != null;
    }

    public static synchronized List<ExternalScreenEntry> externalScreens() {
        List<ExternalScreenEntry> out = new ArrayList<>();
        for (ExternalScreenEntry e : EXTERNAL_SCREENS.values()) {
            try {
                if (e.available().getAsBoolean()) {
                    out.add(e);
                }
            } catch (Throwable ignored) {
            }
        }
        return Collections.unmodifiableList(out);
    }
}
