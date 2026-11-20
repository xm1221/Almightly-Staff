package cn.xm1221.AlmightlyStaff;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/**
 * 测试引导：{@code Component.Serializer.toJson} 等 API 会静态初始化
 * {@code BuiltInRegistries}，而它要求 {@link Bootstrap#bootStrap()} 已调用过，
 * 否则抛 {@code IllegalArgumentException: Not bootstrapped}。
 *
 * <p>{@link SharedConstants} 的版本探测也必须先跑，否则注册表里的
 * 数据包版本校验会失败。两者都是幂等的，重复调用安全。
 */
public final class MinecraftBootstrap {
    private static boolean done;

    private MinecraftBootstrap() {
    }

    public static synchronized void ensure() {
        if (done) return;
        try {
            SharedConstants.tryDetectVersion();
        } catch (Throwable ignored) {
            // 某些环境没有版本资源，继续尝试 bootstrap
        }
        try {
            Bootstrap.bootStrap();
        } catch (Throwable ignored) {
            // 已经引导过会抛异常，忽略
        }
        done = true;
    }
}
