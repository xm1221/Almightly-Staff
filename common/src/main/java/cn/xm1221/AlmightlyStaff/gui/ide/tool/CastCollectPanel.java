package cn.xm1221.AlmightlyStaff.gui.ide.tool;

import at.petrak.hexcasting.api.casting.eval.ExecutionClientView;
import at.petrak.hexcasting.api.casting.eval.ResolvedPattern;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import at.petrak.hexcasting.common.lib.HexSounds;
import cn.xm1221.AlmightlyStaff.gui.IdeSpellcastingAccess;
import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeScissor;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import cn.xm1221.AlmightlyStaff.gui.ide.model.IdeSession;
import cn.xm1221.AlmightlyStaff.spell.StaffHex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 独立的「真实施法采集栈」窗口：明确区分于无消耗画板，展示完整施法栈列表并提供开始/采集/清空控制。
 */
public final class CastCollectPanel implements IdeComponent {
    private static final int TOOLBAR_H = 20;
    private static final int STACK_ROW_H = 13;

    private final IdeSession session;
    private final Consumer<List<CompoundTag>> collectCallback;
    private final List<ResolvedPattern> patterns = new ArrayList<>();

    private Bounds bounds = Bounds.EMPTY;
    @Nullable
    private GuiSpellcasting spellcasting;
    private float hexSize = 20.0f;
    private boolean mouseHeld = false;
    private int stackScroll = 0;

    public CastCollectPanel(IdeSession session, Consumer<List<CompoundTag>> collectCallback) {
        this.session = session;
        this.collectCallback = collectCallback;
    }

    @SuppressWarnings("unchecked")
    private static <X> X as(Object o) {
        return (X) o;
    }

    private Bounds toolbarBounds() {
        return new Bounds(bounds.x(), bounds.y(), bounds.w(), Math.min(TOOLBAR_H, bounds.h()));
    }

    private Bounds canvasBounds() {
        Bounds body = bounds.inset(0, TOOLBAR_H, 0, 0);
        int stackW = Math.min(160, Math.max(80, body.w() / 3));
        return new Bounds(body.x(), body.y(), Math.max(0, body.w() - stackW), body.h());
    }

    private Bounds stackBounds() {
        Bounds cb = canvasBounds();
        return new Bounds(cb.right(), cb.y(), Math.max(0, bounds.right() - cb.right()), cb.h());
    }

    public void beginFreshSession() {
        ensureInitialized();
        if (spellcasting != null) {
            IdeSpellcastingAccess ia = as(spellcasting);
            ia.clearPatterns$ide();
            ia.setStackClear$ide();
        }
        session.network().clearCastStack();
        stackScroll = 0;
    }

    private void ensureInitialized() {
        if (spellcasting != null) return;
        Minecraft mc = Minecraft.getInstance();
        var win = mc.getWindow();
        spellcasting = new GuiSpellcasting(InteractionHand.MAIN_HAND, patterns, List.of(), null, 0);
        spellcasting.init(mc, win.getGuiScaledWidth(), win.getGuiScaledHeight());
        stopSound();
        IdeSpellcastingAccess ia = as(spellcasting);
        ia.setIdeWriteMode$ide(false); // 放行发包：真实施法
        ia.setCastCollectMode$ide(true);
        ia.setStackClear$ide();
        updateOffset();
    }

    private void updateOffset() {
        if (spellcasting == null) return;
        Bounds cb = canvasBounds();
        IdeSpellcastingAccess ia = as(spellcasting);
        ia.setHexSizeOverride$ide(hexSize);
        ia.setCoordsOffsetOverride$ide(new Vec2(cb.x() + cb.w() / 2.0f, cb.y() + cb.h() / 2.0f));
    }

    public void onServerUpdate(ExecutionClientView info, int index) {
        if (spellcasting == null) return;
        if (info.isStackClear()) {
            IdeSpellcastingAccess ia = as(spellcasting);
            ia.setStackClear$ide();
            return;
        }
        spellcasting.recvServerUpdate(info, index);
    }

    public void dispose() {
        mouseHeld = false;
        stopSound();
    }

    private static void stopSound() {
        try {
            Minecraft.getInstance().getSoundManager().stop(HexSounds.CASTING_AMBIANCE.value().getLocation(), null);
        } catch (Exception ignored) {
        }
    }

    @Override
    public Bounds bounds() {
        return bounds;
    }

    @Override
    public void layout(Bounds bounds) {
        this.bounds = bounds;
        if (!bounds.isEmpty() && spellcasting != null) {
            updateOffset();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (bounds.isEmpty()) return;
        ensureInitialized();
        Font font = Minecraft.getInstance().font;
        IdeTheme.drawNineSlice(g, bounds, IdeTheme.NineSlice.ROOT);

        // 顶部控制条（明确提示会消耗媒质）
        Bounds tb = toolbarBounds();
        IdeTheme.drawNineSlice(g, tb, IdeTheme.NineSlice.PANEL);
        try (IdeScissor.Scope sc = IdeScissor.push(g, tb)) {
            int x = tb.x() + 4;
            int y = tb.y() + 2;
            String startTxt = Component.translatable("almightly_staff.ide.cast_reset").getString();
            int startW = font.width(startTxt) + 10;
            drawBtn(g, font, x, y, startW, 15, startTxt, false, mouseX, mouseY);
            x += startW + 4;

            String collectTxt = Component.translatable("almightly_staff.ide.collect_stack").getString();
            int colW = font.width(collectTxt) + 10;
            drawBtn(g, font, x, y, colW, 15, collectTxt, true, mouseX, mouseY);
            x += colW + 8;

            IdeTheme.drawClippedString(g, font, Component.translatable("almightly_staff.ide.cast_media_warn"),
                x, y + 3, Math.max(0, tb.right() - x - 4), IdeTheme.GOLD_SEAL_TEXT);
        }

        // 左：施法画布
        Bounds cb = canvasBounds();
        if (!cb.isEmpty()) {
            try (IdeScissor.Scope sc = IdeScissor.push(g, cb)) {
                g.fill(cb.x(), cb.y(), cb.right(), cb.bottom(), 0xFF101318);
                renderGridDots(g, cb);
                if (spellcasting != null) {
                    spellcasting.render(g, mouseX, mouseY, partialTick);
                }
            }
        }

        // 右：完整施法栈列表
        Bounds sb = stackBounds();
        if (!sb.isEmpty() && spellcasting != null) {
            IdeTheme.drawNineSlice(g, sb, IdeTheme.NineSlice.PANEL);
            g.fill(sb.x(), sb.y(), sb.x() + 1, sb.bottom(), IdeTheme.BORDER);
            IdeSpellcastingAccess ia = as(spellcasting);
            List<CompoundTag> stack = ia.getStack$ide();
            int headerH = 16;
            g.drawString(font, Component.translatable("almightly_staff.ide.stack_header", stack.size()),
                sb.x() + 6, sb.y() + 4, IdeTheme.TEXT_MUTED, false);

            Bounds listB = sb.inset(2, headerH, IdeTheme.SCROLLBAR_WIDTH + 2, 2);
            int visible = Math.max(1, listB.h() / STACK_ROW_H);
            stackScroll = Mth.clamp(stackScroll, 0, Math.max(0, stack.size() - visible));
            try (IdeScissor.Scope sc = IdeScissor.push(g, listB)) {
                for (int i = 0; i < visible; i++) {
                    int idx = i + stackScroll;
                    if (idx >= stack.size()) break;
                    // 栈顶显示在最上方
                    CompoundTag tag = stack.get(stack.size() - 1 - idx);
                    int ry = listB.y() + i * STACK_ROW_H;
                    int col = StaffHex.colorOfTag(tag) | 0xFF000000;
                    g.fill(listB.x() + 2, ry + 3, listB.x() + 5, ry + 9, col);
                    String txt = IdeTheme.trimText(font, StaffHex.displayOfTag(tag).getString(), Math.max(10, listB.w() - 10));
                    g.drawString(font, txt, listB.x() + 8, ry + 2, IdeTheme.TEXT_MAIN, false);
                }
            }
            IdeTheme.drawScrollbar(g, sb.right() - IdeTheme.SCROLLBAR_WIDTH, listB.y(), listB.h(),
                stack.size(), visible, stackScroll);
        }
        driveDrag();
    }

    private void renderGridDots(GuiGraphics g, Bounds cb) {
        if (spellcasting == null) return;
        HexCoord c0 = spellcasting.pxToCoord(new Vec2(cb.x(), cb.y()));
        HexCoord c1 = spellcasting.pxToCoord(new Vec2(cb.right(), cb.bottom()));
        int q0 = Math.min(c0.getQ(), c1.getQ()), q1 = Math.max(c0.getQ(), c1.getQ());
        int r0 = Math.min(c0.getR(), c1.getR()), r1 = Math.max(c0.getR(), c1.getR());
        for (int q = q0 - 1; q <= q1 + 1; q++) {
            for (int r = r0 - 1; r <= r1 + 1; r++) {
                Vec2 px = spellcasting.coordToPx(new HexCoord(q, r));
                if (px.x >= cb.x() && px.x < cb.right() && px.y >= cb.y() && px.y < cb.bottom()) {
                    int x = Math.round(px.x);
                    int y = Math.round(px.y);
                    g.fill(x, y, x + 2, y + 2, 0x33AAAAAA);
                }
            }
        }
    }

    private static void drawBtn(GuiGraphics g, Font font, int x, int y, int w, int h,
                                String txt, boolean primary, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        IdeTheme.NineSlice st = primary ? IdeTheme.NineSlice.BTN_ACTIVE
            : (hov ? IdeTheme.NineSlice.BTN_HOVER : IdeTheme.NineSlice.BTN_NORMAL);
        IdeTheme.drawNineSlice(g, x, y, w, h, st);
        g.drawCenteredString(font, txt, x + w / 2, y + 4, IdeTheme.TEXT_MAIN);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my)) return false;
        ensureInitialized();
        Bounds tb = toolbarBounds();
        if (tb.contains(mx, my) && button == 0) {
            Font font = Minecraft.getInstance().font;
            int x = tb.x() + 4;
            int startW = font.width(Component.translatable("almightly_staff.ide.cast_reset").getString()) + 10;
            if (mx >= x && mx < x + startW) {
                beginFreshSession();
                return true;
            }
            x += startW + 4;
            int colW = font.width(Component.translatable("almightly_staff.ide.collect_stack").getString()) + 10;
            if (mx >= x && mx < x + colW && spellcasting != null) {
                IdeSpellcastingAccess ia = as(spellcasting);
                List<CompoundTag> stack = ia.getStack$ide();
                if (!stack.isEmpty()) {
                    collectCallback.accept(stack);
                }
                return true;
            }
            return true;
        }
        if (canvasBounds().contains(mx, my) && spellcasting != null) {
            if (button == 0) mouseHeld = true;
            spellcasting.mouseClicked(mx, my, button);
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) mouseHeld = false;
        if (spellcasting != null && (canvasBounds().contains(mx, my) || !mouseHeld)) {
            spellcasting.mouseReleased(mx, my, button);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && mouseHeld && spellcasting != null) {
            spellcasting.mouseDragged(mx, my, button, dx, dy);
            return true;
        }
        return false;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (spellcasting != null && canvasBounds().contains(mx, my)) {
            spellcasting.mouseMoved(mx, my);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!bounds.contains(mx, my)) return false;
        Bounds sb = stackBounds();
        if (sb.contains(mx, my) && spellcasting != null) {
            IdeSpellcastingAccess ia = as(spellcasting);
            int total = ia.getStack$ide().size();
            int visible = Math.max(1, (sb.h() - 18) / STACK_ROW_H);
            stackScroll = Mth.clamp(stackScroll - (int) Math.signum(delta), 0, Math.max(0, total - visible));
            return true;
        }
        hexSize = Mth.clamp(hexSize + (float) Math.signum(delta) * 2.0f, 12.0f, 36.0f);
        updateOffset();
        return true;
    }

    private void driveDrag() {
        if (!mouseHeld || spellcasting == null) return;
        Minecraft mc = Minecraft.getInstance();
        var mh = mc.mouseHandler;
        var win = mc.getWindow();
        double x = mh.xpos() * win.getGuiScaledWidth() / win.getScreenWidth();
        double y = mh.ypos() * win.getGuiScaledHeight() / win.getScreenHeight();
        spellcasting.mouseDragged(x, y, 0, 0, 0);
    }
}
