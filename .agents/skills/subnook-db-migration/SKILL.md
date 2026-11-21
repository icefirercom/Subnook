---
name: subnook-db-migration
description: >-
  Safely evolve Subnook Room database schema, write incremental SQLite migrations,
  export schema JSON snapshots, and verify backward compatibility with old JSON backups.
  Use whenever adding/altering Room entities, columns, or AppSettings fields.
---

# Subnook 数据库演进与备份兼容规范

## 强制检查清单
1. **实体定义（`data/Model.kt`）**：
   - Room 新增列必须带 `@ColumnInfo(defaultValue = "...")` 或可空类型。
   - `@Serializable` 新增字段必须赋予 Kotlin 默认值，确保旧版本 JSON 备份解码不失败。
2. **数据库迁移（`data/Database.kt`）**：
   - 递增 `@Database(..., version = N, exportSchema = true)`。
   - 新增 `MIGRATION_(N-1)_N` 并注册到 `AppDatabase.build()` 的 `.addMigrations(...)`。
   - SQL 默认值必须与 `@ColumnInfo(defaultValue = ...)` 严格一致，否则 Room 表结构校验会崩溃。
3. **Schema 审计**：
   - 执行构建后检查 `Subnook/app/schemas/com.subnook.app.data.AppDatabase/<N>.json` 已生成并随本次提交入库。
4. **备份兼容测试（`test/.../BackupCompatTest.kt`）**：
   - 验证旧版 JSON（`v120` / `v100`）导入后新字段为安全默认值。
   - 验证包含新字段的备份导出→导入往返完全一致。
5. **真机/模拟器热升级实测**：
   - 保留旧版数据直接 `adb install -r` 覆盖安装，确认进程正常拉起且 `logcat` 无 `SQLiteException` / `IllegalStateException`。
