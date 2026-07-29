#!/usr/bin/env bash
# ============================================================
# MXbox 一键 APK 打包脚本 (Shell 入口 → 调用 Node.js 版本)
# 用法: ./BuildTools/build_apk.sh [flavor] [abi]
#   flavor: leanback (电视版) | mobile (手机版) | all (默认)
#   abi:    arm64_v8a (64位) | armeabi_v7a (32位) | all (默认)
# ============================================================

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# 检查 Node.js
if ! command -v node &> /dev/null; then
    echo "ERROR: 未找到 Node.js，请先安装 Node.js 18+"
    echo "  Linux:  sudo apt install -y nodejs"
    echo "  macOS:  brew install node"
    echo "  或访问: https://nodejs.org/"
    exit 1
fi

NODE_VERSION=$(node -v | sed 's/v//' | cut -d. -f1)
if [ "$NODE_VERSION" -lt 18 ]; then
    echo "ERROR: Node.js 版本过低 (当前: v$NODE_VERSION)，需要 18+"
    exit 1
fi

# 调用 Node.js 版本（零外部依赖，纯 Node 标准库）
cd "$SCRIPT_DIR"
exec node build_apk.js "$@"
