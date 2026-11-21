# 规范化发版脚本：校验版本号/CHANGELOG/正式签名配置 → 跑全量单测 → 编译 Release APK → apksigner 防呆验签 → 生成 SHA-256 → 可选自动打 Git Tag
# 用法：
#   .\scripts\release.ps1                 # 校验 + 单测 + 正式签名 APK + 验签防呆
#   .\scripts\release.ps1 -Tag            # 额外检查工作区干净并创建带注释的 Git Tag (vX.Y.Z)
#   .\scripts\release.ps1 -AllowDebugSign # 仅本地调试用：允许回退 Debug 签名
param(
    [switch]$Tag,
    [switch]$AllowDebugSign
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path "$PSScriptRoot\..").Path
$gradleFile = "$root\Subnook\app\build.gradle.kts"
$changelogFile = "$root\CHANGELOG.md"
$propsFile = "$root\Subnook\keystore.properties"

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

# 防呆校验 1：构建前确认正式签名配置与密钥库文件存在（避免静默回退 Debug 签名）
if (-not $AllowDebugSign) {
    if (-not (Test-Path $propsFile)) {
        throw "禁止发布：未找到 $propsFile。请先配置正式签名信息（或仅调试时加 -AllowDebugSign）。"
    }
    $props = @{}
    foreach ($line in (Get-Content $propsFile -Encoding UTF8)) {
        $trim = $line.Trim()
        if ($trim -eq "" -or $trim.StartsWith("#")) { continue }
        $idx = $trim.IndexOf("=")
        if ($idx -gt 0) {
            $k = $trim.Substring(0, $idx).Trim()
            $v = $trim.Substring($idx + 1).Trim()
            $props[$k] = $v
        }
    }
    $storeRel = if ($props.ContainsKey("storeFile") -and $props["storeFile"]) { $props["storeFile"] } else { "keystore/release.jks" }
    $storePath = if ([System.IO.Path]::IsPathRooted($storeRel)) { $storeRel } else { Join-Path "$root\Subnook" $storeRel }
    if (-not (Test-Path $storePath)) {
        throw "禁止发布：签名密钥库不存在：$storePath（请确认文件放在 Subnook\keystore\ 下而非仓库根目录）。"
    }
    if ($props["storePassword"] -eq "your-store-password" -or $props["keyPassword"] -eq "your-key-password") {
        throw "禁止发布：Subnook\keystore.properties 中仍包含占位密码，请填写真实签名密码。"
    }
    Write-Host ">>> Signing keystore verified: $storePath (alias=$($props['keyAlias']))" -ForegroundColor DarkCyan
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

# 防呆校验 2：用 apksigner 核验 APK 证书，严禁误发 Android Debug 测试签名包
$apksignerJar = Get-ChildItem "$env:ANDROID_HOME\build-tools\*\lib\apksigner.jar" -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
if ($apksignerJar) {
    Write-Host ">>> Verifying APK signing certificate..." -ForegroundColor Cyan
    $certOut = & "$env:JAVA_HOME\bin\java.exe" -jar $apksignerJar.FullName verify --print-certs $outApk 2>&1
    if ($LASTEXITCODE -ne 0) {
        Remove-Item $outApk -Force -ErrorAction SilentlyContinue
        throw "APK 签名校验失败：`n$($certOut -join "`n")"
    }
    $certText = $certOut -join "`n"
    if (-not $AllowDebugSign -and $certText -match "CN=Android Debug") {
        Remove-Item $outApk -Force -ErrorAction SilentlyContinue
        throw "禁止发布：检测到 APK 使用的是 Android Debug 测试签名！已自动清理产物，请检查 Subnook\keystore.properties 与密钥库路径。`n$certText"
    }
    $dnLine = ($certOut | Select-String "Signer #1 certificate DN:").Line
    $shaLine = ($certOut | Select-String "Signer #1 certificate SHA-256 digest:").Line
    if ($dnLine) { Write-Host ">>> $dnLine" -ForegroundColor Green }
    if ($shaLine) { Write-Host ">>> $shaLine" -ForegroundColor DarkGreen }
} elseif (-not $AllowDebugSign) {
    throw "未找到 Android build-tools 的 apksigner.jar，无法执行签名防呆校验。"
}

$hash = (Get-FileHash $outApk -Algorithm SHA256).Hash.ToLower()
"$hash  Subnook-$tagName.apk" | Set-Content "$distDir\Subnook-$tagName.sha256" -Encoding ASCII

Write-Host ">>> Release APK ready: $outApk" -ForegroundColor Green
Write-Host ">>> SHA-256: $hash" -ForegroundColor Green

if ($Tag) {
    git -C $root tag -a $tagName -m "Release $tagName (versionCode $code)"
    Write-Host ">>> Created annotated tag: $tagName" -ForegroundColor Green
    Write-Host ">>> Next step: git push origin main --follow-tags" -ForegroundColor Yellow
}
