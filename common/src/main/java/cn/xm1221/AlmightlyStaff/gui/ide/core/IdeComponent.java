package cn.xm1221.AlmightlyStaff.gui.ide.core;

import net.minecraft.client.gui.GuiGraphics;

/**
 * IDE 统一面板/控件契约。所有坐标均使用绝对 GUI 像素坐标。
 */
public interface IdeComponent {
    Bounds bounds();

    void layout(Bounds bounds);

    void render(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    /** 悬浮提示、补全列表、拖拽幽灵等高层覆盖绘制。 */
    default void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    default void mouseMoved(double mouseX, double mouseY) {
    }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    default void tick() {
    }

    default void onFocusGained() {
    }

    default void onFocusLost() {
    }
}
