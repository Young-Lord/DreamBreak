# Sane Break 代码导读（面向 Android 版本）

本文基于 `sane-break` 目录下源码整理，目标是帮助当前 `docs/TODO.md` 中安卓护眼应用的设计与实现。

## 1. 这个项目在做什么

Sane Break 是一个“尽量不打断专注，但最终会强制休息”的休息提醒器。其核心不是简单定时弹窗，而是：

- 先给用户一个可感知但不强制的过渡提醒（顶部闪烁提示）
- 当用户自然停下输入或拖延过久时，再进入全屏休息
- 通过状态机严格控制暂停/恢复/强制退出等边界行为

这和 `docs/TODO.md` 里“先通知提醒 -> 顶部闪窗 -> 全屏休息”的目标是高度一致的。

## 2. 核心机制：状态机 + 两阶段休息

核心逻辑在：

- `sane-break/src/core/app.h`
- `sane-break/src/core/app.cpp`
- `sane-break/src/core/app-states.h`
- `sane-break/src/core/app-states.cpp`

主状态有 3 个：

- `Normal`：正常计时
- `Paused`：因空闲/电池/特定程序/睡眠等原因暂停
- `Break`：休息中

其中 `Break` 又拆成 3 个阶段：

- `BreakPhasePrompt`：闪烁提示（小窗、顶部）
- `BreakPhaseFullScreen`：全屏正式休息
- `BreakPhasePost`：休息结束后，窗口可继续停留到用户再次活动

### 触发关系（简化）

- 倒计时到 0：`Normal -> Break`
- 在 Prompt 阶段检测到用户空闲：`Prompt -> FullScreen`
- Prompt 超过 `flashFor` 秒仍未休息：`Prompt -> FullScreen`（强制）
- FullScreen 倒计时结束：进入下一轮周期，回到 `Normal` 或 `Paused`
- 任何可暂停原因出现：`Normal/Break -> Paused`（Break 中的 Idle 例外，见代码）

## 3. 时间与周期模型

核心数据在：

- `sane-break/src/core/app-data.h`
- `sane-break/src/core/app-data.cpp`

关键计数器：

- `secondsToNextBreak`：距离下次休息还有多少秒
- `secondsSinceLastBreak`：距离上次休息已过去多久
- `secondsPaused`：当前已暂停时长
- `breakCycleCount`：用于计算“小休几次后触发大休”

休息类型判断：

- `smallBreaksBeforeBigBreak() == 0` 时为大休，否则为小休
- 休息结束后 `finishAndStartNextCycle()`，重置下一次倒计时

默认参数（`preferences.cpp`）：

- 小休间隔 `smallEvery = 1200s`（20 分钟）
- 小休时长 `smallFor = 20s`
- 每 `bigAfter = 3` 次小休后大休
- 大休时长 `bigFor = 60s`
- 闪烁提示时长 `flashFor = 30s`
- 强制后允许活动退出确认窗口 `confirmAfter = 30s`

## 4. “暂停计时”机制（对 Android 很关键）

暂停原因是位标记（可并存）：

- `Idle`（用户空闲）
- `OnBattery`（电池供电）
- `AppOpen`（监控名单中的程序在运行）
- `Sleep`（系统睡眠）

来源：

- `sane-break/src/core/flags.h`
- `sane-break/src/lib/system-monitor.cpp`

`Paused` 状态退出时有两条关键规则（`app-states.cpp`）：

- 若暂停太久，直接“补满”下一次休息间隔（避免恢复后立刻强制休息）
- 若暂停更久，可重置整个大小休周期

这正是安卓端“特定 App 中暂停计时器”功能应复用的思想。

## 5. UI 交互模型

### 5.1 休息窗口

核心文件：

- `sane-break/src/app/break-window.cpp`
- `sane-break/src/app/break-windows.cpp`

行为：

- Prompt：窗口窄条化、位于屏幕上边缘、闪烁
- FullScreen：动画扩展到全屏，显示文案、倒计时、结束时间、按钮
- 支持“退出强制休息”和“锁屏”按钮（受配置限制）

### 5.2 托盘（可映射为 Android 通知）

核心文件：`sane-break/src/app/tray.cpp`

作用：

- 显示“距下次休息/大休还有多久”
- 提供 `Break now` / `Big break now` / `Postpone` / `Enable break` 等快速动作
- 在休息临近时（<=10 秒）做闪烁提示

对安卓可直接映射为：常驻通知 + Action 按钮。

### 5.3 分层设置

核心文件：`sane-break/src/app/pref-window.h`、`pref-window.cpp`

分组明确：

- `Schedule`（节奏）
- `Reminder`（提醒/强制逻辑）
- `Interface`（视觉）
- `Pause`（暂停条件）
- `Sound`（音效）
- `General`（通用）

这就是 `TODO.md` 中“分层级设置页面”的直接参考结构。

## 6. 与当前 TODO.md 的映射

`docs/TODO.md` 提到的需求，可对照 Sane Break 实现：

- 国际化：已有 `language` 设置与翻译切换流程
- 分层设置页：已有 6 大分组
- 休息前通知提醒：桌面端对应托盘与临近提示
- 顶部闪窗 -> 全屏休息：已完整实现两阶段
- 休息页显示大小休与剩余时长：已支持
- 在某些 App 中暂停计时：`programsToMonitor` + `AppOpen` 暂停原因

## 7. Android 落地建议（按源码语义迁移）

建议按“核心状态机先行，平台能力后接入”方式做。

### 7.1 建议的 Android 模块拆分

- `core-engine`：纯 Kotlin 状态机（Normal/Paused/Break + Prompt/FullScreen/Post）
- `settings`：DataStore 配置层（字段名与 Sane Break 尽量一一对应）
- `platform-monitor`：空闲/充电/前台应用监控
- `ui-overlay`：顶部闪窗与全屏休息页
- `notification`：前台服务通知 + 快捷动作

### 7.2 平台能力对应

- 托盘动作 -> 通知 Action（立即休息、延后、启用）
- 程序监控 -> `UsageStatsManager`/无障碍服务（视权限策略）
- 顶部闪窗/全屏 -> `SYSTEM_ALERT_WINDOW` 或应用内 Activity 接管
- 锁屏（可选） -> Android 通常不建议模拟桌面锁屏，建议改为“提高退出成本”的 UX

### 7.3 实现优先级（MVP）

1. 复刻状态机与计时规则（最关键）
2. 做常驻通知与动作按钮
3. 做 Prompt 顶部条 + FullScreen 休息页
4. 做 Pause 分组（空闲、充电、指定 App）
5. 最后再做主题、音效、更多国际化

## 8. 迁移时容易踩坑的点

- 不要把“提醒 UI”写死在计时器里；应由状态机驱动 UI
- Pause 原因可并存，不能用单一布尔值
- 从暂停恢复时要处理“是否补满间隔/重置周期”，否则体验会很突兀
- Prompt 阶段允许“用户自然收尾”，FullScreen 才是强执行阶段
- “退出强制休息”要有限次和时机控制（参考 `confirmAfter`/`maxForceBreakExits`）

---

如果后续你愿意，我可以基于这份文档继续输出一版 Android 侧的数据结构草案（Kotlin `data class` + sealed state）和最小状态转移表，直接可用于开工。
