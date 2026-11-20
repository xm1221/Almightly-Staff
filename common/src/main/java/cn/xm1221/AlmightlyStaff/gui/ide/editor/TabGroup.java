package cn.xm1221.AlmightlyStaff.gui.ide.editor;

import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.ContextMenuOverlay;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeScissor;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 编辑器标签组：管理多个 {@link TabItem}、Tab 栏水平滚动、星标（*）、关闭按钮与右键分屏菜单。
 */
public final class TabGroup implements IdeComponent {
    public interface Host {
        void onGroupFocused(TabGroup group);

        void onSplitRequested(TabGroup source, TabItem item, boolean vertical);

        void onTabClosed(TabGroup group, TabItem item);

        void onSetCastPage(int pageIndex);

        void openContextMenu(int mx, int my, List<ContextMenuOverlay.MenuItem> items);
    }

    public static final int TAB_BAR_HEIGHT = 22;

    private final String id;
    private final Host host;
    private final List<TabItem> tabs = new ArrayList<>();
    @Nullable
    private TabItem activeTab;
    private Bounds bounds = Bounds.EMPTY;
    private int tabScrollX;
    private boolean focusedGroup;

    public TabGroup(String id, Host host) {
        this.id = id;
        this.host = host;
    }

    public String id() {
        return id;
    }

    public List<TabItem> tabs() {
        return tabs;
    }

    @Nullable
    public TabItem activeTab() {
        return activeTab;
    }

    public void setFocusedGroup(boolean focusedGroup) {
        this.focusedGroup = focusedGroup;
    }

    public Bounds contentBounds() {
        return bounds.inset(0, TAB_BAR_HEIGHT, 0, 0);
    }

    @Override
    public Bounds bounds() {
        return bounds;
    }

    @Override
    public void layout(Bounds bounds) {
        this.bounds = bounds;
        if (activeTab != null) {
            activeTab.view().layout(contentBounds());
        }
    }

    public void openOrFocus(TabItem item) {
        for (TabItem existing : tabs) {
            if (existing.tabId().equals(item.tabId())) {
                activeTab = existing;
                activeTab.view().layout(contentBounds());
                return;
            }
        }
        tabs.add(item);
        activeTab = item;
        activeTab.view().layout(contentBounds());
    }

    public void closeTab(TabItem item) {
        int idx = tabs.indexOf(item);
        if (idx < 0) return;
        tabs.remove(idx);
        if (activeTab == item) {
            activeTab = tabs.isEmpty() ? null : tabs.get(Math.min(idx, tabs.size() - 1));
            if (activeTab != null) {
                activeTab.view().layout(contentBounds());
            }
        }
        host.onTabClosed(this, item);
    }

    public void closeByTabId(String tabId) {
        for (TabItem t : List.copyOf(tabs)) {
            if (t.tabId().equals(tabId)) {
                closeTab(t);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (bounds.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), IdeTheme.BG_ROOT);

        // Tab Bar
        Bounds bar = new Bounds(bounds.x(), bounds.y(), bounds.w(), Math.min(TAB_BAR_HEIGHT, bounds.h()));
        g.fill(bar.x(), bar.y(), bar.right(), bar.bottom(), IdeTheme.BG_PANEL);
        g.fill(bar.x(), bar.bottom() - 1, bar.right(), bar.bottom(), IdeTheme.BORDER);
        if (focusedGroup) {
            g.fill(bar.x(), bar.y(), bar.right(), bar.y() + 1, IdeTheme.ACCENT);
        }

        try (IdeScissor.Scope sc = IdeScissor.push(g, bar)) {
            int totalW = computeTotalTabsWidth(font);
            int maxScroll = Math.max(0, totalW - bar.w());
            tabScrollX = Mth.clamp(tabScrollX, 0, maxScroll);

            int x = bar.x() - tabScrollX;
            for (TabItem tab : tabs) {
                int w = tabWidth(font, tab);
                boolean isActive = tab == activeTab;
                boolean hover = mouseX >= x && mouseX < x + w && mouseY >= bar.y() && mouseY < bar.bottom();
                if (isActive) {
                    g.fill(x, bar.y(), x + w, bar.bottom(), IdeTheme.BG_ROOT);
                    g.fill(x, bar.y(), x + w, bar.y() + 2, IdeTheme.ACCENT);
                } else if (hover) {
                    g.fill(x, bar.y(), x + w, bar.bottom() - 1, IdeTheme.BG_HOVER);
                }
                g.fill(x + w - 1, bar.y(), x + w, bar.bottom(), IdeTheme.BORDER);

                int iconColor = tab instanceof TabItem.SpellTab st && st.document().isSealed()
                    ? IdeTheme.GOLD_SEAL
                    : (isActive ? IdeTheme.ACCENT_LIGHT : IdeTheme.TEXT_MUTED);
                IdeTheme.drawIcon(g, tab.icon(), x + 6, bar.y() + 7, iconColor);

                String label = tab.title().getString() + (tab.isDirty() ? " *" : "");
                int textColor = tab.isDirty() ? IdeTheme.GOLD_SEAL_TEXT
                    : (isActive ? IdeTheme.TEXT_MAIN : IdeTheme.TEXT_MUTED);
                g.drawString(font, label, x + 18, bar.y() + 7, textColor, false);

                // 关闭按钮 10x10
                int cx = x + w - 14;
                int cy = bar.y() + 6;
                if (mouseX >= cx && mouseX < cx + 10 && mouseY >= cy && mouseY < cy + 10) {
                    g.fill(cx, cy, cx + 10, cy + 10, IdeTheme.BG_HOVER);
                }
                IdeTheme.drawIcon(g, IdeTheme.Icon.CLOSE_X, cx + 1, cy + 1, IdeTheme.TEXT_MUTED);
                x += w;
            }
        }

        // Content
        Bounds content = contentBounds();
        if (activeTab == null) {
            if (!content.isEmpty()) {
                try (IdeScissor.Scope sc = IdeScissor.push(g, content)) {
                    g.drawCenteredString(font, Component.translatable("almightly_staff.ide.empty_editor"),
                        content.x() + content.w() / 2, content.y() + Math.max(12, content.h() / 2 - 4),
                        IdeTheme.TEXT_MUTED);
                }
            }
            return;
        }
        try (IdeScissor.Scope sc = IdeScissor.push(g, content)) {
            activeTab.view().render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (activeTab != null) {
            activeTab.view().renderOverlay(g, mouseX, mouseY, partialTick);
        }
    }

    private int tabWidth(Font font, TabItem tab) {
        String label = tab.title().getString() + (tab.isDirty() ? " *" : "");
        return Math.max(70, font.width(label) + 38);
    }

    private int computeTotalTabsWidth(Font font) {
        int sum = 0;
        for (TabItem t : tabs) {
            sum += tabWidth(font, t);
        }
        return sum;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my)) return false;
        host.onGroupFocused(this);
        if (my < bounds.y() + TAB_BAR_HEIGHT) {
            Font font = Minecraft.getInstance().font;
            int x = bounds.x() - tabScrollX;
            for (TabItem tab : List.copyOf(tabs)) {
                int w = tabWidth(font, tab);
                if (mx >= x && mx < x + w) {
                    int cx = x + w - 14;
                    int cy = bounds.y() + 6;
                    if (button == 0 && mx >= cx && mx < cx + 10 && my >= cy && my < cy + 10) {
                        closeTab(tab);
                        return true;
                    }
                    if (button == 2) {
                        closeTab(tab);
                        return true;
                    }
                    activeTab = tab;
                    activeTab.view().layout(contentBounds());
                    host.onGroupFocused(this);
                    if (button == 1) {
                        openTabContextMenu((int) mx, (int) my, tab);
                    }
                    return true;
                }
                x += w;
            }
            return true;
        }
        if (activeTab != null) {
            return activeTab.view().mouseClicked(mx, my, button);
        }
        return true;
    }

    private void openTabContextMenu(int mx, int my, TabItem tab) {
        List<ContextMenuOverlay.MenuItem> items = new ArrayList<>();
        items.add(new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.split_right"),
            true, () -> host.onSplitRequested(this, tab, false)));
        items.add(new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.split_down"),
            true, () -> host.onSplitRequested(this, tab, true)));
        if (tab instanceof TabItem.SpellTab st) {
            items.add(new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.set_cast_page"),
                true, () -> host.onSetCastPage(st.document().pageIndex())));
        }
        items.add(new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.close_others"),
            tabs.size() > 1, () -> {
                for (TabItem t : List.copyOf(tabs)) {
                    if (t != tab) closeTab(t);
                }
            }));
        items.add(new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.close_tab"),
            true, () -> closeTab(tab)));
        host.openContextMenu(mx, my, items);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return activeTab != null && activeTab.view().mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return activeTab != null && activeTab.view().mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!bounds.contains(mx, my)) return false;
        if (my < bounds.y() + TAB_BAR_HEIGHT) {
            Font font = Minecraft.getInstance().font;
            int max = Math.max(0, computeTotalTabsWidth(font) - bounds.w());
            tabScrollX = Mth.clamp(tabScrollX - (int) Math.signum(delta) * 24, 0, max);
            return true;
        }
        return activeTab != null && activeTab.view().mouseScrolled(mx, my, delta);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (activeTab != null) activeTab.view().mouseMoved(mx, my);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return activeTab != null && activeTab.view().keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return activeTab != null && activeTab.view().charTyped(codePoint, modifiers);
    }

    @Override
    public void tick() {
        if (activeTab != null) activeTab.view().tick();
    }
}
