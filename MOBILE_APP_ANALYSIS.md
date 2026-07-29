# Mobile App 代码分析文档

> 项目：MXboxS (FongMi TV)  
> 包名：`com.fongmi.android.tv`  
> 模块：`app/src/mobile/`  

---

## 一、应用基本信息

| 属性 | 值 | 定义位置 |
|------|-----|----------|
| 应用名称 | `TV` | [app/src/main/res/values/strings.xml#L4](file:///workspace/app/src/main/res/values/strings.xml#L4) |
| 包名/ApplicationId | `com.fongmi.android.tv` | [app/build.gradle#L11](file:///workspace/app/build.gradle#L11) |
| 应用图标 | `@mipmap/ic_launcher` | [app/src/main/AndroidManifest.xml#L46](file:///workspace/app/src/main/AndroidManifest.xml#L46) |
| 圆形图标 | `@mipmap/ic_launcher_round` | [app/src/main/AndroidManifest.xml#L52](file:///workspace/app/src/main/AndroidManifest.xml#L52) |
| 应用分类 | `video` | [app/src/main/AndroidManifest.xml#L43](file:///workspace/app/src/main/AndroidManifest.xml#L43) |
| Application 类 | `com.fongmi.android.tv.App` | [app/src/main/AndroidManifest.xml#L41](file:///workspace/app/src/main/AndroidManifest.xml#L41) |

### 图标说明

- 图标使用 **自适应图标 (Adaptive Icon)**，定义在 [app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml](file:///workspace/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml)
- 背景：白色 `@color/white`
- 前景：矢量图 [app/src/main/res/drawable/ic_launcher_foreground.xml](file:///workspace/app/src/main/res/drawable/ic_launcher_foreground.xml)
  - 一个 512x512 的 3D 彩色几何图形，配色为青绿色系(#2DD4AA, #4CF5CB) + 蓝色系(#68AEF4, #8FE1EA) + 黄色系(#FFD159, #FFE08F)
- 多密度适配：`mipmap-mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi`

---

## 二、页面结构总览

### 2.1 入口 Activity

**HomeActivity** — 应用启动入口（MAIN/LAUNCHER）

- 路径：[app/src/mobile/java/com/fongmi/android/tv/ui/activity/HomeActivity.java](file:///workspace/app/src/mobile/java/com/fongmi/android/tv/ui/activity/HomeActivity.java)
- 启动模式：`singleTop`
- 启动主题：`Theme.Splash`
- 支持 Intent 类型：`ACTION_MAIN`, `ACTION_SEARCH`, `ACTION_SEND`, `ACTION_VIEW` (video/audio/text/bt/magnet/rtmp/rtsp/http/smb/ed2k/thunder/jianpian)

### 2.2 所有 Activity 一览

| Activity | 功能 | 路径 |
|----------|------|------|
| `HomeActivity` | 主页（底部导航：Vod/Live/Setting） | `ui/activity/HomeActivity.java` |
| `SearchActivity` | 搜索页面 | `ui/activity/SearchActivity.java` |
| `FileActivity` | 文件浏览器 | `ui/activity/FileActivity.java` |
| `FolderActivity` | 分类文件夹浏览 | `ui/activity/FolderActivity.java` |
| `HistoryActivity` | 观看历史 | `ui/activity/HistoryActivity.java` |
| `KeepActivity` | 收藏/追剧 | `ui/activity/KeepActivity.java` |
| `LiveActivity` | 直播播放（横屏+画中画） | `ui/activity/LiveActivity.java` |
| `ScanActivity` | 二维码扫描 | `ui/activity/ScanActivity.java` |
| `VideoActivity` | 点播播放详情页（竖屏+横屏+画中画） | `ui/activity/VideoActivity.java` |

### 2.3 所有 Fragment 一览

| Fragment | 功能 | 路径 |
|----------|------|------|
| `VodFragment` | 点播首页（Tab 分类） | `ui/fragment/VodFragment.java` |
| `SettingFragment` | 设置主页 | `ui/fragment/SettingFragment.java` |
| `SettingPlayerFragment` | 播放器设置 | `ui/fragment/SettingPlayerFragment.java` |
| `SettingDanmakuFragment` | 弹幕设置 | `ui/fragment/SettingDanmakuFragment.java` |
| `SettingPreloadFragment` | 预加载设置 | `ui/fragment/SettingPreloadFragment.java` |
| `SettingDecodeFragment` | 解码设置 | `ui/fragment/SettingDecodeFragment.java` |
| `FolderFragment` | 分类内容列表 | `ui/fragment/FolderFragment.java` |
| `EpisodeFragment` | 剧集选择页 | `ui/fragment/EpisodeFragment.java` |
| `SearchFragment` | 搜索界面 | `ui/fragment/SearchFragment.java` |
| `CollectFragment` | 合集页面 | `ui/fragment/CollectFragment.java` |
| `TypeFragment` | 类型/分类页 | `ui/fragment/TypeFragment.java` |

---

## 三、导航结构

### 3.1 底部导航栏

定义在 [app/src/mobile/res/menu/menu_nav.xml](file:///workspace/app/src/mobile/res/menu/menu_nav.xml)

```
┌──────────┬──────────┬──────────┐
│   Vod    │   Live   │ Setting  │
│  (点播)   │  (直播)   │  (设置)   │
└──────────┴──────────┴──────────┘
```

| 导航项 | ID | 图标资源 | 字符串 | 功能 |
|--------|-----|----------|--------|------|
| Vod | `R.id.vod` | `@drawable/ic_nav_vod` | `@string/nav_vod` ("Vod") | 点播首页 |
| Live | `R.id.live` | `@drawable/ic_nav_live` | `@string/nav_live` ("Live") | 打开直播 |
| Setting | `R.id.setting` | `@drawable/ic_nav_setting` | `@string/nav_setting` ("Setting") | 设置页面 |

### 3.2 HomeActivity 页面切换逻辑

```java
// 在 HomeActivity 中通过 FragmentStateManager 管理页面切换
mManager = new FragmentStateManager(mBinding.container, getSupportFragmentManager(), position -> switch (position) {
    case 0 -> VodFragment.newInstance();        // 点播首页
    case 1 -> SettingFragment.newInstance();    // 设置主页
    case 2 -> SettingPlayerFragment.newInstance();   // 播放器设置
    case 3 -> SettingDanmakuFragment.newInstance();  // 弹幕设置
    case 4 -> SettingPreloadFragment.newInstance();  // 预加载设置
    case 5 -> SettingDecodeFragment.newInstance();   // 解码设置
    default -> null;
});
```

导航项选择回调：
```java
@Override
public boolean onNavigationItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == R.id.setting) return mManager.change(1);  // 切换到设置页
    if (item.getItemId() == R.id.vod) return mManager.change(0);       // 切换到点播页
    if (item.getItemId() == R.id.live) return openLive();              // 打开直播Activity
    return false;
}
```

### 3.3 返回键层级

```
VodFragment (0) → SettingFragment (1) → SettingPlayerFragment (2) → SettingDanmakuFragment (3) / SettingDecodeFragment (5)
                                       → SettingPreloadFragment (4)
```

---

## 四、页面调用方式详解

### 4.1 各 Activity 启动方式

#### HomeActivity (主入口)
```java
// 系统自动启动（LAUNCHER）
// 外部 Intent 处理:
// - ACTION_SEND → VideoActivity.push(this, text)
// - ACTION_VIEW → VideoActivity.push(this, url) 或 loadLive(url)
// - ACTION_SEARCH → SearchActivity.start(this, keyword)
```

#### SearchActivity (搜索)
```java
// 不带关键词
SearchActivity.start(activity);
// 带关键词
SearchActivity.start(activity, keyword);
// 接收 Intent: key = "keyword"
```

#### VideoActivity (点播播放)
```java
// 方式1：通过 URL 推送播放
VideoActivity.push(activity, "视频URL");

// 方式2：本地文件播放
VideoActivity.file(activity, "/path/to/file");

// 方式3：通过 siteKey + vodId 播放
VideoActivity.start(activity, key, id, name);
VideoActivity.start(activity, key, id, name, pic);
VideoActivity.start(activity, key, id, name, pic, mark);

// 方式4：投屏播放
VideoActivity.cast(activity, history);

// 方式5：从收藏播放
VideoActivity.collect(activity, key, id, name, pic);

// 接收 Intent keys: "key", "id", "name", "pic", "mark", "collect"
```

#### LiveActivity (直播播放)
```java
// 在 HomeActivity 中直接调用
LiveActivity.start(context);
```

#### HistoryActivity (观看历史)
```java
HistoryActivity.start(activity);
```

#### KeepActivity (收藏)
```java
KeepActivity.start(activity);
```

#### FileActivity (文件浏览)
```java
// 在 VideoActivity 内部调用
// 通过文件选择器 Intent 启动
```

#### FolderActivity (分类浏览)
```java
FolderActivity.start(activity, key, result);
// 接收 Intent keys: "key", "result"
```

#### ScanActivity (扫码)
```java
ScanActivity.start(activity);
```

### 4.2 事件通信机制

项目使用 **EventBus** 进行跨组件通信：

| 事件类 | 用途 | 发送方 |
|--------|------|--------|
| `ConfigEvent` | 配置变更通知 | 配置加载完成后 |
| `RefreshEvent` | 刷新通知 | 全局刷新触发 |
| `ServerEvent` | 服务器推送事件 | 本地服务器 |
| `CastEvent` | 投屏事件 | 投屏服务 |
| `StateEvent` | 状态变更 | 各组件 |

```java
// 订阅示例
@Subscribe(threadMode = ThreadMode.MAIN)
public void onConfigEvent(ConfigEvent event) {
    switch (event.type()) {
        case VOD:    RefreshEvent.home(); break;       // 刷新点播首页
        case COMMON: setNavigation(); break;            // 更新导航栏
        case BOOT:   LiveActivity.start(this); break;   // 启动直播
    }
}

@Subscribe(threadMode = ThreadMode.MAIN)
public void onServerEvent(ServerEvent event) {
    if (event.type() == ServerEvent.Type.PUSH) VideoActivity.push(this, event.text());
    if (event.type() == ServerEvent.Type.SEARCH) SearchActivity.start(this, event.text());
}
```

---

## 五、关键文件路径索引

### 5.1 AndroidManifest

| 文件 | 路径 |
|------|------|
| 主 Manifest | [app/src/main/AndroidManifest.xml](file:///workspace/app/src/main/AndroidManifest.xml) |
| Mobile Manifest | [app/src/mobile/AndroidManifest.xml](file:///workspace/app/src/mobile/AndroidManifest.xml) |

### 5.2 图标资源

| 资源 | 路径 |
|------|------|
| 自适应图标定义 | [app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml](file:///workspace/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml) |
| 圆形图标定义 | [app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml](file:///workspace/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml) |
| 图标前景矢量 | [app/src/main/res/drawable/ic_launcher_foreground.xml](file:///workspace/app/src/main/res/drawable/ic_launcher_foreground.xml) |
| 各密度图标 | `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.*` |

### 5.3 字符串资源

| 文件 | 内容 |
|------|------|
| [app/src/main/res/values/strings.xml](file:///workspace/app/src/main/res/values/strings.xml) | 主字符串（app_name, 播放器, 弹幕等） |
| [app/src/mobile/res/values/strings.xml](file:///workspace/app/src/mobile/res/values/strings.xml) | Mobile 专用字符串（导航、菜单等） |

### 5.4 导航菜单

| 文件 | 内容 |
|------|------|
| [app/src/mobile/res/menu/menu_nav.xml](file:///workspace/app/src/mobile/res/menu/menu_nav.xml) | 底部导航栏 |
| [app/src/mobile/res/menu/menu_vod.xml](file:///workspace/app/src/mobile/res/menu/menu_vod.xml) | 点播页菜单 |
| [app/src/mobile/res/menu/menu_search.xml](file:///workspace/app/src/mobile/res/menu/menu_search.xml) | 搜索页菜单 |
| [app/src/mobile/res/menu/menu_history.xml](file:///workspace/app/src/mobile/res/menu/menu_history.xml) | 历史页菜单 |
| [app/src/mobile/res/menu/menu_keep.xml](file:///workspace/app/src/mobile/res/menu/menu_keep.xml) | 收藏页菜单 |

---

## 六、二次开发要点

### 6.1 修改应用名称

编辑文件：[app/src/main/res/values/strings.xml#L4](file:///workspace/app/src/main/res/values/strings.xml#L4)
```xml
<string name="app_name">TV</string>  <!-- 改为你的应用名 -->
```

### 6.2 修改应用图标

替换以下文件：
- `app/src/main/res/drawable/ic_launcher_foreground.xml` — 矢量前景图
- `app/src/main/res/mipmap-*/ic_launcher.png` — 各密度 PNG 图标
- `app/src/main/res/mipmap-*/ic_launcher_round.webp` — 圆形图标

### 6.3 修改包名

编辑 [app/build.gradle](file:///workspace/app/build.gradle#L16)：
```groovy
applicationId "com.fongmi.android.tv"  // 改为你的包名
```

### 6.4 添加新页面

1. 在 `app/src/mobile/AndroidManifest.xml` 注册 Activity
2. 创建 Activity 类继承 `BaseActivity`，实现 `getBinding()` 返回 ViewBinding
3. 添加静态 `start()` 方法供外部调用
4. 如需要在导航栏显示，修改 `menu_nav.xml` 和 `HomeActivity`

### 6.5 页面间通信

- **Activity 间跳转**：使用 Intent + 静态 `start()` 方法
- **Fragment 间通信**：使用 EventBus (`@Subscribe` 注解)
- **数据共享**：通过 ViewModel (`ViewModelProvider`)

---

*文档生成时间：2026-07-29*