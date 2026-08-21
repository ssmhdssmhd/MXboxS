package com.ssmhdssmhd.mxboxs.setting;

import android.text.TextUtils;

import com.google.gson.reflect.TypeToken;
import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.bean.Parse;
import com.github.catvod.utils.Prefers;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置播放解析线路（接口配置）设置。
 *
 * v5.7.1 新增：允许用户在「高级设置 → 接口配置」里直接编辑内置解析线路，
 * 而无需重新编译。线路以 SharedPreferences 持久化（JSON 数组），
 * 运行时由 VodConfig.setParses() 读取；未配置时回退到默认三路
 * （node-1314 / node-1315 / sniff-node）。
 *
 * 编辑器格式：每行一条，`名称|类型|地址`，例如：
 *   node-1314|1|http://114.134.184.91:1314/node.js?url=
 *   node-1315|1|http://114.134.184.91:1315/node.js?url=
 *   sniff-node|1|http://114.134.184.91:1315/sniff?url=
 */
public final class BuiltinParseSetting {

    private static final String KEY = "builtin_parse_config_json";

    private BuiltinParseSetting() {
    }

    /** 默认内置解析线路（多进程 node.js + sniff）。 */
    private static List<Parse> defaults() {
        List<Parse> list = new ArrayList<>();
        list.add(Parse.builtinNode1314());
        list.add(Parse.builtinNode1315());
        list.add(Parse.builtinSniff());
        return list;
    }

    /** 用户已保存的自定义线路；未配置或解析失败返回 null。 */
    public static List<Parse> getCustomLines() {
        String json = Prefers.getString(KEY, "");
        if (TextUtils.isEmpty(json)) return null;
        try {
            List<Parse> list = App.gson().fromJson(json, new TypeToken<List<Parse>>() {
            }.getType());
            return (list == null || list.isEmpty()) ? null : list;
        } catch (Exception e) {
            return null;
        }
    }

    /** 生效线路 = 用户自定义（若有）否则默认。 */
    public static List<Parse> effectiveLines() {
        List<Parse> custom = getCustomLines();
        return custom != null ? custom : defaults();
    }

    /** 编辑器回填文本（当前生效线路的行式文本）。 */
    public static String editorText() {
        StringBuilder sb = new StringBuilder();
        for (Parse p : effectiveLines()) {
            sb.append(TextUtils.isEmpty(p.getName()) ? "parse" : p.getName()).append('|')
                    .append(p.getType() == null ? 1 : p.getType()).append('|')
                    .append(TextUtils.isEmpty(p.getUrl()) ? "" : p.getUrl()).append('\n');
        }
        return sb.toString();
    }

    /** 解析编辑器文本；格式不合法（缺字段/地址非 http）返回 null。 */
    public static List<Parse> parseText(String text) {
        if (text == null) return null;
        List<Parse> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\|");
            if (parts.length < 3) return null;
            String name = parts[0].trim();
            String url = parts[2].trim();
            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(url)
                    || !(url.startsWith("http://") || url.startsWith("https://"))) {
                return null;
            }
            Integer type;
            try {
                type = Integer.parseInt(parts[1].trim());
            } catch (Exception e) {
                type = 1;
            }
            Parse p = new Parse();
            p.setName(name);
            p.setType(type);
            p.setUrl(url);
            out.add(p);
        }
        return out.isEmpty() ? null : out;
    }

    /** 保存编辑器文本；成功返回 true，格式错误返回 false。 */
    public static boolean saveText(String text) {
        List<Parse> list = parseText(text);
        if (list == null) return false;
        Prefers.put(KEY, App.gson().toJson(list));
        return true;
    }

    /** 恢复默认内置线路。 */
    public static void reset() {
        Prefers.put(KEY, "");
    }
}