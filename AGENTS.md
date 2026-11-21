# Subnook（icefirercom 个人定制版）工程规范与项目规则

## 1. 许可证与上游署名红线
- 本仓库基于上游开源项目 `capllen/Subnook`（`https://github.com/capllen/Subnook`）进行个人非商业定制。
- 必须完整保留根目录 [LICENSE](LICENSE) 中的 `PolyForm Noncommercial 1.0.0` 许可证全文及 `Required Notice: Copyright (c) 2026 capllen (https://github.com/capllen/Subnook)`。
- 严禁用于任何商业用途；App「关于」页与 `README.md` 必须保留上游归属说明与非商用声明。
- `Subnook/keystore.properties`、`Subnook/keystore/*.jks`、`Subnook/local.properties` 严禁提交到 Git。

## 2. 技术栈与架构约定
- **UI**：Kotlin + Jetpack Compose（Material 3）+ Navigation Compose + Glance AppWidget。
- **视觉体系**：固定采用「**暖纸墨绿 · 极简账本风**」（浅色米白 `#F5F2EB` + 深松石墨绿 `#165B4C` + 琥珀赤陶 `#C26D2D`；深色松烟夜 `#101412` + 玉石青绿 `#6BC4AD`）。禁止引入花哨蓝紫默认配色或无意义的多套换肤开关。
- **日期交互**：全项目所有日期选择统一使用纯中文、周一起始的 `ChineseDatePickerDialog`，不要使用受系统语言影响的原生英文 `DatePicker`。
- **数据层**：Room SQLite + DataStore Preferences + Kotlinx Serialization JSON。
  - 每次修改 Room 实体结构**必须**：递增 `@Database(version = ...)`、编写对应增量 `Migration`、保持 `exportSchema = true` 并将生成的 `Subnook/app/schemas/.../*.json` 纳入 Git 版本控制。
  - 新增备份/序列化字段**必须提供默认值**，并在 `BackupCompatTest` 补充旧版本备份向后兼容测试。
- **领域层**：核心计算逻辑放在 `domain/`（纯 Kotlin/JVM，不依赖 Android Framework），确保可直接运行快速 JVM 单元测试。

## 3. Windows 中文路径构建与测试规矩
- 本机工作区位于包含中文用户名的路径下，直接跑 JVM 单元测试可能因 Test Worker 编码触发 `ClassNotFoundException`。
- 运行单测或发版构建时，优先使用 [scripts/dev.ps1](scripts/dev.ps1) / [scripts/release.ps1](scripts/release.ps1)（内部自动通过临时 `subst` 映射到纯英文盘符并配置 `JAVA_HOME=C:\Android\jdk-21`、`ANDROID_HOME=C:\Android\sdk`、`GRADLE_USER_HOME=C:\Android\.gradle`）。

## 4. 版本管理与安全回退规范
- 版本号遵循语义化规则 `主版本.功能.修复`（如 `1.4.0`），修改版本必须同步更新 `Subnook/app/build.gradle.kts`（`versionCode` +1、`versionName`）与 `CHANGELOG.md`。
- 每次对外发版前必须通过全量 `testDebugUnitTest`，并通过 `scripts/release.ps1` 打带注释的 Git Tag（`vX.Y.Z`）。
- 线上或测试发现问题需要回退时，**严禁对已推送的 `main` 执行 `git reset --hard` + `push --force`**；统一使用 `scripts/rollback.ps1` 创建回退分支或生成干净的 `git revert` 提交。
