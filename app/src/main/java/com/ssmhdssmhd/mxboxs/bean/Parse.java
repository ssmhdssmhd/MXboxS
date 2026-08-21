package com.ssmhdssmhd.mxboxs.bean;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.R;
import com.ssmhdssmhd.mxboxs.gson.HeaderAdapter;
import com.ssmhdssmhd.mxboxs.impl.Diffable;
import com.ssmhdssmhd.mxboxs.utils.ResUtil;
import com.ssmhdssmhd.mxboxs.utils.UrlUtil;
import com.github.catvod.utils.Util;
import com.google.gson.JsonElement;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Parse implements Diffable<Parse> {

    @SerializedName("name")
    private String name;
    @SerializedName("type")
    private Integer type;
    @SerializedName("url")
    private String url;
    @SerializedName("ext")
    private Ext ext;

    private boolean selected;
    private String click;

    public static Parse objectFrom(JsonElement element) {
        return App.gson().fromJson(element, Parse.class);
    }

    public static Parse get(Integer type, String url) {
        Parse parse = new Parse();
        parse.setType(type);
        parse.setUrl(url);
        return parse;
    }

    public static Parse god() {
        Parse parse = new Parse();
        parse.setName(ResUtil.getString(R.string.parse_god));
        parse.setType(4);
        return parse;
    }

    /**
     * 内置 m3u8 直链嗅探解析器（type = 5）。
     * 不依赖第三方解析站，直接通过 HTTP + 启发式正则抓取页面里的
     * m3u8 / mp4 / flv 等真实视频地址。
     */
    public static Parse builtin() {
        Parse parse = new Parse();
        parse.setName(ResUtil.getString(R.string.parse_builtin));
        parse.setType(5);
        return parse;
    }

    /**
     * 内置官方 ssmhdssmhd Node.js 解析站（type = 1，JSON HTTP 解析）。
     *
     * v5.6.9 新增：用户请求把「http://114.134.184.91:1314/ssmhdssmhd/node.js?url=」
     * 作为内置解析写死进二进制，即使 VodConfig 远程配置 parses 为空 / 没配置，
     * 也有官方解析兜底，ParseJob.fallbackConcurrentParse 会把它和其他 type=1
     * 解析一起并发 jsonParse；jsonParse 里直接 item.getUrl() + webUrl 拼接，
     * 正好匹配 ?url= 前缀；checkResult 会判定 {code:200, url:"...m3u8"} 并回调成功。
     *
     * 之所以不叫 parse_ssmhdssmhd：因为旧配置文件里的 parses.name 也叫 "ssmhdssmhd"，
     * 同名可做去重（Parse.equals/hashCode 是按 name 比较），避免远程配置里写了同名解析时
     * 出现两个相同节点。如果远程已经有同名，setParses 插入前的 distinct() 会自动把内置版本去重，
     * 用远程配置那个（它可能有自定义 header/ext / 新域名），优先级更高。
     */
    public static final String BUILTIN_SSMHDSSMHD_NAME = "ssmhdssmhd-node";
    public static final String BUILTIN_SSMHDSSMHD_URL  = "http://114.134.184.91:1314/ssmhdssmhd/node.js?url=";

    public static Parse builtinSsmhdssmhd() {
        Parse parse = new Parse();
        parse.setName(BUILTIN_SSMHDSSMHD_NAME);
        parse.setType(1);
        parse.setUrl(BUILTIN_SSMHDSSMHD_URL);
        return parse;
    }

    /**
     * 内置嗅探解析线路（type = 1，JSON HTTP 解析）。
     *
     * v5.7.0 新增：用户请求加入「http://114.134.184.91:1315/sniff?url=」作为内置解析线路。
     * 处理方式与 ssmhdssmhd-node 完全一致：注册为 type=1 内置节点，会被
     * ParseJob.fallbackConcurrentParse 与其它 type=1 解析一起并发 jsonParse；
     * jsonParse 里直接 item.getUrl() + webUrl 拼接成 ?url=<播放页地址>，
     * 返回 {code:200,url:"...m3u8"} 即被 checkResult 判定成功并回调，谁先成功用谁（自动选最快线路）。
     */
    public static final String BUILTIN_SNIFF_NAME = "sniff-node";
    public static final String BUILTIN_SNIFF_URL  = "http://114.134.184.91:1315/sniff?url=";

    public static Parse builtinSniff() {
        Parse parse = new Parse();
        parse.setName(BUILTIN_SNIFF_NAME);
        parse.setType(1);
        parse.setUrl(BUILTIN_SNIFF_URL);
        return parse;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getType() {
        return type == null ? 0 : type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getUrl() {
        return TextUtils.isEmpty(url) ? "" : UrlUtil.convert(url);
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Ext getExt() {
        return ext = ext == null ? new Ext() : ext;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setSelected(Parse item) {
        this.selected = item.equals(this);
    }

    public String getClick() {
        return TextUtils.isEmpty(click) ? "" : click;
    }

    public void setClick(String click) {
        this.click = click;
    }

    public Map<String, String> getHeader() {
        return getExt().getHeader();
    }

    public void setHeader(Map<String, String> header) {
        if (getHeader().isEmpty()) getExt().setHeader(header);
    }

    public boolean isEmpty() {
        return getType() == 0 && getUrl().isEmpty();
    }

    public String extUrl() {
        int index = getUrl().indexOf("?");
        if (getExt().isEmpty() || index == -1) return getUrl();
        return getUrl().substring(0, index + 1) + "cat_ext=" + Util.base64(getExt().toString(), Util.URL_SAFE) + "&" + getUrl().substring(index + 1);
    }

    public HashMap<String, String> mixMap() {
        HashMap<String, String> map = new HashMap<>();
        map.put("type", getType().toString());
        map.put("ext", getExt().toString());
        map.put("url", getUrl());
        return map;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Parse it)) return false;
        return Objects.equals(getName(), it.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName());
    }

    @Override
    public boolean isSameItem(Parse other) {
        return equals(other);
    }

    @Override
    public boolean isSameContent(Parse other) {
        return equals(other);
    }

    public static class Ext {

        @SerializedName("flag")
        private List<String> flag;
        @SerializedName("header")
        @JsonAdapter(HeaderAdapter.class)
        private Map<String, String> header;

        public List<String> getFlag() {
            return flag == null ? Collections.emptyList() : flag;
        }

        public void setFlag(List<String> flag) {
            this.flag = flag;
        }

        public Map<String, String> getHeader() {
            return header == null ? new HashMap<>() : header;
        }

        public void setHeader(Map<String, String> header) {
            this.header = header;
        }

        public boolean isEmpty() {
            return getFlag().isEmpty() && getHeader().isEmpty();
        }

        @NonNull
        @Override
        public String toString() {
            return App.gson().toJson(this);
        }
    }
}
