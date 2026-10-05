# CONTEXT — RH-Musicbbit

> 项目领域词汇表。新增/收紧术语时同步更新此文件。
> 架构层面词汇沿用 `superpowers:improve-codebase-architecture` 的 LANGUAGE.md（module / interface / depth / seam / adapter / leverage / locality）。

---

## 顶层概念

| 术语 | 含义 |
|---|---|
| **音乐兔（Musicbbit）** | 整体应用：本地音乐播放器，集成"闹钟到点续播音乐"为核心特色 |
| **Playlist** | 用户组织的歌曲集合。歌曲实体 `Song` 持久化在 Room；进度 `PlaybackProgress` 按 `(songId, playlistId)` 维度存储 |
| **Alarm** | 用户配置的闹钟。领域模型 `Alarm` 字段含 `playlistId / repeatDays: Set<DayOfWeek> / autoStop: AutoStop? / lastTriggeredAt`；`repeatDays.isEmpty()` 表示一次性闹钟。`AlarmEntity` 仍用于 Room 持久化（bitmask + String 编码），但 service 层不直接操作它 |
| **Playback Progress** | 一次播放中"播到了哪首歌、第几毫秒"的快照。`PlaybackProgress(songId, positionMs, updatedAt, playlistId)` — 领域模型，Room 侧由 `PlaybackProgressEntity`（composite primary key）+ `PlaybackProgressMapper` 位拷贝承载 |

---

## 闹钟域（Alarm Domain）

> 闹钟从触发到停止的全生命周期由 `AlarmFireSession` 单点负责。

| 术语 | 含义 |
|---|---|
| **AlarmFireSession** | 单例深模块，承载"一次闹钟响"全生命周期（load alarm → resolve start → playback → autoStop → bookkeeping）。状态机 `Idle → Loading → Playing → (Paused) → Stopped`，错误转 `Error`。同一时刻最多一个 active session |
| **AlarmFireState** | Session 暴露给 UI 的可观察状态（sealed class）：`Idle / Loading(alarmId) / Playing(alarmId, currentSong, positionMs) / Paused(...) / Stopped / Error(alarmId, message)` |
| **AlarmPlaybackHost** | Session ↔ Service 间的回调接口。`MusicPlaybackService` 实现该接口，session 通过 `bindHost / unbindHost` 接管。方法：`preloadFirstSong / playAlarmQueue / pauseAlarm / resumeAlarm / stopPlayback`。Host 完成 stop 后调 `session.onPlaybackStopped()` 收尾 |
| **NextOccurrenceCalculator** | 单点计算"下次响铃时间"。接受 `Set<DayOfWeek>` 参数（不再用原始 bitmask）。依赖 `HolidayRepository` 做节假日判断 + 每月 API 刷新。含纯星期 fallback。下次响铃逻辑只能在此处变更 |
| **AlarmScheduler** | 与 `AlarmManager` 系统服务对话的网关。接受领域模型 `Alarm`（不再接受 `AlarmEntity`）。仅做"算时间 + setExactAndAllowWhileIdle / cancel"，不做闹钟簿记 |
| **bookkeepAlarmTrigger** | Session.fire 末尾的事后簿记：通过 `AlarmRepository.recordTriggered(alarmId)` 执行。更新 `lastTriggeredAt`、一次性闹钟 flip `isEnabled = false`、周期性闹钟重排下次。**只能通过 `recordTriggered` 发生** |
| **Auto-stop** | 闹钟启动 N 分钟后自动停止播放。Session 用 `Job + delay` 调度；`extendAutoStop(minutes)` 取消旧 job、按新时长重排 |
| **Extend-to-end** | 用户在通知里选择"放完当前歌再停"。Session 维护一个 `extendToEnd` 标志，`MusicPlaybackService` 在 `MediaItemTransition(reason=AUTO)` 时查询此标志决定是否 stop |
| **QuietModeBypassResolver** | <!-- 2026-09-06 --> 单点回答"该闹钟在勿扰/静音下是否真的可听"。`resolve(Alarm) → AlarmBypassPlan(useAlarmStream, useBypassNotificationChannel)`，每次 fire 计算一次；播放流、音量斜坡、通知渠道、列表 DND banner 全部消费同一 plan。`needsDndAccessBanner(alarms)` 为纯函数 |
| **AlarmRecovery** | <!-- 2026-09-06 --> 单点"扫已启用闹钟 → 修复调度/状态"深模块。`recoverAll()`（启动对账：一次性已触发未禁用 → 禁用；其余重排）与 `rescheduleEnabledAlarms()`（幂等重排）供 BootReceiver / AlarmStartupReconciler / AlarmIntegrityWorker 三个触发点复用 |
| **isAlarmTrigger** | `fire(alarmId, isAlarmTrigger)` 的第二参数。`true` 表示"由 AlarmManager 真正唤起"，触发 wakeLock + 音量斜坡；`false` 表示"用户在 App 内手动调闹钟播放预览"，跳过这两件事 |

---

## 播放运行时（Playback Runtime）

| 术语 | 含义 |
|---|---|
| **PlayerPort** | 播放运行时的抽象接口。方法集合面向"队列播放器"语义：`setQueue / play / pause / next / previous / seekTo / stop / setShuffleEnabled / setRepeatMode / hasNext / hasPrevious / isPlaying / currentPositionMs / clearQueue`，事件 `Flow<PlayerEvent>` |
| **PlayerEvent** | sealed class：`IsPlayingChanged / MediaItemTransition(itemTag, itemIndex, reason) / PlaybackReady(durationMs) / PositionDiscontinuity(newPositionMs, itemIndex)` |
| **TransitionReason** | `AUTO`（自然播完进入下一首） / `SEEK`（用户跳转） / `REPEAT`（循环回放） / `OTHER` |
| **ExoPlayerAdapter** | `PlayerPort` 的唯一 production adapter，包装 ExoPlayer + `Player.Listener` → `Flow<PlayerEvent>` |
| **UserPlaybackSession** | 用户主动播放的会话（<!-- 2026-09-15 --> 代码类名已对齐为 `UserPlaybackSession`）。持有 `PlayerPort` 用户实例，管理 `PlaybackState`，与闹钟播放完全解耦 |
| **AlarmPlaybackSession** | 闹钟触发专用的播放会话。与用户会话**共享单一 `PlayerPort` 实例**（经 `PlaybackCoordinator` 路由），只暴露闹钟播放需要的 seam：`playAlarmQueue / pause / resume / stop / playbackTransitions`。不携带 `alarmId` / `alarmLabel` 等 UI 状态，这些由 `AlarmFireSession` 持有 |
| **SessionCore** | <!-- 2026-09-06 --> `UserPlaybackSession` / `AlarmPlaybackSession` 的共享实现基类：状态归约、进度跟踪、音频焦点四回调、stop 时序。<!-- 2026-10-04 --> queue-ended 策略统一走 `stopDeferred`（保留所有权至 teardown 完成，发射单一终态转换）；启动次序契约统一走 `coreStartQueue` |
| **issueCommand** | <!-- 2026-10-04 --> `SessionCore` 的唯一命令守卫 seam：查 `PlaybackCoordinator` 所有权→放行/丢弃。两个会话的所有公共命令入口（含挂起恢复后的复检）必须经此，互斥不变量从逐方法 if 封条收敛为结构性守卫 |
| **commandsBlocked** | <!-- 2026-10-04 --> `UserPlaybackSession` 派生的 `StateFlow<Boolean>`（coordinator activeConsumer 映射）。UI（PlayerScreen/MiniPlayer）收集它禁用控件——命令拒绝首次可见 |
| **stopDeferred** | <!-- 2026-10-04 --> `SessionCore` 一等操作：停 loop→await pending save→完整 teardown→发射单一终态转换。队列自然播完走它；所有权保持到 teardown 完成，QueueEnded→stop() 回调竞态结构性关闭 |
| **coreStartQueue** | <!-- 2026-10-04 --> `SessionCore` 启动模板：focus→activate→stream→service→setQueue→play→applyState 的次序契约单点。`play / playQueue / playAlarmQueue` 三入口全部经它启动；`issueCommand` 守卫与 playQueue 挂起后所有权复检保留在入口侧 |
| **MusicPlaybackService** | Android 前台服务。职责：foreground notification + 持有 `PlayerPort` + 实现 `AlarmPlaybackHost` + 在闹钟模式下中转 session 调用。**不做闹钟编排**，那是 session 的事 |

---

## 平台 Port

> 把"会让 session 变得不可在 JVM 测试"的 Android 依赖统一抽象。Session 只依赖接口，production 由 Hilt 注入 Android adapter，测试由 fake 替换。

| 术语 | 含义 |
|---|---|
| **WakeLockPort** | `acquire(timeoutMs) / release()`。Android adapter 包装 `PowerManager.WakeLock`。<!-- 2026-08-06 --> adapter 的 `acquire` 不再 try/catch——SecurityException 等异常直接传播（CLAUDE.md "prefer errors"），调用方 `AlarmFireSession.fire` 不加兜底，让"无法获取 wake lock"对用户可见 |
| **NotificationPort** | `showAlarmPlaying(alarm: Alarm, song) / showAlarmPaused(alarmId) / showError(...) / cancel(alarmId)`。Android adapter 包装 `AlarmNotificationHelper`。参数是领域模型 `Alarm`（非 `AlarmEntity`） |
| **VolumeRampPort** | `startVolumeRamp(scope) / restoreVolume()`。Android adapter 包装 `AlarmVolumeController` |
| **Clock** | `nowMs(): Long`。Production `SystemClock` 调 `System.currentTimeMillis()`；测试用 `FakeClock` 提供固定值，方便断言 `lastTriggeredAt` |
| **MusicNotificationPort**（<!-- 2026-08-06 --> 收窄职责）| `ensureChannelExists() / buildSpec(state): ForegroundNotificationSpec`。**职责从"渲染并通知"收窄为"构造 spec"**——`Notification` 实例的渲染责任迁移到 `ForegroundNotificationController`（per ADR 0008）。Port 仍保持纯 Kotlin，扩展了 ADR 0006 的边界 |
| **AudioStreamPort** | <!-- 2026-09-06 --> `setAlarmStream(Boolean)`：选择 USAGE_ALARM 或 USAGE_MEDIA 输出流。从 `PlayerPort` 队列播放器接口中拆出（闹钟专用关注点不占通用接口）；production adapter 同为 `ExoPlayerAdapter` |
| **ForegroundNotificationSpec** <!-- 2026-08-06 --> 新 | 纯 Kotlin data class：`title, text, isPlaying, smallIconResId, playPauseIconResId, playPauseLabel, previousLabel, nextLabel`。`ForegroundNotificationController` 用其构造 `NotificationCompat.Builder`，可在 JVM 单测里直接断言 |
| **ForegroundNotificationController** <!-- 2026-08-06 --> 拓展 | `service.playback` 包内的"通知生命周期宿主"：持有 nullable `MusicPlaybackService` 引用（`attach` / `detach`），collect `UserPlaybackSession.playbackState`，build Notification，直接调 `service.startForeground(NOTIFICATION_ID, notification)`。`NOTIFICATION_ID` 常量从 `MusicPlaybackService.companion` 迁入此处。`detach` 内部 cancel state-collection Job，确保 service 销毁后无悬挂调用 |
| **UserPlaybackSession.togglePlayPause()** <!-- 2026-08-06 --> 新 API | 替代 `MusicPlaybackService.onStartCommand` 内的 if-isPlaying-pause-else-resume 决策。Service 收到 `ACTION_TOGGLE_PLAY_PAUSE` 直接转发，toggle 语义在 session |

---

## 表现层（Presentation）

| 术语 | 含义 |
|---|---|
| **PermissionStatusMonitor** | <!-- 2026-09-06 --> `PermissionPort` 之上的 UI 深模块：单一 `StateFlow<PermissionStatus>`（电池优化 / 全屏意图 / DND 访问 / READ_MEDIA_AUDIO）+ 三个设置页 Intent 构造。替代各 ViewModel 手写的 boolean + refresh 样板；`launchSettingsSafely` 负责“打开设置”副作用 |
| **LocalPlaybackSession** | <!-- 2026-09-06 --> CompositionLocal，向各屏幕提供应用级 `UserPlaybackSession`。`PlayerViewModel` 纯透传模块已删除（连带 `ViewModelUtils` 作用域 hack）；屏幕直接 collect `UserPlaybackSession.playbackState` |
| **LibraryRefresher** | <!-- 2026-09-06 --> 数据层刷新配方深模块：scan → `SongSyncEngine.sync` → `SyncResult` + 目录生命周期：addDirectoryAndRefresh / removeDirectoryAndCascade 都在此处；进度孤儿由 playback_progress FK CASCADE 兜底（迁移 10→11）。"无扫描目录 ⇒ 清空曲库"策略在此显式化 |
| **MediaStorePort** | <!-- 2026-09-06 --> `ContentResolver` 媒体查询 seam。`MusicScanner` 的 selection 构造与格式白名单变为纯函数（`buildAudioSelection` / `isSupportedAudioFormat`），JVM 可测 |
| **observeWithSongs** | <!-- 2026-10-04 --> `PlaylistDao` 的 `@Transaction` 观察 Flow（playlist + playlist_song + song 三表失效追踪）。`PlaylistRepositoryImpl.getPlaylistWithSongs` 经它 + `playlistSongDao.getByPlaylistId` combine，不再订阅全表快照；null 语义（播放列表已不存在空态）保持 |
| **RepeatSummary** | <!-- 2026-10-04 --> 纯函数星期摘要 seam（`presentation/alarm/components/RepeatSummary.kt`）：`DayOfWeek.fullNameRes / shortLabelRes` 单点映射 + `repeatSummary` 无 Compose 依赖，`AlarmListScreen` 直调 `repeatSummary()`、`DayOfWeekSelector` 仅用 `shortLabelRes`/`fullNameRes` 扩展属性渲染星期标签（未直调 `repeatSummary()`），JVM 可测 |
| **rememberSettingsLauncher** | <!-- 2026-10-04 --> `presentation/components/SettingsLauncher.kt` 组合 helper：设置 Intent 启动 + 失败 toast（`common_settings_open_failed`）一行式。AlarmList / AlarmEdit / PermissionDiagnostics 三屏的唯一设置跳转形态 |

---

## 测试用语

| 术语 | 含义 |
|---|---|
| **JVM 单测可达性** | 模块只依赖纯 Kotlin + 注入接口 → 可以脱离 Robolectric / Hilt 反射 直接 `@Test` 验证。`AlarmFireSession` 是这条路线的样板 |
| **虚拟时间** | `kotlinx-coroutines-test` 的 `TestScope.testScheduler`。让 `delay(N.minutes)` 在测试里 `advanceTimeBy` 即可推进，autoStop 等定时行为变得确定可测 |

---

## 不再使用的旧词

| 已废弃 | 取而代之 |
|---|---|
| `MusicPlaybackService.handlePlayAlarm` | `AlarmFireSession.fire(alarmId, isAlarmTrigger)` |
| `MusicPlaybackService.currentAlarmId / extendToEnd / autoStopRunnable / alarmWakeLock` | `AlarmFireSession.state.alarmId` / `extendToEnd` / `autoStopJob` / `WakeLockPort` |
| `AlarmReceiver` 内的事后簿记代码 | `AlarmFireSession.bookkeepAlarmTrigger` |
| `UserPlaybackSession.blockedByActiveAlarm` + 10 处逐方法 if 封条 | `SessionCore.issueCommand` |
| `AlarmPlaybackSession.queueEndedPending` | `SessionCore.stopDeferred(terminal)` |
| `UserPlaybackSession.playerEvents` | 删除（零消费者；未来需要用户事件应由 coordinator 按所有权过滤另设 seam） |
| `AlarmFireSession` QueueEnded 分支回调 `alarmPlaybackSession.stop()` | `stopDeferred` 内完成 teardown，分支内直接 `onPlaybackStopped()` |
| `AlarmNotificationHelper.lastChannelId` 可变状态 | <!-- 2026-10-04 --> `showAlarmPaused(alarmId, bypassDnd)` 显式契约参数（渠道由每次调用自决，无跨调用可变状态） |
| `DayOfWeekSelector` 本地 `dayShortLabelRes / dayFullNameRes` 双写 + `AlarmListScreen.formatRepeatDays` 的 @Composable 锁死实现 | <!-- 2026-10-04 --> `DayOfWeek.fullNameRes / shortLabelRes` + 共享 `RepeatSummary.repeatSummary` 纯函数 |
| `ExactAlarmPermissionHelper.openSettings` / `FullScreenIntentPermissionHelper.openSettings`（presentation 直调 + Intent 双构造） | <!-- 2026-10-04 --> `PermissionPort.createExactAlarmSettingsIntent` / adapter 单点构造 Intent，presentation 经 `rememberSettingsLauncher` 启动（helper 仅保留 `isGranted` 查询；ExactAlarm object 整体删除） |
| `AlarmScheduler` 内的 `canScheduleExactAlarms` API 31 分支 | <!-- 2026-10-04 --> 委托 `PermissionPort.canScheduleExactAlarms()`（单真相源；分支由 adapter 测试持有） |
| `PlaybackProgressDao.deleteAll` / `SongDao.getById` / `SongDao.update` / `SongDao.insert` 单条 / `HolidayDao.countForYear` | <!-- 2026-10-04 --> 删除（零生产消费者）；测试 fixture 统一 `insertAll` / `getAll` 等价改写 |
| `AlarmActionReceiver` 的 `ACTION_SERVICE_*` 常量与 service intent 转发 | 直接调 `AlarmFireSession` 方法 |
| `AlarmScheduler.calculateNextTriggerTime`（Companion） / `calculateNextTriggerTimeWithHolidays`（重复实现） | `NextOccurrenceCalculator`（单点） |
| `AlarmListViewModel.playlistNameCache / resolvePlaylistName` | <!-- 2026-10-04 --> `AlarmRepository.getAlarmsWithPlaylistName()` 双表 combine |
| `ScanDirectoryRepositoryImpl` 手写级联删除 | <!-- 2026-10-04 --> `LibraryRefresher.removeDirectoryAndCascade`（含目录边界 prefix 匹配） |
| `updateAlarm / persistence.update / deleteProgress` | <!-- 2026-10-04 --> 死代码删除；写入全部走 `saveAlarm` / `setMode` / `deleteAllProgressForPlaylist` |
| `ThemeRepositoryImpl / AlarmRingSettingsRepositoryImpl / HolidayRepositoryImpl` 直连 DataStore | <!-- 2026-10-04 --> `SettingsStore` 小深模块（key + 默认值并置声明） |
| `AlarmPersistenceRepository` 在 domain 包 | <!-- 2026-10-04 --> 移到 data 包（internal seam，不进入领域词汇） |
| `PermissionDiagnosticsViewModel.PermissionStatus` 同名异义类 | <!-- 2026-10-04 --> `PermissionDiagnosticItem` + `PermissionKey` enum |
| `IsWorkdayUseCase` / `AddSongToPlaylistUseCase` / `CreatePlaylistUseCase` / `AddScanDirectoryUseCase` | 逻辑移入 `HolidayRepository` / `PlaylistRepository` / ViewModel；use-case 层已删除 |
| `AlarmScheduler.schedule(AlarmEntity)` | `AlarmScheduler.schedule(Alarm)` — 接受领域模型 |
| `AlarmFireSession` 直接依赖 `AlarmDao` | `AlarmFireSession` 通过 `AlarmRepository` 操作 |
| `BootReceiver` / `AlarmStartupReconciler` 直接依赖 `AlarmDao` | 通过 `AlarmRepository` 操作 |
| `NextOccurrenceCalculator.nextOccurrence(bitmask: Int)` | `nextOccurrence(repeatDays: Set<DayOfWeek>)` — 不再用原始 bitmask |
| <!-- 2026-08-06 --> `MusicPlaybackServiceForegroundBridge` | 删除（per ADR 0008）—— `@Singleton var service` 这一 mutable state 被 `ForegroundNotificationController.attach / detach` 替代 |
| <!-- 2026-08-06 --> `ForegroundServicePort` | 删除（per ADR 0008）—— controller 直接调 `service.startForeground` / `service.stopForeground` 基类方法 |
| <!-- 2026-08-06 --> `MusicNotificationPort.buildAndNotify` / `hideForegroundNotification` | 改为 `buildSpec(state): ForegroundNotificationSpec` |
| <!-- 2026-08-06 --> `MusicPlaybackService` 持有 `wakeLockPort` + `ALARM_WAKE_LOCK_TIMEOUT_MS` | 迁回 `AlarmFireSession`（对称生命周期：fire 时 acquire、stop/onPlaybackStopped 时 release） |
| <!-- 2026-08-06 --> `MusicPlaybackService.onStartCommand` 内的 toggle 分支 | 转发到 `UserPlaybackSession.togglePlayPause()` |
| <!-- 2026-08-06 --> `MusicPlaybackService.NOTIFICATION_ID` | 迁到 `ForegroundNotificationController.companion` |
| <!-- 2026-08-06 --> `NextOccurrenceCalculator.nextOccurrenceFallback` | 删除（per ADR 0007）—— 主路径超过 2 年 search window 改为 `throw IllegalStateException`，调用方 `AlarmScheduler.schedule` try/catch 跳过该 alarm |
| <!-- 2026-08-06 --> `MusicRepositoryImpl.applySyncDiff`（私有）| 删除—— `SongSyncEngine.sync(scanned, existing)` 用 `database.withTransaction` 原子应用 diff |
| <!-- 2026-08-06 --> `AlarmEditEvent` sealed interface + `events: Flow<AlarmEditEvent>` | 删除—— `AlarmEditViewModel.uiState.dialogState: AlarmEditDialogState?` 单一密封类型，覆盖所有 dialog 状态 |
| <!-- 2026-08-06 --> `AlarmEditScreen` 6 个 dialog boolean + `hasUnsavedChanges` + `autostartIntent` 8 个独立 `remember mutableStateOf` | 收敛为单一 `dialogState` + `hasUnsavedChanges` 进 `UiState` |
| <!-- 2026-08-06 --> `AlarmEditScreen` 的 `clearSaveFailedMessage` | 删除—— `saveFailedMessageResId` 与 `errorMessageResId` 共用 `clearError` 清理 |
| <!-- 2026-08-06 --> `AlarmNotificationContent.ActionType`（sealed interface）| 改为 `sealed class`，每个 subtype 自带 `action: String` 与 `extras: Map<String, Int?>`——映射逻辑收回纯 Kotlin 部分 |
| <!-- 2026-08-06 --> `AlarmNotificationHelper.createActionPendingIntentForType` | 删除—— `ActionType` 自描述 action，Helper 只需 `createActionPendingIntent(alarmId, action, extras)` |
| <!-- 2026-09-06 --> `PlayerPort.configureForAlarmPlayback` | 迁出为 `AudioStreamPort.setAlarmStream`（闹钟动词离开队列播放器接口） |
| <!-- 2026-09-06 --> `AlarmNotificationHelper` 内 `DndAccessPermissionHelper.isGranted` 静态检查 | `NotificationPort.showAlarmPlaying(alarm, song, bypassDnd)` —— bypass 决策由 `QuietModeBypassResolver` 算好传入 |
| <!-- 2026-09-06 --> AlarmList/AlarmEdit ViewModel 的 3×boolean + refreshXxx + ON_RESUME 样板；UI 直调静态 `openSettings` | `PermissionStatusMonitor.status` + Intent 构造 + `launchSettingsSafely` |
| <!-- 2026-09-06 --> `PlayerViewModel` / `rememberActivityScopedPlayerViewModel` | `LocalPlaybackSession` + 直接注入 `UserPlaybackSession`（顺带修复 MiniPlayer 第二实例的不一致） |
| <!-- 2026-09-06 --> BootReceiver 内复制的 reschedule 循环；`AlarmIntegrityWorker` 的独立检查逻辑 | `AlarmRecovery.rescheduleEnabledAlarms()` |
| <!-- 2026-09-06 --> `MusicRepositoryImpl.refreshSongs/refreshDirectory` 内联的 4 步刷新配方 | `LibraryRefresher.refreshAll / refreshDirectory` |
| `MusicUiState` / `PlaylistListUiState` / `PlaylistDetailUiState` / `AlarmListUiState` / `AddToPlaylistUiState` | <!-- 2026-10-04 --> `ListUiState<T>` |
| `*.Success.errorMessageResId`（Theme/AlarmRing/ScanDirectory 3 处） | <!-- 2026-10-04 --> `UserMessage` Channel |
| `PermissionDiagnosticsViewModel.PermissionStatus`（同名异义类） | <!-- 2026-10-04 --> `PermissionDiagnosticItem` |
| `PERMISSION_NAME_*` 字符串常量 | <!-- 2026-10-04 --> `PermissionKey` enum |

| 日期 | 变更 |
|---|---|
| 2026-04-26 | 初始化。整理"AlarmFireSession 深模块"重构带来的新词汇与废弃词汇 |
| 2026-04-30 | 架构深化：合并 4 对 Entity/Model；删除 use-case 层；AlarmEntity 清理；bitmask 统一 |
| 2026-06-29 | 架构审查：拆分 `PlaybackSession` 的闹钟职责，引入 `AlarmPlaybackSession` 与 `UserPlaybackSession` |
| 2026-09-06 | 架构审查 6 候选落地：`QuietModeBypassResolver` / `PermissionStatusMonitor` / 删除 `PlayerViewModel` / `SessionCore` 提取 / `LibraryRefresher` + `MediaStorePort` / `AlarmRecovery`；修正 `AlarmPlaybackSession` 共享 `PlayerPort` 与 `PlaybackSession` 类名的文档漂移 |
| 2026-08-06 | 架构深化 6 候选全部落地：`SongSyncEngine.sync` 事务化 / `NextOccurrenceCalculator` 删除 silent fallback（ADR 0007 延续）/ `AlarmNotificationContent.ActionType` 映射收回纯部分 / `AlarmEditScreen` dialog 状态收敛为单一 `AlarmEditDialogState` / `MusicPlaybackService` 薄壳化（toggle + wake-lock 迁回 session，ADR 0003 收尾）/ 删除 `MusicPlaybackServiceForegroundBridge` singleton（ADR 0008） |
| 2026-10-04 | Plan A 架构审查 #6 落地：播放所有权 seam 收敛（`SessionCore.issueCommand` 单一守卫 / `PlaybackCoordinator.activeConsumer` StateFlow / `UserPlaybackSession.commandsBlocked` UI 禁用 / `stopDeferred` 一等操作删除 `queueEndedPending`）/ Paused 态终态修复 / FGS 前台义务无条件履行 / 播放 seam 卫生清理（`playerEvents` 删除、`CHANNEL_ID` 单点、`contentIntent` 走 `MainActivityIntentFactory`） |
| 2026-10-04 | Plan B 架构审查 #6 落地：列表族状态收敛（`ListUiState<T>` + `ScreenStateCrossfade`）5 个消费者；`UserMessage` Channel 替代 6 处死 errorMessageResId 通道；权限读取统一（`PermissionStatusMonitor.isMediaAudioGranted` + `PermissionKey` 枚举 + i18n）；主题映射与播放模式 cycle 抽到 `ThemeExt` / `UserPlaybackSession.cyclePlayMode()` |
| 2026-10-04 | Plan C 架构审查 #6 落地：playback_progress FK CASCADE 迁移 10→11（ad-hoc playlistId≤0 写门 guard）；ScanDirectory 生命周期双配方（addDirectoryAndRefresh + removeDirectoryAndCascade）入 `LibraryRefresher`；`SettingsStore` 小深模块收敛 3 个 RepositoryImpl 的 DataStore 样板；`AlarmPersistenceRepository` 降级到 data 包；删 `updateAlarm / persistence.update / deleteProgress` 死代码；闹钟列表名称 join 下沉到 repository（VM cache 删除） |
| 2026-10-04 | Plan D 旧批次独有项移植落地：闹钟契约三修（`showAlarmPaused` 显式 bypassDnd 契约 / resume 重发播放态通知 / `RepeatSummary` 纯函数星期摘要）；`coreStartQueue` 启动模板收编 play/playQueue/playAlarmQueue 三入口；`PlaylistDao.observeWithSongs` 观察查询；权限表面收敛（`rememberSettingsLauncher` 三屏 + `canScheduleExactAlarms` 单真相源 + presentation 直调收口，`ExactAlarmPermissionHelper` 删除）；DAO 死方法删除（deleteAll/getById/update/insert 单条/countForYear）；domain 纯度（`Playlist` 去 `@Immutable`、`SessionCore.close` `@VisibleForTesting`）；修正「领域模型兼作 Room entity」漂移表述 |
