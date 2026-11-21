#!/usr/bin/env bash
# 抓取预设图标到 app/src/main/res/drawable-nodpi/preset_<key>.(png|jpg|webp)
# 顺序：Google Play 商店图标 -> App Store(中国区，找不到再查美区) 图标 -> 网站 favicon
# 注意：腾讯视频/芒果TV 的 App Store 图标常带活动文案，所以 presets.psv 里只留了域名，走 favicon
# 起点读书 / ABEMA 的图标同理：起点是手工裁掉 App Store 图标底部促销条后的版本，--force 重抓前先备份
# 华为云空间 / OPPO 云服务 / vivo 云服务是手工图标（华为官网 SVG 内嵌原图、OPPO 安装包、vivo 96px 网站图标超分），psv 里来源故意留空
# 用法: bash tools/fetch_icons.sh [--force]     已存在的图标默认跳过
set -u
cd "$(dirname "$0")/.."
OUT=app/src/main/res/drawable-nodpi
TMPD=../toolchain/tmp/icons
mkdir -p "$OUT" "$TMPD"
FORCE=${1:-}
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
ok=0; fail=0; failed=""

# 按文件头判断格式，合格则以正确扩展名放进 res，返回 0
place() { # $1 tmpfile $2 key
  local f=$1 key=$2 ext=""
  [ -f "$f" ] || return 1
  [ "$(wc -c <"$f")" -gt 1500 ] || { rm -f "$f"; return 1; }
  local magic; magic=$(head -c 12 "$f" | od -An -c | tr -d ' \n')
  case "$magic" in
    *PNG*) ext=png;;
    RIFF*WEBP*) ext=webp;;
    *377330377*) ext=jpg;;
    *) rm -f "$f"; return 1;;
  esac
  rm -f "$OUT/preset_$key".*
  mv "$f" "$OUT/preset_$key.$ext"
}

while IFS='|' read -r key name cat color price cur cnt unit method play itunes domain material; do
  [ -z "$key" ] && continue
  case "$key" in \#*|g_*) continue;; esac
  if [ -z "$FORCE" ] && ls "$OUT/preset_$key".* >/dev/null 2>&1; then ok=$((ok+1)); continue; fi
  got=""
  tmp="$TMPD/$key.img"
  if [ -n "$play" ]; then
    url=$(curl -s -m 25 -A "$UA" "https://play.google.com/store/apps/details?id=$play&hl=en" | grep -o 'og:image" content="[^"]*"' | head -1 | sed 's/.*content="//; s/"$//; s/=.*$//')
    if [ -n "$url" ]; then
      curl -s -m 25 -o "$tmp" "${url}=s256"
      place "$tmp" "$key" && got="play"
    fi
  fi
  if [ -z "$got" ] && [ -n "$itunes" ]; then
    case "$itunes" in
      id:*) # 按 bundleId 精确查找；依次试中国区、美区及其他常用地区
            for cc in cn us jp tw hk gb; do
              resp=$(curl -s -m 25 -G "https://itunes.apple.com/lookup" -d "bundleId=${itunes#id:}" -d country=$cc)
              printf "%s" "$resp" | grep -q artworkUrl512 && break
            done;;
      *)    # 可写成 "hk:My CMHK" 指定 App Store 地区；中日文逐字节百分号编码（Windows 下 curl 传中文参数会乱码）
            cc=us; term=$itunes
            case "$itunes" in [a-z][a-z]:*) cc=${itunes%%:*}; term=${itunes#*:};; esac
            enc=$(printf '%s' "$term" | od -An -tx1 | tr -d ' \n' | sed 's/../%&/g')
            resp=$(curl -s -m 25 "https://itunes.apple.com/search?term=$enc&entity=software&country=$cc&limit=1");;
    esac
    art=$(printf '%s' "$resp" | grep -o '"artworkUrl512":"[^"]*"' | head -1 | sed 's/.*:"//; s/"$//')
    track=$(printf '%s' "$resp" | grep -o '"trackName":"[^"]*"' | head -1 | sed 's/.*:"//; s/"$//')
    if [ -n "$art" ]; then
      art=$(printf '%s' "$art" | sed 's#/[0-9]*x[0-9]*bb\.[a-z]*$#/256x256bb.png#')
      curl -s -m 25 -o "$tmp" "$art"
      place "$tmp" "$key" && got="appstore: $track"
    fi
  fi
  if [ -z "$got" ] && [ -n "$domain" ]; then
    curl -s -m 25 -L -o "$tmp" "https://www.google.com/s2/favicons?domain=$domain&sz=256"
    place "$tmp" "$key" && got="favicon"
  fi
  if [ -n "$got" ]; then ok=$((ok+1)); echo "OK   $key ($got)"; else fail=$((fail+1)); failed="$failed $key"; echo "MISS $key"; fi
done < tools/presets.psv
rmdir "$TMPD" 2>/dev/null
echo "done: ok=$ok miss=$fail$([ -n "$failed" ] && echo " ->$failed")"
