package cn.xm1221.AlmightlyStaff.gui.ide.layout;

import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeScissor;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 可拖拽移动、四边/四角缩放、支持“停靠回编辑器”的浮动工具窗。
 */
public final class FloatingWindow {
    public static final int HEADER_HEIGHT = 20;
    private static final int EDGE_HIT = 4;
    private static final int CORNER_HIT = 8;

    private final String id;
    private final Component title;
    private final IdeComponent content;
    private final int minW;
    private final int minH;
    @Nullable
    private final Runnable onDockBack;
    @Nullable
    private final Runnable onClose;

    private Bounds bounds;
    private int dragMode; // 0=none, 1=move, bitmask: 2=L, 4=R, 8=T, 16=B
    private double dragStartX, dragStartY;
    private Bounds dragStartBounds = Bounds.EMPTY;

    public FloatingWindow(String id, Component title, IdeComponent content, Bounds initialBounds,
                          int minW, int minH, @Nullable Runnable onDockBack, @Nullable Runnable onClose) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.minW = Math.max(180, minW);
        this.minH = Math.max(120, minH);
        this.onDockBack = onDockBack;
        this.onClose = onClose;
        this.bounds = clampBounds(initialBounds, 10000, 10000, 0);
        this.content.layout(bodyBounds());
    }

    public String id() {
        return id;
    }

    public Component title() {
        return title;
    }

    public IdeComponent content() {
        return content;
    }

    public Bounds bounds() {
        return bounds;
    }

    public Bounds bodyBounds() {
        return bounds.inset(1, HEADER_HEIGHT, 1, 1);
    }

    public boolean contains(double mx, double my) {
        return bounds.contains(mx, my);
    }

    public void setBounds(Bounds next, int maxW, int maxH, int minTop) {
        this.bounds = clampBounds(next, maxW, maxH, minTop);
        this.content.layout(bodyBounds());
    }

    private Bounds clampBounds(Bounds b, int maxW, int maxH, int minTop) {
        int w = Math.max(minW, Math.min(Math.max(minW, maxW - 8), b.w()));
        int h = Math.max(minH, Math.min(Math.max(minH, maxH - minTop - 8), b.h()));
        int x = Math.max(0, Math.min(Math.max(0, maxW - w), b.x()));
        int y = Math.max(minTop, Math.min(Math.max(minTop, maxH - h), b.y()));
        return new Bounds(x, y, w, h);
    }

    private Bounds closeBtnBounds() {
        return new Bounds(bounds.right() - 17, bounds.y() + 3, 14, 14);
    }

    private Bounds dockBtnBounds() {
        return onDockBack == null ? Bounds.EMPTY
            : new Bounds(bounds.right() - 33, bounds.y() + 3, 14, 14);
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTick, boolean focused) {
        g.fill(bounds.x() - 2, bounds.y() - 2, bounds.right() + 2, bounds.bottom() + 2, 0x77000000);
        IdeTheme.drawPanel(g, bounds, IdeTheme.BG_PANEL, focused ? IdeTheme.ACCENT : IdeTheme.BORDER_STRONG);

        // 标题栏
        Bounds header = new Bounds(bounds.x() + 1, bounds.y() + 1, Math.max(0, bounds.w() - 2), HEADER_HEIGHT - 1);
        g.fill(header.x(), header.y(), header.right(), header.bottom(), IdeTheme.BG_ELEVATED);
        g.fill(header.x(), header.bottom() - 1, header.right(), header.bottom(), IdeTheme.BORDER);
        int textRight = onDockBack != null ? dockBtnBounds().x() - 4 : closeBtnBounds().x() - 4;
        IdeTheme.drawClippedString(g, font, title, header.x() + 6, header.y() + 5,
            Math.max(10, textRight - header.x() - 6), IdeTheme.TEXT_MAIN);

        Bounds dockB = dockBtnBounds();
        if (!dockB.isEmpty()) {
            if (dockB.contains(mouseX, mouseY)) {
                g.fill(dockB.x(), dockB.y(), dockB.right(), dockB.bottom(), IdeTheme.BG_HOVER);
            }
            IdeTheme.drawIcon(g, IdeTheme.Icon.DOCK_BACK, dockB.x() + 3, dockB.y() + 3, IdeTheme.TEXT_MAIN);
        }

        Bounds closeB = closeBtnBounds();
        if (closeB.contains(mouseX, mouseY)) {
            g.fill(closeB.x(), closeB.y(), closeB.right(), closeB.bottom(), IdeTheme.RED_ERROR);
        }
        IdeTheme.drawIcon(g, IdeTheme.Icon.CLOSE_X, closeB.x() + 3, closeB.y() + 3, IdeTheme.TEXT_MAIN);

        // 内容区严格裁剪
        Bounds body = bodyBounds();
        if (!body.isEmpty()) {
            try (IdeScissor.Scope sc = IdeScissor.push(g, body)) {
                content.render(g, mouseX, mouseY, partialTick);
            }
            content.renderOverlay(g, mouseX, mouseY, partialTick);
        }

        // 右下角缩放把手提示线
        int rx = bounds.right() - 2, by = bounds.bottom() - 2;
        g.fill(rx - 6, by, rx, by + 1, IdeTheme.TEXT_MUTED);
        g.fill(rx - 3, by - 3, rx, by - 2, IdeTheme.TEXT_MUTED);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my)) return false;
        if (button == 0) {
            if (closeBtnBounds().contains(mx, my)) {
                if (onClose != null) onClose.run();
                return true;
            }
            if (dockBtnBounds().contains(mx, my)) {
                if (onDockBack != null) onDockBack.run();
                return true;
            }
            int resizeMask = hitResizeMask(mx, my);
            if (resizeMask != 0) {
                dragMode = resizeMask;
                dragStartX = mx;
                dragStartY = my;
                dragStartBounds = bounds;
                return true;
            }
            if (my < bounds.y() + HEADER_HEIGHT) {
                dragMode = 1;
                dragStartX = mx;
                dragStartY = my;
                dragStartBounds = bounds;
                return true;
            }
        }
        if (bodyBounds().contains(mx, my)) {
            content.mouseClicked(mx, my, button);
        }
        return true; // 浮动窗内部点击一律吞掉，不穿透到底层
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy,
                                int maxW, int maxH, int minTop) {
        if (dragMode == 1) {
            int nx = dragStartBounds.x() + (int) Math.round(mx - dragStartX);
            int ny = dragStartBounds.y() + (int) Math.round(my - dragStartY);
            setBounds(new Bounds(nx, ny, dragStartBounds.w(), dragStartBounds.h()), maxW, maxH, minTop);
            return true;
        }
        if (dragMode > 1) {
            int ddx = (int) Math.round(mx - dragStartX);
            int ddy = (int) Math.round(my - dragStartY);
            int x1 = dragStartBounds.x();
            int y1 = dragStartBounds.y();
            int x2 = dragStartBounds.right();
            int y2 = dragStartBounds.bottom();
            if ((dragMode & 2) != 0) x1 = Math.min(x2 - minW, x1 + ddx);
            if ((dragMode & 4) != 0) x2 = Math.max(x1 + minW, x2 + ddx);
            if ((dragMode & 8) != 0) y1 = Math.max(minTop, Math.min(y2 - minH, y1 + ddy));
            if ((dragMode & 16) != 0) y2 = Math.max(y1 + minH, y2 + ddy);
            setBounds(new Bounds(x1, y1, x2 - x1, y2 - y1), maxW, maxH, minTop);
            return true;
        }
        return content.mouseDragged(mx, my, button, dx, dy);
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (dragMode != 0) {
            dragMode = 0;
            return true;
        }
        return content.mouseReleased(mx, my, button);
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!bounds.contains(mx, my)) return false;
        content.mouseScrolled(mx, my, delta);
        return true;
    }

    private int hitResizeMask(double mx, double my) {
        boolean nearL = mx - bounds.x() < CORNER_HIT;
        boolean nearR = bounds.right() - mx <= CORNER_HIT;
        boolean nearT = my - bounds.y() < CORNER_HIT;
        boolean nearB = bounds.bottom() - my <= CORNER_HIT;
        if ((nearL && nearT) || (nearR && nearT) || (nearL && nearB) || (nearR && nearB)) {
            int m = 0;
            if (nearL) m |= 2;
            if (nearR) m |= 4;
            if (nearT) m |= 8;
            if (nearB) m |= 16;
            return m;
        }
        int m = 0;
        if (mx - bounds.x() < EDGE_HIT) m |= 2;
        if (bounds.right() - mx <= EDGE_HIT) m |= 4;
        if (my - bounds.y() < EDGE_HIT) m |= 8;
        if (bounds.bottom() - my <= EDGE_HIT) m |= 16;
        return m;
    }
}
