package com.ssmhdssmhd.mxboxs.setting;

import android.text.TextUtils;

import com.github.catvod.utils.Prefers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ssmhdssmhd.mxboxs.bean.Parse;
import com.ssmhdssmhd.mxboxs.utils.Github;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 内置（视频解析）线路配置：
 * 在「高级设置 → 接口配置」中管理，支持两种来源模式：
 *
 *  - 文件调用（默认，Setting.PARSE_SOURCE_FILE）：
 *      从当前 GitHub 仓库 main 分支根目录拉取线路文件，一行一个。
 *      nzbfq.txt     → 播放器线路（type 0，GET url+webUrl 直接播 / AI 嗅探）
 *      nzbfqjson.txt → JSON 解析接口（type 1，从 JSON 返回的 url 字段取播放地址）
 *      播放时先调 nzbfq.txt 里的播放器，全部失败后才调 nzbfqjson.txt 的 JSON 接口。
 *
 *  - 自定义（Setting.PARSE_SOURCE_CUSTOM）：
 *      类型只有两种（对应 Parse.type）：
 *         1 = 直接播放（type 0）
 *         2 = JSON解析（type 1）
 *
 * 线路以 JSON 数组持久化到 SharedPreferences。
 */
public class BuiltinParseSetting {

    private static final String KEY = "builtin_parse_lines";

    /** 仓库根目录线路文件名。 */
    public static final String FILE_PLAYER = "nzbfq.txt";
    public static final String FILE_JSON = "nzbfqjson.txt";

    /** 文件线路内存缓存（TTL 10 分钟），避免每次配置加载都去 GitHub 拉取两个文件。 */
    private static final long FILE_CACHE_TTL_MS = 10L * 60L * 1000L;
    private static volatile List<Parse> fileCache;
    private static volatile long fileCacheAt;

    private static Parse line(String name, int type, String url) {
        Parse parse = new Parse();
        parse.setName(name);
        parse.setType(type);
        parse.setUrl(url);
        return parse;
    }

    /** UI 展示的类型编号（1 直接播放 / 2 JSON解析）→ Parse.type（0 / 1）。 */
    public static int parseType(int uiType) {
        return uiType == 2 ? 1 : 0;
    }

    /** Parse.type（0 / 1）→ UI 展示的类型编号（1 / 2）。 */
    public static int uiType(int parseType) {
        return parseType == 1 ? 2 : 1;
    }

    /** 生效线路，按来源模式返回：文件调用（GitHub 文件）或自定义。永不返回 null。 */
    public static List<Parse> effectiveLines() {
        if (Setting.isParseSourceFile()) {
            List<Parse> file = cachedFiles();
            if (file != null && !file.isEmpty()) return file;
            // 文件拉取失败 → 兜底用自定义（若没有则为空列表）
        }
        List<Parse> custom = getCustomLines();
        return custom != null ? custom : new ArrayList<>();
    }

    /** 带 TTL 的文件线路缓存读取。 */
    private static List<Parse> cachedFiles() {
        if (fileCache != null && System.currentTimeMillis() - fileCacheAt < FILE_CACHE_TTL_MS) {
            return fileCache;
        }
        List<Parse> fetched = fetchFromGithubFiles();
        if (fetched != null && !fetched.isEmpty()) {
            fileCache = fetched;
            fileCacheAt = System.currentTimeMillis();
            return fetched;
        }
        // 拉取失败则退化为已有缓存（哪怕过期），避免线路瞬间消失导致解析全部失败
        return fileCache != null ? fileCache : new ArrayList<>();
    }

    /** 清空文件线路缓存（下次播放/配置加载时重新拉取）。 */
    public static void refreshFileCache() {
        fileCache = null;
        fileCacheAt = 0L;
    }

    /**
     * 从 GitHub 仓库 main 分支根目录拉取线路文件并合并：
     * 先 nzbfq.txt（播放器，type 0）后 nzbfqjson.txt（JSON 接口，type 1），
     * 顺序即播放优先级（先播放器、全部失败后再 JSON）。
     */
    public static List<Parse> fetchFromGithubFiles() {
        List<Parse> lines = new ArrayList<>();
        String players = Github.getRawFile(FILE_PLAYER);
        if (!TextUtils.isEmpty(players)) {
            int i = 0;
            for (String line : players.split("\\r?\\n")) {
                String u = trimUrl(line);
                if (u.isEmpty()) continue;
                lines.add(line(FILE_PLAYER + (++i), 0, u));
            }
        }
        String jsons = Github.getRawFile(FILE_JSON);
        if (!TextUtils.isEmpty(jsons)) {
            int j = 0;
            for (String line : jsons.split("\\r?\\n")) {
                String u = trimUrl(line);
                if (u.isEmpty()) continue;
                lines.add(line(FILE_JSON + (++j), 1, u));
            }
        }
        return lines;
    }

    /** 文件模式下的「播放器」线路（nzbfq.txt，type 0），先调。 */
    public static List<Parse> filePlayerLines() {
        return Collections.unmodifiableList(playersOnly(effectiveLines()));
    }

    /** 文件模式下的「JSON 解析」线路（nzbfqjson.txt，type 1），播放器全失败后再调。 */
    public static List<Parse> fileJsonLines() {
        return Collections.unmodifiableList(jsonsOnly(effectiveLines()));
    }

    private static List<Parse> playersOnly(List<Parse> src) {
        List<Parse> out = new ArrayList<>();
        for (Parse p : src) if (p != null && p.getType() == 0) out.add(p);
        return out;
    }

    private static List<Parse> jsonsOnly(List<Parse> src) {
        List<Parse> out = new ArrayList<>();
        for (Parse p : src) if (p != null && p.getType() == 1) out.add(p);
        return out;
    }

    /** 去掉行首尾空白与 # 注释，返回规范化后的 URL（空表示跳过）。 */
    private static String trimUrl(String line) {
        if (line == null) return "";
        String t = line.trim();
        if (t.isEmpty() || t.startsWith("#") || !t.startsWith("http")) return "";
        return t;
    }

    /** 仅读取自定义线路（不触发文件拉取）。 */
    public static List<Parse> customLines() {
        List<Parse> custom = getCustomLines();
        return custom != null ? custom : new ArrayList<>();
    }

    private static List<Parse> getCustomLines() {
        String json = Prefers.getString(KEY, "");
        if (TextUtils.isEmpty(json)) return null;
        try {
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            List<Parse> list = new ArrayList<>();
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                list.add(line(
                        obj.has("name") ? obj.get("name").getAsString() : "",
                        obj.has("type") ? obj.get("type").getAsInt() : 1,
                        obj.has("url") ? obj.get("url").getAsString() : ""));
            }
            return list;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 保存整份自定义线路，成功返回 true。 */
    public static boolean saveLines(List<Parse> lines) {
        try {
            JsonArray array = new JsonArray();
            for (Parse parse : lines) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", parse.getName());
                obj.addProperty("type", parse.getType());
                obj.addProperty("url", parse.getUrl());
                array.add(obj);
            }
            Prefers.put(KEY, array.toString());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 清空自定义线路。 */
    public static void reset() {
        Prefers.remove(KEY);
    }
}