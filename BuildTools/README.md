# BuildTools — MXbox 一键 APK 打包工具包

本目录包含 MXbox 打包所需的 **完整预编译环境**，解压后无需额外下载即可一键打包 APK。

> 打包工具已升级为 **Node.js 版本**（带彩色进度条），仅依赖 Node.js 18+ 标准库，**零外部依赖**。

## 目录内容（首次使用前请解压所有 `.tar.gz` 分包）

| 路径 | 说明 | 大小 |
|------|------|------|
| `jbr-21/` | JetBrains JDK 21（AGP 9.2.1 强制要求的 toolchain） | ~800 MB |
| `android-sdk/` | Android SDK：<br>  - `platforms/android-37/` compileSDK 37<br>  - `build-tools/37.0.0/` build-tools<br>  - `cmdline-tools/latest/` sdkmanager 等命令行工具 | ~630 MB |
| `gradle-wrapper/dists/gradle-9.6.1-bin/` | Gradle 9.6.1 分发包（解包即可用） | ~290 MB |
| `gradle-wrapper/caches/` | Gradle 依赖缓存（含 AAR/JAR，首次解压后可避免重复下载） | ~1 MB+（完成打包后显著增大） |
| `config/release.keystore` | 发布签名密钥<br>  alias: `release` / 密码: `123456` | — |
| `apk-out/` | 构建产出的 APK 将复制到这里 | — |
| `build_apk.js` | **Node.js 打包工具**（核心实现，带进度条） ← 主要使用入口 | — |
| `build_apk.sh` | Shell 入口（自动检测 Node.js 后调用 `build_apk.js`） | — |
| `package.json` | npm 脚本定义（提供快捷构建命令） | — |
| `README.md` | 本说明文档 | — |

---

## 使用方法

### 前置条件
1. 操作系统：Linux / macOS（Windows 可用 WSL2）
2. **Node.js 18+**（打包工具基于 Node.js 标准库，无需 `npm install`）
3. `BuildTools` 文件夹必须在 MXbox 项目根目录下，目录结构如下：
   ```
   MXbox/
   ├── BuildTools/        ← 本目录（已解压）
   │   ├── build_apk.js    ← Node.js 打包工具（核心）
   │   ├── build_apk.sh    ← Shell 入口
   │   ├── package.json    ← npm 脚本
   │   ├── jbr-21/
   │   ├── android-sdk/
   │   ├── gradle-wrapper/
   │   └── config/release.keystore
   ├── app/
   ├── gradle/
   ├── gradlew
   ├── build.gradle
   └── README.md
   ```

### 一键打包 4 个 APK（推荐）
```bash
cd MXbox
chmod +x BuildTools/build_apk.sh
./BuildTools/build_apk.sh
```
产出（自动复制到 `BuildTools/apk-out/`）：
- `MXbox-leanback-arm64_v8a.apk` — 电视版 64 位
- `MXbox-leanback-armeabi_v7a.apk` — 电视版 32 位
- `MXbox-mobile-arm64_v8a.apk` — 手机版 64 位
- `MXbox-mobile-armeabi_v7a.apk` — 手机版 32 位

### 使用 Node.js 直接调用
```bash
cd MXbox/BuildTools
node build_apk.js                    # 全部 4 个 APK
node build_apk.js leanback arm64_v8a # 电视版 64 位
```

### 使用 npm 脚本（快捷命令）
```bash
cd MXbox/BuildTools
npm run build:tv64      # 电视版 64 位
npm run build:tv32      # 电视版 32 位
npm run build:tv        # 电视版 32+64
npm run build:mobile64  # 手机版 64 位
npm run build:mobile32  # 手机版 32 位
npm run build:mobile    # 手机版 32+64
npm run build:all       # 全部 4 个
```

### 选择性打包
```bash
# 只打包电视版 64 位
./BuildTools/build_apk.sh leanback arm64_v8a

# 只打包电视版（32+64）
./BuildTools/build_apk.sh leanback all

# 只打包手机版 32 位
./BuildTools/build_apk.sh mobile armeabi_v7a
```

### 参数说明
| 参数 1 (flavor) | 参数 2 (abi) | 说明 |
|-----------------|--------------|------|
| `all`（默认）   | `all`（默认）| 4 个 APK 全部打包 |
| `leanback`      | `arm64_v8a` / `armeabi_v7a` / `all` | 电视版 |
| `mobile`        | `arm64_v8a` / `armeabi_v7a` / `all` | 手机版 |

---

## 脚本做了什么？

1. **环境检查**：验证 JBR 21、Android SDK、Build Tools、签名密钥、Gradle Wrapper 是否就位（缺失则提前报错）。
2. **配置写入**：写入项目根目录的 `local.properties`（sdk.dir、签名密钥路径与密码）；在 `gradle.properties` 尾部注入 `org.gradle.java.home`，并禁用自动下载 toolchain（避免联网下载 JBR）。
3. **设置环境变量**：`JAVA_HOME`、`ANDROID_HOME`、`GRADLE_USER_HOME` 全部指向 `BuildTools/` 内部的预下载文件，**完全不依赖全局环境**。
4. **调用 Gradle**：对每个目标执行对应的 `assemble<Flavor><Abi>Release` 任务，实时解析 Gradle 输出并驱动彩色进度条（编译源码 → 处理资源 → 合并资源 → 打包 APK → 组装完成）。
5. **汇总产物**：将所有 APK 复制到 `BuildTools/apk-out/`，打印构建结果汇总（成功/失败、APK 列表及大小）。

> 注意：首次构建会编译所有源码并下载部分 Gradle 插件依赖（若缓存未完整），之后再打包会快很多。

---

## 常见问题

### Q1: 报错 "未找到 Node.js" 或 "Node.js 版本过低"
A: 打包工具基于 Node.js 18+，请先安装：
   - Linux: `sudo apt install -y nodejs`
   - macOS: `brew install node`
   - 或访问 https://nodejs.org/

### Q2: 报错 "Cannot find a Java installation ... vendor=JetBrains"
A: 说明 `gradle.properties` 没有正确注入。请运行脚本，它会自动写入正确配置。或者手动把 BuildTools/jbr-21 设为 JDK 21。

### Q3: 如何修改签名密钥？
A: 替换 `BuildTools/config/release.keystore`，然后在 `build_apk.js` 的 `writeConfigs()` 函数中更新 alias 和密码即可。

### Q4: 依赖没缓存/下载很慢？
A: 首次打包完成后，把 `BuildTools/gradle-wrapper/caches/` 目录打包备份，下次替换即可。Gradle 缓存是可移植的。

### Q5: Windows 下怎么使用？
A: 推荐 **WSL2 (Ubuntu 22.04+)** 或 Git Bash；也可直接用 `node BuildTools/build_apk.js` 调用（需已安装 Node.js 18+）。
