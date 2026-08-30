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

    /** 用户未配置壁纸 URL 时默认走的内置壁纸 API */
    public static final String BUILTIN_WALLPAPER_URL = "https://www.hhlqilongzhu.cn/api/MP4_xiaojiejie.php";
    /** 显示给用户看的占位文字：不显示真实 API 地址，只显示「内置」两个字 */
    public static final String BUILTIN_DISPLAY_NAME = "内置";

    /** 工具：当 url 空/未配置时，返回 BUILTIN_WALLPAPER_URL；否则原样返回。 */
    public static String useBuiltinIfEmpty(String url) {
        if (url == null || url.isEmpty()) return BUILTIN_WALLPAPER_URL;
        return url;
    }

    /** 工具：当前 url 是否属于「内置」（未配置或显式等于 BUILTIN URL） */
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
        return get().getConfig().getDesc();
    }

    public static void load(Config config, Callback callback) {
        get().config(config).load(callback);
    }

    public WallConfig init() {
        return config(Config.wall());
    }

    public WallConfig config(Config config) {
        this.config = config;
        if (config.isEmpty()) return this;
        this.sync = config.getUrl().equals(VodConfig.get().getWall());
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
        File file = Path.wall(0);
        String url = useBuiltinIfEmpty(config.getUrl());
        checkUrl(url, file);
        setWallType(file);
        setSnapshot(file);
    }

    @Override
    protected boolean isLoaded() {
        return false;
    }

    private void checkUrl(String url, File file) throws Throwable {
        if (url.startsWith("file")) FileUtil.copyAtomically(Path.local(url), file);
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
        try (FileOutputStream fos = new FileOutputStream(Path.wallCache())) {
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
