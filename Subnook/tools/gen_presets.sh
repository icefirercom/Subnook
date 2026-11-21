#!/usr/bin/env bash
# 由 tools/presets.psv 生成 Presets.kt
cd "$(dirname "$0")/.."
OUT=app/src/main/java/com/subnook/app/data/PresetCatalog.kt
{
echo "// 自动生成：bash tools/gen_presets.sh（数据源 tools/presets.psv），请勿手改"
echo "package com.subnook.app.data"
echo
echo "object PresetCatalog {"
echo "    val all: List<Preset> = listOf("
grep -v '^#' tools/presets.psv | grep -v '^$' | awk -F'|' -f tools/preset_line.awk
echo "    )"
echo "}"
} > "$OUT"
echo "generated $(grep -c 'Preset(' "$OUT") presets"
