# 更新日志

## v1.0.0 (2026-07-29)

### 新增
- **AI 设置功能**：在设置页面新增 AI 智能设置入口
  - AI 优化总开关
  - 播放视频加速（支持 1.2x / 1.5x / 2.0x / 3.0x / 4.0x 倍率选择）
  - 自动去广告
  - 跳过插播
  - 智能跳过片头片尾
  - 自动播放下一集
- 新增 `AISetting` 配置管理类
- 新增 `SettingAIActivity` 及对应布局文件
- 新增多语言字符串资源（英文、简体中文、繁体中文）

### 修改
- APK 输出名称从 `{mode}-{abi}.apk` 改为 `MXbox-{mode}-{abi}.apk`
- 设置页面新增 AI 设置入口按钮
- AndroidManifest.xml 注册 SettingAIActivity
- 重写 README.md 为 MXbox 项目文档
- 将 main 分支同步至 FongMi/TV 上游 fongmi 分支

### 基础版本
- 基于 FongMi/TV fongmi 分支 (commit: 5fdff00a6)
