# 用法（Git Bash）:  . scripts/env.sh
# 让 JDK / Android SDK / Gradle 以及它们的缓存、临时文件都放在仓库内的 toolchain/ 目录。
_here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export ROOT="$(cd "$_here/.." && pwd)"
_tc_unix="$ROOT/toolchain"
_tc_win="$(cygpath -m "$_tc_unix" 2>/dev/null || echo "$_tc_unix")"
export JAVA_HOME="$_tc_win/jdk21"
export ANDROID_HOME="$_tc_win/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME="$_tc_win/android-home"
export GRADLE_USER_HOME="$_tc_win/gradle-home"
export PATH="$_tc_unix/jdk21/bin:$_tc_unix/sdk/platform-tools:$_tc_unix/gradle-8.14.3/bin:$PATH"
# 临时文件也放在 toolchain 里
mkdir -p "$_tc_unix/tmp" "$_tc_unix/home"
export TMP="$_tc_win/tmp"
export TEMP="$TMP"
export TMPDIR="$_tc_unix/tmp"
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$_tc_win/tmp -Duser.home=$_tc_win/home -XX:-UsePerfData"
unset _here _tc_unix _tc_win
