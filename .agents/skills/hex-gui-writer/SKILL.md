---
name: hex-gui-writer
description: Write Minecraft Hex Casting (1.20.1, Architectury) GUIs that embed or wrap the vanilla spellcasting GUI (GuiSpellcasting), build spell-editor/library screens, handle iota stacks client-side, and wire server-side cast/parse flows. Use when implementing casting GUIs, spell editors, iota list/sequence screens, pattern drawing sub-screens, stack display/collection, or HexParse code editors for Hex Casting mods.
---

# Hex GUI Writer

为 Hex Casting（1.20.1 · Architectury common/fabric/forge）编写咒法相关 GUI 的技能。
本技能封装了本项目（Almightly Staff）反复踩坑后总结的**可靠模式**，照做即可避免
setScreen 递归、网络线程崩溃、实体 iota 反序列化失败、施法 GUI 自动关屏等经典问题。

## 何时使用

- 内嵌/包装原版 `GuiSpellcasting`（绘制子界面、真实施法界面）
- 法术书 / 法术库 / 序列编辑器界面（iota 列表、格子、拖拽、右键菜单）
- 展示或采集施法栈（cachedStack）
- HexParse 代码编辑器（多行文本、精确光标）
- 服务端执行 / 校验（卓越法术检查、施法、parse 转换）

## 核心背景（必读）

- 项目是 Architectury 多平台，common 模块**可直接引用客户端类**（`GuiSpellcasting` 等）。
- 数据模型：**客户端只持有 iota 的原始 NBT 标签（`List<CompoundTag>`），反序列化一律在服务端**。
  原因：`IotaType.deserialize(tag, world)` 需要 `ServerLevel`（实体 iota 要按 UUID 查实体），
  客户端只有 ClientLevel，用 null 反序列化实体 iota 会失败/丢弃。
- 显示用静态方法：`IotaType.getDisplay(tag)`（文本）、`IotaType.getColor(tag)`（类型色）、
  `IotaType.getTypeFromTag(tag)`（判断类型）。图案可以 `deserialize(tag, null)`（PatternIota 无需 level）。

## 可靠模式

### 1. 包装 GuiSpellcasting（绘制/施法子界面）

新建 `XxxScreen extends Screen`，内嵌一个 `GuiSpellcasting`，全部输入事件转交给它：

```java
spellcasting = new GuiSpellcasting(InteractionHand.MAIN_HAND, patterns, List.of(), null, 0);
spellcasting.init(minecraft, width, height);
```

通过 mixin 实现的访问接口（见模式 2）切换两种模式：

| 模式 | 设置 | 行为 |
|---|---|---|
| write（绘制捕获） | `setIdeWriteMode$ide(true)` + `setOnDrawPattern$ide(回调)` | 图案封包被拦截，本地回调拿到画好的图案 |
| cast（真实施法） | `setIdeWriteMode$ide(false)` + `setCastCollectMode$ide(true)` | 图案封包放行给服务端，消耗媒质真实施放 |

**ESC 必须自己拦截**，否则原版 `GuiSpellcasting` 的 `closeForReal() → super.onClose() → setScreen(null)`
会绕过包装屏直接退到游戏：

```java
@Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    if (keyCode == GLFW.GLFW_KEY_ESCAPE) { this.onClose(); return true; }
    if (spellcasting.keyPressed(keyCode, scanCode, modifiers)) return true;
    return super.keyPressed(keyCode, scanCode, modifiers);
}
```

**onClose 里禁止调用 `super.onClose()`**（默认实现 `setScreen(null)` 会覆盖返回父界面的逻辑）。
返回父界面：`minecraft.setScreen(parent)`；停音：`Minecraft.getInstance().getSoundManager()
.stop(HexSounds.CASTING_AMBIANCE.getLocation(), null)`（不要调 `closeForReal()`，会递归）。

### 2. 访问接口 + Mixin（扩展 GuiSpellcasting）

定义一个接口（如 `IdeSpellcastingAccess`），用 `@Mixin(GuiSpellcasting.class)` 实现它：

```java
@Mixin(GuiSpellcasting.class)
public abstract class MixinGuiSpellcasting implements IdeSpellcastingAccess {
    @Shadow(remap = false) private List<ResolvedPattern> patterns;
    @Shadow(remap = false) private Set<HexCoord> usedSpots;
    @Shadow(remap = false) private List<CompoundTag> cachedStack;  // 施法栈（侧栏显示那份）

    @WrapWithCondition(method = "drawEnd",
        at = @At(value = "INVOKE",
            target = "Lat/petrak/hexcasting/xplat/IClientXplatAbstractions;sendPacketToServer(Lat/petrak/hexcasting/common/msgs/IMessage;)V",
            remap = false), remap = false)
    private boolean redirectPattern$ide(IClientXplatAbstractions inst, IMessage msg) {
        if (onDrawPattern$ide != null && msg instanceof MsgNewSpellPatternC2S s) {
            onDrawPattern$ide.accept(s.pattern(), s.resolvedPatterns().size() - 1);
            return !ideWriteMode; // write=true 拦截, cast=false 放行
        }
        return true;
    }
    // cachedStack 读/清：getStack$ide() / setStackClear$ide()
}
```

要点：
- 记得把 mixin 加进 `xxx.mixins.json` 的 `client` 列表。
- `cachedStack` 就是施法栈显示，**客户端可直接读它采集栈**（参考 HexGuide 的 `getStack$hexguide`），无需服务端回传。

### 3. 真实施法的栈显示与防自动关屏

原版 `MsgNewSpellPatternS2C.handle` 只处理 `screen instanceof GuiSpellcasting`——包装屏收不到更新，
栈永远不显示。解决：新增 mixin 转发：

```java
@Mixin(MsgNewSpellPatternS2C.class)
public abstract class MixinMsgNewSpellPatternS2C {
    @Inject(method = "handle", at = @At("HEAD"), remap = false)
    private static void forward$ide(MsgNewSpellPatternS2C self, CallbackInfo ci) {
        Minecraft.getInstance().execute(() -> {
            var s = Minecraft.getInstance().screen;
            if (s instanceof StaffCastScreen scs) scs.onServerUpdate(self.info(), self.index());
        });
    }
}
```

包装屏收到后：`info.isStackClear()` 为 true 时**只清显示不关屏**（原版此处会 `setScreen(null)` 自动关屏）：

```java
public void onServerUpdate(ExecutionClientView info, int index) {
    if (info.isStackClear()) { ia.setStackClear$ide(); return; }
    spellcasting.recvServerUpdate(info, index); // 原版逻辑更新栈显示
}
```

### 4. 清空服务端施法栈（打开施法界面时）

**必须用原版持久化入口**，且**在打开界面之前发包**（同通道保证先于图案包处理）：

```java
// C2S 处理器（ctx.get().queue 内）：
IXplatAbstractions.INSTANCE.setStaffcastImage(s, null);
// → CCStaffcastImage 置空 tag → 下次 getStaffcastVM 返回全新空 image
```

**不要**用 `vm.setImage(...)`（`getStaffcastVM` 返回瞬时 VM，改了不持久）。
客户端显示同步清：`ia.setStackClear$ide()`。

### 5. 网络消息（Architectury NetworkChannel）

- 消息注册**必须**在 `init()` 里 `CHANNEL.register(...)`，漏注册会抛
  `Unknown message type!` NPE（**新增消息时最容易忘**）。
- S2C 处理器里改界面必须包 `Minecraft.getInstance().execute(() -> ...)`（网络线程 → 渲染线程）。
- 大载荷（整本法术书页）要**按字节分块**：先序列化到临时 `FriendlyByteBuf` 测每页字节，
  累积 ≤ 20000B 发一包 + `lastChunk` 标记；客户端累积到最后一包再应用。
- iota 一律传**原始 NBT 标签**（`b.writeNbt(tag)`），接收端不反序列化；
  服务端用 `IotaType.deserialize(tag, level)` 还原后写回。
- `readNbt()` 只读一次：`CompoundTag t = b.readNbt(); try { ... } catch (ignored) {}`，
  **绝不在 catch 里再 readNbt()**（会把下一条数据读走 → 流错位 → VarInt too big）。

### 6. Parse 代码编辑器（多行文本）

参考 HexGuide `NoteEditorScreen.kt`：用 `font.getSplitter().plainIndexAtWidth(remaining, editWidth(), Style.EMPTY)`
按宽度拆行 + `lineStarts()`（换行符占 1 字符）实现精确光标/点击定位/选择。
代码 ↔ 图案转换**由服务端 HexParse 负责**：
- 法术→代码：客户端把 `List<CompoundTag>` 组装成 ListIota 原始 NBT（`{hexcasting:type: "hexcasting:list", hexcasting:data: ListTag[...]}`）→ `ParserMain.ParseIotaNbt(tag, player, x -> x)`。
- 代码→法术：`ParserMain.ParseCode(code, player)` → 回传 ListIota NBT。

### 7. 卓越法术（per-world）检查

服务端**直接调 HexParse 的检查与图案获取**（不要走 ParseCode 转换）：

```java
PatternMapper.init(level);
var tag = PatternMapper.mapPatternWorld.get(actionId);      // jar 版本值是 CompoundTag
if (tag != null) {
    Iota i = IotaType.deserialize(tag, level);
    if (i instanceof PatternIota pi) {
        boolean unlocked = GreatPatternUnlocker.get(level).isUnlocked(actionId);
        // unlocked → 返回 pi.getPattern().anglesSignature() / getStartDir().name()
    }
}
```

注意依赖 jar 与 GitHub 仓库的 API 可能不一致（如 `mapPatternWorld` 的类型），
**用前先 javap 反编译依赖 jar 确认签名**。

## 常见坑（Checklist）

- [ ] 新网络消息注册了吗？（漏了 → `Unknown message type!` 崩溃）
- [ ] S2C 处理器包 `Minecraft.execute` 了吗？
- [ ] ESC 拦截了吗？`onClose` 里没调 `super.onClose()` 吧？
- [ ] 实体 iota 没在客户端反序列化吧？（用原始标签 + `getDisplay/getColor`）
- [ ] `readNbt()` 没在 catch 里重复读吧？
- [ ] 清服务端栈用的是 `setStaffcastImage(null)` 而不是 `vm.setImage` 吧？
- [ ] 施法回包转发 mixin 加进 mixins.json 了吗？
- [ ] 大载荷按字节分块了吗？
- [ ] 改 GUI 前的服务端清栈是"打开前发包"吗？
