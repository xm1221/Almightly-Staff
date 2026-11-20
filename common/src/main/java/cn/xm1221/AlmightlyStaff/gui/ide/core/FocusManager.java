package cn.xm1221.AlmightlyStaff.gui.ide.core;

import org.jetbrains.annotations.Nullable;

/**
 * 全局唯一焦点控制器：切走焦点时显式触发旧组件 {@link IdeComponent#onFocusLost()}，
 * 避免多输入框并存时中文 IME 候选框与键盘事件错乱。
 */
public final class FocusManager {
    @Nullable
    private IdeComponent focused;
    @Nullable
    private final Runnable onSwitchCallback;

    public FocusManager() {
        this(null);
    }

    public FocusManager(@Nullable Runnable onSwitchCallback) {
        this.onSwitchCallback = onSwitchCallback;
    }

    @Nullable
    public IdeComponent focused() {
        return focused;
    }

    public boolean isFocused(@Nullable IdeComponent component) {
        return component != null && focused == component;
    }

    public void focus(@Nullable IdeComponent next) {
        if (focused == next) {
            return;
        }
        IdeComponent prev = focused;
        focused = next;
        if (prev != null) {
            prev.onFocusLost();
        }
        if (onSwitchCallback != null) {
            onSwitchCallback.run();
        }
        if (next != null) {
            next.onFocusGained();
        }
    }

    public void clear() {
        focus(null);
    }
}
