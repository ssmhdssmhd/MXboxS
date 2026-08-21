package com.ssmhdssmhd.mxboxs.setting;

import android.text.TextUtils;

import com.github.catvod.utils.Prefers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ssmhdssmhd.mxboxs.bean.Parse;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置（视频解析）线路配置：
 * 在「高级设置 → 接口配置」中编辑，支持添加 / 删除 / 保存 / 恢复默认。
 * 类型只有两种（对应 Parse.type）：
 *   1 = 直接播放（type 0，把返回内容当直链/网页直接播）
 *   2 = JSON解析（type 1，从 JSON 返回里的 url 字段取播放地址）
 * 线路以 JSON 数组持久化到 SharedPreferences。
 */
public class BuiltinParseSetting {

    private static final String KEY = "builtin_parse_lines";

    /** 默认线路：两条官方 node.js 多进程 JSON 解析线路（type 1）。 */
    public static List<Parse> defaults() {
        List<Parse> list = new ArrayList<>();
        list.add(line("1314-node", 1, "http://114.134.184.91:1314/node.js?url="));
        list.add(line("1315-node", 1, "http://114.134.184.91:1315/node.js?url="));
        return list;
    }

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

    /** 生效线路：有自定义则用自定义，否则用默认。 */
    public static List<Parse> effectiveLines() {
        List<Parse> custom = getCustomLines();
        return custom != null ? custom : defaults();
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

    /** 保存整份线路，成功返回 true。 */
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

    /** 恢复默认线路。 */
    public static void reset() {
        Prefers.remove(KEY);
    }
}
