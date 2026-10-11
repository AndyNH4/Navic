# Rezeener 构建环境说明（Windows / 国内网络）

Navic 在本机编译需要以下一次性环境配置（2026-10 验证通过，`./gradlew :app:assembleDebug` 全量约 10 分钟）。

## 1. Gradle 发行版镜像

GitHub 直连不可用时，`services.gradle.org` 会跳转到 GitHub 导致 wrapper 下载失败。

- 已改（**本地改动，勿提交**）：`Navic/gradle/wrapper/gradle-wrapper.properties`
  `distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-9.7.1-bin.zip`

## 2. JDK 21 工具链

项目要求 Java 21 工具链（Gradle 守护进程本身跑在 Java 26 没问题）。

- 已安装 Temurin JDK 21 到 `C:\dev\jdks\jdk-21.0.12.1+1`（TUNA 镜像下载）
- 已写入 `E:\GradleUserHome\gradle.properties`（注意：本机 GRADLE_USER_HOME 是 `E:\GradleUserHome`，不是 `~/.gradle`）：
  `org.gradle.java.installations.paths=C:/dev/jdks/jdk-21.0.12.1+1`

## 3. Maven 阿里云镜像

- 已建全局 init 脚本 `E:\GradleUserHome\init.d\aliyun-mirrors.gradle`：把阿里云 google/central/gradle-plugin/public 镜像插到所有仓库列表最前（含 included build），原仓库保留为回退。

## 4. Android SDK

Navic 的 `compileSdk = 37`，对应包名是 `platforms;android-37.0`（新命名）。已通过 sdkmanager 安装：

- `platforms;android-37.0`、`build-tools;37.0.0`（另已有 35/36、NDK 28.2.13676358、CMake 3.22.1）

**如果 AGP 日志反复刷 "Still waiting for package manifests to be fetched remotely"**：就是缺 SDK 组件在尝试后台下载，用 sdkmanager 手动装即可。

## 5. androidx-media 子模块（media3 fork）

GitHub 不可达时的获取方式（当前工作区已就位）：

```bash
# 按 .gitmodules 锁定的 SHA 下载源码包（ghproxy 镜像）
SHA=f91dcba7fcad0cbf36a06e0d66aa13aa2856a6d2
curl -sL -o /tmp/media3.tar.gz "https://ghproxy.net/https://github.com/Nightdavisao/android-media3/archive/$SHA.tar.gz"
tar -xzf /tmp/media3.tar.gz -C Navic/androidx-media --strip-components=1
```

注意 `git submodule` 的名字是 `media3`（不是路径名 androidx-media）；直接用 git 克隆需先 `git config submodule.media3.url <镜像URL>`。

## 6. FFmpeg 原生解码的说明

- `androidx-media/libraries/decoder_ffmpeg/build.gradle.kts` 已打补丁：只有存在 FFmpeg 源码或预编译库时才走原生构建（官方脚本不支持 Windows）。
- 当前本地构建**不含** `libffmpegJNI.so`——与 F-Droid 官方包行为一致，`FfmpegLibrary.isAvailable()` 运行时返回 false，常规格式（含 FLAC）走 MediaCodec 不受影响，仅缺 ALAC 等冷门格式。
- 如需补回：在 Linux/WSL 环境准备 ffmpeg 源码后按原脚本构建，输出到 `src/main/jni/ffmpeg/android-libs` 即可自动启用。

## 7. 构建与安装

```bash
cd Navic && ./gradlew :app:assembleDebug
# APK: Navic/app/build/outputs/apk/debug/Navic.apk
adb install -r Navic/app/build/outputs/apk/debug/Navic.apk
```

## 8. Release 构建与签名（Rezeener 1.0.0）

- 应用身份：`applicationId = com.andy.rezeener`（与 Navic 官方版可共存安装），版本 1.0.0 (versionCode 1)，应用名 Rezeener（debug 为 Rezeener (Dev)），deep link scheme `rezeener://`
- **签名密钥库**：`Navic/rezeener-release.keystore`（4096 位 RSA，有效期 30 年），凭证在 `Navic/keystore.properties`——两个文件都已 gitignore，**务必自行备份到安全位置**，丢失将无法发布同一应用的更新
- 构建（release 默认 R8 全量混淆 + 资源压缩，首次较慢）：

```bash
cd Navic && ./gradlew :app:assembleRelease
# 签名 APK: Navic/app/build/outputs/apk/release/Navic.apk
```

- 签名配置优先读 `keystore.properties`，也兼容 CI 环境变量（`SIGNING_STORE_FILE` / `SIGNING_STORE_PASSWORD` / `SIGNING_KEY_ALIAS` / `SIGNING_KEY_PASSWORD`）
- 验证签名：`"E:\Android\Sdk\build-tools\37.0.0\apksigner.bat" verify --print-certs app/build/outputs/apk/release/Navic.apk`
