package com.ssmhdssmhd.mxboxs.ui.custom;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.net.http.SslError;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.WebViewClient;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;

import androidx.annotation.NonNull;

import com.ssmhdssmhd.mxboxs.App;
import com.ssmhdssmhd.mxboxs.Constant;
import com.ssmhdssmhd.mxboxs.api.config.RuleConfig;
import com.ssmhdssmhd.mxboxs.api.config.VodConfig;
import com.ssmhdssmhd.mxboxs.impl.ParseCallback;
import com.ssmhdssmhd.mxboxs.setting.Setting;
import com.ssmhdssmhd.mxboxs.ui.dialog.WebDialog;
import com.ssmhdssmhd.mxboxs.utils.Sniffer;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class CustomWebView extends WebView implements DialogInterface.OnDismissListener {

    private static final String TAG = CustomWebView.class.getSimpleName();

    private static final Pattern PLAYER = Pattern.compile("player.*https?://");
    private static final String BLANK = "about:blank";
    private static final int MAX_URLS = 5;

    private final AtomicReference<ParseCallback> callbackRef = new AtomicReference<>();
    private LinkedHashSet<String> urls;
    private WebResourceResponse empty;
    private WebDialog dialog;
    private Runnable timer;
    private boolean stopped;
    private boolean detect;
    private String click;
    private String from;
    private String key;
    private String url;

    public static CustomWebView create(@NonNull Context context) {
        return new CustomWebView(context);
    }

    private CustomWebView(@NonNull Context context) {
        super(context);
        initSettings();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initSettings() {
        timer = () -> stop(true);
        urls = new LinkedHashSet<>();
        empty = new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
        WebSettings setting = getSettings();
        setting.setSupportZoom(true);
        setting.setUseWideViewPort(true);
        setting.setDatabaseEnabled(true);
        setting.setDomStorageEnabled(true);
        setting.setJavaScriptEnabled(true);
        setting.setBuiltInZoomControls(true);
        setting.setDisplayZoomControls(false);
        setting.setLoadWithOverviewMode(true);
        setting.setUserAgentString(Setting.getUa());
        setting.setMediaPlaybackRequiresUserGesture(false);
        setting.setJavaScriptCanOpenWindowsAutomatically(false);
        setting.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        setWebViewClient(webViewClient());
        // ===== v5.6.7：设置 WebChromeClient，拦截 onJsPrompt 取到探针 JS 抛出的真实视频 URL =====
        //     探针 JS 轮询到 window.Xmflv.* / <video>.currentSrc 等真实 m3u8/mp4 后，
        //     会 document.dispatchEvent 然后 prompt('MVIDURL:' + url)；
        //     这里 prompt 被 Java 层 onJsPrompt 拦截 → 立即 onParseSuccess 返回 → 命中即关 WebView。
        //     （用 prompt 而不用 addJavascriptInterface 是因为 addJavascriptInterface 有安全隐患，
        //       且 prompt 拦截是 Android WebView 最通用、跨版本最稳的 JS→Java 桥）
        setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                if (message != null && message.startsWith("MVIDURL:")) {
                    String cand = message.substring("MVIDURL:".length()).trim();
                    if (!TextUtils.isEmpty(cand) && cand.length() > 20) {
                        Map<String, String> safeHeaders = com.ssmhdssmhd.mxboxs.utils.UrlUtil.mergeDefaultHeaders(new HashMap<>(), cand);
                        result.cancel();
                        onParseSuccess(safeHeaders, cand);
                        return true;
                    }
                }
                // isVideoFormat(cand) 的二次检查（兜底）：先允许 Sniffer.isVideoFormat 判断是否真实视频 URL
                if (message != null && message.length() > 30) {
                    String cand = message.trim();
                    if (Sniffer.isVideoFormat(cand)) {
                        Map<String, String> safeHeaders = com.ssmhdssmhd.mxboxs.utils.UrlUtil.mergeDefaultHeaders(new HashMap<>(), cand);
                        result.cancel();
                        onParseSuccess(safeHeaders, cand);
                        return true;
                    }
                }
                return super.onJsPrompt(view, url, message, defaultValue, result);
            }
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                if (message != null && message.length() > 30 && Sniffer.isVideoFormat(message)) {
                    Map<String, String> safeHeaders = com.ssmhdssmhd.mxboxs.utils.UrlUtil.mergeDefaultHeaders(new HashMap<>(), message.trim());
                    result.cancel();
                    onParseSuccess(safeHeaders, message.trim());
                    return true;
                }
                return super.onJsAlert(view, url, message, result);
            }
        });
    }

    public CustomWebView start(String key, String from, Map<String, String> headers, String url, String click, ParseCallback callback, boolean detect) {
        SpiderDebug.log(TAG, "key=%s, from=%s, click=%s, url=%s, headers=%s", key, from, click, url, headers);
        App.post(timer, Constant.TIMEOUT_PARSE_WEB);
        callbackRef.set(callback);
        this.detect = detect;
        this.click = click;
        this.from = from;
        this.key = key;
        this.url = url;
        start(headers);
        return this;
    }

    private void start(Map<String, String> headers) {
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);
        checkHeader(url, headers);
        loadUrl(url, headers);
    }

    private void checkHeader(String url, Map<String, String> headers) {
        for (String key : headers.keySet()) {
            if (HttpHeaders.USER_AGENT.equalsIgnoreCase(key)) getSettings().setUserAgentString(headers.get(key));
            else if (HttpHeaders.COOKIE.equalsIgnoreCase(key)) CookieManager.getInstance().setCookie(url, headers.get(key));
        }
    }

    private WebViewClient webViewClient() {
        return new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String host = request.getUrl().getHost();
                if (TextUtils.isEmpty(host) || isAd(host)) return empty;
                Map<String, String> headers = request.getRequestHeaders();
                if (url.contains("/cdn-cgi/challenge-platform/")) post(() -> showDialog());
                // 过滤第三方解析站伪造的 127.0.0.1 本地代理 URL（端口不在 9978~9999 的都是假的），
                // 直接交给上层 ParseJob.onParseSuccess 的拦截逻辑还原真实 URL，不要把假地址当视频直链
                if (com.ssmhdssmhd.mxboxs.utils.UrlUtil.unwrapFakeLocalProxy(url).length() > 0) {
                    return super.shouldInterceptRequest(view, request);
                }
                if (detect && PLAYER.matcher(url).find() && addUrl(url)) onParseAdd(headers, url);
                else if (isVideoFormat(url)) onParseSuccess(headers, url);
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url.equals(BLANK)) return;
                // ===== v5.6.7 新增：Sniffer.getScript 里加了「轮询抓 URL 再通过 videourlfound DOM 事件抛出」的探针，
                //        先在这里 addEventListener 接住，一旦探针 JS 轮询到真实 m3u8/mp4，就立刻 onParseSuccess。
                try {
                    view.evaluateJavascript("(function(){if(window.__mxboxsSniffListenerInstalled)return;window.__mxboxsSniffListenerInstalled=true;document.addEventListener('videourlfound',function(e){try{var u=e.url||e.detail;if(u&&u.length>20){prompt('MVIDURL:'+u)}}catch(err){}});})()", null);
                } catch (Throwable ignored) {}
                evaluate(getScript(url), 0);
            }

            @Override
            @SuppressLint("WebViewClientOnReceivedSslError")
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        };
    }

    private boolean addUrl(String url) {
        if (urls.size() > MAX_URLS) urls.clear();
        return urls.add(url);
    }

    private void showDialog() {
        if (dialog != null || App.activity() == null) return;
        if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
        dialog = WebDialog.create(this).show();
        App.removeCallbacks(timer);
    }

    private void hideDialog() {
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        stop(true);
    }

    private List<String> getScript(String url) {
        List<String> script = new ArrayList<>(Sniffer.getScript(Uri.parse(url)));
        if (TextUtils.isEmpty(click) || script.contains(click)) return script;
        script.add(0, click);
        return script;
    }

    private void evaluate(List<String> script, int index) {
        if (index >= script.size()) return;
        String js = script.get(index);
        if (TextUtils.isEmpty(js)) {
            evaluate(script, index + 1);
        } else {
            evaluateJavascript(js, value -> evaluate(script, index + 1));
        }
    }

    private boolean isAd(String host) {
        for (String ad : RuleConfig.get().getAds()) if (Util.containOrMatch(host, ad)) return true;
        return false;
    }

    private boolean isVideoFormat(String url) {
        try {
            if (!detect && url.equals(this.url)) return false;
            Spider spider = VodConfig.get().getSite(key).spider();
            if (spider.manualVideoCheck()) return spider.isVideoFormat(url);
            return Sniffer.isVideoFormat(url);
        } catch (Exception ignored) {
            return Sniffer.isVideoFormat(url);
        }
    }

    private void onParseAdd(Map<String, String> headers, String url) {
        ParseCallback cb = callbackRef.get();
        if (cb == null) return;
        post(() -> CustomWebView.create(App.get()).start(key, from, headers, url, click, cb, false));
    }

    private void onParseSuccess(Map<String, String> headers, String url) {
        ParseCallback cb = callbackRef.getAndSet(null);
        if (cb != null) cb.onParseSuccess(headers, url, from);
        post(() -> stop(false));
    }

    private void onParseError() {
        ParseCallback cb = callbackRef.getAndSet(null);
        if (cb != null) cb.onParseError();
    }

    public void stop(boolean error) {
        if (stopped) return;
        stopped = true;
        hideDialog();
        stopLoading();
        loadUrl(BLANK);
        App.removeCallbacks(timer);
        if (error) onParseError();
        else callbackRef.set(null);
    }
}
