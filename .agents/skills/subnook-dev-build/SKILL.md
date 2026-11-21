---
name: subnook-dev-build
description: >-
  Build, run unit tests, install Debug APK onto emulator/device, and verify runtime health
  for the Subnook Android project on Windows (handling JDK 21 and Chinese path workaround).
  Use when compiling, testing, or deploying changes locally.
---

# Subnook 本地开发构建与测试指南

## 前置环境
- JDK 21：`C:\Android\jdk-21`（必须含 `jlink.exe`，勿用 JDK 25 或精简 JBR）
- Android SDK：`C:\Android\sdk`（已装 `android-36`、`platform-tools`、`emulator`）
- Gradle 缓存：`C:\Android\.gradle`

## 标准执行步骤
1. 一键运行全部单元测试、编译 Debug APK 并安装到已连接的模拟器/手机：
   ```powershell
   .\scripts\dev.ps1
   ```
   - 仅跑单测：`.\scripts\dev.ps1 -TestOnly`
   - 跳过单测仅快速热装：`.\scripts\dev.ps1 -SkipTest`
2. 若需要核对数据库迁移或崩溃日志：
   ```powershell
   adb shell pidof com.subnook.app.debug
   adb logcat -d -t 60 *:E | Select-String -Pattern "subnook|AndroidRuntime|SQLite|Migration"
   ```
3. 启动本地模拟器（在用户交互终端执行）：
   ```powershell
   emulator -avd Subnook_API36
   ```

## 验证标准
- `testDebugUnitTest` 全部通过（零失败）
- `assembleDebug` 生成 `Subnook\app\build\outputs\apk\debug\app-debug.apk`
- 安装后主进程存活且 `logcat *:E` 无 `AndroidRuntime` / `Migration` 异常
