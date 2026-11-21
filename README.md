# Almightly Staff（万法之杖）

Hex Casting 附属模组：一把全能法杖。内置 **法术库 IDE**——以书页形式管理法术，支持可视化图案编辑、真实施法采集、Parse 代码双向转换。

- 平台：Minecraft 1.20.1 · Architectury（common / fabric / forge）
- 前置：Hex Casting 0.11.4、HexParse（可选但推荐）

---

## 功能一览

### 法术库主界面（~~AS Code~~）


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

### 图案目录悬停详情
悬停中间图案目录的任意图案，弹出 CyberStaff 风格详情框（z=600 最上层，不遮挡）：
- **图案预览** + **名称**（白色）
- **栈变化摘要**（灰）：`input -> output`，优先取手册 JSON 的 input/output
- **详细描述**（灰）：直接读**所有 mod 的 Patchouli 手册**（hexcasting / hexal / hexical…，参考 hexcessible 的 `BookEntries`），按 `op_id` 建立 `{text, input, output}` 映射，显示时按当前语言解析翻译键；

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

---


## 致谢
- GUI 布局与交互参考 Hex CyberStaff（Aurover）
- 卓越法术检查与图案获取基于 HexParse（yukkuric）
