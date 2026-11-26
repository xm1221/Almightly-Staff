package cn.xm1221.AlmightlyStaff.gui.ide.layout;

import cn.xm1221.AlmightlyStaff.client.AlmightlyStaffClientConfig;
import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeScissor;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 现代浮动工具窗：支持标题栏拖拽移动、四边/四角缩放、最小化到工具条、工作区内最大化、双击最大化以及拖回停靠区预览。
 */
public final class FloatingWindow {
    public static final int HEADER_HEIGHT = 20;
    private static final int EDGE_HIT = 4;
    private static final int CORNER_HIT = 8;
    private static final long DOUBLE_CLICK_MS = 280L;

    private final String id;
    private final Component title;
    @Nullable
    private final IdeTheme.Icon icon;
    private final boolean showTitle;
    private final IdeComponent content;
    private final int minW;
    private final int minH;
    @Nullable
    private final Runnable onMinimize;
    @Nullable
    private final Runnable onClose;

    private Bounds bounds;
    private Bounds restoreBounds = Bounds.EMPTY;
    private boolean maximized = false;
    private int dragMode; // 0=none, 1=move, bitmask: 2=L, 4=R, 8=T, 16=B
    private double dragStartX, dragStartY;
    private Bounds dragStartBounds = Bounds.EMPTY;
    private long lastHeaderClickMs = 0L;

    public FloatingWindow(String id, Component title, IdeComponent content, Bounds initialBounds,
                          int minW, int minH, @Nullable Runnable onMinimize, @Nullable Runnable onClose) {
        this(id, title, null, true, content, initialBounds, minW, minH, onMinimize, onClose);
    }

    public FloatingWindow(String id, Component title, @Nullable IdeTheme.Icon icon, boolean showTitle,
                          IdeComponent content, Bounds initialBounds,
                          int minW, int minH, @Nullable Runnable onMinimize, @Nullable Runnable onClose) {
        this.id = id;
        this.title = title;
        this.icon = icon;
        this.showTitle = showTitle;
        this.content = content;
        this.minW = Math.max(160, minW);
        this.minH = Math.max(110, minH);
        this.onMinimize = onMinimize;
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

    public boolean isMoving() {
        return dragMode == 1;
    }

    public boolean isMaximized() {
        return maximized;
    }

    public Bounds bodyBounds() {
        int inset = IdeTheme.contentInset();
        return bounds.inset(inset, HEADER_HEIGHT, inset, inset);
    }

    public boolean contains(double mx, double my) {
        return bounds.contains(mx, my);
    }

    public void setBounds(Bounds next, int maxW, int maxH, int minTop) {
        this.bounds = clampBounds(next, maxW, maxH, minTop);
        this.content.layout(bodyBounds());
    }

    public void toggleMaximize(Bounds workArea) {
        if (workArea.isEmpty()) return;
        if (!maximized) {
            restoreBounds = bounds;
            maximized = true;
            bounds = workArea;
        } else {
            maximized = false;
            bounds = clampBounds(restoreBounds.isEmpty() ? workArea.inset(40) : restoreBounds,
                workArea.right(), workArea.bottom(), workArea.y());
        }
        content.layout(bodyBounds());
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

    private Bounds maxBtnBounds() {
        return new Bounds(bounds.right() - 32, bounds.y() + 3, 14, 14);
    }

    private Bounds minBtnBounds() {
        return onMinimize == null ? Bounds.EMPTY : new Bounds(bounds.right() - 47, bounds.y() + 3, 14, 14);
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTick, boolean focused) {
        g.fill(bounds.x() - 2, bounds.y() - 2, bounds.right() + 2, bounds.bottom() + 2, 0x77000000);
        IdeTheme.drawNineSlice(g, bounds, focused ? IdeTheme.NineSlice.FLOAT_ACTIVE : IdeTheme.NineSlice.FLOAT_INACTIVE);

        int inset = IdeTheme.borderThickness();
        Bounds header = new Bounds(
            bounds.x() + inset,
            bounds.y() + inset,
            Math.max(0, bounds.w() - inset * 2),
            Math.max(1, HEADER_HEIGHT - inset)
        );
        IdeTheme.drawNineSlice(g, header, IdeTheme.NineSlice.HEADER);
        if (focused) {
            g.fill(header.x(), header.y(), header.right(), header.y() + 2, IdeTheme.ACCENT);
        }

        int textX = header.x() + 6;
        if (icon != null) {
            IdeTheme.drawIcon(g, icon, header.x() + 6, header.y() + 6, focused ? IdeTheme.ACCENT_LIGHT : IdeTheme.TEXT_MUTED);
            textX = header.x() + (icon.size() > 8 ? 20 : 16);
        }
        if (showTitle) {
            int textRight = onMinimize != null ? minBtnBounds().x() - 4 : maxBtnBounds().x() - 4;
            IdeTheme.drawClippedString(g, font, title, textX, header.y() + 5,
                Math.max(10, textRight - textX), focused ? IdeTheme.TEXT_MAIN : IdeTheme.TEXT_MUTED);
        }

        Bounds minB = minBtnBounds();
        if (!minB.isEmpty()) {
            if (minB.contains(mouseX, mouseY)) IdeTheme.drawNineSlice(g, minB, IdeTheme.NineSlice.BTN_HOVER);
            IdeTheme.drawIcon(g, IdeTheme.Icon.MINIMIZE_BAR, minB.x() + 3, minB.y() + 3, IdeTheme.TEXT_MAIN);
        }

        Bounds maxB = maxBtnBounds();
        if (maxB.contains(mouseX, mouseY)) IdeTheme.drawNineSlice(g, maxB, IdeTheme.NineSlice.BTN_HOVER);
        IdeTheme.drawIcon(g, maximized ? IdeTheme.Icon.RESTORE_BOX : IdeTheme.Icon.MAXIMIZE_BOX,
            maxB.x() + 3, maxB.y() + 3, IdeTheme.TEXT_MAIN);

        Bounds closeB = closeBtnBounds();
        if (closeB.contains(mouseX, mouseY)) {
            g.fill(closeB.x(), closeB.y(), closeB.right(), closeB.bottom(), IdeTheme.RED_ERROR);
        }
        IdeTheme.drawIcon(g, IdeTheme.Icon.CLOSE_X, closeB.x() + 3, closeB.y() + 3, IdeTheme.TEXT_MAIN);

        Bounds body = bodyBounds();
        if (!body.isEmpty()) {
            try (IdeScissor.Scope sc = IdeScissor.push(g, body)) {
                content.render(g, mouseX, mouseY, partialTick);
            }
            content.renderOverlay(g, mouseX, mouseY, partialTick);
        }

        if (!maximized) {
            int handleInset = Math.max(2, IdeTheme.contentInset() + 1);
            int rx = bounds.right() - handleInset, by = bounds.bottom() - handleInset;
            g.fill(rx - 6, by, rx, by + 1, IdeTheme.TEXT_MUTED);
            g.fill(rx - 3, by - 3, rx, by - 2, IdeTheme.TEXT_MUTED);
        }
        if (IdeTheme.borderThickness() > 1) {
            IdeTheme.drawBorder(g, bounds, focused ? IdeTheme.ACCENT : IdeTheme.BORDER_STRONG);
        }
        if (!showTitle && icon != null && mouseX >= header.x() + 2 && mouseX < header.x() + 18
            && mouseY >= header.y() && mouseY < header.bottom()) {
            g.renderTooltip(font, title, mouseX, mouseY);
        }
    }

    public boolean mouseClicked(double mx, double my, int button, Bounds workArea) {
        if (!bounds.contains(mx, my)) return false;
        if (button == 0) {
            if (closeBtnBounds().contains(mx, my)) {
                if (onClose != null) onClose.run();
                return true;
            }
            if (maxBtnBounds().contains(mx, my)) {
                toggleMaximize(workArea);
                return true;
            }
            if (minBtnBounds().contains(mx, my)) {
                if (onMinimize != null) onMinimize.run();
                return true;
            }
            if (!maximized) {
                int resizeMask = hitResizeMask(mx, my);
                if (resizeMask != 0) {
                    dragMode = resizeMask;
                    dragStartX = mx;
                    dragStartY = my;
                    dragStartBounds = bounds;
                    return true;
                }
            }
            if (my < bounds.y() + HEADER_HEIGHT) {
                long now = System.currentTimeMillis();
                if (now - lastHeaderClickMs <= DOUBLE_CLICK_MS) {
                    lastHeaderClickMs = 0L;
                    toggleMaximize(workArea);
                    return true;
                }
                lastHeaderClickMs = now;
                if (!maximized) {
                    dragMode = 1;
                    dragStartX = mx;
                    dragStartY = my;
                    dragStartBounds = bounds;
                }
                return true;
            }
        }
        if (bodyBounds().contains(mx, my)) {
            content.mouseClicked(mx, my, button);
        }
        return true;
    }

    public void beginExternalDrag(double mx, double my) {
        dragMode = 1;
        dragStartX = mx;
        dragStartY = my;
        dragStartBounds = bounds;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy,
                                Bounds workArea, java.util.List<FloatingWindow> others) {
        int snap = AlmightlyStaffClientConfig.get().windowSnapDistance();
        if (dragMode == 1) {
            int nx = dragStartBounds.x() + (int) Math.round(mx - dragStartX);
            int ny = dragStartBounds.y() + (int) Math.round(my - dragStartY);
            int w = dragStartBounds.w();
            int h = dragStartBounds.h();
            java.util.List<Bounds> otherBounds = new java.util.ArrayList<>();
            for (FloatingWindow o : others) {
                if (o != this) otherBounds.add(o.bounds());
            }
            Bounds snapped = snapMoveBounds(nx, ny, w, h, workArea, otherBounds, snap);
            setBounds(snapped, Math.max(workArea.right(), w + 16), Math.max(workArea.bottom(), h + 40), workArea.y());
            return true;
        }
        if (dragMode > 1) {
            int ddx = (int) Math.round(mx - dragStartX);
            int ddy = (int) Math.round(my - dragStartY);
            int x1 = dragStartBounds.x();
            int y1 = dragStartBounds.y();
            int x2 = dragStartBounds.right();
            int y2 = dragStartBounds.bottom();
            java.util.List<Bounds> otherBounds = new java.util.ArrayList<>();
            for (FloatingWindow o : others) {
                if (o != this) otherBounds.add(o.bounds());
            }
            if ((dragMode & 2) != 0) {
                int raw = Math.min(x2 - minW, x1 + ddx);
                x1 = Math.min(x2 - minW, snapEdge(raw, snap, collectVerticalEdges(workArea, otherBounds)));
            }
            if ((dragMode & 4) != 0) {
                int raw = Math.max(x1 + minW, x2 + ddx);
                x2 = Math.max(x1 + minW, snapEdge(raw, snap, collectVerticalEdges(workArea, otherBounds)));
            }
            if ((dragMode & 8) != 0) {
                int raw = Math.max(workArea.y(), Math.min(y2 - minH, y1 + ddy));
                y1 = Math.max(workArea.y(), Math.min(y2 - minH, snapEdge(raw, snap, collectHorizontalEdges(workArea, otherBounds))));
            }
            if ((dragMode & 16) != 0) {
                int raw = Math.max(y1 + minH, y2 + ddy);
                y2 = Math.max(y1 + minH, snapEdge(raw, snap, collectHorizontalEdges(workArea, otherBounds)));
            }
            setBounds(new Bounds(x1, y1, x2 - x1, y2 - y1), workArea.right(), workArea.bottom(), workArea.y());
            return true;
        }
        return content.mouseDragged(mx, my, button, dx, dy);
    }

    /** 贴边吸附（工作区边缘与其它浮窗边缘），返回吸附后的窗口矩形。 */
    public static Bounds snapMoveBounds(int nx, int ny, int w, int h, Bounds workArea,
                                        java.util.List<Bounds> others, int snapDistance) {
        if (snapDistance <= 0) {
            return new Bounds(nx, ny, w, h);
        }
        int[] vEdges = collectVerticalEdges(workArea, others);
        int[] hEdges = collectHorizontalEdges(workArea, others);
        int snappedX = snapSpan(nx, w, snapDistance, vEdges);
        int snappedY = snapSpan(ny, h, snapDistance, hEdges);
        return new Bounds(snappedX, snappedY, w, h);
    }

    private static int[] collectVerticalEdges(Bounds workArea, java.util.List<Bounds> others) {
        int count = 2 + (others != null ? others.size() * 2 : 0);
        int[] out = new int[count];
        int i = 0;
        out[i++] = workArea.x();
        out[i++] = workArea.right();
        if (others != null) {
            for (Bounds ob : others) {
                out[i++] = ob.x();
                out[i++] = ob.right();
            }
        }
        return out;
    }

    private static int[] collectHorizontalEdges(Bounds workArea, java.util.List<Bounds> others) {
        int count = 2 + (others != null ? others.size() * 2 : 0);
        int[] out = new int[count];
        int i = 0;
        out[i++] = workArea.y();
        out[i++] = workArea.bottom();
        if (others != null) {
            for (Bounds ob : others) {
                out[i++] = ob.y();
                out[i++] = ob.bottom();
            }
        }
        return out;
    }

    private static int snapSpan(int start, int size, int snap, int[] targets) {
        int bestDelta = snap + 1;
        int result = start;
        for (int t : targets) {
            int dStart = Math.abs(start - t);
            if (dStart < bestDelta) {
                bestDelta = dStart;
                result = t;
            }
            int dEnd = Math.abs((start + size) - t);
            if (dEnd < bestDelta) {
                bestDelta = dEnd;
                result = t - size;
            }
        }
        return result;
    }

    private static int snapEdge(int pos, int snap, int[] targets) {
        if (snap <= 0) return pos;
        int bestDist = snap + 1;
        int result = pos;
        for (int t : targets) {
            int d = Math.abs(pos - t);
            if (d < bestDist) {
                bestDist = d;
                result = t;
            }
        }
        return result;
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
