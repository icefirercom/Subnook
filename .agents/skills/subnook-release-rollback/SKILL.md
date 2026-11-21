---
name: subnook-release-rollback
description: >-
  Manage semantic versioning, CHANGELOG updates, signed Release APK builds, Git annotated tags,
  GitHub Releases publishing, and safe rollback procedures for Subnook.
  Use when preparing a release, tagging a milestone, or rolling back a faulty release.
---

# Subnook 版本发布与安全回退手册

## 发版流程（Release）
1. 递增 `Subnook/app/build.gradle.kts` 中的 `versionCode`（+1）和 `versionName`（`主.功能.修复`）。
2. 在仓库根目录 `CHANGELOG.md` 顶部补充对应 `## X.Y.Z` 变更记录。
3. 确保 `Subnook/keystore.properties` 及 `keystore/release.jks` 已配置（不提交到 Git）。
4. 运行发布脚本（自动执行单测 → 编 Release APK → 生成 SHA-256 校验码，加 `-Tag` 自动打 Git Tag）：
   ```powershell
   .\scripts\release.ps1 -Tag
   ```
5. 推送主分支与标签（触发 `.github/workflows/release.yml` 自动云端发版，或手动将 `dist/Subnook-vX.Y.Z.apk` 上传至 GitHub Releases）：
   ```powershell
   git push origin main --follow-tags
   ```

## 安全回退流程（Rollback）
1. 列出所有历史版本标签：
   ```powershell
   .\scripts\rollback.ps1 -List
   ```
2. **推荐方式 A（安全撤销提交，不改写远端历史）**：生成一次回退到目标 Tag 的提交，检查 `git diff` 无误后 commit & push：
   ```powershell
   .\scripts\rollback.ps1 -Target v1.4.0 -Mode revert
   ```
3. **方式 B（基于历史版本拉取热修复分支）**：
   ```powershell
   .\scripts\rollback.ps1 -Target v1.4.0 -Mode branch
   ```
4. **App 内用户数据回退**：若升级或导入备份出现数据异常，可在 App「设置 → 备份 → 本地回退快照」一键恢复到升级前/导入前自动保存的本地快照。
