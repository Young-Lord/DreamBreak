DreamBreak 维护指引（持续更新）

## 1. 项目架构总览

本项目采用“单一运行时状态 + 多入口驱动”的结构：

- 核心状态源：`BreakRuntime`（`StateFlow<BreakUiState>`）
- 核心状态机：`BreakEngine`（纯函数推进 `BreakState`）
- 数据持久化：`SettingsStore`（DataStore <-> `AppSettings`）
- 前台入口：`MainActivity`（主题与生命周期）+ `DreamBreakApp.kt`（Compose 主界面与各页）
- 后台入口：`BreakReminderService`（前台服务 + 通知 + Overlay）
- 系统入口：`BootCompletedReceiver` / `DreamBreakTileService` / `NotificationActionReceiver` / `ScreenLockReceiver`
- 监控层：`AppPauseMonitor` + `ForegroundAppMonitor` + `ScreenLockMonitor`
- 展示层：`BreakOverlayController`（系统悬浮窗）

一句话：所有入口都应“读取设置 -> 恢复/启动 `BreakRuntime` -> 按需启动监控与服务”，不要并行维护第二套计时状态。

## 2. 核心模块职责

### 2.1 `core/`

- `BreakEngine`：只负责状态迁移，不依赖 Android API。
- `BreakRuntime`：运行时状态容器，持有 ticker（1s）并暴露统一 setter/action。
- 规则：新增业务行为时优先加 `BreakEngine`/`BreakRuntime` 能力，不要直接在 UI/Service 里拼状态。

### 2.2 `data/`

- `SettingsStore` 是唯一持久化入口，负责默认值、字段约束、兼容旧字段。
- `AppSettings` 与 `BreakUiState` 通过 `toAppSettings()/applyToUiState()` 双向映射。
- 新增设置项时，必须同时更新：
  - `AppSettings`
  - `SettingsStore.settingsFlow` 读取逻辑
  - `SettingsStore.save` 写入逻辑
  - `BreakUiState`
  - 映射函数（`toAppSettings` / `applyToUiState`）

### 2.3 `monitor/`

- `AppPauseMonitor`：轮询前台应用并更新 `PauseReason.APP_OPEN`。
- `ForegroundAppMonitor`：基于 UsageStats 查询当前前台包名（含缓存/增量窗口）。
- `ScreenLockMonitor/ScreenLockReceiver`：监听灭屏与解锁，更新 `PauseReason.SLEEP` 并处理解锁后重启逻辑。
- 规则：监控对象必须幂等启动，避免重复注册 Receiver / 重复起 Job。

### 2.4 `notification/`

- `BreakReminderService`：前台服务主循环，负责
  - 常驻通知刷新
  - 预提醒通知
  - Overlay 渲染
  - 被系统杀后延迟拉起（`BreakReminderRestarterReceiver`）
- `NotificationActionReceiver`：通知快捷动作入口。
- `PostponePickerActivity`：从通知进入的延后时长选择器。

### 2.5 `startup/` 与 `tile/`

- `BootCompletedReceiver`：开机/升级后按设置恢复运行时和服务。
- `DreamBreakTileService`：快捷开关入口，状态展示与点击切换。
- 规则：这些入口只能“驱动 Runtime”，不要复制计时逻辑。

### 2.6 UI（`MainActivity.kt` / `DreamBreakApp.kt` / `ui/`）

- `MainActivity`：主题包装、`BreakRuntime.start()`、从多任务隐藏等；`companion` 提供悬浮窗/电池/通知设置跳转（供引导与其它模块调用）。
- `DreamBreakApp.kt`：根 Composable、导航壳、设置加载/落盘与 `RuntimeBootstrap` 副作用。
- `ui/onboarding/OnboardingScreen.kt`：首次引导流程。
- `ui/home/HomeScreen.kt`：首页与「下一次休息」推算辅助函数。
- `ui/settings/SettingsScreen.kt`：设置页、应用多选子页、通用输入组件；`parsePackageList` 供根界面回调使用。

## 3. 关键状态与不变量

- `BreakRuntime.uiState` 是单一可信状态源（SSOT）。
- `BreakEngine` 只处理状态推进；外部副作用（通知、UI、Overlay、持久化）由调用方执行。
- `appEnabled=false` 时应停止/隐藏所有主动提醒（包括 Overlay 与常驻通知）。
- Pause 原因通过 `pauseReasons` 集合合并，不要用单布尔覆盖。
- 配置类数值必须做范围钳制（已存在常量统一管理，新增字段延续该模式）。
- 引导解锁逻辑（`hasVisitedSpecificAppsPage` 等）影响 `appEnabled` 可用性，改动时需评估首启与恢复路径。

## 4. 常见改动策略

### 4.1 新增一个设置项

按“数据模型 -> Runtime -> UI -> 入口恢复 -> 回归验证”顺序改，避免只改 UI 不落盘。

### 4.2 新增一个暂停来源

- 在 `PauseReason` 新增枚举值
- 通过 `BreakEngine.setPauseReason(...)` 接入
- 在对应 monitor/receiver 中只上报 active/inactive，不直接改 mode

### 4.3 修改通知展示逻辑

- 优先修改 `BreakReminderService.resolvePersistentNotificationContent` 与 `shouldNotify`
- 保证“低频模式下不抖动，高状态变化时及时刷新”

### 4.4 修改 Overlay 行为

- 只改 `BreakOverlayController` 与 `BreakRuntime` 参数传递
- 保持无悬浮窗权限时 fail-safe（直接不显示，不崩溃）

## 5. 回归检查清单（每次改动后）

### 5.1 必跑命令（编译与安装）

每次修改代码后，运行以下命令编译并安装：

```
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleDebug
zsh -i -c "adb kde"
adb install <build apk path>
```

### 5.2 手动功能回归

- 首页开关可启停；倒计时正常推进。
- 进入 break 时 top flash/full-screen overlay 行为符合设置。
- 通知动作（立即休息/延后）可达且生效。
- 亮灭屏后 `PauseReason.SLEEP` 与恢复逻辑符合预期。
- 开机自启、磁贴点击、应用升级后恢复路径可正常工作。

## 6. 代码风格与维护约定

- 新增逻辑先放在对应分层，不跨层“偷写”状态。
- 任何入口（Activity/Service/Receiver/Tile）都避免保存私有业务状态，以 `BreakRuntime` 为准。
- 新字段命名要和现有前缀风格保持一致（如 `break*`、`persistentNotification*`）。
- 对兼容逻辑（legacy key）保守处理，迁移时优先“可回退、可容错”。
- 涉及并发的启动逻辑保持幂等（锁、`Job?.isActive`、receiver null-check）。
