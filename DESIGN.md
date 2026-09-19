# DESIGN.md — 音乐兔 UI 设计契约

> 2026-09-19 由 UI 统一化改造（plan: `_Project/superpowers/plans/20260919_0953_ui-style-unification.md`）沉淀。
> 本文是长期设计契约：新增/修改 UI 必须遵守。决策背景见 ADR 0011（轻 token 化）。

## Colors

品牌三色 + 暖染色中性色，全部经 `MaterialTheme.colorScheme` 消费：

- **品牌**：Coral（primary）/ Slate（secondary）/ Amber（tertiary），定义于 `ui/theme/Color.kt`
- **表面层级**：background/surface → surfaceContainerLow → surfaceContainer → surfaceContainerHighest（暖珊瑚染色）
- **语义扩展**：`extendedColors().success`（`ui/theme/ExtendedColors.kt`，light `#2F7A3D` / dark `#9CD6A5`，对 SurfaceContainerLow 对比度 4.70:1 / 9.85:1，用于"已授权"等成功状态）

规则：
- 只准 `MaterialTheme.colorScheme.*` + `extendedColors().success`；`ui/theme/` 之外禁止颜色字面量
- `.copy(alpha = ...)` 作用于 colorScheme 颜色上合法；禁用态统一用内容色 `.copy(alpha = 0.38f)` 或组件原生 `enabled`
- 响铃页豁免：沉浸 primaryContainer 容器内副文本允许 `onPrimaryContainer.copy(alpha = 0.8f)`

## Shape

`ui/theme/Shape.kt` 的 `AppShapes`：extraSmall=4 / small=8 / medium=12 / large=16 / extraLarge=28（dp）。

- 一律 `MaterialTheme.shapes.*` 或 `CircleShape`；禁止 `RoundedCornerShape(N.dp)`（Shape.kt 定义处除外）
- 分组容器 = Card（shapes.medium，surfaceContainerLow）；嵌套子块 = Surface（shapes.small，surfaceContainerHigh）
- 封面圆角全局统一 `shapes.medium`（MiniPlayer 与 PlayerScreen 一致）

## Spacing（ADR 0011：不建常量层）

| 场景 | 值 |
|---|---|
| 屏幕级横向边距 | 16 |
| 卡片内边距 | 16（嵌套子块 12） |
| 分组之间 | 24 |
| 列表行垂直边距 | 8 |
| 行内微间距 / 图文间距 | 4 / 12 |
| 空状态容器 | 32 |
| 白名单全集 | 4 / 8 / 12 / 16 / 24 / 32 |

白名单外大尺寸必须命名常量：`PlayerTokens`（CoverSize/CoverIconSize/PrevNextIconSize/PlayButtonSize/PlayIconSize）、`RingTokens`（响铃页 32/48/80 档）、`DragTokens`（拖拽 72f/1.02f/8dp）、`SheetTokens.ListMaxHeight`（320dp）、`SwipeMaxDistance`（80dp）。组件 API 默认参数可用字面量（如 `EmptyState(iconSize = 48.dp)`）。

## Typography

- 全部走 `MaterialTheme.typography`（完整 M3 阶，`ui/theme/Type.kt`）；禁止 fontSize/fontWeight 字面量
- 时钟显示专用 token：`timeDisplayStandard`（编辑页 44sp）/ `timeDisplayLarge`（响铃页 64sp）
- 时间文案一律 `FormatUtils.formatClockTime`（Locale.US，拉丁数字，有 ar-locale 回归测试）

## Motion

- 只用 `MotionTokens`：DurationShort=150 / Medium=250 / Long=300 + `EasingEmphasized`（FastOutSlowIn）

## 三态渲染

- Loading → `LoadingState()`；Error → `ErrorContent()`；Empty → `EmptyState()`（`presentation/components/StateView.kt`）；空态图标统一 48dp
- 屏幕级三态切换一律包 `Crossfade(tween(DurationLong, EasingEmphasized))`，无例外
- **豁免**：表单内联加载态（PlaylistSelector 禁用输入框）与 sheet 内文字级空态（居中 bodyMedium + onSurfaceVariant）

## 组件词汇

| 控件 | 唯一标准 |
|---|---|
| 搜索框 | 共享 `SongSearchField`（OutlinedTextField + trailing 清除按钮；placeholder/a11y 标签参数化） |
| 开关行 | 整行 `toggleable(role = Role.Switch)` + `Switch(onCheckedChange = null)` |
| 删除图标 | `Icons.Outlined.Delete`（滑删层内 tint=onErrorContainer） |
| 对话框按钮 | 确认/取消 `TextButton`；危险确认 `TextButton + error 色` |
| 页面操作 | 主 `Button`；次 `FilledTonalButton`；"去系统设置" `TextButton` |
| FAB | 仅「新建」动作，禁止导航 |
| sheet 列表行 | `Row + leading Icon(24dp) + titleMedium + vertical 8` |
| sheet 列表高度 | `heightIn(max = SheetTokens.ListMaxHeight)` |

## 交互范式分工（保持现状，不得顺手统一）

- 删除：闹钟列表 = 滑动揭示 + errorContainer 层；播放列表/歌曲/扫描目录 = 直接可见删除按钮
- 选播放列表：闹钟编辑（表单）= `SingleChoiceDropdown`；播放器（快捷动作）= `ModalBottomSheet`
- 播放器返回 = `KeyboardArrowDown`（收起语义）；播放器无 Scaffold、显式 background 合法
- 响铃页 = 沉浸式专属刻度（`RingTokens`）

## 图标

默认 24dp（不写 size）；行内进度圈 16dp/stroke 2dp；行首封面/文件夹图标档 40dp；其余尺寸命名常量。

## i18n

`values/`（英）+ `values-zh-rCN/` + `values-ja/` 三语言强制同步；新增 key 前先 grep 复用（本改造全程零新增 key）。
