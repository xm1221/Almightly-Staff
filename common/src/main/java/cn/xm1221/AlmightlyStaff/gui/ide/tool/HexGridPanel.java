package cn.xm1221.AlmightlyStaff.gui.ide.tool;

import at.petrak.hexcasting.api.casting.eval.ExecutionClientView;
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
 * 下侧工具窗：内嵌原版 {@link GuiSpellcasting} 六边形网格（写入模式直插光标 / 施法采集栈模式）。
 * 复用 HexGuide SpellcastingPage 已验证技术方案：只 init 一次并停掉环境音，
 * 布局变动只更新 {@code hexSizeOverride$ide} 与 {@code coordsOffsetOverride$ide}。
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
    private boolean castMode = false;
    private boolean mouseHeld = false;

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
        ia.setIdeWriteMode$ide(!castMode);
        ia.setCastCollectMode$ide(true);
        ia.setHexSizeOverride$ide(hexSize);
        ia.setOnDrawPattern$ide((pat, idx) -> {
            if (!castMode) {
                onPatternDrawnInWriteMode(pat);
            }
        });
        updateCenterOffset();
    }

    private void updateCenterOffset() {
        if (spellcasting == null) return;
        Bounds cb = canvasBounds();
        IdeSpellcastingAccess ia = as(spellcasting);
        ia.setHexSizeOverride$ide(hexSize);
        ia.setCoordsOffsetOverride$ide(new Vec2(cb.x() + cb.w() / 2.0f, cb.y() + cb.h() / 2.0f));
    }

    private void onPatternDrawnInWriteMode(HexPattern pat) {
        session.learnGreatSpell(StaffHex.refFor(pat));
        CompoundTag tag = IotaType.serialize(new PatternIota(pat));
        insertCallback.accept(List.of(tag));
        if (spellcasting != null) {
            IdeSpellcastingAccess ia = as(spellcasting);
            ia.clearPatterns$ide();
        }
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
            Minecraft.getInstance().getSoundManager().stop(HexSounds.CASTING_AMBIANCE.getLocation(), null);
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
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), IdeTheme.BG_ROOT);

        // 工具条
        Bounds tb = toolbarBounds();
        g.fill(tb.x(), tb.y(), tb.right(), tb.bottom(), IdeTheme.BG_PANEL);
        g.fill(tb.x(), tb.bottom() - 1, tb.right(), tb.bottom(), IdeTheme.BORDER);
        try (IdeScissor.Scope sc = IdeScissor.push(g, tb)) {
            int x = tb.x() + 4;
            int y = tb.y() + 2;
            String modeTxt = Component.translatable(castMode ? "almightly_staff.ide.grid_cast" : "almightly_staff.ide.grid_write").getString();
            int modeW = font.width(modeTxt) + 10;
            drawMiniButton(g, font, x, y, modeW, 14, modeTxt, castMode, mouseX, mouseY);
            x += modeW + 4;

            drawMiniButton(g, font, x, y, 14, 14, "-", false, mouseX, mouseY);
            x += 16;
            drawMiniButton(g, font, x, y, 14, 14, "+", false, mouseX, mouseY);
            x += 18;

            String clearTxt = Component.translatable("almightly_staff.gui.clear_pattern").getString();
            int clearW = font.width(clearTxt) + 10;
            drawMiniButton(g, font, x, y, clearW, 14, clearTxt, false, mouseX, mouseY);
            x += clearW + 4;

            if (castMode) {
                String collectTxt = Component.translatable("almightly_staff.ide.collect_stack").getString();
                int colW = font.width(collectTxt) + 10;
                drawMiniButton(g, font, x, y, colW, 14, collectTxt, true, mouseX, mouseY);
            }
        }

        // 画布区
        Bounds cb = canvasBounds();
        if (cb.isEmpty()) return;
        try (IdeScissor.Scope sc = IdeScissor.push(g, cb)) {
            g.fill(cb.x(), cb.y(), cb.right(), cb.bottom(), 0xFF101318);
            renderGridDots(g, cb);
            if (spellcasting != null) {
                spellcasting.render(g, mouseX, mouseY, partialTick);
            }
            if (castMode && spellcasting != null) {
                renderCompactStackOverlay(g, font, cb);
            }
        }
        driveDrag();
    }

    private void renderCompactStackOverlay(GuiGraphics g, Font font, Bounds cb) {
        IdeSpellcastingAccess ia = as(spellcasting);
        List<CompoundTag> stack = ia.getStack$ide();
        if (stack == null || stack.isEmpty()) return;
        int showCount = Math.min(4, stack.size());
        int boxW = Math.min(cb.w() - 8, 170);
        int boxH = showCount * 11 + 6;
        int x = cb.x() + 4;
        int y = cb.y() + 4;
        g.fill(x, y, x + boxW, y + boxH, 0xBB181C22);
        IdeTheme.drawBorder(g, x, y, boxW, boxH, IdeTheme.BORDER);
        for (int i = 0; i < showCount; i++) {
            CompoundTag tag = stack.get(stack.size() - 1 - i);
            String txt = IdeTheme.trimText(font, IotaType.getDisplay(tag).getString(), boxW - 14);
            int col = IotaType.getColor(tag) | 0xFF000000;
            g.fill(x + 4, y + 4 + i * 11, x + 7, y + 10 + i * 11, col);
            g.drawString(font, txt, x + 10, y + 3 + i * 11, IdeTheme.TEXT_MAIN, false);
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

    private static void drawMiniButton(GuiGraphics g, Font font, int x, int y, int w, int h,
                                       String text, boolean accent, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        int fill = accent ? IdeTheme.BG_ACTIVE : (hover ? IdeTheme.BG_HOVER : IdeTheme.BG_ELEVATED);
        int border = accent ? IdeTheme.ACCENT : IdeTheme.BORDER_STRONG;
        g.fill(x, y, x + w, y + h, fill);
        IdeTheme.drawBorder(g, x, y, w, h, border);
        g.drawCenteredString(font, text, x + w / 2, y + 3, IdeTheme.TEXT_MAIN);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my)) return false;
        ensureInitialized();
        if (toolbarBounds().contains(mx, my) && button == 0) {
            return handleToolbarClick(mx, my);
        }
        if (canvasBounds().contains(mx, my) && spellcasting != null) {
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

        String modeTxt = Component.translatable(castMode ? "almightly_staff.ide.grid_cast" : "almightly_staff.ide.grid_write").getString();
        int modeW = font.width(modeTxt) + 10;
        if (mx >= x && mx < x + modeW) {
            toggleCastMode();
            return true;
        }
        x += modeW + 4;
        if (mx >= x && mx < x + 14) {
            adjustZoom(-2f);
            return true;
        }
        x += 16;
        if (mx >= x && mx < x + 14) {
            adjustZoom(2f);
            return true;
        }
        x += 18;
        String clearTxt = Component.translatable("almightly_staff.gui.clear_pattern").getString();
        int clearW = font.width(clearTxt) + 10;
        if (mx >= x && mx < x + clearW) {
            if (spellcasting != null) {
                IdeSpellcastingAccess ia = as(spellcasting);
                ia.clearPatterns$ide();
            }
            return true;
        }
        x += clearW + 4;
        if (castMode) {
            String collectTxt = Component.translatable("almightly_staff.ide.collect_stack").getString();
            int colW = font.width(collectTxt) + 10;
            if (mx >= x && mx < x + colW && spellcasting != null) {
                IdeSpellcastingAccess ia = as(spellcasting);
                List<CompoundTag> stack = ia.getStack$ide();
                if (!stack.isEmpty()) {
                    insertCallback.accept(stack);
                }
                return true;
            }
        }
        return true;
    }

    private void toggleCastMode() {
        castMode = !castMode;
        if (spellcasting != null) {
            IdeSpellcastingAccess ia = as(spellcasting);
            ia.setIdeWriteMode$ide(!castMode);
            ia.clearPatterns$ide();
            if (castMode) {
                ia.setStackClear$ide();
                session.network().clearCastStack();
            }
        }
    }

    private void adjustZoom(float delta) {
        hexSize = Mth.clamp(hexSize + delta, 14.0f, 36.0f);
        updateCenterOffset();
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

    @Override
    public void tick() {
        if (spellcasting != null && !bounds.isEmpty()) {
            // 不调 spellcasting.tick() 以避免它检查手中法杖后误调 closeForReal()
        }
    }
}
