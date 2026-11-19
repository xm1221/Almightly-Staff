package cn.xm1221.AlmightlyStaff.gui.ide.tool;

import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeScissor;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import cn.xm1221.AlmightlyStaff.gui.ide.layout.DockNode;
import cn.xm1221.AlmightlyStaff.gui.ide.layout.SplitterDragState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 下侧双模输入面板容器：支持「网格 + HexParse 并排」、「仅 HexGrid」和「仅 HexParse」切换。
 */
public final class BottomToolDock implements IdeComponent {
    private static final int TAB_H = 20;

    private final HexGridPanel gridPanel;
    private final IdeComponent parsePanel;
    private final DockNode.LeafNode gridLeaf = new DockNode.LeafNode("bottom_grid");
    private final DockNode.LeafNode parseLeaf = new DockNode.LeafNode("bottom_parse");
    private final DockNode.SplitNode splitRoot;
    private final SplitterDragState dragState = new SplitterDragState();
    private Bounds bounds = Bounds.EMPTY;
    private String mode = "split"; // "split" | "grid" | "parse"

    public BottomToolDock(HexGridPanel gridPanel, IdeComponent parsePanel, float initialRatio, String initialMode) {
        this.gridPanel = gridPanel;
        this.parsePanel = parsePanel;
        this.splitRoot = new DockNode.SplitNode("bottom_split", DockNode.Axis.HORIZONTAL,
            initialRatio, 140, 180, gridLeaf, parseLeaf);
        setMode(initialMode);
    }

    public String mode() {
        return mode;
    }

    public float splitRatio() {
        return splitRoot.ratio();
    }

    public void setMode(String nextMode) {
        if (!"split".equals(nextMode) && !"grid".equals(nextMode) && !"parse".equals(nextMode)) {
            nextMode = "split";
        }
        this.mode = nextMode;
        gridLeaf.setCollapsed("parse".equals(nextMode));
        parseLeaf.setCollapsed("grid".equals(nextMode));
        layout(bounds);
    }

    @Override
    public Bounds bounds() {
        return bounds;
    }

    @Override
    public void layout(Bounds bounds) {
        this.bounds = bounds;
        Bounds body = bounds.inset(0, TAB_H, 0, 0);
        splitRoot.layout(body);
        gridPanel.layout(gridLeaf.bounds());
        parsePanel.layout(parseLeaf.bounds());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (bounds.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        Bounds header = new Bounds(bounds.x(), bounds.y(), bounds.w(), Math.min(TAB_H, bounds.h()));
        g.fill(header.x(), header.y(), header.right(), header.bottom(), IdeTheme.BG_PANEL);
        g.fill(header.x(), header.bottom() - 1, header.right(), header.bottom(), IdeTheme.BORDER);

        try (IdeScissor.Scope sc = IdeScissor.push(g, header)) {
            int x = header.x() + 6;
            x = drawModeTab(g, font, x, header.y() + 2, "split",
                Component.translatable("almightly_staff.ide.bottom_split").getString(), mouseX, mouseY);
            x = drawModeTab(g, font, x + 4, header.y() + 2, "grid",
                Component.translatable("almightly_staff.ide.bottom_grid").getString(), mouseX, mouseY);
            drawModeTab(g, font, x + 4, header.y() + 2, "parse",
                Component.translatable("almightly_staff.ide.bottom_parse").getString(), mouseX, mouseY);
        }

        if (!gridLeaf.isCollapsed()) {
            gridPanel.render(g, mouseX, mouseY, partialTick);
        }
        if (!parseLeaf.isCollapsed()) {
            parsePanel.render(g, mouseX, mouseY, partialTick);
        }
        Bounds sb = splitRoot.splitterBounds();
        if (!sb.isEmpty()) {
            boolean hot = dragState.isDragging() || sb.contains(mouseX, mouseY);
            g.fill(sb.x(), sb.y(), sb.right(), sb.bottom(), IdeTheme.BORDER);
            if (hot) {
                int cx = sb.x() + sb.w() / 2;
                g.fill(cx, sb.y(), cx + 1, sb.bottom(), IdeTheme.ACCENT);
            }
        }
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!gridLeaf.isCollapsed()) gridPanel.renderOverlay(g, mouseX, mouseY, partialTick);
        if (!parseLeaf.isCollapsed()) parsePanel.renderOverlay(g, mouseX, mouseY, partialTick);
    }

    private int drawModeTab(GuiGraphics g, Font font, int x, int y, String id, String label, int mx, int my) {
        int w = font.width(label) + 12;
        boolean active = id.equals(mode);
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 16;
        if (active) {
            g.fill(x, y, x + w, y + 16, IdeTheme.BG_ACTIVE);
        } else if (hover) {
            g.fill(x, y, x + w, y + 16, IdeTheme.BG_HOVER);
        }
        g.drawString(font, label, x + 6, y + 4, active ? IdeTheme.TEXT_MAIN : IdeTheme.TEXT_MUTED, false);
        return x + w;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my)) return false;
        if (my < bounds.y() + TAB_H && button == 0) {
            Font font = Minecraft.getInstance().font;
            int x = bounds.x() + 6;
            String[] ids = {"split", "grid", "parse"};
            String[] keys = {"almightly_staff.ide.bottom_split", "almightly_staff.ide.bottom_grid", "almightly_staff.ide.bottom_parse"};
            for (int i = 0; i < ids.length; i++) {
                int w = font.width(Component.translatable(keys[i]).getString()) + 12;
                if (mx >= x && mx < x + w) {
                    setMode(ids[i]);
                    return true;
                }
                x += w + 4;
            }
            return true;
        }
        if (button == 0 && dragState.beginDrag(splitRoot, mx, my)) {
            return true;
        }
        if (!gridLeaf.isCollapsed() && gridPanel.bounds().contains(mx, my)) {
            return gridPanel.mouseClicked(mx, my, button);
        }
        if (!parseLeaf.isCollapsed() && parsePanel.bounds().contains(mx, my)) {
            return parsePanel.mouseClicked(mx, my, button);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragState.drag(splitRoot, bounds.inset(0, TAB_H, 0, 0), mx, my)) {
            gridPanel.layout(gridLeaf.bounds());
            parsePanel.layout(parseLeaf.bounds());
            return true;
        }
        return (!gridLeaf.isCollapsed() && gridPanel.mouseDragged(mx, my, button, dx, dy))
            || (!parseLeaf.isCollapsed() && parsePanel.mouseDragged(mx, my, button, dx, dy));
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragState.endDrag()) return true;
        boolean a = !gridLeaf.isCollapsed() && gridPanel.mouseReleased(mx, my, button);
        boolean b = !parseLeaf.isCollapsed() && parsePanel.mouseReleased(mx, my, button);
        return a || b;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!bounds.contains(mx, my)) return false;
        if (!gridLeaf.isCollapsed() && gridPanel.bounds().contains(mx, my)) {
            return gridPanel.mouseScrolled(mx, my, delta);
        }
        if (!parseLeaf.isCollapsed() && parsePanel.bounds().contains(mx, my)) {
            return parsePanel.mouseScrolled(mx, my, delta);
        }
        return false;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (!gridLeaf.isCollapsed()) gridPanel.mouseMoved(mx, my);
        if (!parseLeaf.isCollapsed()) parsePanel.mouseMoved(mx, my);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return (!parseLeaf.isCollapsed() && parsePanel.keyPressed(keyCode, scanCode, modifiers))
            || (!gridLeaf.isCollapsed() && gridPanel.keyPressed(keyCode, scanCode, modifiers));
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return (!parseLeaf.isCollapsed() && parsePanel.charTyped(codePoint, modifiers))
            || (!gridLeaf.isCollapsed() && gridPanel.charTyped(codePoint, modifiers));
    }

    @Override
    public void tick() {
        if (!gridLeaf.isCollapsed()) gridPanel.tick();
        if (!parseLeaf.isCollapsed()) parsePanel.tick();
    }
}
