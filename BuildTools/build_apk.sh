#!/usr/bin/env bash
# ============================================================
# MXbox 一键 APK 打包脚本
# 用法: ./BuildTools/build_apk.sh [flavor] [abi]
#   flavor: leanback (电视版) 或 mobile (手机版)，默认 all (两者)
#   abi: arm64_v8a (64位) 或 armeabi_v7a (32位)，默认 all (两者)
# 示例:
#   ./BuildTools/build_apk.sh leanback arm64_v8a    # 电视版 64位
#   ./BuildTools/build_apk.sh                        # 全部 4 个 APK
# ============================================================

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_DIR="$( cd "$SCRIPT_DIR/.." && pwd )"

FLAVOR="${1:-all}"
ABI="${2:-all}"

# ---------- 1. 设置环境变量 ----------
echo "==> [1/4] 设置构建环境变量"

export JAVA_HOME="$SCRIPT_DIR/jbr-21"
export ANDROID_HOME="$SCRIPT_DIR/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$SCRIPT_DIR/gradle-wrapper"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "    JAVA_HOME    = $JAVA_HOME"
echo "    ANDROID_HOME = $ANDROID_HOME"
echo "    GRADLE_USER_HOME = $GRADLE_USER_HOME"

if [ ! -f "$JAVA_HOME/bin/java" ]; then
    echo "ERROR: JBR-21 不存在，请确认 BuildTools 已完整解压"
    exit 1
fi
if [ ! -d "$ANDROID_HOME/platforms/android-37" ]; then
    echo "ERROR: Android SDK (android-37) 不存在，请确认 BuildTools 已完整解压"
    exit 1
fi

# ---------- 2. 写入 local.properties ----------
echo "==> [2/4] 配置 local.properties"
cat > "$PROJECT_DIR/local.properties" <<EOF
sdk.dir=$ANDROID_HOME
storeFile=$SCRIPT_DIR/config/release.keystore
keyAlias=release
storePassword=123456
keyPassword=123456
EOF
echo "    local.properties 已生成"

# ---------- 3. 写入 gradle.properties 构建配置 ----------
echo "==> [3/4] 配置 gradle.properties (不覆盖已有配置)"
# 如果用户已有 gradle.properties，我们只增补必要配置，并做备份
if [ -f "$PROJECT_DIR/gradle.properties.bak_bt" ]; then
    # 已经做过备份，直接恢复作为基底
    cp "$PROJECT_DIR/gradle.properties.bak_bt" "$PROJECT_DIR/gradle.properties"
else
    cp "$PROJECT_DIR/gradle.properties" "$PROJECT_DIR/gradle.properties.bak_bt" 2>/dev/null || true
fi

cat >> "$PROJECT_DIR/gradle.properties" <<EOF

# ---------- BuildTools 自动注入 (build_apk.sh) ----------
org.gradle.java.home=$JAVA_HOME
org.gradle.java.installations.auto-download=false
org.gradle.java.installations.auto-detect=false
org.gradle.java.installations.paths=$JAVA_HOME
# --------------------------------------------------------
EOF
echo "    gradle.properties 已注入 JDK 路径"

# ---------- 4. 组装 Gradle 任务并执行 ----------
echo "==> [4/4] 开始打包 APK (flavor=$FLAVOR, abi=$ABI)"

cd "$PROJECT_DIR"
chmod +x gradlew

TASKS=()
case "$FLAVOR" in
    leanback)
        case "$ABI" in
            arm64_v8a)   TASKS+=( ":app:assembleLeanbackArm64_v8aRelease" ) ;;
            armeabi_v7a) TASKS+=( ":app:assembleLeanbackArmeabi_v7aRelease" ) ;;
            all|*)
                TASKS+=( ":app:assembleLeanbackArm64_v8aRelease" )
                TASKS+=( ":app:assembleLeanbackArmeabi_v7aRelease" ) ;;
        esac ;;
    mobile)
        case "$ABI" in
            arm64_v8a)   TASKS+=( ":app:assembleMobileArm64_v8aRelease" ) ;;
            armeabi_v7a) TASKS+=( ":app:assembleMobileArmeabi_v7aRelease" ) ;;
            all|*)
                TASKS+=( ":app:assembleMobileArm64_v8aRelease" )
                TASKS+=( ":app:assembleMobileArmeabi_v7aRelease" ) ;;
        esac ;;
    all|*)
        TASKS+=( ":app:assembleLeanbackArm64_v8aRelease" )
        TASKS+=( ":app:assembleLeanbackArmeabi_v7aRelease" )
        TASKS+=( ":app:assembleMobileArm64_v8aRelease" )
        TASKS+=( ":app:assembleMobileArmeabi_v7aRelease" ) ;;
esac

echo "    执行任务: ${TASKS[*]}"
echo ""
./gradlew ${TASKS[*]} --no-daemon 2>&1
BUILD_RC=$?

# ---------- 5. 收尾：汇总产物 ----------
OUT_DIR="$SCRIPT_DIR/apk-out"
mkdir -p "$OUT_DIR"
APK_FOUND=0
echo ""
echo "==> 打包完成，汇总 APK 到 $OUT_DIR"
if [ -d "$PROJECT_DIR/Release/apk" ]; then
    cp -f "$PROJECT_DIR/Release/apk"/*.apk "$OUT_DIR/" 2>/dev/null || true
fi
cp -f "$PROJECT_DIR/app/build/outputs/apk"/*/*/release/*.apk "$OUT_DIR/" 2>/dev/null || true

for APK in "$OUT_DIR"/*.apk; do
    [ -e "$APK" ] || continue
    APK_FOUND=$((APK_FOUND + 1))
    SIZE=$(du -h "$APK" | cut -f1)
    echo "    - $(basename $APK)  ($SIZE)"
done

echo ""
if [ "$BUILD_RC" -eq 0 ] && [ "$APK_FOUND" -gt 0 ]; then
    echo "✅ 构建成功，共产出 $APK_FOUND 个 APK。"
else
    echo "❌ 构建失败或无 APK 生成，请查看上方日志。Exit Code: $BUILD_RC"
    exit 1
fi
