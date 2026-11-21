# 编译 APK。用法: .\scripts\build.ps1 [debug|release]   (默认 debug)
param([string]$type = "debug")
. "$PSScriptRoot\env.ps1"
Push-Location "$PSScriptRoot\..\Subnook"
try {
    if ($type -eq "release") { gradle :app:assembleRelease --console=plain } else { gradle :app:assembleDebug --console=plain }
} finally { Pop-Location }
