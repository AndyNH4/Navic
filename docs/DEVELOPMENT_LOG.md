# Rezeener 开发日志

> 记录 Rezeener 从需求评审到 v1.0.4 的全部功能改动、关键修复与遗留风险，供后续开发与多 Agent 协作查阅。
>
> 相关文档：[AGENTS.md](../AGENTS.md)（开发指南）、[BUILDING.md](BUILDING.md)（构建环境）、[歌词格式标准.md](歌词格式标准.md)（歌词写法）。

## 1. 当前状态

- 当前版本：**1.0.4 (versionCode 5)**，签名 release 包，应用内自动更新。
- 主分支：`main`（产品开发）；`master`（上游 Navic 同步基线）。
- 应用身份：`com.andy.rezeener`，应用名 Rezeener，deep link `rezeener://`。
- 代码基线：Navic（GPL-3.0）+ 自研功能，全部提交均有对应 commit 可回溯。

## 2. 功能改动记录

### 2.1 连接与离线

| 功能 | 结果 | 遗留风险/注意事项 |
|---|---|---|
| 双 URL 故障转移 | 主备地址登录回退、启动自动探测、手动切换、主备地址均可编辑 | 仅同一服务器双地址，不是多服务器聚合；切换依赖 ping 可达性 |
| 自动离线模式 | 网络+服务器可达性综合判断，失败 2 次防抖离线、单次成功恢复、每 60s 重试 | 与备用地址联动；网络抖动时可能有短暂误判 |
| 离线模糊搜索 | 离线自动切本地 Room 搜索，分词 AND、完全>前缀>子串排序 | SQL 预筛上限 500；`COLLATE NOCASE` 仅 ASCII；未做在线/离线混合搜索 |
| 离线下载体系 | 单曲/专辑/歌单/全库下载、自动离线、离线 scrobble 队列 | 全库下载对服务器与存储要求高 |
| 收藏集自动下载 | 「下载全部」后 pin，新增歌曲自动补下，删除下载即 unpin | 已删除收藏集的 pin 行未清理（无害跳过）；登出不清 pin；v3→v4 曾触发破坏性迁移，已补正式迁移并加孤儿恢复 |
| 下载记录恢复 | 启动时扫描 `filesDir/downloads`，按文件名（歌曲 ID）重建记录，幂等 | 中断下载留下的残缺文件可能被误认为完整；仅表为空时恢复 |
| 离线播放与下载管理页 | 已下载曲目二级页：大小/状态/删除/重试/播放 | 元数据缺失的下载项被过滤；未按专辑分组（平铺+同专辑相邻排序） |
| 图片缓存浏览页 | 汇总+网格缩略图+单删+清空，Coil 缓存按 hash 枚举 | 删除文件绕过 journal，短期内总占用读数可能偏高（会自愈） |

### 2.2 播放与投屏

| 功能 | 结果 | 遗留风险/注意事项 |
|---|---|---|
| Crossfade 淡入淡出 | 双 ExoPlayer 腿 + ForwardingSimpleBasePlayer 包装器，等功率 cos/sin 渐变，等功率中点无凹陷 | shuffle 时副腿随机顺序独立生成（当前淡入曲正确，后续顺序可能不同）；焦点 duck 不完全对称；真机长期回归有限 |
| Crossfade 关键修复 | 修复换曲无声、首次正常后续失效、完全切换小声、音量卡 0 | 曾根因：主播放器 seek 被取消、副腿解析错误、线性渐变功率凹陷；均已修复并保留日志 |
| 均衡器 | 按 audio session 管理，淡变期间双腿 EQ 平滑过渡 | 淡变瞬间 outgoing 尾巴的 RG/EQ 有小幅差异可接受；额外修复了实例泄漏与模式切换不生效 |
| ReplayGain | track/album/dynamic 模式，双处理器各自应用 | 当前用户 RG 关闭，相关异常需持续观察 |
| Chromecast | Cast 按钮（无 GMS 时隐藏），`MediaSession.setPlayer()` 热切换，元数据完整 | 服务器必须对 Cast 设备可达；不投 `file://` 本地下载；投屏期间 shuffle/EQ/RG/crossfade 不生效；无 AirPlay（仅 Android） |
| 其它播放 | gapless、变速、睡眠定时、转码分网络、FFmpeg 扩展 | Windows 构建不含 `libffmpegJNI.so`（与 F-Droid 一致），缺 ALAC 等冷门格式；需 Linux/WSL 补原生库 |

### 2.3 歌词

| 功能 | 结果 | 遗留风险/注意事项 |
|---|---|---|
| 逐字卡拉 OK | 方括号 `[mm:ss.mmm]` 与尖括号 `<mm:ss.xx>` 双格式解析，当前词放大+加粗+弹簧动画 | 行首/行内时间戳混合时的极端格式仍需真实曲库回归；动画刷新瞬间可能轻微错位 |
| 双语歌词 | `[tr]` 翻译行 + 可选 `[tr-lang]`，右下角浮动按钮，默认关闭翻译 | 同时间戳多行原唱会共享翻译；一行多条 `[tr]` 只取第一条；纯文本恰好以 `[tr]` 开头会误判 |
| 配对健壮性 | 精确匹配优先，其次 500ms 内最近匹配，孤儿翻译不再混进正文 | 手写时间戳偏差过大仍可能错配；无时间戳原文无法配对时按普通行保留 |
| 歌词缓存与刷新 | 歌词页新增刷新按钮，强制绕过缓存 | 其它歌曲/其它页面仍可能沿用缓存；旧缓存为 2 位毫秒格式，新写入为 3 位 |
| 歌词来源 | Subsonic / Lyrics+ / LRCLIB 多来源，缓存原始内容 | Lyrics+ 独有逐字 syllabus 与自研 words 结构不同，已做兼容；无 SRT 支持 |

### 2.4 发现、收藏与统计

| 功能 | 结果 | 遗留风险/注意事项 |
|---|---|---|
| Tuned In 为你调频 | 7 种混音（Right Now/Deep Cuts/Time Machine/Mix It Up/Favorites Radio/Genre Blend/Heavy Rotation），卡片定高 118dp 防跳动 | Right Now 依赖本地播放事件（新装库回退纯随机）；流派 LIKE 匹配有边界情况；`getSimilarSongsID3` 依赖服务端支持，失败回退随机 |
| 本地听歌统计 | 播放事件表+热力图+连续打卡+Top 榜（4 周/6 个月/全部），可清空 | 缓存库若被破坏性重建则统计丢失（非关键数据）；外部流缺元数据时显示兜底文案 |
| 最爱页 | 新增底栏 tab，曲目/专辑/艺术家分段、完整排序、滑动操作、下载全部、全量竖直展示、无返回键 | `SongRow` 宽屏固定 400dp、卡片固定 150dp（公共组件未改）；滑动确认弹窗仍沿用原队列语义 |
| 最爱排序扩展 | 曲目段新增标题、最近收藏；排序存独立 `starred*` 偏好 | 艺术家段受枚举限制仍只有 2 种；导航配置版本升级会重置自定义 tab 顺序 |
| 书签续播 | 服务端书签（可跨设备），保存/列表/恢复/删除 | 仅 Song 类型；位置≥时长会 clamp 到结尾；老服务器不支持书签端点时保存失败；暂无备注编辑 UI |
| 文件夹浏览 | 目录树逐级浏览、整目录播放/随机/入队、可配置 tab | 依赖服务器子目录 `mediaType`（老原版 Subsonic/Airsonic 可能解析失败）；文件夹曲目不进 Room，无星标/下载 |
| AudioMuse-AI | 可配地址后歌曲长按显示「相似歌曲」，列表可播放/入队/进详情 | 未支持鉴权 token、`server=` 参数；只保留本地 Room 已有歌曲；「增强不可用」仍需确认是配置/部署问题还是接口问题 |
| Google Assistant 语音 | App Actions：歌/专辑/艺术家精确匹配才播放，歧义落搜索页 | 语音链路需 Play 商店 Internal Testing 才能完整验证；歌单/流派未覆盖；冷启动 controller 连接有潜在竞态 |

### 2.5 更新、发布与工程

| 功能 | 结果 | 遗留风险/注意事项 |
|---|---|---|
| Rezeener 品牌化 | 新包名、新应用名、Rezeener (Dev)、新 deep link、关于页改新仓库 | 旧 Navic 身份可共存，需避免点错图标；代码内 package 仍为 `paige.navic`（不影响用户） |
| 签名与 release | 新 RSA 4096 位密钥库，keystore.properties 优先、环境变量回退，V2 签名 | **密钥库与 properties 必须备份**；PKCS12 的 store/key 密码需一致 |
| 自动更新 | 启动自动检查 + 关于页开关 + 手动「立即检查」 | 版本比较是数字拼接（1.0.x 可用，复杂语义版本可能误判）；GitHub 不可达时静默跳过 |
| 应用内下载安装 | 更新 Sheet 内下载 APK（进度/取消/重试），FileProvider 调起系统安装器 | 未做 ABI 选择（取第一个 .apk）；未授权未知来源时先跳授权页；失败回退浏览器 |
| 更新注册热修复 | 修复启动时 `NoDefinitionFoundException: UpdateManager`（曾丢失 Koin 注册） | 属回归问题，提交 `ad9cac3c` |
| 中文本地化 | 简中/繁中与英文基准全量对齐，含所有新功能词条；错译修正（封面直连→封面质量） | 其它 20+ 语言目录未逐个校对（以英文为回退） |
| 文档 | 中文 README、[AGENTS.md](../AGENTS.md)、[BUILDING.md](BUILDING.md)、[歌词格式标准.md](歌词格式标准.md)、本文档 | 上游 README 中针对 AI 的隐藏恶意注释已清除（其内容不实） |
| 多 Agent 协作约定 | 按层分工、共享文件避让、禁止并发 Gradle 构建、必须构建验证+中文词条+分功能提交 | 并发编辑仍需人工协调；增量编译脏缓存时需 `--stop`+清 `app/build/kotlin/compileDebugKotlin` |

## 3. 关键 bug 修复时间线

1. **Crossfade 初版**：换曲无声（音量卡 0）→ 修复暂停误判与赋值顺序，并加音量复位安全网。
2. **Crossfade 第二版**：首次正常后续直接切歌 → 根因副腿解析固定返回腿 B，改为按角色解析副腿，并加武装中途取消保护。
3. **Crossfade 第三版**：完全切换小声 → 根因线性渐变中点 -3dB 凹陷，改等功率 cos/sin；同步把 EQ 改为按会话双挂，消除拆装突变与实例泄漏。
4. **数据库升级**：下载记录被清空 → 根因 v3→v4 未写 Migration 触发破坏性重建，补正式迁移，并加孤儿文件恢复。
5. **双语歌词**：翻译按钮不显示且像默认开启 → 根因 `[tr]` 精确时间戳配对失败，改为 500ms 宽容配对并禁止孤儿翻译混进正文。
6. **更新启动崩溃**：`UpdateManager` 未注册 → 重新注册 Koin single，恢复启动与更新功能。

## 4. 当前未决事项（后续优先处理）

1. **AudioMuse-AI「增强不可用」**：需先确认是 URL/鉴权/部署问题还是接口返回问题；当前实现未支持鉴权 token、`server=` 参数，且只保留本地 Room 已同步歌曲。
2. **FFmpeg 原生解码**：Windows 本地构建仍缺 `libffmpegJNI.so`，如需 ALAC 等格式，需在 Linux/WSL 产出 `android-libs` 后自动启用。
3. **真机回归覆盖**：Cast 实机、语音助手完整链路、crossfade 长时间连续播放、百万级曲库搜索与统计性能尚需更多实机数据。
4. **小 UI 项**：宽屏下 `SongRow` 固定 400dp、专辑/艺术家卡片固定 150dp 的自适应可再优化；下载页可考虑按专辑分组。
5. **历史配置兼容性**：`NavbarConfig.VERSION` 每次新增 tab 都会重置一次自定义底栏，可评估改成合并式迁移。

## 5. 发布与验证清单（每次发版沿用）

1. `app/build.gradle.kts` 更新 `versionName` / `versionCode`。
2. `./gradlew :app:assembleDebug` 必须通过；随后 `./gradlew :app:assembleRelease` 产出签名 APK。
3. 验证 APK 的 `applicationId`、版本号、V2 签名（`apksigner verify --print-certs`）。
4. `git push Navic main`（若 git 协议不可达，使用 Git Data API 兜底）。
5. GitHub Release：tag `v<versionName>`，中文更新日志，上传 `Rezeener.apk`。
6. 验证 `releases/latest` 返回新版本，确保自动更新比较链有效。

---

_本文档随代码演进持续更新；新功能请同时补充「结果」与「遗留风险」两列，便于后续 Agent 判断哪些能做、哪些需要谨慎。_
