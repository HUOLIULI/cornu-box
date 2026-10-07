# 开发者指南

## 环境
- JDK 17、Android SDK 35、build-tools 35.0.0
- Gradle 8.9（本项目已内置 Gradle 发行版路径 `/opt/gradle-8.9`；仓库未提交 wrapper jar，`gradle-wrapper.properties` 已生成）

## 构建
```
export ANDROID_HOME=/opt/android-sdk
/opt/gradle-8.9/bin/gradle :app:assembleDebug --no-daemon
```
产物：`app/build/outputs/apk/debug/app-debug.apk`

## 运行单测
```
/opt/gradle-8.9/bin/gradle :core:source:testDebugUnitTest --no-daemon
```

## 启用 Python 源（可选）
在 `gradle.properties` 设 `enableChaquopy=true`，并在 `:core:source` 引入 Chaquopy 插件与实现。默认关闭，保证壳子 APK 可无 native 构建。
