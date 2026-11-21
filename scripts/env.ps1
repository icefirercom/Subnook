# 用法:  . .\scripts\env.ps1
# 让 JDK / Android SDK / Gradle 以及它们的缓存、临时文件都放在仓库内的 toolchain/ 目录（不写 C 盘）。
# toolchain/ 不在 git 里：没有它时请直接用 Android Studio，或自行安装 JDK 17+ / Android SDK 后用 Subnook\gradlew 编译。
$root = (Resolve-Path "$PSScriptRoot\..").Path
$tc = "$root\toolchain"
$env:JAVA_HOME = "$tc\jdk21"
$env:ANDROID_HOME = "$tc\sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = "$tc\android-home"
$env:GRADLE_USER_HOME = "$tc\gradle-home"
$env:Path = "$tc\jdk21\bin;$tc\sdk\platform-tools;$tc\gradle-8.14.3\bin;" + $env:Path
# 临时文件也放在 toolchain 里
New-Item -ItemType Directory -Force "$tc\tmp", "$tc\home" | Out-Null
$env:TMP = "$tc\tmp"
$env:TEMP = $env:TMP
$env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$($tc -replace '\\','/')/tmp -Duser.home=$($tc -replace '\\','/')/home -XX:-UsePerfData"
