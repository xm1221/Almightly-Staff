# Almightly Staff — IDEA 风格法术编辑器细化实施计划（1.20.1 主线 / 1.21.1 预留）

> 目标受众：供自动化编码模型按里程碑（**M0 → M9**）逐步执行的工程规格书。每次只执行一个里程碑，严格遵守红线与验收命令。
>
> 参考蓝图：
> - 原型交互：`Almightly Staff — IDEA 风格法术编辑器原型.html`
> - 原 TODO：`Almightly Staff Ide Todo.md`
> - 核心技术约束：`hex-gui-writer`、`minecraft-gui-art-direction`（1.20.1 Architectury common/fabric/forge）

---

## 0. 全局技术红线（所有里程碑必须无条件遵守）

1. **目标平台与编译基准**
   - Minecraft `1.20.1` · Java `17` · Kotlin `2.2.20` · Architectury `9.2.14` · Hex Casting `0.11.4`（Fabric + Forge）。
   - 严禁升级 MC 版本、移除 Fabric/Forge 任一平台、或修改 `gradle/gradle-daemon-jvm.properties`（`toolchainVersion=21`）。
2. **客户端只持有原始 NBT 标签（`List<CompoundTag>`）**
   - **严禁**在客户端反序列化非图案 Iota（如 `EntityIota` 在无 `ServerLevel` 时会失败/丢弃）。
   - 客户端显示统一使用 `IotaType.getDisplay(tag)`、`IotaType.getColor(tag)`、`IotaType.getTypeFromTag(tag)`；仅 `PatternIota` 允许 `IotaType.deserialize(tag, null)` 渲染线条。
3. **Mixin 重映射（Remap）规则（1.20.1）**
   - 注入 **Minecraft 原版类/方法**（如 `Screen.renderBackground`、`Minecraft.setScreen`）：**禁止写 `remap = false`**，让 Architectury Loom 自动生成 Fabric Intermediary 与 Forge SRG refmap。
   - 注入 **Hex Casting 模组自身类/方法**（如 `GuiSpellcasting`、`MsgNewSpellPatternS2C`）：保留 `remap = false`。
   - 所有新增客户端 Mixin 必须登记到 `common/src/main/resources/almightly_staff.mixins.json` 的 `"client"` 数组中。
4. **网络消息规范**
   - 新增任何消息必须在 `ModNetworking.init()` 注册（否则运行时抛 `Unknown message type!`）。
   - S2C 修改 UI 状态必须包裹在 `Minecraft.getInstance().execute(() -> ...)` 中。
   - `FriendlyByteBuf.readNbt()` 只读一次，**禁止在 `catch` 块里再次 `readNbt()`**。
   - 整书同步继续遵守单包 `<= 20000B` 分块策略。
5. **可选依赖隔离（`hexparse` / `hexguide` / `patchouli`）**
   - 所有对可选模组类的 `import` 与直接引用必须隔离在独立的 `*Bridge` 类中。
   - 调用前必须经过 `Platform.isModLoaded("<modid>")` 守卫，确保缺省任意可选模组时 JVM 类验证不抛 `NoClassDefFoundError`。
6. **UI 裁剪、焦点与像素美术规范**
   - `gui/ide/**` 下**禁止直接调用** `GuiGraphics.enableScissor` / `disableScissor`，必须使用 `try (var _ = IdeScissor.push(g, x, y, w, h))`（底层委托 MC 1.20.1 内置 `GuiGraphics$ScissorStack`，传绝对 GUI 像素坐标）。
   - 全局唯一 `FocusManager`：焦点切换时显式触发旧组件 `onFocusLost()`，非原版 `EditBox` 聚焦时确保 `Screen.setFocused(null)`，防止中文 IME 候选框漂移。
   - **禁止使用系统 Emoji**（`📁 ✦ 📝 📖 ◫ ❐ ⌕` 在 MC 字体中易显示为豆腐块），所有图标使用 `IdeTheme` 像素绘制方法；所有运行时文本走 `Component.translatable("almightly_staff.ide.*")`，同步更新 `zh_cn.json` 与 `en_us.json`。
7. **既有能力零回退**
   - 必须完整保留 `StaffPages` 稀疏页号 `1..64`、封存页保护（可改名/复制，禁改内容/删除/重排）、`castPage`（`▶`）与悬停名同步、`StaffBookData` 手册悬浮详情、卓越法术服务端检查（`MsgStaffGreatSpellCheckC2S/S2C`）、真实施法采集栈（`StaffCastScreen` + `MsgStaffCastClearStackC2S`）。
   - M0–M8 期间通过 `AlmightlyStaffConfig.get().useIdeScreen` 控制入口（默认 `false` 保留旧 `StaffLibScreen`），M9 验收后切换默认 `true`。

---

## 1. 视觉风格卡（对齐 `minecraft-gui-art-direction` + `StaffLibScreen`）

- **风格路线**：`stylized`（延续 `StaffLibScreen` 深板岩 + 紫水晶硬边像素语法，整数坐标，1px 边框，无网页圆角/模糊/毛玻璃）。
- **调色板（`IdeTheme`）**：
  - 编辑器底色：`0xFF14181E`
  - 面板/工具栏底色：`0xFF1D2229`
  - 抬升标题栏/弹窗：`0xFF252B34`
  - 悬停填充：`0xFF303744`
  - 选中/激活行：`0xAA493662`
  - 常规边框：`0xFF38404B`
  - 分割线高亮 & 主题紫：`0xFF9C6ADE`（浅紫高亮：`0xFFD7B5FF`）
  - 正文文字：`0xFFE2E6EA`，次级文字：`0xFF8A93A0`
  - 状态色：施放页绿 `0xFF8CE99A`、脏页星标/封存金 `0xFFE8A82E`、报错红 `0xFFE05555`

---

## 2. 目标代码目录结构（`common/src/main/java/cn/xm1221/AlmightlyStaff/`）

```text
gui/ide/
├── StaffIdeScreen.java              # 唯一主 Screen
├── core/
│   ├── Bounds.java                  # record Bounds(int x, int y, int w, int h)
│   ├── IdeTheme.java                # 调色板、边框、滚动条、像素图标绘制
│   ├── IdeScissor.java              # AutoCloseable 嵌套裁剪栈包装
│   ├── IdeComponent.java            # 面板统一接口
│   ├── FocusManager.java            # 全局唯一焦点管理
│   ├── ContextMenuOverlay.java      # 最高 Z-order 通用右键菜单
│   └── ToastOverlay.java            # 右下角状态提示
├── layout/
│   ├── DockNode.java                # sealed interface: SplitNode / LeafNode（纯逻辑可单测）
│   ├── SplitterDragState.java       # 5px 命中热区、拖拽更新 ratio + minPx 钳位
│   ├── ToolWindowStripe.java        # 20px 边栏窄条（折叠/展开并记录 lastRatio）
│   ├── FloatingWindow.java          # 标题栏拖拽、四边四角缩放、停靠回 TabGroup
│   └── WindowManager.java           # Z-order 浮动窗管理
├── model/
│   ├── SpellDocument.java           # 单页文档模型（List<CompoundTag> + UndoRedo + dirty）
│   ├── IdeSession.java              # 会话状态（页表、castPage、staffUuid、同步路由）
│   ├── WorkspaceState.java          # 侧车存储 DTO（虚拟目录树、布局比例、打开 Tab）
│   └── IdeWorkspaceStorage.java     # 读写 config/almightly_staff/ide/<uuid>.json（纯逻辑可单测）
├── editor/
│   ├── TabItem.java                 # sealed interface: SpellTab / NoteTab / ExternalScreenTab
│   ├── TabGroup.java                # 多 Tab 栏、星标、关闭、右键分屏菜单
│   ├── EditorArea.java              # 左右/上下分屏 TabGroup 容器
│   └── SequenceEditorView.java      # 18×18 Iota 格子序列编辑（迁自 StaffLibScreen）
├── tool/
│   ├── BottomToolDock.java          # 下侧工具窗容器（并排 / 仅网格 / 仅 HexParse）
│   ├── HexGridPanel.java            # 内嵌 GuiSpellcasting（写模式插光标 + 施法采集栈）
│   ├── HexParseLexer.java           # 词法高亮 + 括号匹配 + 行级错误定位（纯逻辑可单测）
│   ├── HexParseEditorPanel.java     # TextFieldHelper 多行编辑 + 自动补全 + 200ms 防抖
│   ├── PatternCatalogPanel.java     # 图案目录 + StaffBookData 详情浮窗
│   └── PageTreePanel.java           # 左侧虚拟文件夹树（拖拽排序/跨目录/右键菜单）
├── search/
│   ├── SearchIndex.java             # 四类聚合索引 + 子序列模糊匹配（纯逻辑可单测）
│   └── TopSearchBar.java            # 顶部 26px 工具栏 + Search Everywhere 悬浮弹窗
└── host/
    ├── MarkdownNoteParser.java      # 轻量 MD 解析（标题/列表/代码块/图案芯片，可单测）
    ├── MarkdownNotePanel.java       # 本地 .md 笔记 + HexGuide 笔记渲染与插入
    ├── HexGuideNotesBridge.java     # 隔离类：软读 HexGuide ClientNotes
    ├── EmbeddedScreenHost.java      # 外部 Screen 宿主（坐标偏移/裁剪/ESC拦截/setScreen接管）
    └── PatchouliHostBridge.java     # 隔离类：软打开 Patchouli 手册到宿主
```

---

## 3. 统一编译与测试验证命令

在仓库根目录执行（每个里程碑结束必跑）：

```powershell
.\gradlew.bat :common:test :common:classes :fabric:classes :forge:classes --console=plain
```

---

## 4. 分阶段里程碑（M0 → M9）

### M0：底座抽象、UUID 持久化、协议扩展与既有 Bug 修复
- **目标**：不改动现有 GUI 行为，建立平台数据/网络抽象、侧车存储、UUID 持久化与 IDE 专用解析协议。
- **改动清单**：
  1. `spell/StaffHex.java`：`loadCatalog()` 中收集 `perWorldActionIds` 并为循环体添加 `try-catch` 防单个异常条目阻塞目录加载。
  2. `network/StaffPages.java`：新增 `KEY_STAFF_UUID = "staff_uuid"`、`getStaffUuid`、`ensureStaffUuid`（纯 NBT 层 + `ItemStack` 包装层）。
  3. `network/StaffStorageAdapter.java` + `NbtStaffStorageAdapter.java`：抽离法杖页读写接口（1.20.1 委托 `StaffPages`，为 1.21.1 `DataComponents` 实现预留边界）。
  4. `network/StaffNetworkBridge.java` + `ArchitecturyNetworkBridge.java`：抽离客户端发包入口。
  5. `network/ModNetworking.java` + `parse/HexParseBridge.java`：
     - 定义 `StaffSyncReceiver` 接口供 `StaffLibScreen` 与未来 `StaffIdeScreen` 共用。
     - `MsgStaffLibReadC2S` 首次同步时调用 `StaffPages.ensureStaffUuid(st)`；`MsgStaffLibSyncS2C` 携带 `String staffUuid`。
     - 新增并注册 IDE 专用解析消息：`MsgIdeParseToCodeC2S/S2C(requestId, pageIndex, ...)` 与 `MsgIdeParseToIotasC2S/S2C(requestId, pageIndex, ok, List<CompoundTag>, error, errorLine)`；服务端解析错误时返回 `ok=false` 与错误描述，**绝不在客户端反序列化 Iota**。
  6. `gui/ide/model/WorkspaceState.java` + `IdeWorkspaceStorage.java`：读写 `config/almightly_staff/ide/<uuid>.json`（Gson + 临时文件原子替换）。
  7. `AlmightlyStaffConfig.java`：新增 `public boolean useIdeScreen = false;`。
- **单元测试**：
  - `StaffPagesTest` 增加 `ensureStaffUuidIsStable()`。
  - 新增 `IdeWorkspaceStorageTest`（读写往返、损坏 JSON 降级默认、非法 UUID 字符清洗）。

---

### M1：核心 UI 底座（`Bounds`、`IdeTheme`、`IdeScissor`、`IdeComponent`、`FocusManager`）
- **目标**：搭建统一组件接口、防溢出裁剪栈与唯一焦点管理器。
- **改动清单**：
  1. `gui/ide/core/Bounds.java`：不可变矩形，提供 `right()`、`bottom()`、`contains(mx, my)`、`inset(...)`、`intersect(Bounds)`。
  2. `gui/ide/core/IdeScissor.java`：`AutoCloseable` 包装 `GuiGraphics.enableScissor/disableScissor`，宽或高 `<= 0` 时安全退化为空裁剪区，禁止上层直接调用原版裁剪方法。
  3. `gui/ide/core/IdeComponent.java`：统一 `bounds`、`layout`、`render`、`renderOverlay`、`mouse*`、`keyPressed`、`charTyped`、`tick`、`onFocusGained`、`onFocusLost`。
  4. `gui/ide/core/FocusManager.java`：全局唯一焦点；切换焦点时先触发旧组件 `onFocusLost()`，切换至非 `EditBox` 时清 `Screen.setFocused(null)`。
  5. `gui/ide/core/IdeTheme.java`、`ContextMenuOverlay.java`、`ToastOverlay.java`：色板、边框、滚动条、像素图标（含迁移 `drawPadlock`）、最高层右键菜单与 Toast。
- **单元测试**：`BoundsTest`、`FocusManagerTest`。

---

### M2：递归分割布局引擎（`DockNode`、`ToolWindowStripe`）与浮动窗口（`WindowManager`）
- **目标**：实现 IDEA 风格拖拽分屏、边栏折叠恢复与多浮动窗 Z-order 管理。
- **改动清单**：
  1. `gui/ide/layout/DockNode.java`：纯 Java `SplitNode` / `LeafNode`，支持 `layout(Bounds)`、`minFirstPx`/`minSecondPx` 钳位、任一侧折叠时另一侧占满 100%、`5px` 分割线命中测试。
  2. `gui/ide/layout/SplitterDragState.java`：拖拽时仅更新 `ratio` 并调用 `layout(...)`，**绝不调用 `Screen.init()`**。
  3. `gui/ide/layout/ToolWindowStripe.java`：`20px` 边栏按钮，折叠时保存 `lastRatio`，展开时恢复。
  4. `gui/ide/layout/FloatingWindow.java` + `WindowManager.java`：底到顶渲染、顶到底命中、点击置顶、标题栏拖拽、四边四角缩放、停靠回主区域回调。
- **单元测试**：`DockLayoutTest`、`WindowManagerTest`。

---

### M3：统一文档模型（`SpellDocument`、`IdeSession`）与多 Tab 分屏编辑区
- **目标**：落地 `StaffIdeScreen` 主框架，并完整迁入 `StaffLibScreen` 的序列编辑能力。
- **改动清单**：
  1. `gui/ide/model/SpellDocument.java`：以 `List<CompoundTag>` 为唯一数据源，封装 `UndoRedoStack`（上限 64）、`dirty` 标记、封存写守卫与观察者回调。
  2. `gui/ide/model/IdeSession.java`：实现 `StaffSyncReceiver`，管理全杖页文档、剪贴板、`learnedPerWorld`、`WorkspaceState` 同步与脏页保存。
  3. `gui/ide/editor/TabItem.java`、`TabGroup.java`、`EditorArea.java`：多 Tab 栏（`*` 标记、`×` 关闭、右键 `Split Right`/`Split Down`/`Close Others`/`设为施放页`），支持同页双 Tab 独立滚动与光标。
  4. `gui/ide/editor/SequenceEditorView.java`：迁移 `StaffLibScreen` 序列网格（18×18 格子、点击/Ctrl/Shift 多选、整块拖拽重排、右键菜单、悬停图案/Iota 提示、光标插入线）。
  5. `gui/ide/StaffIdeScreen.java` + `client/AlmightlyStaffKeybinds.java`：按 `AlmightlyStaffConfig.get().useIdeScreen` 路由打开主屏。
- **单元测试**：`SpellDocumentTest`。

---

### M4：下侧双模输入 — `HexGridPanel`（内嵌真画板）+ `PatternCatalogPanel`
- **目标**：直接在下侧面板画图案插入光标，支持“写入模式 / 真实施法采集栈”切换，并迁入图案目录。
- **改动清单**：
  1. `gui/IdeSpellcastingAccess.java` + `mixin/MixinGuiSpellcasting.java`：新增 `hexSizeOverride$ide`、`coordsOffsetOverride$ide`（用 `@ModifyReturnValue(..., remap = false)` 覆写）与 `recalcDisplays$ide()`；保持 `remap = false`。
  2. `mixin/MixinMsgNewSpellPatternS2C.java`：同步支持向 `StaffIdeScreen` 转发施法回包。
  3. `gui/ide/tool/HexGridPanel.java`：
     - `spellcasting.init(mc, winW, winH)` 仅首次调用一次并立即停音；`layout(bounds)` 仅更新中心坐标偏移，不重建音效。
     - 铺满网格点 + `IdeScissor` 裁剪渲染 + 保留 `driveDrag()` Forge 拖拽兜底。
     - 写入模式：画完单图案插入激活 Tab 光标并 `clearPatterns$ide()`；施法模式：清空服务端栈、放行发包、支持“采集栈到光标”。
     - 滚轮调整画板缩放，不透传给 `GuiSpellcasting` 翻页。
  4. `gui/ide/tool/PatternCatalogPanel.java`：迁移 `StaffLibScreen` 图案目录、搜索过滤、`StaffBookData` 悬浮详情、双击/拖拽插入、卓越法术检查。
  5. `gui/ide/tool/BottomToolDock.java`：提供 `并排 / 仅 HexGrid / 仅 HexParse` 切换与中间水平分割条。

---

### M5：下侧双模输入 — `HexParseLexer` + `HexParseEditorPanel`
- **目标**：常驻下侧的多行 HexParse 编辑器，支持语法高亮、前缀补全与 200ms 防抖双向同步。
- **改动清单**：
  1. `gui/ide/tool/HexParseLexer.java`（纯 Java）：逐行词法着色（注释/括号/考察转义/数字向量/已知图案/未知符号）+ 括号匹配检查 + 配对括号高亮定位。
  2. `gui/ide/tool/HexParseEditorPanel.java`：
     - 基于 `TextFieldHelper` 的多行文本编辑 + 折行缓存（按文本版本号缓存，渲染仅遍历可见行，保证 500+ 行流畅帧率）。
     - 基于 `StaffHex.loadCatalog()` 的前缀自动补全悬浮窗（`Up/Down/Tab/Enter/ESC`）。
     - 200ms（4 ticks）防抖解析：先跑本地括号校验，通过后发 `MsgIdeParseToIotasC2S`（携带 `requestId` + `pageIndex` 丢弃过期回包）；未装 HexParse 时降级为轻量本地字面量/已知图案解析；报错时保留上次合法 `iotas` 并在报错行标红。
- **单元测试**：`HexParseLexerTest`（含 500 行词法耗时验证）。

---

### M6：左侧虚拟书页树（`PageTreePanel`）
- **目标**：用侧车虚拟目录树管理书页、笔记与外部入口，不膨胀物品 NBT。
- **改动清单**：
  1. `gui/ide/tool/PageTreePanel.java`：
     - 展示虚拟文件夹（存 `WorkspaceState`）+ `未分类 (Pages)` + `笔记 (Notes)` + `外部界面 (External)`。
     - 文件夹折叠/展开；书页单击选中、双击打开或聚焦 Tab。
     - 拖拽：拖至文件夹标题跨目录归属；拖至同级书页上/下半区发送 `OP_REORDER` 调整真实页号（遵守封存页避让）。
     - 右键菜单：新建书页/文件夹、重命名、新分屏打开、设为施放页、复制书页、复制为 HexParse、导出 `.hex`、删除（两步确认/封存禁用）。
  2. 服务端同步回调后自动清洗虚拟文件夹中已失效的页号引用。
- **单元测试**：`WorkspaceTreeSyncTest`。

---

### M7：统一搜索引擎（`SearchIndex`）与顶部工具栏（`TopSearchBar`）
- **目标**：实现 `Ctrl+K` / 双击 `Shift` 唤醒的 Search Everywhere 与顶部 `26px` 工具栏。
- **改动清单**：
  1. `gui/ide/search/SearchIndex.java`（纯 Java）：聚合 `PAGE` / `PATTERN` / `NOTE` / `COMMAND` 四类索引，支持子序列模糊评分与 `@p`/`@f`/`@n`/`>` 分类前缀过滤。
  2. `gui/ide/search/TopSearchBar.java`：顶部 `26px` 固定条（搜索框 + `分屏`、`浮动笔记`、`手册`、`保存`、`重置布局` 按钮）+ 最高 Z-order 悬浮结果弹窗（键盘上下选择、回车跳转 Tab 或插入图案到光标）。
- **单元测试**：`SearchIndexTest`。

---

### M8：原生 Markdown 笔记（`MarkdownNotePanel`）与 HexGuide 软联动
- **目标**：支持在 Tab 或浮动窗中查阅笔记，并一键把笔记内嵌图案插入法术光标。
- **改动清单**：
  1. `gui/ide/host/MarkdownNoteParser.java`（纯 Java）：解析 `#` 标题、`-` 列表、`` `code` ``、```` ```hexparse ``` ```` 代码块与 `[[pattern:...]]` / `[[sig:DIR:sig|名]]` 图案卡片标记。
  2. `gui/ide/host/MarkdownNotePanel.java`：读取 `config/almightly_staff/notes/*.md`（首次空目录自动生成示例笔记）并支持新建/编辑保存；点击图案卡片直插当前激活 `SpellDocument` 光标。
  3. `gui/ide/host/HexGuideNotesBridge.java`：严格按 `Platform.isModLoaded("hexguide")` 隔离读取 `ClientNotes.sections()` 中的 `NoteIota`。
- **单元测试**：`MarkdownNoteParserTest`。

---

### M9：外部 `Screen` 宿主（`EmbeddedScreenHost`）、Patchouli 嵌入与联调验收
- **目标**：无重影、无退屏地嵌入外部非容器 `Screen`（Patchouli 手册），完成双端冒烟与边界压测。
- **改动清单**：
  1. `mixin/MixinScreen.java` & `mixin/MixinMinecraft.java`（1.20.1 **不写 `remap = false`**，登记到 `almightly_staff.mixins.json`）：
     - `MixinScreen`：在 `EmbeddedScreenHost.isRendering()` 为 `true` 时取消 `renderBackground`（屏蔽全屏暗色遮罩）。
     - `MixinMinecraft`：在 `EmbeddedScreenHost.isDispatching()` 期间拦截 `setScreen`——`null` 仅关闭当前嵌入 Tab/浮动窗，非容器 `Screen` 就地替换子屏，`AbstractContainerScreen` 保存 IDE 快照安全切出并在关闭后恢复。
  2. `gui/ide/host/EmbeddedScreenHost.java`：`IdeScissor` + `pose.translate` + 自适应缩放（Patchouli `GuiBook` 272×180）+ 事件坐标逆变换 + 拦截 `ESC`。
  3. `gui/ide/host/PatchouliHostBridge.java`：`Platform.isModLoaded("patchouli")` 隔离打开咒法手册，底部附带一键插入当前条目图案辅助条。
  4. `AlmightlyStaffConfig.java`：将 `useIdeScreen` 默认值改为 `true`；补齐 `zh_cn.json` / `en_us.json` 全量翻译键。
  5. **联调验收清单**：
     - 跑通 `./gradlew :common:test :common:classes :fabric:classes :forge:classes build`。
     - Fabric & Forge 1.20.1 客户端实测：64 页读写/封存/改名/V 键施法、中文 IME 切换不漂移、布局持久化、缺失 `hexparse`/`hexguide`/`patchouli` 时零崩溃、多浮动窗置顶与缩放、500+ 行 HexParse 滚动帧率、内嵌 Patchouli 翻页与 ESC 行为。
