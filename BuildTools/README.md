# BuildTools — MXbox 一键 APK 打包工具包

本目录包含 MXbox 打包所需的 **完整预编译环境**，解压后无需额外下载即可一键打包 APK。

## 目录内容（首次使用前请解压所有 `.tar.gz` 分包）

| 路径 | 说明 | 大小 |
|------|------|------|
| `jbr-21/` | JetBrains JDK 21（AGP 9.2.1 强制要求的 toolchain） | ~800 MB |
| `android-sdk/` | Android SDK：<br>  - `platforms/android-37/` compileSDK 37<br>  - `build-tools/37.0.0/` build-tools<br>  - `cmdline-tools/latest/` sdkmanager 等命令行工具 | ~630 MB |
| `gradle-wrapper/dists/gradle-9.6.1-bin/` | Gradle 9.6.1 分发包（解包即可用） | ~290 MB |
| `gradle-wrapper/caches/` | Gradle 依赖缓存（含 AAR/JAR，首次解压后可避免重复下载） | ~1 MB+（完成打包后显著增大） |
| `config/release.keystore` | 发布签名密钥<br>  alias: `release` / 密码: `123456` | — |
| `apk-out/` | 构建产出的 APK 将复制到这里 | — |
| `build_apk.sh` | **一键打包脚本** ← 主要使用入口 | — |
| `README.md` | 本说明文档 | — |

---

## 使用方法

### 前置条件
1. 操作系统：Linux / macOS（Windows 可用 WSL2）
2. `BuildTools` 文件夹必须在 MXbox 项目根目录下，目录结构如下：
   ```
   MXbox/
   ├── BuildTools/        ← 本目录（已解压）
   │   ├── build_apk.sh
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

1. 设置环境变量：`JAVA_HOME`、`ANDROID_HOME`、`GRADLE_USER_HOME` 全部指向 `BuildTools/` 内部的预下载文件，**完全不依赖全局环境**。
2. 写入项目根目录的 `local.properties`（sdk.dir、签名密钥路径与密码）。
3. 在 `gradle.properties` 尾部注入 `org.gradle.java.home`，并禁用自动下载 toolchain（避免联网下载 JBR）。
4. 调用对应的 Gradle assemble 任务。
5. 将 APK 汇总到 `BuildTools/apk-out/`。

> 注意：首次构建会编译所有源码并下载部分 Gradle 插件依赖（若缓存未完整），之后再打包会快很多。

---

## 常见问题

### Q1: 报错 "Cannot find a Java installation ... vendor=JetBrains"
A: 说明 `gradle.properties` 没有正确注入。请运行脚本，它会自动写入正确配置。或者手动把 BuildTools/jbr-21 设为 JDK 21。

### Q2: 如何修改签名密钥？
A: 替换 `BuildTools/config/release.keystore`，然后在 `build_apk.sh` 的第 46~49 行更新 alias 和密码即可。

### Q3: 依赖没缓存/下载很慢？
A: 首次打包完成后，把 `BuildTools/gradle-wrapper/caches/` 目录打包备份，下次替换即可。Gradle 缓存是可移植的。

### Q4: Windows 下怎么使用？
A: 推荐 **WSL2 (Ubuntu 22.04+)** 或 Git Bash；也可直接参考脚本里的环境变量写法手动配置（设置 JAVA_HOME / ANDROID_HOME / GRADLE_USER_HOME 后执行 gradlew 即可）。
