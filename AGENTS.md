# Rezeener 开发指南（供后续开发与多 Agent 协作）

> 本文件面向继续开发本项目的人与 AI 代理。先读这里再动手。

## 项目概述

Rezeener 是基于 [Navic](https://github.com/ssalggnikool/Navic) 深度定制的 Android 音乐客户端，服务端为 Subsonic / OpenSubsonic 兼容协议（Navidrome、gonic、LMS 等）。

- **分支**：`main` = 产品主分支（所有功能在此开发）；`master` = 上游 Navic 同步基线（用于定期合并上游更新）
- **应用身份**：`applicationId = com.andy.rezeener`，应用名 Rezeener（debug 加 `.debug` 后缀、应用名 Rezeener (Dev)）
- **版本**：语义化 `versionName` + 递增 `versionCode`（见 `app/build.gradle.kts`）
- **协议**：GPL-3.0，衍生分发需保留上游署名

## 技术栈

Kotlin（100%）+ Jetpack Compose（Material 3 Expressive）+ media3/ExoPlayer 1.11.1（**源码级集成**，见下）+ Koin 4（DI）+ Room 3（KSP）+ Ktor Client + Coil 3 + multiplatform-settings + kotlinx-serialization/datetime。

**没有传统 View/XML 布局**（XML 仅 manifest、widget、图标资源）。

## 目录结构（`app/src/main/kotlin/paige/navic/`）

- `ui/screen/`：约 25 个屏幕包（library、albums、artists、songs、playlists、genres、radios、starred（最爱）、search、nowPlaying、lyrics、queue、folder、bookmark、stats、tunedin、settings、login 等）
- `ui/component/`：自研组件库（common/layout/sheet/dialog/snackbar/toolbar）
- `ui/navigation/`：Screen 路由（Navigation 3 自定义 backstack + BottomSheetScene）
- `domain/manager/`：单例管理器（SessionManager、DownloadManager、ConnectivityManager、CastManager、UpdateManager 等）
- `domain/repository/`：数据仓库（Subsonic API + Room 组合）
- `domain/model/`：领域模型 + 设置枚举
- `data/database/`：Room 双库（CacheDatabase 元数据 / DownloadDatabase 下载）+ entity/dao/mapper
- `exoplayer/`：播放服务（ExoPlaybackService）、ExoStateHolder、impl/（CrossfadeEngine 等）
- `di/`：Koin 模块（ManagerModule、RepositoryModule、ViewModelModule、DatabaseModule）
- `shared/`：播放控制层（AndroidMediaPlayerViewModel、MediaPlayer）
- `widget/`：Glance 桌面小部件

## 关键架构约定（改动前必读）

1. **SessionManager.api 动态读取**：所有仓库/管理器都通过 `sessionManager.api.xxx` 在调用时取客户端（主备 URL 切换后自动生效），**不要**长期持有 client 引用。SessionManager 还支持 `activeInstanceUrl` / `isUsingBackupUrl` 状态流与 `checkAndFailover()`。
2. **播放层是双腿结构**：`CrossfadeEngine`（leading/standby 两腿）+ `CrossfadeForwardingPlayer`（ForwardingSimpleBasePlayer 包装器）。MediaSession 终身绑定包装器，**不要对包装器 seek/换绑**；换腿在引擎内部完成。音量渐变用等功率 cos/sin（不要用线性，会有 -3dB 中点凹陷）。
3. **投屏用 `MediaSession.setPlayer()`**：CastManager 在本机 ExoPlayer 与 RemoteCastPlayer 之间切换，MediaController 无感知。
4. **设置项**：`PreferenceManager` 的 `var x by preference(default)` 委托，新键追加到文件末尾（JSON 复杂类型用 `jsonPreference`）。**不要改已有键**。
5. **状态流**：用 Kotlin 显式后备字段语法 `val x: StateFlow<T>\n\tfield = MutableStateFlow(...)`。
6. **数据库**：Room 双库均配 `fallbackToDestructiveMigration(true)`。**加表/改表必须同时写 Migration 并注册**（`addMigrations(...)`），否则升级即清库（教训见 `fix: preserve downloads across database upgrades`）。
7. **字符串**：`values/strings.xml`（英文 Tab 缩进）+ `values-zh-rCN` / `values-zh-rTW`（4 空格）。**新功能必须同时补三种语言**。
8. **BuildInfo 在 `paige.navic.generated` 包**（不是 paige.navic）。

## 界面与设置整合规范

以下规则适用于新页面和对既有页面的修改，避免按功能迭代时再次产生一套页面一套样式：

1. **组件复用**：竖向专辑/艺术家/歌单网格统一使用 `ArtGrid` / `ArtGridItem` 或其领域包装组件；`ArtCarouselItem` 仅用于横向轮播。歌曲列表优先复用公共 `SongRow`，场景差异通过参数或轻量包装实现，禁止复制一套仅样式不同的歌曲行。
2. **响应式尺寸**：媒体行默认填充可用宽度；网格列数必须服从手机 `gridSize` 或宽屏 `artGridItemSize`。新增页面不得使用固定 400dp 歌曲行、固定 150dp 网格卡片或无理由的固定两列布局。
3. **视觉与交互一致**：封面形状、字体、颜色、圆角和间距优先使用 `MaterialTheme`、`CoverArt` 与现有 defaults；同一实体的点击、长按、Sheet、星标、评分、下载和分享行为不因入口不同而改变。合理的详情页音轨序号等情境差异可以保留。
4. **视图模式**：支持网格/列表的资料库类页面使用 `ListViewMode` 和 `SortSheet` 的既有能力；特定页面的独立视图偏好只追加新键，不改写或复用会造成互相覆盖的已有键。
5. **设置唯一归属**：新增设置先归入设置首页的现有类别，同一偏好只保留一个设置入口；歌词相关选项统一进入独立“歌词”页，不再混入“当前播放”或“播放”。条件子项必须呈现真实依赖状态，并正确计算可见项的分段圆角。
6. **导航与文案**：新设置页同时注册路由和双栏 detail pane，验证手机返回与平板切换；新增或调整文案必须同步英文、简体中文、繁体中文，并让副标题准确反映页内能力。
7. **验收要求**：涉及媒体组件或设置信息架构的改动，至少验证手机与宽屏、网格与列表（若支持）、一种 Material 与一种非 Material 主题，以及相关偏好的重启持久化。

## 构建与环境

- 命令：`./gradlew :app:assembleDebug`（debug）/ `:app:assembleRelease`（签名 release）
- 详细环境说明见 [docs/BUILDING.md](docs/BUILDING.md)：JDK 21 工具链路径（`org.gradle.java.installations.paths`）、`platforms;android-37.0`、Gradle 腾讯镜像、阿里云 Maven 镜像 init 脚本（`GRADLE_USER_HOME/init.d/`）、androidx-media 子模块经 ghproxy 镜像取 tarball、FFmpeg 在 Windows 下跳过原生构建（`decoder_ffmpeg/build.gradle.kts` 有补丁，**不要回滚**）
- **不要修改**：`gradle/wrapper/gradle-wrapper.properties`（本机镜像补丁，不提交）、`androidx-media/`（media3 fork 源码目录，不是 git 仓库）
- 并行构建会抢 Gradle 锁并可能产生脏缓存，**不要并发跑多个 Gradle 构建**；出现莫名编译错误时先 `--stop` 再清 `app/build/kotlin/compileDebugKotlin`

## 发布流程（每次发版）

统一使用**标签触发 CI、草稿审核后发布**的流程，不再并行维护一套手动上传步骤：

1. `app/build.gradle.kts` 改 `versionName`/`versionCode`（递增），同步更新 `docs/DEVELOPMENT_LOG.md` 与本文件状态；新功能必须补齐英文/简中/繁中。
2. 顺序运行 `./gradlew :app:assembleDebug` 和 `./gradlew :app:assembleRelease`。Release 产物为 `app/build/outputs/apk/release/Rezeener.apk`（`keystore.properties` 或 CI 签名环境变量提供签名，**密钥库文件勿提交、务必备份**）。
3. 验证 APK 的 `applicationId`、版本号与签名：`apksigner verify --print-certs app/build/outputs/apk/release/Rezeener.apk`。
4. 按功能提交改动，**排除本机 `gradle/wrapper/gradle-wrapper.properties` 镜像补丁**；`git push Navic main`，创建 `v<versionName>` 标签并推送标签。
5. 标签触发 `.github/workflows/build.yml`：构建标准版与 F-Droid 版，产出 `Rezeener.apk` / `Rezeener.fdroid.apk`，并创建同标签的**草稿 Release**。Release 只附标准版 `Rezeener.apk`，F-Droid 版保留为 Actions artifact，避免应用内更新器误选变体。检查 Actions 与资产后，在草稿中整理中文更新日志并正式发布；不要重复手动上传同名资产。
6. 发布后确认 `api.github.com/repos/AndyNH4/Navic/releases/latest` 返回新版本且资产可下载。应用内更新检查读取该接口；只有非草稿、非预发布的 Release 才会成为 latest。

若 CI 不可用，唯一回退是复用第 2 步的本地签名 APK，在同一标签的草稿中上传 `Rezeener.apk`，再按第 5–6 步发布与验证。

## 多 Agent 协作建议

- **按层分工，避免文件交叉**：播放器引擎层（exoplayer/）/ 歌词管线（domain/parser、ui/screen/lyrics/）/ 数据库（data/database/）/ 设置页（ui/screen/settings/）/ 导航与 DI（di/、App.kt、Screen.kt）分别派给不同 agent
- **共享文件**（PreferenceManager、strings.xml、Screen.kt、App.kt、DI 模块、SongDao 等）尽量只让一个 agent 改；英文 strings 与中文 strings 可由不同 agent 处理
- 子代理只读参照其他仓库（如 substreamer）时，**不要修改参照仓库**
- 每个任务结束必须 `./gradlew :app:assembleDebug` 验证 + 中文词条补齐 + 按功能拆 commit（英文 conventional commits）
- 真机验证优先；无真机时至少做解析器/迁移等可本地自验的部分

## 当前功能状态（截至 v1.0.7）

另见：[开发日志](docs/DEVELOPMENT_LOG.md)（逐项改动结果与遗留风险）。

- **连接**：主备双 URL 故障转移、启动自动探测、服务器不可达自动离线/恢复、代理/自定义头/SSL；连接与同步设置集中管理
- **播放**：双腿 crossfade（等功率）、均衡器（按会话双挂）、ReplayGain、变速、睡眠定时、FFmpeg 扩展（Linux 构建时）
- **投屏**：Chromecast（setPlayer 热切换）
- **离线**：离线下载/全库下载、离线模糊搜索、收藏集 pin 自动下载新增歌曲、孤儿下载记录恢复
- **浏览**：专辑/艺术家/曲目/歌单/流派/电台/文件夹/最爱（分段+排序+滑动+下载全部）；最爱专辑/艺术家支持独立网格/列表模式并复用公共响应式组件
- **发现**：Tuned In 7 种混音、AudioMuse-AI 相似推荐（本地缓存、离线回退、已下载歌曲预取与缓存管理）
- **统计**：本地播放事件热力图、连续打卡、Top 榜
- **歌词**：逐字卡拉 OK（方/尖括号两种格式）、`[tr]` 双语（宽容配对 + 强制刷新）、逐字放大；独立歌词设置页管理来源、翻译和动态效果
- **其他**：书签续播（服务端）、语音搜索、响应式桌面小组件（正在播放/队列/快捷播放）、Android Auto、完整简繁中文
- **更新**：GitHub Releases 自动/手动检查 + 应用内下载安装

## 歌词格式标准

见 [docs/歌词格式标准.md](docs/歌词格式标准.md)：逐字用行内时间戳（方/尖括号），翻译行 = 同时间戳 + `[tr]` 前缀 + 可选 `[tr-lang]` 文件头。music-tag-web 直接按此写即可。
