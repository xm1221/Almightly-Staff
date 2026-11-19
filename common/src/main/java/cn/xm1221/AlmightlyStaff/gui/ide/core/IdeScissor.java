package cn.xm1221.AlmightlyStaff.gui.ide.core;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 嵌套裁剪栈薄包装（底层委托 MC 1.20.1 {@code GuiGraphics$ScissorStack} 自动求交）。
 *
 * <p>IDE 内部所有面板严禁直接调用 {@code GuiGraphics.enableScissor/disableScissor}，
 * 必须使用 {@code try (var sc = IdeScissor.push(g, ...))} 保证栈平衡与永不溢出。
 */
public final class IdeScissor {
    private IdeScissor() {
    }

    public static final class Scope implements AutoCloseable {
        private final GuiGraphics graphics;
        private boolean closed;

        private Scope(GuiGraphics graphics) {
            this.graphics = graphics;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                graphics.disableScissor();
            }
        }
    }

    public static Scope push(GuiGraphics g, Bounds b) {
        if (b == null) {
            return push(g, 0, 0, 0, 0);
        }
        return push(g, b.x(), b.y(), b.w(), b.h());
    }

    public static Scope push(GuiGraphics g, int x, int y, int w, int h) {
        int cw = Math.max(0, w);
        int ch = Math.max(0, h);
        g.enableScissor(x, y, x + cw, y + ch);
        return new Scope(g);
    }
}
