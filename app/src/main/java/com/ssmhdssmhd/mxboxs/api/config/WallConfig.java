package com.ssmhdssmhd.mxboxs.api.config;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.bean.Config;
import com.ssmhdssmhd.mxboxs.event.ConfigEvent;
import com.ssmhdssmhd.mxboxs.impl.Callback;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.ssmhdssmhd.mxboxs.utils.Download;
import com.ssmhdssmhd.mxboxs.utils.FileUtil;
import com.ssmhdssmhd.mxboxs.utils.ResUtil;
import com.ssmhdssmhd.mxboxs.utils.UrlUtil;
import com.github.catvod.utils.Path;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class WallConfig extends BaseConfig {

    private static final String TAG = WallConfig.class.getSimpleName();
    /** 用户未配置壁纸 URL 时默认走的内置小姐姐壁纸 API（官方内置） */
    public static final String BUILTIN_WALLPAPER_URL = "https://www.hhlqilongzhu.cn/api/MP4_xiaojiejie.php";
    /** 显示给用户看的占位文字：不显示真实 API 地址，只显示「内置」两个字 */
    public static final String BUILTIN_DISPLAY_NAME = "内置";

    /** 工具：当 url 空/未配置时，返回 BUILTIN_WALLPAPER_URL；否则原样返回。 */
    public static String useBuiltinIfEmpty(String url) {
        if (url == null || url.isEmpty()) return BUILTIN_WALLPAPER_URL;
        return url;
    }

    /** 工具：当前 wallpaper config.getUrl() 是否属于「内置」（未配置或显式等于 BUILTIN URL） */
    public static boolean isBuiltin(String url) {
        if (url == null || url.isEmpty()) return true;
        return BUILTIN_WALLPAPER_URL.equals(url);
    }

    public static WallConfig get() {
        return Loader.INSTANCE;
    }

    public static String getUrl() {
        return useBuiltinIfEmpty(get().getConfig().getUrl());
    }

    public static String getDesc() {
        Config cfg = get().getConfig();
        if (cfg != null && !android.text.TextUtils.isEmpty(cfg.getName())) return cfg.getName();
        String url = cfg == null ? "" : cfg.getUrl();
        if (isBuiltin(url)) return BUILTIN_DISPLAY_NAME;
        if (!android.text.TextUtils.isEmpty(url)) return url;
        return BUILTIN_DISPLAY_NAME;
    }

    public static void load(Config config, Callback callback) {
        get().config(config).load(callback);
    }

    public WallConfig init() {
        return config(Config.wall());
    }

    public WallConfig config(Config config) {
        this.config = config;
        String cfgUrl = config.getUrl();
        boolean builtinEmpty = isBuiltin(cfgUrl);
        if (builtinEmpty) {
            // 内置：config.isEmpty() 会基于 getUrl() 判断，但内置应该视为「已配置」，
            // 所以 sync 用内置 URL 与 VodConfig.wall() 比较
            this.sync = BUILTIN_WALLPAPER_URL.equals(VodConfig.get().getWall());
        } else {
            if (config.isEmpty()) return this;
            this.sync = config.getUrl().equals(VodConfig.get().getWall());
        }
        return this;
    }

    public void load() {
        if (sync) return;
        load(new Callback());
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected Config defaultConfig() {
        return Config.wall();
    }

    @Override
    protected void postEvent() {
        super.postEvent();
        ConfigEvent.wall();
    }

    @Override
    protected void load(Config config) throws Throwable {
        File file = FileUtil.getWall(0);
        // url 空时用内置小姐姐壁纸 API 兜底
        String url = useBuiltinIfEmpty(config.getUrl());
        String realUrl = resolveRealUrl(url);
        checkUrl(realUrl, file);
        setWallType(file);
        setSnapshot(file);
    }

    private String resolveRealUrl(String url) {
        try {
            // 尝试获取 URL 内容，判断是否为 JSON
            String content = com.github.catvod.net.OkHttp.string(UrlUtil.convert(url));
            if (content != null && !content.isEmpty()) {
                com.google.gson.JsonElement element = com.github.catvod.utils.Json.parse(content);
                if (element.isJsonObject()) {
                    com.google.gson.JsonObject object = element.getAsJsonObject();
                    // 尝试从常见字段中提取壁纸 URL
                    if (object.has("wallpaper")) return object.get("wallpaper").getAsString();
                    if (object.has("url")) return object.get("url").getAsString();
                    if (object.has("img")) return object.get("img").getAsString();
                    if (object.has("image")) return object.get("image").getAsString();
                    // 如果没有上述字段，可能本身就是图片URL字符串或者对象结构不对
                } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    // 如果返回的是纯字符串，则直接作为 URL
                    return element.getAsString();
                }
            }
        } catch (Exception e) {
            // 获取内容失败（非 JSON 或网络问题），回退使用原始 URL
        }
        return url;
    }

    @Override
    protected boolean isLoaded() {
        return false;
    }

    private void checkUrl(String url, File file) throws Throwable {
        if (url.startsWith("file")) Path.copy(Path.local(url), file);
        else Download.create(UrlUtil.convert(url), file).tag(TAG).get();
        if (!Path.exists(file)) throw new FileNotFoundException();
    }

    private void setWallType(File file) {
        Setting.putWallType(0);
        if (isGif(file)) Setting.putWallType(1);
        else if (isVideo(file)) Setting.putWallType(2);
    }

    private void setSnapshot(File file) throws Throwable {
        Bitmap bitmap = Glide.with(App.get()).asBitmap().frame(0).load(file).override(ResUtil.getScreenWidth(), ResUtil.getScreenHeight()).skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE).submit().get();
        try (FileOutputStream fos = new FileOutputStream(FileUtil.getWallCache())) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        } finally {
            bitmap.recycle();
        }
    }

    private boolean isVideo(File file) {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(file.getAbsolutePath());
            return "yes".equalsIgnoreCase(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isGif(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            return "image/gif".equals(options.outMimeType);
        } catch (Exception e) {
            return false;
        }
    }

    private static class Loader {
        static volatile WallConfig INSTANCE = new WallConfig();
    }
}
