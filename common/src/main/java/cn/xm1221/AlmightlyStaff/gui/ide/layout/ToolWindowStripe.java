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
 * 左侧 20px 边栏工具窗窄条（ToolWindow Stripe），支持一键折叠/展开面板。
 */
public final class ToolWindowStripe implements IdeComponent {
    public static final int WIDTH = 20;

    public record StripeButton(String id, IdeTheme.Icon icon, Component tooltip,
                               BooleanSupplier isActive, Runnable onToggle) {
    }

    private final List<StripeButton> buttons = new ArrayList<>();
    private Bounds bounds = Bounds.EMPTY;

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
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), IdeTheme.BG_PANEL);
        g.fill(bounds.right() - 1, bounds.y(), bounds.right(), bounds.bottom(), IdeTheme.BORDER);
        try (IdeScissor.Scope sc = IdeScissor.push(g, bounds)) {
            int btnSize = 16;
            int y = bounds.y() + 6;
            int x = bounds.x() + 2;
            for (StripeButton btn : buttons) {
                boolean active = btn.isActive().getAsBoolean();
                boolean hover = mouseX >= x && mouseX < x + btnSize && mouseY >= y && mouseY < y + btnSize;
                if (active) {
                    g.fill(x, y, x + btnSize, y + btnSize, IdeTheme.BG_ACTIVE);
                    g.fill(bounds.x(), y + 2, bounds.x() + 2, y + btnSize - 2, IdeTheme.ACCENT);
                } else if (hover) {
                    g.fill(x, y, x + btnSize, y + btnSize, IdeTheme.BG_HOVER);
                }
                IdeTheme.drawIcon(g, btn.icon(), x + 4, y + 4,
                    active ? IdeTheme.TEXT_MAIN : IdeTheme.TEXT_MUTED);
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
                btn.onToggle().run();
                return true;
            }
            y += btnSize + 4;
        }
        return true;
    }
}
