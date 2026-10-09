# 发布与安装说明

MediaShell（`com.aggregator.shell`）v1.6.0 的构建、签名、ABI 与安装清单。

## 构建产物

| 构建 | 命令 | 产物 | 说明 |
|------|------|------|------|
| debug | `/opt/gradle-8.9/bin/gradle :app:assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` | 不混淆，applicationId 加 `.debug` 后缀，约 25 MB |
| release | `/opt/gradle-8.9/bin/gradle :app:assembleRelease` | `app/build/outputs/apk/release/app-release.apk` | R8 混淆 + 资源收缩 + 签名，约 4.6 MB |

两个产物均被 `.gitignore` 排除（`**/build/`、`*.apk`），需本地编译。

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
/opt/gradle-8.9/bin/gradle :app:assembleRelease --console=plain --no-daemon
```

## 应用信息

| 项 | 值 |
|----|----|
| applicationId | `com.aggregator.shell` |
| versionCode / versionName | `7` / `1.6.0` |
| minSdk | 26（Android 8.0） |
| targetSdk / compileSdk | 35（Android 15） |
| 版本展示 label | MediaShell |

## ABI

- 支持：`arm64-v8a`、`armeabi-v7a`（在 `app/build.gradle.kts` 通过 `ndk { abiFilters }` 限定）
- x86 / x86_64 已被剔除，无法安装到 x86 模拟器
- 应用主体为纯 Kotlin/Compose，APK 内的 `.so` 仅来自依赖：
  - `libandroidx.graphics.path.so`（androidx.graphics）
  - `libdatastore_shared_counter.so`（androidx.datastore）

## 签名

release 构建的签名策略：

1. 仓库根存在 `keystore.properties` 时，使用其中的正式 keystore
2. 否则回退 debug keystore（`~/.android/debug.keystore`，别名 `androiddebugkey`，口令 `android`）

正式发布前在仓库根新建 `keystore.properties`（已被 `.gitignore` 排除，含密码严禁提交）：

```properties
storeFile=/absolute/path/to/release.jks
storePassword=****
keyAlias=****
keyPassword=****
```

验证签名：

```bash
/opt/android-sdk/build-tools/35.0.0/apksigner verify --verbose app-release.apk
/opt/android-sdk/build-tools/35.0.0/apksigner verify --print-certs app-release.apk
```

当前默认产物使用 debug 证书签名，采用 APK Signature Scheme v2。

## 安装

```bash
# 通过 adb 安装（-r 覆盖安装）
adb install -r app/build/outputs/apk/release/app-release.apk
```

安装后首次启动使用内置演示数据（离线兜底），在「设置 → 接口管理」导入真实源后生效。
关于应用不内置任何内容源 / 弹幕 / LLM 凭据的说明见 [SOURCE_CONFIG.md](./SOURCE_CONFIG.md)。

## 体积说明

release 相对 debug 的体积缩减来自：

- `isMinifyEnabled = true`：R8 代码混淆与无用代码移除
- `isShrinkResources = true`：未引用资源移除
- `ndk.abiFilters` 仅打包 arm64-v8a / armeabi-v7a

如需进一步压缩，可考虑 Android App Bundle（`bundleRelease`，由 Play 分发按 ABI 拆分）。
