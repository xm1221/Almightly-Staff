package cn.xm1221.AlmightlyStaff.gui.ide.layout;

import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeScissor;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 边缘 20px 窗口启动条：每个图标代表「打开或聚焦对应窗口」（支持左/右侧栏）。
 */
public final class ToolWindowStripe implements IdeComponent {
    public static final int WIDTH = 20;

    public record StripeButton(String id, IdeTheme.Icon icon, Component tooltip,
                               BooleanSupplier isOpen, BooleanSupplier isMinimized, Runnable onActivate) {
    }

    private final boolean rightSide;
    private final List<StripeButton> buttons = new ArrayList<>();
    private Bounds bounds = Bounds.EMPTY;

    public ToolWindowStripe(boolean rightSide) {
        this.rightSide = rightSide;
    }

    public void addButton(StripeButton button) {
        buttons.add(button);
    }

    @Override
    public Bounds bounds() {
        return bounds;
    }

    @Override
    public void layout(Bounds bounds) {
        this.bounds = bounds;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (bounds.isEmpty()) return;
        IdeTheme.drawNineSlice(g, bounds, IdeTheme.NineSlice.PANEL);
        if (rightSide) {
            g.fill(bounds.x(), bounds.y(), bounds.x() + 1, bounds.bottom(), IdeTheme.BORDER);
        } else {
            g.fill(bounds.right() - 1, bounds.y(), bounds.right(), bounds.bottom(), IdeTheme.BORDER);
        }

        try (IdeScissor.Scope sc = IdeScissor.push(g, bounds)) {
            int btnSize = 16;
            int y = bounds.y() + 6;
            int x = bounds.x() + 2;
            for (StripeButton btn : buttons) {
                boolean open = btn.isOpen().getAsBoolean();
                boolean min = btn.isMinimized().getAsBoolean();
                boolean hover = mouseX >= x && mouseX < x + btnSize && mouseY >= y && mouseY < y + btnSize;
                if (open) {
                    IdeTheme.drawNineSlice(g, x, y, btnSize, btnSize, IdeTheme.NineSlice.BTN_ACTIVE);
                    int barX = rightSide ? bounds.right() - 2 : bounds.x();
                    g.fill(barX, y + 2, barX + 2, y + btnSize - 2, IdeTheme.ACCENT);
                } else if (hover) {
                    IdeTheme.drawNineSlice(g, x, y, btnSize, btnSize, IdeTheme.NineSlice.BTN_HOVER);
                }
                int iconCol = open ? IdeTheme.TEXT_MAIN : (min ? IdeTheme.GOLD_SEAL_TEXT : IdeTheme.TEXT_MUTED);
                IdeTheme.drawIcon(g, btn.icon(), x + 4, y + 4, iconCol);
                if (min) {
                    g.fill(x + 12, y + 2, x + 14, y + 4, IdeTheme.GOLD_SEAL);
                }
                y += btnSize + 4;
            }
        }
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!bounds.contains(mouseX, mouseY)) return;
        Font font = Minecraft.getInstance().font;
        int btnSize = 16;
        int y = bounds.y() + 6;
        int x = bounds.x() + 2;
        for (StripeButton btn : buttons) {
            if (mouseX >= x && mouseX < x + btnSize && mouseY >= y && mouseY < y + btnSize) {
                g.renderTooltip(font, btn.tooltip(), mouseX, mouseY);
                return;
            }
            y += btnSize + 4;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !bounds.contains(mouseX, mouseY)) return false;
        int btnSize = 16;
        int y = bounds.y() + 6;
        int x = bounds.x() + 2;
        for (StripeButton btn : buttons) {
            if (mouseX >= x && mouseX < x + btnSize && mouseY >= y && mouseY < y + btnSize) {
                btn.onActivate().run();
                return true;
            }
            y += btnSize + 4;
        }
        return true;
    }
}
