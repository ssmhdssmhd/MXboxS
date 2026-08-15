package com.ssmhdssmhd.mxboxs.utils;

import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;

import com.ssmhdssmhd.mxboxs.api.config.RuleConfig;
import com.ssmhdssmhd.mxboxs.bean.Result;
import com.ssmhdssmhd.mxboxs.bean.Rule;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Trans;
import com.github.catvod.utils.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Sniffer {

    public static final Pattern CLICKER = Pattern.compile("\\[a=cr:(\\{.*?\\})\\/](.*?)\\[\\/a]");
    public static final Pattern AI_PUSH = Pattern.compile("(https?|thunder|magnet|ed2k|video):\\S+");
    public static final Pattern SNIFFER = Pattern.compile("https?://[^\\s]{12,}\\.(?:m3u8|mp4|mkv|flv|mp3|m4a|aac|mpd)(?:\\?.*)?|https?://.*?video/tos[^\\s]*|rtmp:[^\\s]+");

    public static String getUrl(String text) {
        if (Json.isObj(text) || text.contains("$")) return text;
        Matcher m = AI_PUSH.matcher(text);
        if (m.find()) return m.group(0);
        return text;
    }

    public static boolean isVideoFormat(String url) {
        Rule rule = getRule(UrlUtil.uri(url));
        for (String exclude : rule.getExclude()) if (url.contains(exclude)) return false;
        for (String exclude : rule.getExclude()) if (Pattern.compile(exclude).matcher(url).find()) return false;
        for (String regex : rule.getRegex()) if (url.contains(regex)) return true;
        for (String regex : rule.getRegex()) if (Pattern.compile(regex).matcher(url).find()) return true;
        if (url.contains("url=http") || url.contains("v=http") || url.contains(".html")) return false;
        return SNIFFER.matcher(url).find();
    }

    public static List<String> getScript(Uri uri) {
        List<String> base = new ArrayList<>(getRule(uri).getScript());
        // ===== v5.6.7 新增：HTML 嗅探站通用 JS 探针（兜底）=====
        // 不管 RuleConfig 里有没有为 jx.xmflv.cc / qq 万能解析 / cfss 等站配置自定义脚本，
        // 这里都额外追一批「onPageFinished 后轮询式抓真实视频 URL」的 JS：
        //   1) 抓 <video>/<source> 的 currentSrc / src；
        //   2) 抓常见播放器实例（Hls/DPlayer/ArtPlayer/Xmflv/ckplayer 等）的 url/src；
        //   3) 抓 window / document 全局对象上的常见赋值变量（now/playUrl/playerData 等）；
        //   4) 兼容混淆变量里嵌了 base64(encoded URL)，先 atob 再返回。
        // WebView 超时已从 15s → 45s，给这类 JS 足够多轮轮询时间（每 200ms 一轮，最多 40 轮 = 8s）。
        base.add("javascript:(function(){var tries=0;var timer=setInterval(function(){try{tries++;if(tries>40){clearInterval(timer);return;}function ok(u){try{if(u&&u.length>24){clearInterval(timer);var e=document.createEvent('HTMLEvents');e.initEvent('videourlfound',true,true);e.url=u;document.dispatchEvent(e);}}}function atobIf(u){try{if(u.length>24&&!(u.startsWith('http')||u.startsWith('/'))){var d=atob(u);if(d&&d.length>10)return d}}catch(e){}return u}function chk(s){try{if(!s)return null;s=String(s);if(!s)return null;s=atobIf(s);if(s&&s.length>0)return s}catch(e){}return null}var V=null,vs=document.querySelectorAll('video,source,audio,iframe');for(var i=0;i<vs.length;i++){var v=vs[i];V=chk(v.currentSrc)||chk(v.src)||chk(v.getAttribute('src'))||chk(v.getAttribute('data-src'));if(V){ok(V);return}}try{if(window.player&&chk(window.player.src)){ok(window.player.src);return}}catch(e){}try{if(window.dplayer&&window.dplayer.video&&chk(window.dplayer.video.currentSrc)){ok(window.dplayer.video.currentSrc);return}}catch(e){}try{if(window.artplayer&&window.artplayer.video&&chk(window.artplayer.video.currentSrc)){ok(window.artplayer.video.currentSrc);return}}catch(e){}try{if(window.Xmflv&&(chk(window.Xmflv.url)||chk(window.Xmflv.src)||chk(window.Xmflv.now)||(window.Xmflv.player&&(chk(window.Xmflv.player.currentSrc)||chk(window.Xmflv.player.src))))){ok(chk(window.Xmflv.url)||chk(window.Xmflv.src)||chk(window.Xmflv.now)||chk(window.Xmflv.player.currentSrc)||chk(window.Xmflv.player.src));return}}catch(e){}try{if(window.ckplayer&&chk(window.ckplayer.status)){ok(window.ckplayer.status);return}}catch(e){}var keys=['now','playUrl','play_url','videoUrl','video_url','playerUrl','player_url','mediaUrl','media_url','sourceUrl','source_url','realUrl','real_url','m3u8','url','src','video','playerData'];for(var k=0;k<keys.length;k++){var g=keys[k];try{var wg=window[g];if(wg){if(typeof wg==='string'){var V2=chk(wg);if(V2){ok(V2);return}}}}catch(e){}try{var doc=document[g];if(doc&&typeof doc==='string'){var V3=chk(doc);if(V3){ok(V3);return}}}catch(e){}}if(tries%5===0){try{var scripts=document.querySelectorAll('script');for(var s=0;s<scripts.length;s++){var txt=scripts[s].innerText||scripts[s].textContent||'';if(!txt)continue;var mm=txt.match(/[\"']((?:https?:[^\"']{24,})|(?:[^\"']{8,}\\.(?:m3u8|mp4|flv|m4v|ts|mpd)[^\"']*))[\"']/g);if(mm){for(var j=0;j<mm.length;j++){var cv=mm[j].slice(1,-1);var cv2=chk(cv);if(cv2){ok(cv2);return}}}}}catch(e){}}}catch(err){}};},200);})();");
        return base;
    }

    private static Rule getRule(Uri uri) {
        if (uri.getHost() == null) return Rule.empty();
        String hosts = TextUtils.join(",", Arrays.asList(UrlUtil.host(uri), UrlUtil.host(uri.getQueryParameter("url"))));
        for (Rule rule : RuleConfig.get().getRules()) for (String host : rule.getHosts()) if (Util.containOrMatch(hosts, host)) return rule;
        return Rule.empty();
    }

    public static SpannableStringBuilder buildClickable(String text, Function<Result, ClickableSpan> factory) {
        SpannableStringBuilder span = new SpannableStringBuilder();
        Matcher matcher = CLICKER.matcher(text);
        int last = 0;
        while (matcher.find()) {
            span.append(text, last, matcher.start());
            int start = span.length();
            span.append(Trans.s2t(matcher.group(2).trim()));
            span.setSpan(factory.apply(Result.type(matcher.group(1))), start, span.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            last = matcher.end();
        }
        span.append(text, last, text.length());
        return span;
    }
}
