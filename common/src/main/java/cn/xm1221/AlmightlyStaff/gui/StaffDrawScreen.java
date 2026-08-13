package cn.xm1221.AlmightlyStaff.gui;

import at.petrak.hexcasting.api.casting.eval.ResolvedPattern;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import at.petrak.hexcasting.common.lib.HexSounds;
import cn.xm1221.AlmightlyStaff.spell.PatternRef;
import cn.xm1221.AlmightlyStaff.spell.StaffHex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 浮动图案绘制子界面：内嵌 Hex Casting 原版 GuiSpellcasting。
 * 玩家绘制一个图案后自动关闭并回调该图案（写入对应格子）。
 */
public class StaffDrawScreen extends Screen {
    @SuppressWarnings("unchecked")
    private static <X> X as(Object o) { return (X) o; }

    private final Screen parent;
    private final Consumer<PatternRef> callback;
    private final List<ResolvedPattern> patterns = new ArrayList<>();
    private GuiSpellcasting spellcasting;
    private boolean done;

    public StaffDrawScreen(Screen parent, Consumer<PatternRef> callback) {
        super(Component.translatable("almightly_staff.gui.draw_pattern"));
        this.parent = parent;
        this.callback = callback;
    }

    @Override
    protected void init() {
        spellcasting = new GuiSpellcasting(InteractionHand.MAIN_HAND, patterns, List.of(), null, 0);
        spellcasting.init(minecraft, width, height);
        IdeSpellcastingAccess ia = as(spellcasting);
        ia.setIdeWriteMode$ide(true); // 拦截封包，不发给服务器
        ia.setOnDrawPattern$ide((pat, idx) -> {
            if (done) return;
            done = true;
            PatternRef ref = StaffHex.refFor(pat);
            minecraft.setScreen(parent);
            callback.accept(ref);
        });
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        spellcasting.render(g, mx, my, pt);
        super.render(g, mx, my, pt);
    }

    @Override
    public void tick() {
        super.tick();
        if (spellcasting != null) spellcasting.tick();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (spellcasting.mouseClicked(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (spellcasting.mouseReleased(mx, my, button)) return true;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (spellcasting.mouseDragged(mx, my, button, dx, dy)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        if (spellcasting.mouseScrolled(mx, my, hScroll, vScroll)) return true;
        return super.mouseScrolled(mx, my, hScroll, vScroll);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 直接拦截 ESC：原版 GuiSpellcasting 的 ESC 会 closeForReal → setScreen(null)，
        // 绕过本界面导致无法返回父界面；这里统一走 onClose 返回父界面。
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (spellcasting.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (spellcasting.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        stopSound(); // 直接停音，不走 closeForReal（会触发 Screen.onClose→setScreen 递归）
        if (!done && minecraft != null) minecraft.setScreen(parent); // ESC 取消，不回调
        // 不调用 super.onClose()：其默认实现会 setScreen(null)，覆盖返回父界面的逻辑
    }

    @Override
    public void removed() {
        stopSound();
        super.removed();
    }

    /** 停止施法网格环境音（等价 closeForReal 的停音部分，但不触发 onClose）。 */
    private void stopSound() {
        try {
            Minecraft.getInstance().getSoundManager().stop(HexSounds.CASTING_AMBIANCE.value().getLocation(), null);
        } catch (Exception ignored) { }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
