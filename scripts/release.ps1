# 规范化发版脚本：校验版本号与 CHANGELOG → 跑全量单测 → 编译 Release APK → 生成 SHA-256 → 可选自动打 Git Tag
# 用法：
#   .\scripts\release.ps1             # 校验 + 单测 + 输出 dist\Subnook-vX.Y.Z.apk 与 SHA-256
#   .\scripts\release.ps1 -Tag        # 额外检查工作区干净并创建带注释的 Git Tag (vX.Y.Z)
param(
    [switch]$Tag
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path "$PSScriptRoot\..").Path
$gradleFile = "$root\Subnook\app\build.gradle.kts"
$changelogFile = "$root\CHANGELOG.md"

$gradleText = Get-Content $gradleFile -Raw -Encoding UTF8
if ($gradleText -notmatch 'versionName\s*=\s*"([^"]+)"') { throw "无法从 build.gradle.kts 读取 versionName" }
$ver = $Matches[1]
if ($gradleText -notmatch 'versionCode\s*=\s*(\d+)') { throw "无法从 build.gradle.kts 读取 versionCode" }
$code = $Matches[1]
$tagName = "v$ver"

Write-Host ">>> Release Target: $tagName (versionCode=$code)" -ForegroundColor Cyan

# 校验 CHANGELOG.md 是否记录了该版本
$changelogText = Get-Content $changelogFile -Raw -Encoding UTF8
if ($changelogText -notmatch "(?m)^##\s+$([regex]::Escape($ver))\b") {
    throw "CHANGELOG.md 中未找到 '## $ver' 变更记录，请先补充后再发版。"
}

if ($Tag) {
    $dirty = git -C $root status --porcelain
    if ($dirty) {
        throw "工作区有未提交改动，请先 git commit 后再使用 -Tag 打版。`n$dirty"
    }
    $existing = git -C $root tag -l $tagName
    if ($existing) { throw "Tag $tagName 已存在，请先递增版本号。" }
}

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\jlink.exe")) {
    if (Test-Path "C:\Android\jdk-21\bin\jlink.exe") { $env:JAVA_HOME = "C:\Android\jdk-21" }
}
if (-not $env:ANDROID_HOME -and (Test-Path "C:\Android\sdk")) { $env:ANDROID_HOME = "C:\Android\sdk" }
if (-not $env:GRADLE_USER_HOME -and (Test-Path "C:\Android")) { $env:GRADLE_USER_HOME = "C:\Android\.gradle" }
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

$drive = @("S:", "T:", "U:", "V:") | Where-Object { -not (Test-Path "$_\\") } | Select-Object -First 1
if (-not $drive) { throw "没有可用的临时映射盘符" }

subst $drive $root
try {
    Push-Location "$drive\Subnook"
    Write-Host ">>> Running unit tests & assembleRelease..." -ForegroundColor Cyan
    & .\gradlew.bat testDebugUnitTest assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location -ErrorAction SilentlyContinue
    subst $drive /D | Out-Null
}

$srcApk = "$root\Subnook\app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $srcApk)) { throw "未找到编译产物: $srcApk" }

$distDir = "$root\dist"
New-Item -ItemType Directory -Force $distDir | Out-Null
$outApk = "$distDir\Subnook-$tagName.apk"
Copy-Item $srcApk $outApk -Force
$hash = (Get-FileHash $outApk -Algorithm SHA256).Hash.ToLower()
"$hash  Subnook-$tagName.apk" | Set-Content "$distDir\Subnook-$tagName.sha256" -Encoding ASCII

Write-Host ">>> Release APK ready: $outApk" -ForegroundColor Green
Write-Host ">>> SHA-256: $hash" -ForegroundColor Green

if ($Tag) {
    git -C $root tag -a $tagName -m "Release $tagName (versionCode $code)"
    Write-Host ">>> Created annotated tag: $tagName" -ForegroundColor Green
    Write-Host ">>> Next step: git push origin main --follow-tags" -ForegroundColor Yellow
}
