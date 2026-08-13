package cn.xm1221.AlmightlyStaff.gui;

import at.petrak.hexcasting.api.casting.eval.ExecutionClientView;
import at.petrak.hexcasting.api.casting.eval.ResolvedPattern;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import at.petrak.hexcasting.common.lib.HexSounds;
import cn.xm1221.AlmightlyStaff.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 真实施法子界面：内嵌 Hex Casting 原版 GuiSpellcasting，且【不拦截】图案封包——
 * 玩家绘制的图案会真正施放（消耗法杖媒质），右侧实时显示施法栈。
 * 服务端回包经 MixinMsgNewSpellPatternS2C 转发到 {@link #onServerUpdate} 更新栈显示；
 * 打开时先清空服务端施法栈，退出时把栈内元素全部交给回调（法术库追加到序列末尾）。
 */
public class StaffCastScreen extends Screen {
    @SuppressWarnings("unchecked")
    private static <X> X as(Object o) { return (X) o; }

    private final Screen parent;
    private final Consumer<List<CompoundTag>> onExit;
    private final List<ResolvedPattern> patterns = new ArrayList<>();
    private GuiSpellcasting spellcasting;

    public StaffCastScreen(Screen parent, Consumer<List<CompoundTag>> onExit) {
        super(Component.translatable("almightly_staff.gui.cast_collect"));
        this.parent = parent;
        this.onExit = onExit;
    }

    @Override
    protected void init() {
        spellcasting = new GuiSpellcasting(InteractionHand.MAIN_HAND, patterns, List.of(), null, 0);
        spellcasting.init(minecraft, width, height);
        IdeSpellcastingAccess ia = as(spellcasting);
        ia.setIdeWriteMode$ide(false); // 真实施法：放行封包给服务器执行
        ia.setCastCollectMode$ide(true); // 不自动关屏（isStackClear 时由 onServerUpdate 清显示）
        ia.setStackClear$ide(); // 客户端同步清空栈显示（服务端清栈已由 openCastScreen 在打开前发包完成）
    }

    /** 服务端施法回包：更新栈显示；isStackClear 时只清显示、不关屏（原版此处会 setScreen(null)）。 */
    public void onServerUpdate(ExecutionClientView info, int index) {
        if (spellcasting == null) return;
        if (info.isStackClear()) {
            IdeSpellcastingAccess ia = as(spellcasting);
            ia.setStackClear$ide();
            return;
        }
        spellcasting.recvServerUpdate(info, index);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
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
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (spellcasting.mouseScrolled(mx, my, delta)) return true;
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 拦截 ESC：统一走 onClose（停音 + 采集栈 + 返回父界面），避免原版 closeForReal→setScreen(null)
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
        stopSound();
        // 退出时采集本地施法栈（显示在侧栏的那份，随服务端回包实时更新）
        List<CompoundTag> collected = new ArrayList<>();
        if (spellcasting != null) {
            IdeSpellcastingAccess ia = as(spellcasting);
            List<CompoundTag> stack = ia.getStack$ide();
            if (stack != null) for (CompoundTag t : stack) if (t != null) collected.add(t.copy());
        }
        if (minecraft != null) minecraft.setScreen(parent);
        if (onExit != null) onExit.accept(collected);
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
            Minecraft.getInstance().getSoundManager().stop(HexSounds.CASTING_AMBIANCE.getLocation(), null);
        } catch (Exception ignored) { }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
