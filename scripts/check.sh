#!/bin/sh
# 工程完整性自检 —— 不需要 JDK / Gradle / Android SDK 就能跑。
# 用途：接手本项目时先跑一遍，确认骨架没被改坏。
#
# 用法：sh scripts/check.sh

cd "$(dirname "$0")/.." || exit 1

fail=0
ok()   { echo "  [OK]   $1"; }
bad()  { echo "  [FAIL] $1"; fail=$((fail + 1)); }

echo "== 1. 关键文件 =="
for f in settings.gradle.kts build.gradle.kts gradle.properties \
         gradle/libs.versions.toml gradle/wrapper/gradle-wrapper.jar \
         gradle/wrapper/gradle-wrapper.properties gradlew; do
    [ -f "$f" ] && ok "$f" || bad "$f 缺失"
done

echo "== 2. Version Catalog 可解析 =="
if command -v python >/dev/null 2>&1; then
    python - <<'PY'
import sys
try:
    import tomllib
except ImportError:
    print("  [SKIP] Python < 3.11，跳过 TOML 解析")
    sys.exit(0)
d = tomllib.load(open('gradle/libs.versions.toml', 'rb'))
print(f"  [OK]   versions={len(d.get('versions',{}))} libraries={len(d.get('libraries',{}))} plugins={len(d.get('plugins',{}))}")
PY
else
    echo "  [SKIP] 未找到 python"
fi

echo "== 3. include 的模块目录都存在 =="
python - <<'PY' 2>/dev/null || echo "  [SKIP] 无 python，跳过"
import re, os
txt = open('settings.gradle.kts', encoding='utf-8').read()
mods = re.findall(r'include\(":([^"]+)"\)', txt)
bad = []
for m in mods:
    p = m.replace(':', '/')
    if not os.path.isdir(p):
        bad.append(p)
print(f"  [OK]   {len(mods)} 个模块" if not bad else f"  [FAIL] 目录缺失: {bad}")
PY

echo "== 4. 每个模块都有 build.gradle.kts 与源码 =="
for d in app core/* feature/*; do
    [ -d "$d" ] || continue
    [ -f "$d/build.gradle.kts" ] || bad "$d 缺 build.gradle.kts"
    if [ -z "$(find "$d/src" -name '*.kt' 2>/dev/null)" ]; then
        bad "$d 没有 Kotlin 源码"
    fi
done
[ "$fail" -eq 0 ] && ok "全部模块结构完整"

echo "== 5. 禁止项检查 =="
# 只看非注释行，避免把说明性注释判成违规
if cat build.gradle.kts */build.gradle.kts */*/build.gradle.kts 2>/dev/null \
   | grep -v '^[[:space:]]*//' \
   | grep -q 'org\.jetbrains\.kotlin\.android'; then
    bad "检测到 kotlin.android 插件——AGP 9 内置 Kotlin，不允许应用"
else
    ok "未误用 kotlin.android 插件"
fi

if grep -rn 'usesCleartextTraffic="true"' app/src/main/AndroidManifest.xml >/dev/null 2>&1; then
    bad "Manifest 开启了全局明文流量"
else
    ok "未开启全局明文流量"
fi

echo "== 6. UI 代码状态 =="
ui=$(find feature -path '*/ui/*' -name '*.kt' 2>/dev/null | wc -l)
if [ "$ui" -gt 0 ]; then
    echo "  [WARN] feature 下已有 $ui 个 ui 文件——确认 docs/06 的 Q1-Q4 是否已定稿"
else
    ok "feature 下无界面代码（符合 UI 未定状态）"
fi

echo
if [ "$fail" -eq 0 ]; then
    echo "自检通过。下一步：按 BUILD-NOTES.md 跑 ./gradlew :app:assembleDebug"
    exit 0
else
    echo "自检失败 $fail 项，先修再继续。"
    exit 1
fi
