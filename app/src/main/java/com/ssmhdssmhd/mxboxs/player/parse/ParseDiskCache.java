package com.ssmhdssmhd.mxboxs.player.parse;

import android.text.TextUtils;

import com.ssmhdssmhd.mxboxs.App;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 解析结果磁盘持久化缓存（L2 层）。
 * <p>
 * L1 = {@link ParseJob#PARSE_CACHE} 内存 LRU（200 条 / 30 分钟）；
 * L2 = 本类，磁盘 JSON 文件（1000 条 / 12 小时）。
 * <p>
 * 杀进程 / 冷启动后仍能命中，跳过 HTTP + WebView 解析。
 * 写入异步，不阻塞解析回调线程；读取同步（文件小，<2KB，够快）。
 */
public final class ParseDiskCache {

    private static final int MAX_ENTRIES = 1000;
    private static final long TTL_MS = 12L * 60L * 60L * 1000L;  // 12 小时
    private static final int TRIM_INTERVAL = 50;  // 每 50 次 put 才 trim 一次（惰性化）
    private static final File DIR = new File(App.get().getCacheDir(), "parse_disk_cache");

    // 单线程异步写盘，不抢解析/播放资源
    private static final ExecutorService WRITE_EXEC = new ThreadPoolExecutor(
            0, 1, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
        Thread t = new Thread(r, "parse-disk-write"); t.setDaemon(true); return t;
    }, new ThreadPoolExecutor.DiscardPolicy());
    // put 计数：TRIM_INTERVAL 次触发一次 trim（惰性化，避免每次 put 都 listFiles+sort）
    private static final java.util.concurrent.atomic.AtomicInteger PUT_COUNT =
            new java.util.concurrent.atomic.AtomicInteger(0);

    private ParseDiskCache() {}

    /** 读取磁盘缓存；过期或不存在返回 null。 */
    public static ParseJob.CacheEntry get(String cacheKey) {
        if (TextUtils.isEmpty(cacheKey)) return null;
        File f = file(cacheKey);
        if (!f.exists()) return null;
        try {
            String json = readFile(f);
            if (TextUtils.isEmpty(json)) return null;
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            long createAt = obj.has("createAt") ? obj.get("createAt").getAsLong() : 0;
            if (System.currentTimeMillis() - createAt > TTL_MS) {
                f.delete();
                return null;
            }
            Map<String, String> headers = null;
            if (obj.has("headers") && obj.get("headers").isJsonObject()) {
                headers = new HashMap<>();
                for (Map.Entry<String, com.google.gson.JsonElement> e : obj.getAsJsonObject("headers").entrySet()) {
                    headers.put(e.getKey(), e.getValue().getAsString());
                }
            }
            String url = obj.has("url") ? obj.get("url").getAsString() : null;
            String from = obj.has("from") ? obj.get("from").getAsString() : null;
            return new ParseJob.CacheEntry(headers, url, from);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 异步写入磁盘缓存。 */
    public static void put(String cacheKey, Map<String, String> headers, String url, String from) {
        if (TextUtils.isEmpty(cacheKey) || TextUtils.isEmpty(url)) return;
        WRITE_EXEC.execute(() -> {
            try {
                if (!DIR.exists()) DIR.mkdirs();
                JsonObject obj = new JsonObject();
                JsonObject h = new JsonObject();
                if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) h.addProperty(e.getKey(), e.getValue());
                obj.add("headers", h);
                obj.addProperty("url", url);
                obj.addProperty("from", from == null ? "" : from);
                obj.addProperty("createAt", System.currentTimeMillis());
                writeFile(file(cacheKey), obj.toString());
                // 惰性化：每 TRIM_INTERVAL 次 put 才 trim 一次，避免频繁 listFiles+sort
                if (PUT_COUNT.incrementAndGet() % TRIM_INTERVAL == 0) trimIfNeeded();
            } catch (Throwable ignored) {}
        });
    }

    /** 清空磁盘缓存，返回被清空的条目数。 */
    public static int clear() {
        File[] files = DIR.listFiles();
        if (files == null) return 0;
        int n = 0;
        for (File f : files) if (f.delete()) n++;
        return n;
    }

    /** 返回磁盘缓存条目数。 */
    public static int size() {
        File[] files = DIR.listFiles();
        return files == null ? 0 : files.length;
    }

    private static File file(String cacheKey) {
        return new File(DIR, md5(cacheKey) + ".json");
    }

    /** 超容量时按 lastModified 删最老的。 */
    private static void trimIfNeeded() {
        File[] files = DIR.listFiles();
        if (files == null || files.length <= MAX_ENTRIES) return;
        // 按 lastMethod 升序（最老在前），删到只剩 MAX_ENTRIES
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
        int toDelete = files.length - MAX_ENTRIES;
        for (int i = 0; i < toDelete; i++) files[i].delete();
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static String readFile(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[(int) Math.min(f.length(), 8192)];
            int len = fis.read(buf);
            return len > 0 ? new String(buf, 0, len, StandardCharsets.UTF_8) : "";
        }
    }

    private static void writeFile(File f, String content) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
