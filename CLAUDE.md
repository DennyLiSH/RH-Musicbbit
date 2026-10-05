# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

**音乐兔（RH-Musicbbit）** — Android 本地音乐播放器，集成后台定时闹钟功能。闹钟到点后自动播放指定播放列表，并从上次播放进度续播。

核心功能：本地音乐文件夹浏览、播放列表管理、播放进度记录、定时闹钟调度、闹钟触发续播。

详细的变更记录请查看 `_Project/DONE.md`。

## 技术栈

| 项目 | 版本 |
|------|------|
| Kotlin | 2.3.21 |
| AGP | 9.4.1 |
| Compose BOM | 2026.05.01 |
| Gradle | 9.6.0 |
| JDK | 21 (JVM Toolchain) |
| compileSdk / targetSdk | 36 (Android 15) |
| minSdk | 24 (Android 7.0) |
| 包名 | `com.rabbithole.musicbbit` |

### 架构

MVVM + Clean Architecture（单模块）：
- **Presentation**: Compose UI + ViewModel + StateFlow (UDF)
- **Domain**: Repository 接口 + 领域模型（纯 Kotlin；<!-- 2026-04-30 --> use-case 层已删除，逻辑收入 Repository 或内联 ViewModel）
- **Data**: Repository 实现 + Room + DataStore + Retrofit/OkHttp（仅节假日 API）

依赖注入：Hilt | 本地存储：Room | 偏好设置：DataStore | 多语言：AppCompat Per-App Language API | 异步：Coroutines + Flow | 网络：Retrofit + OkHttp（timor.tech）| 后台任务：WorkManager（闹钟完整性检查）

#### 播放抽象层（深模块）

`service/playback/` 封装了播放器的所有复杂性，核心抽象：

- **[`PlayerPort`](app/src/main/java/com/rabbithole/musicbbit/service/playback/PlayerPort.kt)** — 播放运行时接口，隔离 ExoPlayer 具体实现。生产实现 `ExoPlayerAdapter`，测试可用 Fake 实现。
- **[`SessionCore`](app/src/main/java/com/rabbithole/musicbbit/service/playback/SessionCore.kt)** — <!-- 2026-10-04 --> `UserPlaybackSession` 与 `AlarmPlaybackSession` 的共享基类：状态归约、进度追踪、音频焦点处理、stop 序列（save/tick/deactivate/emit 顺序契约单点维护）。子类仅差异于队列启动方式与 queue-ended 策略。统一启动入口 `coreStartQueue` 维护启动顺序契约（焦点先于激活、流路由先于设队列、前台服务先于播放、状态最后应用）；alarm 侧 queue-ended 经 `stopDeferred` 延迟停止并抑制最终 save（skipSave=true），`QueueEnded` 作为单一 terminal transition 发出，且在整个停止窗口持有 player 所有权（用户会话无法在队列结束与停止之间插入播放）
- **[`UserPlaybackSession`](app/src/main/java/com/rabbithole/musicbbit/service/playback/UserPlaybackSession.kt)** — `@Singleton` 深模块，封装**用户播放**状态管理、音频焦点协调、进度追踪、通知状态驱动。`MusicPlaybackService` 仅负责 Android Service 生命周期与前台调用执行，不处理播放逻辑；UI 直接注入 `UserPlaybackSession` 调用其公共 API。
- **[`AlarmPlaybackSession`](app/src/main/java/com/rabbithole/musicbbit/service/playback/AlarmPlaybackSession.kt)** — `@Singleton` 深模块，封装**闹钟播放**状态管理与 `PlayerPort` 交互。逻辑上与 `PlaybackSession` 独立，共享同一 `PlayerPort` / `AudioFocusPort` 实例。
- **[`PlaybackCoordinator`](app/src/main/java/com/rabbithole/musicbbit/service/playback/PlaybackCoordinator.kt)** — `@Singleton` 路由层，订阅一次 `PlayerPort.events` 与 `AudioFocusPort` 回调，仅向当前 active 的播放会话（`PlaybackSession` 或 `AlarmPlaybackSession`）转发，保证两者互斥响应共享 ExoPlayer 事件。
- ~~**[`PlaybackController`](app/src/main/java/com/rabbithole/musicbbit/service/playback/PlaybackController.kt)** — 播放控制接口~~（已删除，2026-05-11：shallow 接口，13 方法单一实现，无 leverage）
- **端口体系**: `AudioFocusPort`、`MusicNotificationPort`（纯 Kotlin，无 Android 类型泄漏）、`ServiceStarter`、`PlaybackProgressTracker`、`PermissionPort`（<!-- 2026-08-16 --> 含 `isNotificationPolicyAccessGranted()` 勿扰权限查询） — 每个端口封装一个 Android 系统能力
- <!-- 2026-05-18 --> **PlaybackTransition 窄 seam**：`PlaybackSession` 与 `AlarmPlaybackSession` 均对外暴露 `Flow<PlaybackTransition>`（`SongCompleted / QueueEnded / PlaybackStopped`），闹钟域通过此窄接口监听播放状态转换，无需导入 `PlayerEvent` / `TransitionReason`，消除播放层与闹钟层的跨层耦合

#### 闹钟核心逻辑

`service/alarm/` 包含闹钟触发全链路：

- **[`AlarmFireSession`](app/src/main/java/com/rabbithole/musicbbit/service/alarm/AlarmFireSession.kt)** — `@Singleton` 深模块，状态机驱动：`Idle → Loading → Playing → (Paused) → Stopped`。负责委托 `AlarmPlaybackResolver` 解析播放内容、驱动 `AlarmPlaybackSession`、管理唤醒锁、通知管理。自动停止逻辑已提取到 `AutoStopController`。
- **[`AlarmPlaybackResolver`](app/src/main/java/com/rabbithole/musicbbit/service/alarm/AlarmPlaybackResolver.kt)** — 解析 alarm → 播放列表 → 起始歌曲/索引；依据 `alarm.resumePlayback` 决定是否恢复保存进度，`resumePlayback=false` 时从第一首开始并清空进度。
- **[`NextOccurrenceCalculator`](app/src/main/java/com/rabbithole/musicbbit/service/alarm/NextOccurrenceCalculator.kt)** — 纯函数计算下次响铃时间，支持工作日/节假日排除。
- **[`AlarmStartupReconciler`](app/src/main/java/com/rabbithole/musicbbit/service/alarm/AlarmStartupReconciler.kt)** — 开机/应用启动时重新注册所有启用闹钟。
- **[`Clock`](app/src/main/java/com/rabbithole/musicbbit/service/alarm/Clock.kt)** — 时钟抽象，便于测试时注入假时间。

#### 类型安全导航

使用 Kotlin Serialization 实现类型安全导航（Compose Navigation 2.8+）：

```kotlin
@Serializable data object Alarm              // 路由对象
@Serializable data class AlarmEdit(val alarmId: Long = 0)  // 带参数路由
```

导航在 `navigation/AppNavigation.kt` 中集中配置，底部导航项定义在 `presentation/components/BottomNavItem.kt`。

#### DI 模块组织

```
di/
├── DatabaseModule.kt      // Room Database + DAO
├── DataStoreModule.kt     // Preferences DataStore
├── DispatcherModule.kt    // @IoDispatcher / @MainDispatcher / @DefaultDispatcher
├── NetworkModule.kt       // Retrofit + OkHttp（节假日 API）
├── RepositoryModule.kt    // Repository 接口→实现绑定
├── AlarmModule.kt         // AlarmScheduler + AlarmFireSession 端口绑定（含 NotificationPort, PermissionPort）
└── PlaybackModule.kt      // PlayerPort + 播放相关端口绑定
```

#### DataStore 偏好设置

读写全部经 `data/local/datastore/SettingsStore.kt`（`@Singleton` 深模块，提供 `booleanFlow/intFlow/longFlow/stringFlow + write(...)`）。新增偏好项必须在 SettingsStore 范围内使用，禁止在 Repository/VM/Compose 层直接注入 `DataStore<Preferences>`。所有 DataStore Key 集中定义在 `data/local/datastore/SettingsKeys.kt`：

| Key | 类型 | 用途 |
|-----|------|------|
| `THEME_MODE` | string | 主题模式 (SYSTEM/LIGHT/DARK) |
| `BREATHING_ENABLED` | boolean | 呼吸灯效果开关 |
| `BREATHING_PERIOD_MS` | long | 呼吸灯周期 |
| `VOLUME_RAMP_DURATION_SECONDS` | int | 音量渐强时长 (0=关闭) |
| `LAST_HOLIDAY_API_CALL_MONTH` | string | 上次调用节假日 API 的月份 |

### 项目结构

```
app/src/main/java/com/rabbithole/musicbbit/
├── data/
│   ├── mapper/          # Entity ↔ Domain 转换（AlarmMapper, HolidayMapper, PlaylistSongMapper, SongMapper, PlaylistMapper, PlaybackProgressMapper, ScanDirectoryMapper）
│   ├── model/           # Room Entity（AlarmEntity, PlaylistSongEntity）
│   ├── repository/      # Repository 实现
│   ├── local/
│   │   ├── model/       # Room Entity（SongEntity, PlaylistEntity, PlaybackProgressEntity, ScanDirectoryEntity, HolidayEntity, PlaylistWithSongsEntity）
│   │   ├── dao/         # Room DAO（全部使用 Entity 类型；PlaylistDao.observeWithSongs = @Transaction 观察 Flow，playlists+playlist_songs+songs 三表失效追踪）
│   │   ├── LibraryRefresher.kt # 曲库刷新配方+扫描目录生命周期深模块（refreshAll/refreshDirectory/addDirectoryAndRefresh/removeDirectoryAndCascade；「无扫描目录⇒清空曲库」策略所在；进度孤儿由 FK CASCADE 兜底）
│   │   ├── sync/        # 歌曲同步模块（SongDiff, SongSyncEngine）
│   │   ├── datastore/   # DataStore 偏好设置（读写统一经 `SettingsStore`，新偏好项禁止直接注入 DataStore）
│   │   └── Migration2To3.kt / ... / Migration10To11.kt  # Room 迁移（10→11 playback_progress FK CASCADE + 索引）
│   └── remote/          # 网络层（仅节假日 API）
│       ├── api/         # Retrofit 接口
│       └── dto/         # API DTO（Kotlin Serialization）
├── di/                  # Hilt Module
├── domain/
│   ├── model/           # 领域模型（纯 Kotlin data class / enum：Song, Playlist, PlaybackProgress, ScanDirectory, Alarm, AlarmRingMode, Holiday）
│   ├── repository/      # Repository 接口
│   └── validation/      # 领域验证（ScanDirectoryValidator）
├── presentation/
│   ├── music/           # 音乐浏览 & 播放
│   ├── playlist/        # 播放列表管理
│   ├── alarm/           # 闹钟设置 + 全屏响铃界面
│   │   ├── AlarmEditViewModel.kt    # 闹钟编辑 UI 状态
│   │   ├── AlarmSaveOrchestrator.kt # 保存工作流编排（验证→权限→持久化→引导）
│   │   └── components/              # 闹钟相关 UI 组件
│   │       ├── RingModeSelector.kt  # 响铃方式单选组（Normal / FullScreen）
│   │       ├── DayOfWeekSelector.kt # 重复规则星期选择器
│   │       └── RepeatSummary.kt     # 纯函数星期摘要 seam（DayOfWeek.fullNameRes/shortLabelRes + repeatSummary()，无 Compose 依赖，JVM 可测）
│   ├── player/          # 播放器 UI
│   ├── settings/        # 设置（扫描目录、主题切换、语言选择）
│   │   └── AppLanguage.kt  # 语言枚举（SYSTEM / ENGLISH / CHINESE / JAPANESE）
│   ├── permissions/     # 权限状态 UI 深模块（PermissionStatusMonitor：PermissionPort 之上单一 StateFlow<PermissionStatus> + 各设置页 Intent 构造；SettingsIntentLauncher）
│   ├── about/           # 关于页面（版本信息）
│   └── components/      # 共享组件（StateView 三态渲染 / HapticExt 触觉扩展 / InfoBanner / BottomNavItem / ViewModelUtils / AppToast 封装 android.widget.Toast / SingleChoiceDropdown 泛型单选下拉框 / SongSearchField 共享搜索框 / SheetTokens sheet 尺寸 / ListUiState 泛型列表三态（Loading/Error/Content，Empty 非独立态=Content(emptyList)） / ScreenStateCrossfade 统一 Crossfade 渲染 ListUiState / UserMessage+CollectUserMessages Channel-based 一次性消息（经 AppToast 渲染） / rememberSettingsLauncher 设置跳转+失败 toast 一行式）
├── ui/theme/            # 主题（Coral/Slate/Amber 品牌色 + 暖染色中性色 + 完整 M3 Typography + timeDisplay token + MotionTokens 动画时长 + AppShapes 圆角 + ExtendedColors.success 语义色；UI 视觉契约见根目录 DESIGN.md）
├── navigation/
│   └── AppNavigation.kt
├── service/             # Service 与核心逻辑
│   ├── alarm/           # 闹钟深模块
│   │   ├── AlarmFireSession.kt      # 闹钟触发会话状态机（播放编排、唤醒锁、通知管理）
│   │   ├── AlarmFireState.kt        # 状态机状态定义
│   │   ├── AlarmPlaybackResolver.kt # 播放内容解析 seam（alarm → playlist → startIndex）
│   │   ├── AutoStopController.kt    # 自动停止深模块（计时器/计数器/extend-to-end）
│   │   ├── AlarmStartupReconciler.kt # 开机/启动时重新注册闹钟（覆盖所有闹钟类型）
│   │   ├── AlarmIntegrityWorker.kt  # WorkManager 周期检查闹钟注册完整性（15分钟）
│   │   ├── NextOccurrenceCalculator.kt # 下次响铃时间计算（含节假日排除）
│   │   ├── AlarmScheduler.kt        # AlarmManager 封装
│   │   ├── Clock.kt                 # 时钟抽象（测试注入）
│   │   └── ports/                   # 闹钟端口（VolumeRampPort, WakeLockPort, NotificationPort, PermissionPort）
│   ├── playback/        # 播放深模块
│   │   ├── PlayerPort.kt            # 播放运行时接口（隔离 ExoPlayer）
│   │   ├── SessionCore.kt           # 两播放会话共享基类（状态归约/进度追踪/焦点/stop 序列；coreStartQueue 启动顺序契约 + stopDeferred 延迟停止）
│   │   ├── UserPlaybackSession.kt   # 用户播放会话（状态管理、音频焦点、进度追踪、通知状态驱动）
│   │   ├── AlarmPlaybackSession.kt  # 闹钟播放会话（逻辑独立，共享 ExoPlayer）
│   │   ├── PlaybackCoordinator.kt   # PlayerPort 事件与音频焦点回调路由器
│   │   ├── ForegroundNotificationController.kt # 前台通知状态协调器（随 Service 生命周期创建/销毁）
│   │   ├── ExoPlayerAdapter.kt      # PlayerPort 的 ExoPlayer 实现
│   │   ├── PlaybackProgressTracker.kt # 进度保存（5秒间隔）
│   │   ├── PlayerEvent.kt           # 播放器事件定义
│   │   ├── PlaybackTransition.kt    # 播放状态转换窄 seam（SongCompleted / QueueEnded / PlaybackStopped）
│   │   ├── AudioFocusPort.kt        # 音频焦点接口
│   │   ├── MusicNotificationPort.kt # 通知接口（纯 Kotlin，无 Android 类型泄漏）
│   │   └── ServiceStarter.kt        # Service 启动接口
│   ├── AndroidServiceStarter.kt     # ServiceStarter 的 Android 实现
│   ├── AlarmNotificationHelper.kt   # 闹钟通知 Android 渲染（实现 NotificationPort；双渠道路由：普通 + bypass DND，按 alarm.ignoreQuietMode 与勿扰权限状态选择）
│   ├── AlarmNotificationContent.kt  # 通知内容纯 Kotlin 描述 + ContentBuilder；按 `alarm.ringMode` 决定是否附加全屏意图
│   ├── AudioFocusManager.kt         # AudioFocusPort 实现
│   ├── AlarmVolumeController.kt     # VolumeRampPort 实现（渐强目标流随 ignoreQuietMode 在 STREAM_ALARM/STREAM_MUSIC 间切换）
│   ├── DndAccessPermissionHelper.kt # 勿扰权限（Notification Policy Access）检查 + 跳设置（object + @JvmStatic，可 mockStatic）
│   ├── MusicNotificationManager.kt  # MusicNotificationPort 实现
│   ├── NotificationResources.kt     # getString + NotFoundException fallback（共享工具）
│   ├── NotificationChannelFactory.kt # NotificationChannel 创建工厂（共享工具；bypassDnd 仅创建时生效，渠道永不 delete/recreate）
│   ├── MainActivityIntentFactory.kt # MainActivity contentIntent 工厂（共享工具）
│   ├── MusicPlaybackService.kt      # 前台 Service 薄壳（生命周期管理）
│   └── AlarmReceiver.kt             # 闹钟广播接收器（委托给 AlarmFireSession）
├── LocaleHelper.kt      # API < 33 locale context 包装 + SharedPreferences 持久化
├── ReleaseTree.kt       # Release 构建日志 Tree
└── MainActivity.kt
```

## 开发命令

> AI 可通过 CLI 运行 Gradle 命令进行编译验证和测试；Compose Preview 等可视化功能需在 Android Studio 中操作。

```bash
# 构建（在 Android Studio 终端中）
./gradlew assembleDebug
./gradlew assembleRelease

# 测试
./gradlew test                              # 单元测试
./gradlew connectedAndroidTest              # Instrumented 测试

# 清理
./gradlew clean
```

## 任务分工

> 详细分工说明见 `.claude/skills/bp-android-app/references/vscode-as-division.md`

### Tier 1: AI 全自主
- 编写/修改 `*.kt` 文件（ViewModel, Repository, Compose UI, 测试）
- 修改 `strings.xml`, `colors.xml` 等资源文件
- 运行 `./gradlew` 命令（compileDebugKotlin, test, assembleDebug 等）
- Git 操作（commit, push, branch）

### Tier 2: AI 执行 → 用户验证
- 修改 `build.gradle.kts` → `./gradlew compileDebugKotlin` 验证 → 用户确认
- 修改 `libs.versions.toml` 版本号 → 同上
- 修改 `settings.gradle.kts` → 同上
- 修改 `AndroidManifest.xml` → 同上
- 安装/运行应用到设备 → 用户确认效果

### Tier 3: Android Studio 专有（用户操作）
- Compose Preview 实时预览
- Layout Inspector 布局检查
- Profiler 性能分析
- 签名密钥管理（keystore 创建/修改）

### AI 禁止操作
- 不修改 `local.properties`

## 开发规范

详细规范见 `.claude/skills/bp-android-app/` 及其 `references/` 目录。关键要点：

- Compose UI：使用 `collectAsStateWithLifecycle()` 收集 Flow，列表用稳定 `key`
- ViewModel：`MutableStateFlow<UiState>` + `sealed interface Action` 模式
- Repository：返回 `Result<T>` 或 `Flow<T>`，不直接抛异常
- 本地存储：优先使用 DataStore 而非 SharedPreferences（仅 locale 偏好例外——`attachBaseContext` 需要同步读取，使用 SharedPreferences）
- <!-- 2026-05-27 --> **i18n 资源结构**：`values/strings.xml` = 英文默认；`values-zh-rCN/strings.xml` = 简体中文；`values-ja/strings.xml` = 日文。新增字符串必须同步更新全部三个文件。`LocaleHelper` 处理 API < 33 的 Context 包装，API 33+ 由系统 `localeConfig` 自动处理
- 测试：关键业务逻辑有单元测试，复杂 UI 有 UI 测试
- <!-- 2026-04-28 --> **Hilt 测试**：`app/src/test/java/.../di/TestDatabaseModule.kt` 提供 `@TestInstallIn` 内存数据库模块，供 `@HiltAndroidTest` 使用
- <!-- 2026-04-28 --> **Robolectric 资源兼容性**：通知/渠道构建中使用 `context.getString(R.string.xxx)` 时，必须包裹 try/catch 回退（Robolectric 可能抛出 `Resources$NotFoundException`）。回退逻辑统一封装在 `service/NotificationResources` 中，`AlarmNotificationHelper` 和 `MusicNotificationManager` 通过注入使用，自身不再写 try/catch
- <!-- 2026-04-29 --> **DataStore 测试**：DataStore 1.2.1 无 `createForTesting()`，测试需用 mock DataStore + MutableStateFlow 模拟偏好读写
- <!-- 2026-04-29 --> **ViewModel 测试**：统一使用 `UnconfinedTestDispatcher`（消除 `viewModelScope` 时序竞态）；仅 `debounce`/`delay` 场景用 `StandardTestDispatcher` + `advanceUntilIdle`
- <!-- 2026-04-29 --> **Robolectric Shadow 访问**：`ShadowAlarmManager.nextAlarmClock` 等 protected 成员在 Kotlin 中不可直接访问，需反射：`method.isAccessible = true`
- <!-- 2026-05-01 --> **PlayerPort 测试**：`AlarmFireSession` 和 `UserPlaybackSession` 的单元测试使用 Fake `PlayerPort` 实现（基于 `MutableSharedFlow` 回放事件），无需 Robolectric 即可测试播放编排逻辑
- <!-- 2026-06-25 --> **mock 测试栈**：使用 `mockito-core 5.23.0` + `mockito-kotlin 6.3.0`（testImplementation），所有 mock 测试放在 `app/src/test/`（Robolectric JVM）。`src/androidTest/` 仅保留 instrumented-only 测试（Room DAO、Hilt E2E），不使用 mockito-android（其要求 API 28+ 设备，与 minSdk 24 冲突）。suspend 函数用 `wheneverBlocking { } doReturn/doAnswer`，verify 用 `verifyBlocking(x) { ... }`。**mock Kotlin object singleton** 用 `mockStatic(X::class.java)`（依赖 mock-maker-inline SPI 配置，位于 `app/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`，仅 test classpath，不进入 release APK）—— **被 mock 的 object 方法必须加 `@JvmStatic`**（如 `FullScreenIntentPermissionHelper`、`AutostartHelper`），否则 mockStatic 在 Robolectric sandbox 下不拦截。**`mockStatic` 返回的 `MockedStatic` 必须显式 close**（try-with-resources 或 @After teardown），否则跨测试污染（Robolectric 共享 JVM 进程）。**mockito 严格要求 matcher/literal 一致**：`verify(m).foo(any(), 0)` 会抛 `InvalidUseOfMatchersException`，需用 `eq(0)`。详见 `_Project/superpowers/specs/20260625_0025_mockk-jvmti-migration-design.md`
- <!-- 2026-06-24 --> **Robolectric + Compose UI 测试基础设施**：`TestActivity`（`app/src/main/java/.../TestActivity.kt`，空 `ComponentActivity` 子类）+ `src/main/AndroidManifest.xml` 注册（`exported="false"`）+ `app/build.gradle.kts` 的 `testOptions.unitTests.isIncludeAndroidResources = true` 三件套；测试用 `@Config(sdk = [33], application = HiltTestApplication::class)`。<!-- 2026-09-19 --> 组合树内含 `hiltViewModel()` 的测试改用 `HiltTestActivity`（`@AndroidEntryPoint` 变体，同样 manifest 注册）+ `@HiltAndroidTest` + `HiltAndroidRule(order = 0)`；勿给 `TestActivity` 加 `@AndroidEntryPoint`（会波及所有未配 Hilt 的既有测试）。详见 `_Project/HOWTO.md` 的 "Robolectric + Compose UI 测试" 章节
- <!-- 2026-09-29 --> **Robolectric application 声明纪律**：所有 Robolectric 测试类必须显式声明 `@Config(application = HiltTestApplication::class)`（除非被测对象就是 MusicApplication 本身）。未声明时默认加载 MusicApplication，其 onCreate 副作用（AlarmStartupReconciler reconcile 协程、MediaStoreObserver 注册）在测试沙盒执行，是 2026-09-18 跨类 flake 的根因面（根因链见 `_Project/superpowers/plans/20260914_2210_alarm-edit-test-flakiness.md`）
- <!-- 2026-09-19 --> **冷启动冒烟守卫（强制）**：每个组合入口（Activity `setContent` 根、`AppNavigation` 等导航根）必须有 "renders without crashing" 级 Robolectric Compose 冒烟测试，新增 Screen 重组树结构、新增 CompositionLocal、改主题/资源引用时同步补齐（模板：`AppNavigationSmokeTest`、`AlarmRingSmokeTest`）。理由：组合期缺陷（CompositionLocal 未 provide、资源/主题断链）对编译器和传参单测不可见，只有真实组合+首帧测量才暴露——26.9.1 实机冷启动闪退（`91320c1` 引入，13 天后才在实机暴露）即为此类。发版/实机安装前在有模拟器/设备连接时跑 `./gradlew connectedDebugAndroidTest`——<!-- 2026-10-05 --> instrumented 冷启动冒烟 `MainActivityColdStartSmokeTest`（真实 MusicApplication 进程 + 真实 Hilt 图 + 首帧渲染断言）随既有 DAO instrumented 一同执行，兜底 OEM/API-level 特有路径；无设备环境至少保持 JVM 冒烟全绿，实机安装后首次启动人工确认一次

### 所需权限

| 权限 | 用途 |
|------|------|
| `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` (API <33) | 读取本地音乐文件 |
| `FOREGROUND_SERVICE` | 音乐播放前台服务 |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | API 34+ 前台服务类型 |
| `POST_NOTIFICATIONS` | API 33+ 通知权限 |
| `SCHEDULE_EXACT_ALARM` | 精确闹钟（API 31+ 需运行时授权） |
| `WAKE_LOCK` | 闹钟唤醒 |
| `RECEIVE_BOOT_COMPLETED` | 开机后重新注册闹钟 |
| `USE_FULL_SCREEN_INTENT` | 全屏响铃界面 |
| `ACCESS_NOTIFICATION_POLICY` | 勿扰访问（appop 级特殊权限：声明仅为前提，需用户在系统设置手动授予；`ignoreQuietMode` 闹钟的通知渠道穿透 DND 的前提） |
| `INTERNET` | 节假日数据在线获取 |
