package com.fongmi.android.tv.ui.custom;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.ApiConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.utils.Sniffer;
import com.github.catvod.crawler.Spider;
import com.google.common.net.HttpHeaders;

import java.io.ByteArrayInputStream;
import java.util.Map;

public class CustomWebView extends WebView {

    private WebResourceResponse empty;
    private ParseCallback callback;
    private AlertDialog dialog;
    private Runnable timer;
    private String from;
    private String key;

    public static CustomWebView create(@NonNull Context context) {
        return new CustomWebView(context);
    }

    public CustomWebView(@NonNull Context context) {
        super(context);
        initSettings();
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void initSettings() {
        this.timer = () -> stop(true);
        this.empty = new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
        getSettings().setUseWideViewPort(true);
        getSettings().setDatabaseEnabled(true);
        getSettings().setDomStorageEnabled(true);
        getSettings().setJavaScriptEnabled(true);
        getSettings().setLoadWithOverviewMode(true);
        getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        setWebViewClient(webViewClient());
    }

    private void setUserAgent(Map<String, String> headers) {
        for (String key : headers.keySet()) {
            if (key.equalsIgnoreCase(HttpHeaders.USER_AGENT)) {
                getSettings().setUserAgentString(headers.get(key));
                break;
            }
        }
    }

    public CustomWebView start(String key, String from, Map<String, String> headers, String url, ParseCallback callback) {
        App.post(timer, Constant.TIMEOUT_PARSE_WEB);
        this.callback = callback;
        setUserAgent(headers);
        loadUrl(url, headers);
        this.from = from;
        this.key = key;
        return this;
    }

    private WebViewClient webViewClient() {
        return new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String host = request.getUrl().getHost();
                if (TextUtils.isEmpty(host) || ApiConfig.get().getAds().contains(host)) return empty;
                if (host.equals("challenges.cloudflare.com")) App.post(() -> showDialog());
                Map<String, String> headers = request.getRequestHeaders();
                if (isVideoFormat(headers, url)) post(headers, url);
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (dialog != null) hideDialog();
            }

            @Override
            @SuppressLint("WebViewClientOnReceivedSslError")
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        };
    }

    private boolean isVideoFormat(Map<String, String> headers, String url) {
        try {
            Site site = ApiConfig.get().getSite(key);
            Spider spider = ApiConfig.get().getSpider(site);
            if (spider.manualVideoCheck()) return spider.isVideoFormat(url);
            return Sniffer.isVideoFormat(url, headers);
        } catch (Exception ignored) {
            return Sniffer.isVideoFormat(url, headers);
        }
    }

    private void post(Map<String, String> headers, String url) {
        String cookie = CookieManager.getInstance().getCookie(url);
        if (cookie != null) headers.put(HttpHeaders.COOKIE, cookie);
        onParseSuccess(headers, url);
    }

    private void showDialog() {
        if (dialog != null) return;
        if (getParent() != null) ((ViewGroup) getParent()).removeView(this);
        dialog = new AlertDialog.Builder(App.activity()).setView(this).show();
    }

    private void hideDialog() {
        if (dialog != null) dialog.dismiss();
        dialog = null;
    }

    private void onParseSuccess(Map<String, String> headers, String url) {
        if (callback != null) callback.onParseSuccess(headers, url, from);
        App.post(() -> stop(false));
        callback = null;
    }

    private void onParseError() {
        if (callback != null) callback.onParseError();
        callback = null;
    }

    public void stop(boolean error) {
        hideDialog();
        stopLoading();
        loadUrl("about:blank");
        App.removeCallbacks(timer);
        if (error) onParseError();
        else callback = null;
    }
}
