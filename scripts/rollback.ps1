# 安全版本回退脚本（绝不破坏远端提交历史）
# 用法：
#   .\scripts\rollback.ps1 -List                             # 查看全部历史发布标签与提交
#   .\scripts\rollback.ps1 -Target v1.4.0 -Mode branch       # 基于目标版本创建独立回退/热修复分支
#   .\scripts\rollback.ps1 -Target v1.4.0 -Mode revert       # 生成一次干净的回退暂存（保留历史，可直接 commit & push）
param(
    [switch]$List,
    [string]$Target,
    [ValidateSet("branch", "revert")]
    [string]$Mode = "branch"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path "$PSScriptRoot\..").Path

if ($List -or [string]::IsNullOrWhiteSpace($Target)) {
    Write-Host "=== 已发布版本标签 (Git Tags) ===" -ForegroundColor Cyan
    $tags = git -C $root tag -l --sort=-v:refname --format="%(refname:short) | %(creatordate:short) | %(subject)"
    if ($tags) { $tags } else { Write-Host "（暂无版本标签，可运行 .\scripts\release.ps1 -Tag 创建）" -ForegroundColor Yellow }
    Write-Host "`n=== 最近 10 条提交 ===" -ForegroundColor Cyan
    git -C $root log --oneline --decorate -n 10
    exit 0
}

$dirty = git -C $root status --porcelain
if ($dirty) {
    throw "工作区有未提交改动，请先 commit 或 stash 后再执行回退。"
}

# 验证目标 ref 存在
git -C $root rev-parse --verify "$Target^{commit}" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "找不到目标版本或提交: $Target" }

if ($Mode -eq "branch") {
    $safeName = $Target -replace '[^a-zA-Z0-9._-]', '-'
    $branch = "rollback/$safeName-$(Get-Date -Format 'yyyyMMdd-HHmm')"
    git -C $root checkout -b $branch $Target
    Write-Host ">>> 已基于 $Target 切换到新分支: $branch（原 main 分支完好保留）" -ForegroundColor Green
} else {
    # 安全回退：将工作区内容恢复到 Target 状态并暂存，生成一条正向回退提交（无需 push --force）
    git -C $root checkout $Target -- .
    Write-Host ">>> 已将工作区文件恢复到 $Target 的状态并暂存。" -ForegroundColor Green
    Write-Host ">>> 请核对 git status / git diff --cached，确认无误后执行：" -ForegroundColor Yellow
    Write-Host "    git commit -m `"revert: rollback to $Target`"" -ForegroundColor Yellow
}
