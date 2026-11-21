# 一键本地开发测试脚本：自动处理中文路径 subst + JDK 21 + 单测 + 编 Debug APK + 热装到设备
# 用法：
#   .\scripts\dev.ps1              # 跑单测 + 编 Debug APK + 若连着模拟器/手机自动安装重启
#   .\scripts\dev.ps1 -TestOnly    # 仅运行全部 JVM 单元测试
#   .\scripts\dev.ps1 -SkipTest    # 跳过单测，仅快速编译并安装
param(
    [switch]$TestOnly,
    [switch]$SkipTest
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path "$PSScriptRoot\..").Path

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\jlink.exe")) {
    if (Test-Path "C:\Android\jdk-21\bin\jlink.exe") { $env:JAVA_HOME = "C:\Android\jdk-21" }
}
if (-not $env:ANDROID_HOME -and (Test-Path "C:\Android\sdk")) { $env:ANDROID_HOME = "C:\Android\sdk" }
if (-not $env:GRADLE_USER_HOME -and (Test-Path "C:\Android")) { $env:GRADLE_USER_HOME = "C:\Android\.gradle" }
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

# 挑一个空闲盘符临时映射，避免中文路径导致 Gradle Test Worker ClassNotFoundException
$drive = @("S:", "T:", "U:", "V:") | Where-Object { -not (Test-Path "$_\\") } | Select-Object -First 1
if (-not $drive) { throw "没有可用的临时映射盘符 (S:/T:/U:/V:)" }

subst $drive $root
try {
    Push-Location "$drive\Subnook"
    $tasks = @()
    if (-not $SkipTest) { $tasks += "testDebugUnitTest" }
    if (-not $TestOnly) { $tasks += "assembleDebug" }
    if ($tasks.Count -eq 0) { $tasks += "assembleDebug" }

    Write-Host ">>> Running Gradle: $($tasks -join ' ')" -ForegroundColor Cyan
    & .\gradlew.bat @tasks --console=plain
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    if (-not $TestOnly) {
        $apk = "$drive\Subnook\app\build\outputs\apk\debug\app-debug.apk"
        $adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
        if (Test-Path $adb) {
            $devs = (& $adb devices) -join "`n"
            if ($devs -match "device\b") {
                Write-Host ">>> Installing Debug APK to connected device..." -ForegroundColor Green
                & $adb install -r $apk
                & $adb shell am force-stop com.subnook.app.debug
                & $adb shell am start -n "com.subnook.app.debug/com.subnook.app.MainActivity"
            } else {
                Write-Host ">>> Build OK. No connected Android device/emulator found." -ForegroundColor Yellow
            }
        }
    }
} finally {
    Pop-Location -ErrorAction SilentlyContinue
    subst $drive /D | Out-Null
}
