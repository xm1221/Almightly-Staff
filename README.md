# Almightly Staff（万法之杖）

Hex Casting 附属模组：一把可编程的法杖。内置 **法术库 IDE**——以"法术书页"形式管理法术（每页 = 一个法术，页名 = 法术名），支持可视化图案编辑、真实施法采集、Parse 代码双向转换。

- 平台：Minecraft 1.20.1 · Architectury（common / fabric / forge）
- 前置：Hex Casting 0.11.3、HexParse（可选但推荐）
- 语言：Java 17

---

## 功能一览

### 法术库主界面（StaffLibScreen）
CyberStaff 风格三栏布局：

| 栏 | 内容 |
|---|---|
| 左 | 法术页列表（每页一个法术，页名 = 法术名） |
| 中 | 图案目录 + 搜索框（按动作注册表加载，含卓越法术占位） |
| 右 | 当前法术序列（槽位网格，支持选中/拖拽/右键菜单） |

### 序列编辑
- **绘制**：点击空格子 → 弹出浮动绘制子 GUI（内嵌原版 `GuiSpellcasting`，write 模式拦截封包，画完自动回填/覆盖）
- **复制/粘贴/剪切**：Ctrl+C / Ctrl+V / Ctrl+X，支持多选（Ctrl 切换、Shift 范围），剪贴板支持**所有 iota 类型**
- **拖拽**：目录拖入序列（计算插入点）、序列内重排；拖拽中自动滚动
- **撤销**：Ctrl+Z（操作级撤销栈）
- **右键菜单**：移除 / 复制 / 粘贴 / 重绘覆盖 / **施法**（菜单 z=600 抬高、点击优先级高于重叠按钮）
- **自动保存**：任何编辑（增删改排/改名）立即写回服务端；回包与当前页相同时保留本地草稿、选择与撤销栈

### 真实施法采集（施法）
右键菜单 → **施法**：打开真实 `GuiSpellcasting`（图案封包放行，消耗法杖媒质），右侧实时显示施法栈。
- 打开前**先发包清空服务端施法栈**（`setStaffcastImage(null)` 持久化清空），客户端同步清显示
- 施法回包经 mixin 转发更新栈显示；`isStackClear` 时只清显示不关屏
- **ESC 退出** → 栈内所有 iota **插入到右键打开菜单的那一格** + 自动保存

### Parse 代码双向转换
- **法术 → 代码**：Parse 按钮 / P 键 → 客户端组装 ListIota 原始 NBT → 服务端 `ParserMain.ParseIotaNbt` → 回传代码并打开多行文本编辑器
- **代码 → 法术**：编辑器内确认 → 服务端 `ParserMain.ParseCode` → 结果回填序列
- 多行编辑器（参考 HexGuide NoteEditorScreen）：精确光标定位、选择、Home/End/Enter 换行、点击定位

### 卓越法术（per-world）
- 目录中显示为占位（无固定签名）
- 添加/拖入时**服务端直接调 HexParse 检查**：`GreatPatternUnlocker.isUnlocked` + `PatternMapper.mapPatternWorld`（不经 ParseCode 转换）
- 已学习 → 返回真实签名/方向 → 自动加入 + 目录条目升级为可直接添加；未学习 → 拒绝提示
- 绘制/解析出卓越法术图案时自动记住其签名

### Iota 显示
- 图案 iota：渲染真实图案（`PatternRenderer` + Hex 官方可读样式）
- 非图案 iota（数字/实体/列表/注释…）：**Hex 类型色方块**（`IotaType.getColor`）+ 内高光，悬停 tooltip 显示完整数值

---

## 架构要点

### 数据模型
- 存储：**法术书页 NBT**（参考 ItemSpellbook：`TAG_PAGES` / `TAG_PAGE_NAMES` / `TAG_SELECTED_PAGE`，最多 64 页）
- **客户端只持有 iota 的原始 NBT 标签（`List<CompoundTag>`），反序列化全在服务端**（实体等 iota 需要 ServerLevel 查 UUID）
- 显示用 `IotaType.getDisplay(tag)` / `getColor(tag)`；图案用 `deserialize(tag, null)`（pattern 无需 level）

### 网络层（ModNetworking，Architectury NetworkChannel）
- 页同步：`MsgStaffLibReadC2S` / `MsgStaffLibSyncS2C`（**按字节分块**，单包 ≤ 20000B，`lastChunk` 标记）
- 页编辑：Rename / SetIota / AppendIota / Swap / Write（整页写）/ Select，全部携带原始 NBT 标签
- Parse：`MsgParseToCodeC2S(CompoundTag)` / `MsgParseToCodeS2C` / `MsgParseToIotasC2S` / `MsgParseToIotasS2C`
- 卓越法术：`MsgStaffGreatSpellCheckC2S/S2C`
- 施法：`MsgStaffCastClearStackC2S`（打开前清栈）
- S2C 处理器必须包 `Minecraft.getInstance().execute(...)`（网络线程 → 渲染线程）

### Mixin
- `MixinGuiSpellcasting`（实现 `IdeSpellcastingAccess`）：拦截 `drawEnd` 发包（write 模式本地捕获）、影子 `cachedStack`（客户端读施法栈）、`setStackClear`、cast-collect 模式
- `MixinMsgNewSpellPatternS2C`：把施法回包转发给包装屏（原版只处理 `screen instanceof GuiSpellcasting`）

### GUI 复用 GuiSpellcasting 的要点（详见 skill）
- write 模式：`setIdeWriteMode$ide(true)` + `setOnDrawPattern$ide(回调)`，封包被拦截、图案本地捕获
- 真实模式：`setIdeWriteMode$ide(false)`，封包放行真实施放
- **ESC 必须自己拦截**：原版 `closeForReal → setScreen(null)` 会绕过包装屏返回逻辑
- **禁止调用 `super.onClose()`**（默认 `setScreen(null)` 会覆盖返回父界面的逻辑）；停音用 `soundManager.stop(HexSounds.CASTING_AMBIANCE.getLocation(), null)`

---

## 构建

```bash
./gradlew common:compileJava forge:compileJava fabric:compileJava   # 三平台编译
./gradlew :fabric:assemble                                            # 打包 fabric jar
```

产物：`fabric/build/libs/almightly_staff-fabric-<version>.jar`（若 `build` 报 test 任务错误，用 `assemble` 跳过 test/check）。

## 致谢
- GUI 布局与交互参考 [Hex CyberStaff](https://github.com/batchpacket/Hex-CyberStaff)（Aurover）
- Parse 编辑器光标方案与施法栈访问参考 HexGuide
- 卓越法术检查与图案获取基于 HexParse（yukkuric）
