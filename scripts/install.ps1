# 编译并通过 adb 安装到已连接的手机。用法: .\scripts\install.ps1 [debug|release]
param([string]$type = "debug")
. "$PSScriptRoot\env.ps1"
& "$PSScriptRoot\build.ps1" $type
if (-not $?) { exit 1 }
$apk = "$PSScriptRoot\..\Subnook\app\build\outputs\apk\$type\app-$type.apk"
adb install -r $apk
$pkg = if ($type -eq "release") { "com.subnook.app" } else { "com.subnook.app.debug" }
adb shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
