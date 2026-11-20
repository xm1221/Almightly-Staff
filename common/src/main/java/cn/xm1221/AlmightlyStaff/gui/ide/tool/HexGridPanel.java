package cn.xm1221.AlmightlyStaff.gui.ide.tool;

import at.petrak.hexcasting.api.casting.eval.ResolvedPattern;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexPattern;
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
 * 独立六边形画板窗口（纯写入模式：画完立刻插入光标并清网格）。
 * 支持滚轮缩放、右键/中键拖拽平移、重置居中以及 ESC 先取消未完成笔划。
 */
public final class HexGridPanel implements IdeComponent {
    private static final int TOOLBAR_H = 18;

    private final IdeSession session;
    private final Consumer<List<CompoundTag>> insertCallback;
    private final List<ResolvedPattern> patterns = new ArrayList<>();

    private Bounds bounds = Bounds.EMPTY;
    @Nullable
    private GuiSpellcasting spellcasting;
    private float hexSize = 22.0f;
    private float panX = 0.0f;
    private float panY = 0.0f;
    private boolean mouseHeld = false;
    private boolean panning = false;

    public HexGridPanel(IdeSession session, Consumer<List<CompoundTag>> insertCallback) {
        this.session = session;
        this.insertCallback = insertCallback;
    }

    @SuppressWarnings("unchecked")
    private static <X> X as(Object o) {
        return (X) o;
    }

    private Bounds toolbarBounds() {
        return new Bounds(bounds.x(), bounds.y(), bounds.w(), Math.min(TOOLBAR_H, bounds.h()));
    }

    private Bounds canvasBounds() {
        return bounds.inset(0, TOOLBAR_H, 0, 0);
    }

    private void ensureInitialized() {
        if (spellcasting != null) return;
        Minecraft mc = Minecraft.getInstance();
        var win = mc.getWindow();
        spellcasting = new GuiSpellcasting(InteractionHand.MAIN_HAND, patterns, List.of(), null, 0);
        spellcasting.init(mc, win.getGuiScaledWidth(), win.getGuiScaledHeight());
        stopSound();
        IdeSpellcastingAccess ia = as(spellcasting);
        ia.setIdeWriteMode$ide(true);
        ia.setCastCollectMode$ide(true);
        ia.setHexSizeOverride$ide(hexSize);
        ia.setOnDrawPattern$ide((pat, idx) -> onPatternDrawn(pat));
        updateCenterOffset();
    }

    private void updateCenterOffset() {
        if (spellcasting == null) return;
        Bounds cb = canvasBounds();
        IdeSpellcastingAccess ia = as(spellcasting);
        ia.setHexSizeOverride$ide(hexSize);
        ia.setCoordsOffsetOverride$ide(new Vec2(cb.x() + cb.w() / 2.0f + panX, cb.y() + cb.h() / 2.0f + panY));
    }

    private void onPatternDrawn(HexPattern pat) {
        session.learnGreatSpell(StaffHex.refFor(pat));
        CompoundTag tag = StaffHex.serializeIota(new PatternIota(pat));
        insertCallback.accept(List.of(tag));
        if (spellcasting != null) {
            IdeSpellcastingAccess ia = as(spellcasting);
            ia.clearPatterns$ide();
        }
    }

    public boolean hasUnfinishedStroke() {
        if (spellcasting == null) return false;
        IdeSpellcastingAccess ia = as(spellcasting);
        return ia.isDrawing$ide();
    }

    public boolean cancelUnfinishedStroke() {
        if (!hasUnfinishedStroke()) return false;
        IdeSpellcastingAccess ia = as(spellcasting);
        ia.cancelStroke$ide();
        mouseHeld = false;
        return true;
    }

    public void dispose() {
        mouseHeld = false;
        panning = false;
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
            updateCenterOffset();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (bounds.isEmpty()) return;
        ensureInitialized();
        Font font = Minecraft.getInstance().font;
        IdeTheme.drawNineSlice(g, bounds, IdeTheme.NineSlice.ROOT);

        Bounds tb = toolbarBounds();
        IdeTheme.drawNineSlice(g, tb, IdeTheme.NineSlice.PANEL);
        try (IdeScissor.Scope sc = IdeScissor.push(g, tb)) {
            int x = tb.x() + 4;
            int y = tb.y() + 2;
            drawTextBtn(g, font, x, y, 14, 14, "-", mouseX, mouseY);
            x += 16;
            drawTextBtn(g, font, x, y, 14, 14, "+", mouseX, mouseY);
            x += 18;
            drawIconBtn(g, x, y, 14, 14, IdeTheme.Icon.CENTER_RESET, mouseX, mouseY);
            x += 18;
            String clr = Component.translatable("almightly_staff.gui.clear_pattern").getString();
            int clrW = font.width(clr) + 10;
            drawTextBtn(g, font, x, y, clrW, 14, clr, mouseX, mouseY);
        }

        Bounds cb = canvasBounds();
        if (cb.isEmpty()) return;
        try (IdeScissor.Scope sc = IdeScissor.push(g, cb)) {
            g.fill(cb.x(), cb.y(), cb.right(), cb.bottom(), 0xFF101318);
            renderGridDots(g, cb);
            if (spellcasting != null) {
                spellcasting.render(g, mouseX, mouseY, partialTick);
            }
        }
        driveDrag();
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Bounds tb = toolbarBounds();
        if (!tb.contains(mouseX, mouseY)) return;
        int x = tb.x() + 4 + 16 + 18;
        if (mouseX >= x && mouseX < x + 14 && mouseY >= tb.y() + 2 && mouseY < tb.y() + 16) {
            g.renderTooltip(Minecraft.getInstance().font,
                Component.translatable("almightly_staff.ide.grid_center"), mouseX, mouseY);
        }
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

    private static void drawTextBtn(GuiGraphics g, Font font, int x, int y, int w, int h, String txt, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        IdeTheme.drawNineSlice(g, x, y, w, h, hov ? IdeTheme.NineSlice.BTN_HOVER : IdeTheme.NineSlice.BTN_NORMAL);
        g.drawCenteredString(font, txt, x + w / 2, y + 3, IdeTheme.TEXT_MAIN);
    }

    private static void drawIconBtn(GuiGraphics g, int x, int y, int w, int h, IdeTheme.Icon icon, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        IdeTheme.drawNineSlice(g, x, y, w, h, hov ? IdeTheme.NineSlice.BTN_HOVER : IdeTheme.NineSlice.BTN_NORMAL);
        IdeTheme.drawIcon(g, icon, x + (w - 8) / 2, y + (h - 8) / 2, IdeTheme.TEXT_MAIN);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my)) return false;
        ensureInitialized();
        if (toolbarBounds().contains(mx, my) && button == 0) {
            return handleToolbarClick(mx, my);
        }
        if (canvasBounds().contains(mx, my) && spellcasting != null) {
            if (button == 1 || button == 2) {
                panning = true;
                return true;
            }
            if (button == 0) mouseHeld = true;
            spellcasting.mouseClicked(mx, my, button);
            return true;
        }
        return true;
    }

    private boolean handleToolbarClick(double mx, double my) {
        Font font = Minecraft.getInstance().font;
        Bounds tb = toolbarBounds();
        int x = tb.x() + 4;
        int y = tb.y() + 2;
        if (my < y || my >= y + 14) return true;
        if (mx >= x && mx < x + 14) { adjustZoom(-2f); return true; }
        x += 16;
        if (mx >= x && mx < x + 14) { adjustZoom(2f); return true; }
        x += 18;
        if (mx >= x && mx < x + 14) {
            panX = 0.0f;
            panY = 0.0f;
            updateCenterOffset();
            return true;
        }
        x += 18;
        String clr = Component.translatable("almightly_staff.gui.clear_pattern").getString();
        int clrW = font.width(clr) + 10;
        if (mx >= x && mx < x + clrW && spellcasting != null) {
            IdeSpellcastingAccess ia = as(spellcasting);
            ia.clearPatterns$ide();
            return true;
        }
        return true;
    }

    private void adjustZoom(float delta) {
        hexSize = Mth.clamp(hexSize + delta, 12.0f, 40.0f);
        updateCenterOffset();
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 1 || button == 2) {
            boolean was = panning;
            panning = false;
            return was;
        }
        if (button == 0) mouseHeld = false;
        if (spellcasting != null && (canvasBounds().contains(mx, my) || !mouseHeld)) {
            spellcasting.mouseReleased(mx, my, button);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning && (button == 1 || button == 2)) {
            panX += (float) dx;
            panY += (float) dy;
            updateCenterOffset();
            return true;
        }
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
        adjustZoom((float) Math.signum(delta) * 2.0f);
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
