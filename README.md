# Rezeener

一款现代化的 Subsonic / Navidrome 音乐流媒体客户端（Android），基于 [Navic](https://github.com/ssalggnikool/Navic) 深度定制。

[![下载最新版](https://img.shields.io/badge/下载-最新版本-blue)](https://github.com/AndyNH4/Navic/releases/latest)

## 功能特性

**界面与体验**
- Material 3 Expressive 设计，动态取色 + 5 种主题风格（含 Apple Music / Spotify 风格），AMOLED 纯黑
- 可自定义的底部导航（10 个栏目自由选择与排序），平板/横屏自适应布局
- 逐字卡拉 OK 歌词、歌词分享卡片、共享元素转场动画、桌面小部件
- 完整的简体中文 / 繁体中文

**播放**
- 无缝播放（Gapless）与**交叉淡化**（Crossfade，等功率渐变，均衡器平滑过渡）
- 内置均衡器、ReplayGain、转码/码率分网络设置、播放速度调节、睡眠定时
- **Chromecast 投屏**：一键投射，状态双向迁移
- Android Auto

**连接与离线**
- **双 URL 故障转移**：主备服务器地址（内网/公网）自动切换
- **自动离线模式**：服务器不可达自动切换离线，恢复后自动回来
- 离线下载（单曲/专辑/歌单/最爱/全库），**收藏集新增歌曲自动下载**（pin 语义）
- **离线模糊搜索**、离线下载与图片缓存的浏览管理页
- 代理、自定义请求头、自签名证书信任

**歌词**
- **逐字放大歌词**：方括号 `[mm:ss.mmm]` 与尖括号 `<mm:ss.xx>` 两种逐字时间戳格式
- **双语歌词**：`[tr]` 翻译行约定，歌词页一键切换双语显示
- 多歌词来源（Subsonic / Lyrics+ / LRCLIB），格式约定详见 [歌词格式标准](docs/歌词格式标准.md)

**发现与管理**
- **为你调频**：7 种智能混音（流派/年代/相似艺术家/收藏电台/本周热播等）
- **最爱页**：曲目/专辑/艺术家三段，完整排序（含最近收藏）与滑动操作
- **书签续播**：服务端书签，任意曲目断点续播（跨设备同步）
- **本地听歌统计**：热力图、连续打卡、Top 榜单
- AudioMuse-AI 声波相似推荐（可选）、Google Assistant 语音搜索播放
- 文件夹浏览、网络电台、歌单管理、分享链接

## 下载与更新

从 [Releases](https://github.com/AndyNH4/Navic/releases/latest) 下载 `Rezeener.apk` 安装。应用内置自动更新：新版本发布后，旧版本打开时会收到更新提示。

可与 Navic 官方版共存安装（包名 `com.andy.rezeener`，互不影响）。

## 自行构建

见 [docs/BUILDING.md](docs/BUILDING.md)（含国内网络环境的镜像配置说明）。

## 致谢与许可

- 基于 [Navic](https://github.com/ssalggnikool/Navic)（作者 paige）开发，感谢上游项目
- 使用 [androidx-media](https://github.com/Nightdavisao/android-media3)（media3 fork）
- 交叉淡化实现参考 [react-native-queue-player](https://github.com/ghenry22/substreamer)
- 协议：GPL-3.0（见 [LICENSE](LICENSE)）

---

<sub>Rezeener is a customized fork of Navic (GPL-3.0). All credit for the original app goes to its author.</sub>
